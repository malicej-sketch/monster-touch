# MONSTER Touch Working Rules

These rules are mandatory for every change in this repository. Read this file before
investigating, editing, building, installing, or publishing anything.

## 1. Product Intent

- MONSTER Touch maps a deliberately selected external controller to four saved screen taps.
- Unknown controller models must be supported through observation and learning, not through a
  hardcoded model allowlist.
- Compatibility must never make the phone's normal touchscreen harder to use.
- Actual code, Android device data, and captured events are the source of truth. Do not infer a
  controller's input type from its product name.

## 2. Controller Selection Is Mandatory

- The app must not learn or execute mappings until the user selects a controller.
- Only the selected controller may learn mappings or trigger actions.
- Do not add an "all devices" operating mode.
- Button bindings, learned signal signatures, button names, profiles, saved tap positions, input
  mode, and trap configuration must remain scoped per controller.
- Selecting a previously configured controller must restore its complete saved configuration.

## 3. Input Strategy Rules

Classify the selected controller from Android's real `InputDevice` sources before choosing a
blocking strategy.

- Mouse, relative mouse, touchpad, trackball, joystick, or rotary motion on Android 14+:
  use `AccessibilityServiceInfo.setMotionEventSources()` with the narrowest practical source set.
- Key-only controllers: use `AccessibilityService.onKeyEvent()` and consume both down and up
  events when mapped or intentionally suppressed.
- External touchscreen-style clickers must use a device trap fallback. Never add
  `SOURCE_TOUCHSCREEN` to `setMotionEventSources()` because it suppresses the built-in touchscreen
  before the service can distinguish physical devices.
- A mouse-class controller such as LP910 must not use the trap fallback.
- A trap from a previous controller must never survive a controller change.
- Remember that `setMotionEventSources()` filters by source, not by physical device. Do not claim
  device-level isolation that Android cannot provide.

## 4. Disconnect Safety

When the selected controller disconnects or powers off, immediately stop all MONSTER Touch input
behavior:

- remove trap overlays;
- set motion event sources to `0`;
- release touch lock;
- hide position markers;
- close controller learning and diagnostic overlays;
- stop mapping execution.

Keep saved mappings. If the same controller reconnects, restore only the strategy appropriate for
its current Android input sources.

## 5. Touchscreen Safety

- Never leave a full-screen overlay active for a disconnected, changed, or mouse-class controller.
- Except while the user has explicitly enabled touch lock, selecting or using a controller must
  never make the entire touchscreen unavailable.
- Controller traps must never cover the full screen. Use bounded trap zones that leave normal
  one-tap touchscreen operation available outside those zones.
- Before installing a controller-input change, verify that the accessibility motion-source mask
  excludes `SOURCE_TOUCHSCREEN` and that controller trap windows are not full-screen.
- Normal phone touches must work with one tap when controller functionality is inactive.
- Do not introduce global touchscreen interception without explicit user approval and a tested
  pass-through design.
- Treat double-tap-to-register symptoms as a blocking regression.

## 6. Evidence Before Editing

For controller issues, inspect available evidence before changing code:

1. Selected controller name, descriptor, vendor ID, and product ID.
2. Android `InputDevice` sources and EventHub classes.
3. Whether events arrive as `KeyEvent`, `MotionEvent`, or both.
4. Current accessibility-service binding and requested motion sources.
5. Existing mapping mode, signal signatures, and trap state.

State clearly which facts were observed and which conclusions are inferences.

## 7. Input Learning Is A Product Contract

- Motion-button learning must collect exactly five completed presses of the same physical button.
- Keep every distinct motion signature observed across those five presses, deduplicate exact
  repeats, and save the remaining signatures together as one binding for that controller and slot.
- Show both `sample N/5` and the unique-signature count throughout motion learning.
- Do not finish a motion-capable controller's normal binding from an incidental key event. Once
  motion is observed, the five-sample motion path has priority and must run to completion.
- A mixed controller may expose mouse motion and consumer-control keys as sibling logical devices;
  treat them as one selected physical controller while keeping normal motion learning and explicit
  long/key learning separate.
- Key-only controller learning completes from one matching key down/up pair. This single-key rule
  must never replace or weaken the five-sample rule for motion-capable controllers.
- Canceling learning must discard only the in-progress samples and preserve the previously saved
  binding until a replacement learning session completes successfully.
- If the accessibility-service learning overlay cannot start, fail visibly and preserve every
  saved binding. Never fall back to Activity-level `dispatchKeyEvent()` single-key learning.
- Every controller-input change must verify both paths: five-sample grouped motion learning and
  one-pair key-only learning.

## 8. Version And Build Rules

- A behavior change increments both `versionCode` and `versionName`.
- Build both `monsterDebug` and `plainDebug` for every behavior or version change.
- Keep the branded and plain variants behaviorally identical.
- Update README version and output names with the build version.
- Copy verified APKs to `outputs/` using versioned names and refresh the stable aliases.
- Do not uninstall the installed app or clear its data without explicit user approval.

## 9. Required Verification

Run the checks that apply to the change and report anything that could not be tested.

- `assembleDebug` succeeds for both variants.
- `git diff --check` succeeds.
- No trap exists for a selected mouse-class controller.
- The selected controller triggers mappings; an unselected controller does not.
- Disconnecting the selected controller removes overlays and restores one-tap phone touch.
- Reconnecting the same controller preserves its saved mappings.
- Switching controller types removes the old strategy before enabling the new one.
- Accessibility-service restart and app update do not leave stale overlays.
- Motion learning waits for five completed presses and restores all distinct saved signatures.
- Incidental keys from a motion-capable controller do not prematurely finish normal learning.
- An unavailable accessibility service cannot silently create or replace a binding.

## 10. Work Protocol

Before editing:

- read this file and the current code involved;
- identify which rules the change touches;
- inspect the live device when it is available instead of guessing.

After editing:

- review the change against every affected rule;
- run the required build and behavioral checks;
- include a short "Rules check" in the final report;
- explicitly name any device behavior that still requires physical testing.

Do not commit, push, publish, uninstall, or clear user data unless the user asks for that action.
