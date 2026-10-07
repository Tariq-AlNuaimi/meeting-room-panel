# Technical research — Android panel + Microsoft 365 (Oct 2026)

> Items marked **[verify]** come from the researcher's own knowledge or partner write-ups, not a Microsoft doc read directly — confirm in a test tenant before relying on them.

## 1. Microsoft 365 setup

**Room mailbox** — `New-Mailbox -Name "Board Room" -Room` (or M365 admin centre → Rooms & equipment). No user licence needed; that only applies if it signs in to a certified Teams Rooms device.

**Calendar processing** — defaults replace the subject with the organiser's name and strip the body. Fix (applies to new bookings only):

```powershell
Set-CalendarProcessing -Identity boardroom@contoso.com -AutomateProcessing AutoAccept `
  -DeleteSubject $false -AddOrganizerToSubject $false -DeleteComments $false `
  -RemovePrivateProperty $false -ProcessExternalMeetingMessages $true
```

`RemovePrivateProperty $false` keeps the private flag so the panel can mask private meetings. `DeleteComments $false` keeps the Teams join link in the room's copy.

**Room Finder / Teams discovery** — room list (`New-DistributionGroup -RoomList`) plus place metadata (`Set-Place -City -Building -Capacity -Floor`). Room Finder hides rooms with empty City/Building/Capacity. Sources: [configure Room Finder](https://learn.microsoft.com/en-us/outlook/troubleshoot/calendaring/configure-room-finder-rooms-workspaces), [Practical365](https://practical365.com/room-mailbox-meeting-organizer/).

## 2. Graph operations (`{room}` = room UPN)

| Need                           | Call                                                                                                                                      | Notes                                                                                                |
| ------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| Today's agenda                 | `GET /users/{room}/calendarView?startDateTime&endDateTime&$select=subject,start,end,organizer,attendees,sensitivity,onlineMeeting,showAs` | Header `Prefer: outlook.timezone="Arabian Standard Time"`; expands recurrences                       |
| Book now                       | `POST /users/{room}/events`                                                                                                               | Room is organiser. **Check free/busy first** — a direct write skips conflict checking                |
| Extend / end early (ad-hoc)    | `PATCH /users/{room}/events/{id}` new `end`                                                                                               | Clean only for room-organised events                                                                 |
| Release someone else's meeting | `POST /users/{room}/events/{id}/decline` `sendResponse:true`                                                                              | Organiser is notified **[verify]**                                                                   |
| Free/busy                      | `POST /users/{room}/calendar/getSchedule`                                                                                                 | App permission `Calendars.ReadBasic`                                                                 |
| Suggest times                  | `findMeetingTimes`                                                                                                                        | **No application permission** — use getSchedule instead                                              |
| Incremental sync               | `GET /users/{room}/calendarView/delta`                                                                                                    | Store the deltaLink                                                                                  |
| Push changes                   | `POST /subscriptions` on `/users/{room}/events`                                                                                           | Max lifetime ~7 days → renew via cron; handle `lifecycleNotificationUrl` (`missed` → delta catch-up) |

Throttling: 10,000 requests / 10 min and 4 concurrent per app per mailbox; honour `429 Retry-After`. One backend cache per room fanned out to tablets stays far under this.

**Sync pattern:** webhook marks room dirty → backend runs delta → pushes to tablet (SSE/WebSocket, or tablet polls every 15–30 s). Keep a 60–120 s safety poll.

## 3. Auth for an unattended kiosk

**Chosen: app-only credentials in a backend, scoped to room mailboxes with Exchange RBAC for Applications** (replaces legacy Application Access Policies — [MS Learn](https://learn.microsoft.com/Exchange/permissions-exo/application-rbac)).

```powershell
New-ServicePrincipal -AppId <appId> -ObjectId <spObjectId>
New-ManagementScope -Name Rooms -RecipientRestrictionFilter "RecipientTypeDetails -eq 'RoomMailbox'"
New-ManagementRoleAssignment -App <spObjectId> -Role "Application Calendars.ReadWrite" -CustomResourceScope Rooms
Test-ServicePrincipalAuthorization -Identity <spObjectId> -Resource boardroom@contoso.com
```

**[verify]** Entra and Exchange RBAC grants are additive: do **not** also admin-consent tenant-wide `Calendars.ReadWrite` in Entra, or the scope is meaningless. `Place.Read.All` (application) is granted in Entra. Prefer a certificate or Vercel OIDC workload identity federation over a client secret.

**The tablet never holds Microsoft credentials.** An APK is trivially decompiled, so a stolen tablet would give whoever has it read/write access to every room calendar. Instead:

- The tablet shows a pairing code.
- An admin binds it to a room in the portal.
- The backend issues a revocable device token, stored hashed.
- The tablet may only call `/api/device/*` for its own room.

Rejected alternative: device-code sign-in as the room account. It needs the room account enabled with a password and an MFA/CA exception, puts the refresh token on the tablet, requires a re-sign-in after ~90 days, and offers no webhooks.

## 4. Teams

- The Teams panels app can't run on non-certified Android.
- The join link is in `event.onlineMeeting.joinUrl` → show as QR.
- Creating Teams meetings app-only from an unlicensed room is unreliable (`onlineMeeting: null`). **"Book now" creates in-person bookings only.** Teams meetings come from Outlook/Teams.

## 5. Android kiosk

- **Lock Task Mode** needs the app to be Device Owner:
  - provision with `adb shell dpm set-device-owner com.rihal.roompanel/.AdminReceiver` on a factory-reset device (no accounts), or
  - via the QR provisioning flow (tap the welcome screen 6×).
  - Then call `setLockTaskPackages()` and `startLockTask()`. [Docs](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode)
- **Boot:** a `HOME` launcher intent-filter plus a `BOOT_COMPLETED` receiver.
- **Screen:**
  - `FLAG_KEEP_SCREEN_ON`
  - dim outside working hours via `screenBrightness`, or `DevicePolicyManager.setSystemSetting(SCREEN_BRIGHTNESS)`.
- **Updates without the Play Store:** as Device Owner, the backend serves a signed APK and the app installs it silently with `PackageInstaller`. Managed Google Play private apps also work.
- **Hardware:**
  - Consumer tablet (e.g. Samsung Galaxy Tab A9+): cheap, but no PoE and no LED.
  - Room panels: ProDVX APPC-10SLB / R23 (free LED API), Qbic TD-1070, IAdea XDS-1078, Philips 10BDL4551T. These have PoE, LED bars and VESA/glass mounts.
  - Put LEDs behind a per-vendor adapter interface.

## 6. Stack decision

- **Native Kotlin + Jetpack Compose** was chosen by the owner.
  - It gives full control of Device Owner, lock task, `PackageInstaller` and vendor LED SDKs.
  - The research's alternative was a Next.js page in Fully Kiosk Browser (faster to build, weaker device control). It was considered and not taken.
- **Backend:** a fork of the Rihal Next.js template (Prisma + Postgres on Neon, Vercel). It holds the Graph credentials, runs the webhook/cron, and serves the admin portal.

## 7. Google Workspace (later)

Resource calendars via the Admin SDK `resources.calendars`, plus Calendar API `events.list/insert/patch`, and `events.watch` push. Use a service account with domain-wide delegation. All of this sits behind the same `CalendarProvider` interface.

Sources:

- https://learn.microsoft.com/en-us/graph/api/calendar-getschedule
- https://learn.microsoft.com/en-us/graph/api/event-delta
- https://learn.microsoft.com/graph/api/resources/subscription
- https://learn.microsoft.com/en-us/graph/throttling-limits
- https://learn.microsoft.com/en-us/microsoftteams/devices/teams-panels-certified-hardware
- https://developers.google.com/android/management/policies/dedicated-devices
- https://www.prodvx.com/blog/simplified-development-with-prodvx-api
