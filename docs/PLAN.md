# Meeting Room Panel — Build Plan

> Research: [competitors](research/competitors.md) · [technical](research/technical.md)

## Decisions so far

| Decision                 | Choice                                                                   | Why                                                                               |
| ------------------------ | ------------------------------------------------------------------------ | --------------------------------------------------------------------------------- |
| Tablet app               | **Native Android — Kotlin + Jetpack Compose**                            | Full kiosk control (Device Owner, lock task, silent updates, LED SDKs)            |
| Calendar source of truth | **Microsoft 365 room mailbox** (Google Workspace later)                  | Outlook & Teams book the room natively; the tablet reads/writes the same calendar |
| Calendar API             | **Microsoft Graph only**                                                 | EWS is being switched off (Oct 2026 → Apr 2027)                                   |
| Credentials              | **Backend holds them; tablet gets a revocable device token**             | An APK is decompilable — no Microsoft secrets on the device                       |
| Backend + admin portal   | **Fork of the Rihal Next.js template** (Prisma/Postgres on Neon, Vercel) | Already have it: auth, audit, zod, rate limiting                                  |
| Scale                    | **One room now**, data model allows more                                 | Keep it simple                                                                    |
| Repo                     | `tariq-alnuaimi/meeting-room-panel` (private)                            | Android app + docs; backend as a template fork                                    |

## Architecture

```
 Outlook / Teams users ──book──▶ Exchange Online room mailbox (boardroom@…)
                                        ▲      │ change notifications (webhook)
                       Graph (app-only, │      ▼
                       RBAC-scoped)     │   Backend  (Next.js on Vercel + Neon Postgres)
                                        └── • CalendarProvider: MicrosoftGraph (Google later)
                                            • /api/device/*  (device-token auth, zod, rate limit)
                                            • /api/graph/notify + /lifecycle, cron renew + delta
                                            • Admin portal: rooms, pair/revoke devices, settings, audit
                                                       ▲ HTTPS (device token)
                                                       │ poll 15–30 s / SSE
                                            Android tablet app (Kotlin/Compose, Device Owner kiosk)
```

## Tablet screens (v1)

1. **Status** (home): big colour state (green Available / red Busy / amber Starting soon), room name, current meeting (subject or "Private", organiser, time left), next meeting, and a scrollable day timeline. Background colour fills the screen so it is readable from down the corridor.
2. **Book now**: 15 / 30 / 60 min / until next meeting. Duration is capped at the next booking; optional title ("Ad-hoc meeting"). Confirmation in one tap.
3. **Meeting detail**: attendees list (configurable: off / count only / names), Teams join QR when `onlineMeeting.joinUrl` exists.
4. **Actions on current meeting**: Check in, Extend (+15/+30 if free), End early.
5. **Pairing**: first-run screen showing a pairing code + backend URL.
6. **Offline / stale**: banner "Last updated hh:mm — reconnecting", last known agenda stays visible, booking disabled.

Design: tablet landscape at 10–11", large touch targets (≥ 64 dp), English + Arabic (RTL), dark idle theme, dim outside working hours.

## Backend API (device-facing)

| Route                                  | Purpose                                                                             |
| -------------------------------------- | ----------------------------------------------------------------------------------- |
| `POST /api/device/pair/start`          | Tablet requests a pairing code (unauthenticated, rate-limited, short-lived)         |
| `POST /api/device/pair/poll`           | Tablet polls; receives device token once admin approves                             |
| `GET  /api/device/agenda`              | Today's events for the device's room (cached, normalised, privacy-masked)           |
| `POST /api/device/book`                | Book now `{durationMin, title?}` — re-checks free/busy server-side                  |
| `POST /api/device/events/{id}/extend`  | `{minutes}` — only if free and room-organised                                       |
| `POST /api/device/events/{id}/end`     | Room-organised → shorten end to now; otherwise decline                              |
| `POST /api/device/events/{id}/checkin` | Records check-in (stops auto-release)                                               |
| `GET  /api/device/config`              | Room name, timezone, working hours, privacy & check-in settings, latest APK version |

Every route goes through a device-token wrapper, uses zod validation, is rate-limited, and is audited. A device can only touch its own room.

## Data model (Prisma sketch)

