# MONSTER Touch / Touch Mapper

Android accessibility-based button mapper for external hardware keys.

## Current Version

- Version name: `A-0.1.67`
- Version code: `75`
- minSdk: `26`
- targetSdk: `36`

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

- Fixed 4 hardware button slots with per-controller input bindings.
- A controller must be selected before mappings can learn or run; other devices are ignored.
- Newly selected controllers can learn keyboard, gamepad, mouse, touchpad, trackball,
  joystick, and rotary input paths without a model-specific allowlist.
- The controller picker shows Android's actual input-source classification. After selection,
  the main screen explains the active processing strategy and touchscreen-capture warning.
- Profiles, button names, input bindings, tap positions, learned signals, input mode, and trap
  configuration are stored per controller and restored when that controller is selected again.
- Key-event learning completes after one matching key down/up pair; multi-sample learning remains
  limited to motion input.
- Mixed motion/key controllers defer incidental keys during normal learning. Motion takes priority
  as soon as it is observed, and learning completes only after five presses with all distinct
  motion signatures grouped into one binding.
- Learning never falls back to Activity-level single-key capture. If the accessibility service is
  unavailable, the app preserves existing bindings and shows a direct accessibility-settings path.
- External touchscreen-style clickers use bounded trap zones. `SOURCE_TOUCHSCREEN` is never
  requested through accessibility motion capture, and controller traps never cover the full
  screen, so selecting a controller cannot disable the entire touchscreen.
- Touchscreen-style controller trap zones are rebuilt from each saved motion signature's start
  position, allowing every learned button to be captured without a full-screen overlay.
- Disconnecting the selected controller immediately stops motion capture, trap overlays,
  touch lock, position markers, and controller setup overlays. Reconnecting restores the
  appropriate input strategy without deleting that controller's saved mappings.
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
- Power source, charging speed, and battery temperature are shown as three separate cells
  on the main screen and in a floating overlay, each colored by its own state. The power
  source reads wireless, wired, or discharging; every cable type shares one label because
  they share the same speed boundaries.
  The main screen refreshes every 2 seconds while visible; the overlay every 3 seconds.
- Charging current is read as microamps per the Android spec. A device is treated as
  reporting milliamps only when it is charging yet reports an implausibly small value.
  The sign is never forced to match the plug state, so a phone that drains while mounted
  on a charger reports that honestly.
- Current is presented as charging speed in four tiers — slow, normal, fast, super fast —
  plus discharging for any negative value. The cell's caption carries the speed word and
  its color the tier.
- Wired and wireless use different tier boundaries because wireless loses up to 30% in
  transfer. Wired splits at 500 / 1,500 / 3,500 mA, wireless at 300 / 900 / 2,000 mA,
  measured at the battery terminal.
- A charger that is connected but has stopped charging (battery protection limit or heat)
  is shown as connected, not as absent.

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
outputs/MONSTER-Touch-A-0.1.49-monster-debug.apk
outputs/Touch-Mapper-A-0.1.49-plain-debug.apk
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
