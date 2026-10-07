# Meeting Room Panel

An Android tablet app mounted outside a meeting room. It shows whether the room is free, what's on now and next, and who's attending. It also lets people book, extend, end or check in with a tap.

The room's calendar lives in **Microsoft 365** (an Exchange room mailbox), so bookings made in Outlook or Teams show up on the panel and panel bookings show up in Outlook and Teams. Google Workspace is planned.

- **Plan:** [docs/PLAN.md](docs/PLAN.md)
- **Research:** [competitors](docs/research/competitors.md) · [Microsoft 365 + Android kiosk](docs/research/technical.md)

| Free | Starting soon | In use | Arabic (RTL) |
|---|---|---|---|
| ![](android/app/src/test/screenshots/free.png) | ![](android/app/src/test/screenshots/starting_soon.png) | ![](android/app/src/test/screenshots/busy.png) | ![](android/app/src/test/screenshots/busy_ar.png) |

## Status

**Phase 2 shell.** The panel UI runs on demo data (`DemoAgendaRepository`). The backend and the Microsoft 365 connection come next; see the plan's phases.

| Works now | Not yet |
|---|---|
| Free / starting-soon / busy states, day agenda, attendees, private-meeting masking | Backend client and device pairing |
| Book now, extend, end early, check in (in memory) | Kiosk lock (Device Owner + lock task), boot start, dimming |
| English + Arabic (RTL), keep-screen-on, full-screen | Auto-release of no-shows, Teams join QR, LED bar |

## Build

You need JDK 17+ and the Android SDK (platform 37). Android Studio provides both.

```bash
cd android
./gradlew testDebugUnitTest      # domain unit tests + screenshot tests
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew lintDebug
./gradlew recordRoborazziDebug   # re-record screenshots after an intentional UI change
./gradlew verifyRoborazziDebug   # fail if the UI changed unexpectedly
```

Install on a tablet with USB debugging on: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Layout

```
android/app/src/main/java/com/rihal/roompanel/
  domain/   Meeting, RoomStatus calculator, BookingRules   (pure Kotlin, unit-tested)
  data/     AgendaRepository interface + DemoAgendaRepository
  ui/       PanelViewModel, PanelScreen (Compose), Theme
docs/       plan and research
```

## Security model

The tablet **never** holds Microsoft credentials. A backend holds the Graph credentials, scoped by Exchange RBAC to room mailboxes only. The tablet pairs with a code and gets a revocable device token. App data is excluded from cloud backup and device transfer.
