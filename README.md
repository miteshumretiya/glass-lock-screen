# Glass Lock

An iOS‑style "liquid glass" lock screen for Google Pixel (Android 12+, built for the Pixel 10 Pro).

- Tall glass clock and date, centred like iOS
- Frosted, refracting glass cards with light‑catching rims that shift as you tilt the phone
- iOS‑style notification stack (tap to fan out, swipe sideways to clear)
- Now Playing card with album art, progress and controls
- Press‑and‑hold flashlight and camera buttons
- Status row with carrier, signal, Wi‑Fi and a battery capsule
- Swipe up to unlock with a spring animation and wallpaper blur
- Use any photo as the wallpaper, or the built‑in original one

## How it works (and its limits)

Android does not let any app replace the real system lock screen without rooting the phone.
Glass Lock sits on top of it instead: it is ready the moment your screen turns on, and when you
swipe up it hands over to the Pixel's own fingerprint/PIN prompt. Your phone stays protected by
the Pixel's normal security the whole time.

## Install

1. Download the latest `GlassLock-*.apk` from **Releases** (or the `apk` branch).
2. Open it on the phone and allow installing from that source when asked.
3. Open **Glass Lock** and complete the four setup steps:
   1. Show over other apps
   2. Notification access (if it says "Restricted setting": App info → ⋮ → Allow restricted settings)
   3. Notifications
   4. Unrestricted battery
4. Turn on **Use Glass Lock**, pick a wallpaper and tap **Preview lock screen**.

If your Always‑on display stops appearing, turn off "Get ready when the screen turns off".

## Build

Every push to `main` builds a release APK with GitHub Actions and publishes it as a release.
Add a repository secret named `SIGNING_PASSWORD` (Settings → Secrets and variables → Actions) so
builds are signed with the app's permanent key from `signing/` and install as updates.
Locally: open in Android Studio, or run `gradle :app:assembleRelease` with JDK 17 and the Android SDK.

Fonts: Inter (SIL Open Font License, see `licenses/`).