- `Room` — name, email (mailbox UPN), timezone, workingHours, provider (`MICROSOFT`/`GOOGLE`), settings JSON (attendee display mode, check-in window, auto-release minutes, max ad-hoc duration)
- `Device` — roomId, label, tokenHash, lastSeenAt, appVersion, revokedAt
- `PairingCode` — codeHash, expiresAt, deviceFingerprint, approvedDeviceId
- `CalendarCache` — roomId, eventsJSON, deltaLink, syncedAt
- `GraphSubscription` — roomId, subscriptionId, expiresAt, clientStateHash
- `CheckIn` — roomId, eventId, at
- `BookingAudit` — via the template's audit log (action, roomId, deviceId, eventId)

## Android app structure

```
android/app/src/main/java/com/rihal/roompanel/
  ui/          status/, book/, detail/, pairing/, theme/ (Compose, RTL-ready)
  data/        ApiClient (Retrofit/Ktor + kotlinx.serialization), AgendaRepository, Room DB cache
  domain/      RoomState calculator (free/busy/soon), BookingRules
  kiosk/       AdminReceiver, LockTaskController, BootReceiver, BrightnessScheduler, Updater
  led/         LedController interface + NoopLed, ProDvxLed, QbicLed
  di/          Hilt modules
```

Stack:

- Kotlin, Compose Material 3, Hilt, Ktor client, Room (offline cache), WorkManager (background refresh, update checks).
- Device token stored in EncryptedSharedPreferences / Android Keystore.
- Min SDK 28 (Android 9, for lock task features).

## Phases

| #   | Phase                                     | Deliverable                                                                                                | Done when                                                                                      |
| --- | ----------------------------------------- | ---------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| 0   | **M365 setup** (you, ~1 hr with an admin) | Room mailbox, calendar processing fix, room list + place metadata, Entra app, RBAC scope to room mailboxes | `Test-ServicePrincipalAuthorization` passes for the room and fails for a normal user's mailbox |
| 1   | **Backend MVP**                           | Template fork, Graph provider, agenda + book/extend/end routes, pairing, admin page to pair/revoke, audit  | Book in Outlook → visible via `/api/device/agenda`; book via API → visible in Outlook          |
| 2   | **Android MVP**                           | Status, Book now, Extend/End, attendees, pairing, offline cache, polling                                   | Running on the real tablet, round-trips with Outlook within 30 s                               |
| 3   | **Kiosk hardening**                       | Device Owner + lock task, boot start, keep-on + dimming schedule, silent self-update from backend          | Tablet reboots straight into the app; staff cannot exit it                                     |
| 4   | **Check-in & auto-release**               | Check-in window, no-show release (decline + organiser notified), configurable per room                     | A meeting with no check-in is released after N min and disappears in Outlook                   |
| 5   | **Polish**                                | Arabic/RTL, privacy modes, Teams join QR, webhooks + delta (replace pure polling), LED adapter             | —                                                                                              |
| 6   | **Later**                                 | Google Workspace provider, more rooms, utilisation analytics, find-another-free-room                       | —                                                                                              |

## Hardware recommendation (one room)

- **Budget:** Samsung Galaxy Tab A9+ / similar 11" plus a lockable wall mount and a USB-C cable run to a socket. No LED.
- **Proper:** a PoE room panel with an LED bar and an open LED API, such as the ProDVX APPC-10SLB or Qbic TD-1070. One cable gives power and network, and the LED is visible down the corridor.

Choose before Phase 3, because the LED adapter and Device Owner provisioning depend on the device.

## Risks / verify early (Phase 0–1 spikes)

1. **Ending someone else's meeting.** `decline` vs PATCH behaviour on the room's copy; confirm the organiser is notified and Outlook updates.
2. **RBAC for Applications scope.** Confirm a tenant-wide Entra grant isn't also present.
3. **Private meetings.** Confirm `sensitivity` survives with `RemovePrivateProperty $false`.
4. **Conflicts.** Concurrent book-now from the tablet and Outlook: server-side free/busy re-check plus the room's AutoAccept conflict rejection.
5. **Device Owner provisioning** on the chosen tablet. Some OEM ROMs need a factory reset first.

## Next steps

1. ~~Create the repo~~ — done. Android scaffold (Phase 2 shell with demo data) is in `android/`.
2. Fork the template for the backend via `/new-project` (e.g. `meeting-room-backend`).
3. Do Phase 0 yourself (you are the tenant admin): follow [m365-setup.md](m365-setup.md).
