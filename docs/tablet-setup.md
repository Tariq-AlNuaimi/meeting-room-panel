# Tablet setup (kiosk mode)

This turns an ordinary Android tablet (here a **Miulesight**) into a locked room panel. When it's done:
- the panel app is the home screen and starts on boot;
- people can't leave the app, pull down notifications or reach Settings;
- the screen stays on while charging and dims outside working hours.

It uses Android's built-in **Device Owner** mode, so no MDM subscription is needed. You need a computer with [platform-tools (`adb`)](https://developer.android.com/tools/releases/platform-tools) and a USB cable. Allow about 20 minutes.

## 1. Check the tablet

**Settings → About tablet → Android version** must be **9 or newer**. The app's `minSdk` is 28.

Write down the version. If it's older, tell me: the app can't install, and that tablet can't run lock-task kiosk mode properly.

## 2. Factory reset and skip accounts

Android only allows a Device Owner on a device with **no accounts**.

1. Back up anything on the tablet, then **Settings → System → Reset options → Erase all data**.
2. In the setup wizard, connect to Wi-Fi, but **skip the Google sign-in** and any vendor account. If the wizard insists, choose "Set up offline" or "Skip".
3. Set **Date & time → Time zone: Muscat (GMT+4)** with automatic time on. The panel's clock and dimming depend on it.

## 3. Turn on USB debugging

1. **Settings → About tablet** → tap **Build number** 7 times.
2. **Settings → System → Developer options → USB debugging: on.**
3. Connect the USB cable and accept the "Allow USB debugging?" prompt on the tablet.

```bash
adb devices          # should list the tablet as "device", not "unauthorized"
```

## 4. Install and make it Device Owner

```bash
cd meeting-room-panel/android
./gradlew assembleDebug        # or download app-debug.apk from the latest CI run
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell dpm set-device-owner com.rihal.roompanel/.kiosk.PanelDeviceAdminReceiver
```

Expected output: `Success: Device owner set to package ComponentInfo{com.rihal.roompanel/...}`.

Open the app once (`adb shell monkey -p com.rihal.roompanel 1`, or tap the icon). It pins itself, becomes the home screen, and hides the status bar.

**Pair it with its room:**

1. The tablet shows **Pair this tablet** and an 8-character code.
2. In the admin portal (`https://meeting-room-backend-cyan.vercel.app`), go to **Tablets → Pair a tablet**.
3. Enter the code, choose the room, and name the tablet.
4. Within a few seconds the tablet switches to the room's schedule.
5. If you later revoke the tablet in the portal, it returns to the pairing screen by itself.

**Check it:**
- Press Home and swipe up: you should stay in the panel.
- Reboot with `adb reboot`: the tablet should come straight back to the panel without a lock screen.

## 5. Mount and power

- **Keep it charging.** The screen only stays on while plugged in.
- Consumer tablet batteries kept at 100% for months can swell. Use the tablet's battery-protection option if it has one (often "Protect battery" or "Charge to 85%"). Otherwise put the charger on a smart plug that turns off overnight. The panel dims outside working hours anyway.
- Run a short USB-C cable through the wall mount. Avoid a cable that can be yanked out from the door side.

## Updating the app (no USB after the first install)

Paired tablets check the backend about 2 minutes after start and then every 6 hours. If you have published a newer version, the tablet downloads it, verifies its SHA-256 and installs it silently, then restarts the panel. This only works when the tablet is Device Owner.

**One-time: create the release signing key.** Keep it in your password manager and **never** commit it. Every APK the tablet ever installs must be signed with this key, so if you lose it, every tablet has to be factory-reset and set up again.

```bash
keytool -genkeypair -v -keystore ~/room-panel.jks -alias room-panel -keyalg RSA -keysize 3072 -validity 10000
```

Install the **first** APK on each tablet as a release build signed with this key, using the same `adb install` / `dpm` steps as above but with `app-release.apk`. A debug build can't be upgraded by a release build.

**Each release:**

1. Raise `versionCode` (and `versionName`) in `android/app/build.gradle.kts`.
2. Build and sign the release APK:
   ```bash
   cd android
   ./gradlew assembleRelease -PreleaseKeystore=$HOME/room-panel.jks -PreleaseKeyAlias=room-panel \
     -PreleaseStorePassword=... -PreleaseKeyPassword=...
   sha256sum app/build/outputs/apk/release/app-release.apk
   ```
3. Upload `app-release.apk` anywhere the tablets can download it over **https**. For example, attach it to a GitHub release of a **public** repo, or put it in any static file host.
4. In Vercel → `meeting-room-backend` → Settings → Environment Variables, set:
   - `APP_UPDATE_VERSION_CODE` to the new `versionCode`
   - `APP_UPDATE_URL` to the https link
   - `APP_UPDATE_SHA256` to the hash from step 2

   Then redeploy.
5. Tablets pick it up within 6 hours. To force it sooner, reboot a tablet; it checks 2 minutes after start.

## Leaving kiosk mode

**Debug builds** (what you install today): from the computer, run

```bash
adb shell am broadcast -n com.rihal.roompanel/.kiosk.DebugKioskExitReceiver -a com.rihal.roompanel.EXIT_KIOSK
# also drop Device Owner so the app can be uninstalled:
adb shell am broadcast -n com.rihal.roompanel/.kiosk.DebugKioskExitReceiver -a com.rihal.roompanel.EXIT_KIOSK --ez relinquish true
```

USB debugging must still be on. Leave it on during the pilot, and keep the tablet physically secured.

**Release builds** have no exit receiver. The way out is a factory reset (most tablets: hold **Power + Volume Up** at boot → *Wipe data*). A remote "unlock" command from the admin page is planned with the backend.

## Troubleshooting

| Message from `dpm set-device-owner` | Fix |
|---|---|
| `Not allowed to set the device owner because there are already some accounts on the device` | Remove every account (**Settings → Accounts**), or factory reset and skip sign-in again |
| `...because there are already several users on the device` | **Settings → System → Multiple users**: delete the guest/other users |
| `Unknown admin` / `ComponentInfo... not found` | The app isn't installed, or the component name is mistyped: it is `com.rihal.roompanel/.kiosk.PanelDeviceAdminReceiver` |
| Works, but the vendor's launcher still appears after reboot | Open the panel once after `set-device-owner`. It registers itself as the home screen on first resume. |

Some budget tablets ship heavily customised Android with extra "security" apps that block `dpm`. If that happens, send me the exact error and the Android version.
