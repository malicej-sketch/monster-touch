# MONSTER Touch

Android accessibility-based button mapper for external hardware keys.

## Version

- Current version: `0.5.0`
- Version code: `6`

## Current behavior

- Default 4 hardware button slots, expandable up to 12.
- Each physical button supports three trigger types:
  - Once click
  - Double click
  - Long click, 3 seconds or longer
- Each trigger chooses an action category:
  - Touch
  - Function
  - Launch app
- Function actions include zoom, volume, media controls, camera shutter, call controls, and touch lock toggle.
- Coordinate-based actions can show saved positions on screen.
- The setup overlay can be dragged and has a scrollable button/trigger list.
- Touch lock blocks screen touches while still allowing configured hardware button actions.

## Install and try

1. Install the APK on an Android phone.
2. Open MONSTER Touch.
3. Tap `접근성`.
4. Enable `MONSTER Touch Controller`.
5. Return to the app.
6. Use `키 지정` to bind each physical button.
7. Use `위치 설정` to save coordinates for coordinate-based actions.
8. Use `행동 변경` to cycle through actions for each trigger type.

## Delivery APK

The latest shared debug APK is copied to:

`outputs/MONSTER-Touch-debug.apk`

Versioned APK snapshots should use:

`outputs/MONSTER-Touch-v{versionName}-debug.apk`

## Notes

- This app uses Android `AccessibilityService.dispatchGesture`.
- The user must manually enable accessibility permission.
- Some protected apps, games, or OEM Android builds may block or alter accessibility gestures.
- OS or manufacturer policy changes may require additional maintenance.
