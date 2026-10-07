# CLAUDE.md — Meeting Room Panel

Android (Kotlin + Jetpack Compose) meeting-room door panel. The calendar source of truth is a Microsoft 365 room mailbox, reached **only** through a backend; the plan is in `docs/PLAN.md`.

## Rules

- **No Microsoft/Graph credentials on the device, ever.** The tablet talks only to the backend's `/api/device/...` routes with a revocable device token.
- **Microsoft Graph only**, never EWS (retired in Exchange Online 2026–27).
- Domain logic goes in `domain/` as pure Kotlin with JUnit tests. The UI only renders `PanelUiState`.
- Every user-facing string goes in `res/values/strings.xml` **and** `res/values-ar/strings.xml`. Layouts must work in RTL.
- Status colours must keep ≥ 4.5:1 contrast with white. Touch targets must be ≥ 64 dp.
- **Don't put `/*` inside a KDoc.** Kotlin nests block comments, so it silently swallows the rest of the file.
- Before pushing, from `android/`: `./gradlew testDebugUnitTest lintDebug assembleDebug verifyRoborazziDebug`. Lint must be clean. After an intentional UI change, re-record the screenshots with `recordRoborazziDebug` and look at them.
