# MONSTER Touch / Touch Mapper

Android accessibility-based button mapper for external hardware keys.

## Current Version

- Version name: `A-0.1.5`
- Version code: `13`
- minSdk: `26`
- targetSdk: `35`

## Variants

This project builds two APK variants from the same source.

- `monsterDebug`
  - Package: `com.example.touchmapper`
  - App name: `MONSTER Touch`
  - Includes the MONSTER logo and tagline.
- `plainDebug`
  - Package: `com.example.touchmapper.plain`
  - App name: `Touch Mapper`
  - Removes the MONSTER logo and tagline.

Both variants should be built whenever the version is updated.

## Current A Behavior

- Fixed 4 hardware button slots.
- Each button performs a single tap at its saved screen position.
- Button names can be edited.
- Four setting profiles are available:
  - 배달의민족
  - 쿠팡이츠
  - 요기요
  - 직접 입력
- Each profile stores button names, key bindings, and tap positions separately.
- Button 4 long press for 5 seconds toggles touch lock.
- Saved position markers can be shown on screen.
- A position marker can be held for 1 second to enter move mode, with vibration feedback.
- Position markers are hidden while the app's own settings screen is open.

## Build

```powershell
.\gradlew.bat assembleDebug
```

Expected APK outputs:

```text
app/build/outputs/apk/monster/debug/app-monster-debug.apk
app/build/outputs/apk/plain/debug/app-plain-debug.apk
```

Delivery copies:

```text
outputs/MONSTER-Touch-A-0.1.5-monster-debug.apk
outputs/Touch-Mapper-A-0.1.5-plain-debug.apk
```

## Install and Try

1. Install one APK on an Android phone.
2. Open the app.
3. Tap `접근성`.
4. Enable the app's accessibility service.
5. Return to the app.
6. Use `키 입력` to bind each physical button.
7. Use `위치 설정` to save tap coordinates.

## Notes

- This app uses Android `AccessibilityService.dispatchGesture`.
- The user must manually enable accessibility permission.
- Some protected apps, games, or OEM Android builds may block or alter accessibility gestures.
- Debug APKs are for testing. Release distribution needs release signing and policy review.
