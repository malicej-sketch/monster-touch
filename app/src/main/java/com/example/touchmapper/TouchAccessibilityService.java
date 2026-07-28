package com.example.touchmapper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.GestureResultCallback;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class TouchAccessibilityService extends AccessibilityService {
    private static final String PROBE_TAG = "TouchMapperProbe";
    private static final long LOCK_LONG_CLICK_MS = 2000L;
    private static final long MOVE_MARKER_LONG_PRESS_MS = 1000L;
    private static final int MAX_VIBRATION_AMPLITUDE = 255;
    private static final long REMOTE_GESTURE_COOLDOWN_MS = 220L;
    private static final long REMOTE_VOLUME_SUPPRESS_MS = 400L;
    private static final long VOLUME_KEY_BURST_GAP_MS = 700L;
    private static final long POST_CAPTURE_COOLDOWN_MS = 900L;
    private static final long INPUT_CAPTURE_KEY_GRACE_MS = 750L;
    private static final int POSITION_TOGGLE_SLOT = MappingStore.MARKER_TOGGLE_SLOT;
    private static final int INPUT_CAPTURE_SAMPLE_TARGET = 5;
    private static final int CONTROLLER_MOTION_SOURCES = InputDevice.SOURCE_MOUSE
            | InputDevice.SOURCE_MOUSE_RELATIVE
            | InputDevice.SOURCE_TOUCHPAD
            | InputDevice.SOURCE_TRACKBALL
            | InputDevice.SOURCE_JOYSTICK
            | InputDevice.SOURCE_ROTARY_ENCODER;
    private static TouchAccessibilityService instance;
    private static boolean configurationActive;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private InputManager inputManager;
    private String activeControllerDescriptor = "";
    private boolean selectedControllerConnected;
    private View pickerOverlay;
    private View setupOverlay;
    private WindowManager.LayoutParams setupOverlayParams;
    private View inputCaptureOverlay;
    private int inputCaptureSlot = -1;
    private boolean inputCaptureLongMode;
    private int inputCaptureKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private int inputCapturePendingKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private boolean inputCaptureMotionObserved;
    private boolean inputCaptureMotionActive;
    private float inputCaptureDownX;
    private float inputCaptureDownY;
    private String inputCaptureMotionSignature = "";
    private final MotionBurst inputCaptureBurst = new MotionBurst();
    private int inputCaptureSampleAttempts;
    private int inputCaptureSampleTarget = INPUT_CAPTURE_SAMPLE_TARGET;
    private int inputCaptureSampleKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private int inputCaptureSampleDirection = MappingStore.TRIGGER_UNKNOWN;
    private final List<String> inputCaptureSampleSignatures = new ArrayList<>();
    private TextView inputCaptureStatus;
    private final Runnable finishPendingInputCaptureKeyRunnable = () -> {
        if (inputCaptureSlot < 0 || inputCaptureMotionObserved
                || inputCapturePendingKeyCode == KeyEvent.KEYCODE_UNKNOWN) {
            return;
        }
        int keyCode = inputCapturePendingKeyCode;
        inputCapturePendingKeyCode = KeyEvent.KEYCODE_UNKNOWN;
        finishInputCaptureWithKey(keyCode);
    };
    private View keyDiagnosticOverlay;
    private WindowManager.LayoutParams keyDiagnosticOverlayParams;
    private TextView keyDiagnosticLog;
    private final List<String> keyDiagnosticLines = new ArrayList<>();
    private View motionDiagnosticOverlay;
    private TextView motionDiagnosticLog;
    private final List<String> motionDiagnosticLines = new ArrayList<>();
    private long lastMotionDiagnosticMoveMs;
    private View controllerAnalyzerOverlay;
    private TextView controllerAnalyzerLog;
    private TextView controllerAnalyzerStatus;
    private Button controllerAnalyzerStartButton;
    private final List<String> controllerAnalyzerLines = new ArrayList<>();
    private final StringBuilder controllerAnalyzerReport = new StringBuilder();
    private long controllerAnalyzerStartMs;
    private long lastControllerAnalyzerMoveMs;
    private boolean controllerAnalyzerCapturing;
    private int controllerAnalyzerKeyEvents;
    private int controllerAnalyzerMotionEvents;
    private View controllerDiagnosticOverlay;
    private TextView controllerDiagnosticLog;
    private final List<RectF> controllerDiagnosticZones = new ArrayList<>();
    private boolean controllerDiagnosticMotionActive;
    private float controllerDiagnosticDownX;
    private float controllerDiagnosticDownY;
    private int controllerDiagnosticKeyCount;
    private int controllerDiagnosticMotionCount;
    private View adbProbeOverlay;
    private TextView adbProbeLog;
    private final List<String> adbProbeLines = new ArrayList<>();
    private final StringBuilder adbProbeReport = new StringBuilder();
    private final StringBuilder adbProbeButtonReport = new StringBuilder();
    private long adbProbeStartMs;
    private long lastAdbProbeMoveMs;
    private int adbProbeButtonCount;
    private int adbProbeCurrentButton;
    private final MotionBurst adbProbeBurst = new MotionBurst();
    private final List<View> positionMarkers = new ArrayList<>();
    private View touchLockOverlay;
    private final List<View> remoteTrapOverlays = new ArrayList<>();
    private boolean remoteTrapVisible;
    private boolean touchLocked;
    private boolean positionsVisible;
    private boolean remoteGestureActive;
    private float remoteDownX;
    private float remoteDownY;
    private long lastRemoteGestureMs;
    private String recentMotionSignature = "";
    private long recentMotionSignatureMs;
    private final MotionBurst remoteGestureBurst = new MotionBurst();
    private long remoteVolumeSuppressUntilMs;
    private int lastAcceptedVolumeKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private long lastAcceptedVolumeKeyMs;
    /** 학습 중 관측한 실제 DOWN 좌표. 트랩 존을 여기서 직접 만든다. */
    private final List<float[]> inputCaptureAnchors = new ArrayList<>();
    private View noSavedPointOverlay;
    private long inputCaptureCooldownUntilMs;
    private int heldKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private int heldSlot = -1;
    private int heldLongSlot = -1;
    private boolean longClickTriggered;
    private final Runnable lockLongClickRunnable = new Runnable() {
        @Override
        public void run() {
            if (heldLongSlot < 0) {
                return;
            }
            longClickTriggered = true;
            executeLongSlot(heldLongSlot);
        }
    };
    private final InputManager.InputDeviceListener inputDeviceListener = new InputManager.InputDeviceListener() {
        @Override
        public void onInputDeviceAdded(int deviceId) {
            refreshSelectedControllerState();
        }

        @Override
        public void onInputDeviceRemoved(int deviceId) {
            refreshSelectedControllerState();
        }

        @Override
        public void onInputDeviceChanged(int deviceId) {
            refreshSelectedControllerState();
        }
    };

    private static final class MotionBurst {
        boolean active;
        boolean hasAnchor;
        float anchorX;
        float anchorY;
        float endX;
        float endY;
        long startTime;
        long endTime;
        int moveCount;

        void reset() {
            active = false;
            hasAnchor = false;
            anchorX = 0f;
            anchorY = 0f;
            endX = 0f;
            endY = 0f;
            startTime = 0L;
            endTime = 0L;
            moveCount = 0;
        }

        boolean record(MotionEvent event) {
            int action = event.getActionMasked();
            if (!active && !isStartAction(event)) {
                return false;
            }

            if (!active) {
                active = true;
                hasAnchor = true;
                anchorX = event.getRawX();
                anchorY = event.getRawY();
                endX = anchorX;
                endY = anchorY;
                startTime = event.getEventTime();
                endTime = event.getEventTime();
                return false;
            }

            if (!hasAnchor && isStartAction(event)) {
                hasAnchor = true;
                anchorX = event.getRawX();
                anchorY = event.getRawY();
                if (startTime == 0L) {
                    startTime = event.getEventTime();
                }
            }

            if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_HOVER_MOVE) {
                moveCount++;
            }

            endX = event.getRawX();
            endY = event.getRawY();
            endTime = event.getEventTime();
            return action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_BUTTON_RELEASE;
        }

        private static boolean isStartAction(MotionEvent event) {
            int action = event.getActionMasked();
            return action == MotionEvent.ACTION_DOWN
                    || action == MotionEvent.ACTION_BUTTON_PRESS
                    || (action == MotionEvent.ACTION_MOVE && event.getButtonState() != 0);
        }
    }

    static boolean isRunning() {
        return instance != null;
    }

    static boolean tapSavedPoint(int slot) {
        TouchAccessibilityService service = instance;
        if (service == null) {
            return false;
        }

        MappingStore.Mapping mapping = MappingStore.get(service, slot);
        if (!mapping.canRun()) {
            return false;
        }

        service.tap(mapping.x, mapping.y);
        return true;
    }

    static boolean togglePositionOverlay() {
        TouchAccessibilityService service = instance;
        if (service == null) {
            return false;
        }

        if (service.positionsVisible) {
            service.hidePositionOverlay();
            service.positionsVisible = false;
            return true;
        }

        if (!service.hasAnySavedPoint()) {
            // 앱에서 누른 경우다. 안내와 선택지는 MainActivity가 다이얼로그로 보여준다.
            return true;
        }

        service.positionsVisible = true;
        if (!configurationActive) {
            service.showPositionOverlay();
        }
        return true;
    }

    static boolean arePositionsVisible() {
        TouchAccessibilityService service = instance;
        return service != null && service.positionsVisible;
    }

    static void refreshPositionOverlay() {
        TouchAccessibilityService service = instance;
        if (service != null && service.positionsVisible && !configurationActive) {
            service.showPositionOverlay();
        }
    }

    static boolean isTouchLocked() {
        TouchAccessibilityService service = instance;
        return service != null && service.touchLocked;
    }

    static boolean toggleRemoteTrapOverlay() {
        TouchAccessibilityService service = instance;
        if (service == null) {
            return false;
        }

        if (!service.remoteTrapVisible
                && MappingStore.inputDeviceMode(service) == MappingStore.DEVICE_MODE_KEYBOARD) {
            Toast.makeText(service, "Keyboard controllers do not need trap mode.", Toast.LENGTH_SHORT).show();
            return true;
        }

        if (Build.VERSION.SDK_INT >= 34 && !service.selectedDeviceUsesTouchscreen()) {
            service.remoteTrapVisible = false;
            service.hideRemoteTrapOverlay();
            service.updateMotionCapture();
            Toast.makeText(service, "Android 14+ uses motion capture instead of trap.", Toast.LENGTH_SHORT).show();
            return true;
        }

        service.remoteTrapVisible = !service.remoteTrapVisible;
        if (service.remoteTrapVisible) {
            service.showRemoteTrapOverlay();
        } else {
            service.hideRemoteTrapOverlay();
        }
        service.updateMotionCapture();
        return true;
    }

    static boolean isRemoteTrapVisible() {
        TouchAccessibilityService service = instance;
        return service != null && service.remoteTrapVisible;
    }

    static void setConfigurationActive(boolean active) {
        configurationActive = active;
        TouchAccessibilityService service = instance;
        if (service == null) {
            return;
        }
        if (active) {
            service.hidePositionOverlay();
            service.hideNoSavedPointPanel();
        } else if (service.positionsVisible) {
            service.showPositionOverlay();
        }
        service.refreshRemoteTrapOverlay();
    }

    static boolean showSetupOverlay() {
        TouchAccessibilityService service = instance;
        if (service == null) {
            return false;
        }

        service.showSetupPanel();
        return true;
    }

    static boolean showInputCaptureOverlay(int slot) {
        return showInputCaptureOverlay(slot, false);
    }

    static boolean showInputCaptureOverlay(int slot, boolean longMode) {
        TouchAccessibilityService service = instance;
        if (service == null) {
            return false;
        }

        service.showInputCapturePanel(slot, longMode);
        return true;
    }

    static boolean showKeyDiagnosticOverlay() {
        TouchAccessibilityService service = instance;
        if (service == null) {
            return false;
        }

        service.showKeyDiagnosticPanel();
        return true;
    }

    static boolean showMotionDiagnosticOverlay() {
        TouchAccessibilityService service = instance;
        if (service == null) {
            return false;
        }

        service.showMotionDiagnosticPanel();
        return true;
    }

    static boolean showControllerDiagnosticOverlay() {
        TouchAccessibilityService service = instance;
        if (service == null || !MappingStore.hasSelectedInputDevice(service)) {
            return false;
        }

        service.showControllerDiagnosticPanel();
        return true;
    }

    static boolean toggleControllerAnalyzerOverlay() {
        TouchAccessibilityService service = instance;
        if (service == null) {
            return false;
        }

        if (service.controllerAnalyzerOverlay == null) {
            service.showControllerAnalyzerPanel();
        } else {
            service.hideControllerAnalyzerPanel();
        }
        return true;
    }

    static boolean toggleAdbProbeOverlay() {
        TouchAccessibilityService service = instance;
        if (service == null) {
            return false;
        }

        if (service.adbProbeOverlay == null) {
            service.showAdbProbePanel();
        } else {
            service.hideAdbProbePanel();
        }
        return true;
    }

    private void showAdbProbePanel() {
        showAdbProbePanel(4);
    }

    static boolean startAdbProbeOverlay(int buttonCount) {
        TouchAccessibilityService service = instance;
        if (service == null) {
            return false;
        }

        service.showAdbProbePanel(Math.max(1, buttonCount));
        return true;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        InputDevice inputDevice = event.getDevice();
        String descriptor = inputDevice == null ? "" : inputDevice.getDescriptor();

        if (adbProbeOverlay != null) {
            return handleAdbProbeKeyEvent(event, inputDevice);
        }

        if (controllerAnalyzerOverlay != null) {
            return handleControllerAnalyzerKeyEvent(event, inputDevice);
        }

        if (controllerDiagnosticOverlay != null) {
            return handleControllerDiagnosticKeyEvent(event, inputDevice, descriptor);
        }

        if (inputCaptureSlot >= 0) {
            return handleInputCaptureKeyEvent(event);
        }

        if (keyDiagnosticOverlay != null) {
            return handleKeyDiagnosticEvent(event, inputDevice, descriptor);
        }

        if (configurationActive) {
            // 설정 화면이 떠 있어도 선택된 컨트롤러의 키는 시스템으로 넘기지 않는다.
            // 넘기면 동작에 등록해 둔 볼륨 키가 실제 볼륨을 조절해버린다.
            return MappingStore.hasSelectedInputDevice(this) && acceptsInputDevice(inputDevice);
        }

        if (inPostCaptureCooldown(event.getEventTime())) {
            return true;
        }

        if (isVolumeKey(event.getKeyCode()) && event.getEventTime() <= remoteVolumeSuppressUntilMs) {
            return true;
        }

        if (!acceptsInputDevice(inputDevice)) {
            return false;
        }

        if (shouldSuppressRepeatedVolumeKey(event)) {
            return true;
        }

        String keySignature = recentMotionSignatureForKey(event.getEventTime());
        MappingStore.Mapping longMapping = MappingStore.findByLongKeySignature(this, event.getKeyCode(), keySignature);
        if (longMapping == null) {
            longMapping = MappingStore.findByLongKeyCode(this, event.getKeyCode());
        }

        MappingStore.Mapping mapping = MappingStore.findByKeySignature(this, event.getKeyCode(), keySignature);
        if (mapping == null) {
            mapping = MappingStore.findByKeyCode(this, event.getKeyCode());
        }

        if (mapping == null) {
            // 이 키는 동작 전용으로 등록된 별개 신호다. 누르는 즉시 실행한다.
            if (longMapping != null) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    executeLongSlot(longMapping.slot);
                }
                return true;
            }
            return MappingStore.hasSelectedInputDevice(this);
        }

        // 같은 키에 탭과 동작이 함께 걸려 있으면 누른 시간으로 가른다.
        return handleMappedKey(event, mapping.slot, longMapping);
    }

    @Override
    public void onServiceConnected() {
        instance = this;
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
        if (inputManager != null) {
            inputManager.registerInputDeviceListener(inputDeviceListener, mainHandler);
        }
        refreshSelectedControllerState();
    }

    static void refreshMotionCapture() {
        TouchAccessibilityService service = instance;
        if (service != null) {
            service.refreshSelectedControllerState();
        }
    }

    private void updateMotionCapture() {
        if (Build.VERSION.SDK_INT >= 34) {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                boolean enable = selectedControllerConnected && (adbProbeOverlay != null
                        || controllerAnalyzerOverlay != null
                        || controllerDiagnosticOverlay != null
                        || motionDiagnosticOverlay != null
                        || inputCaptureSlot >= 0
                        || MappingStore.hasSelectedInputDevice(this));
                info.setMotionEventSources(enable ? CONTROLLER_MOTION_SOURCES : 0);
                setServiceInfo(info);
            }
        }
    }

    @Override
    public void onDestroy() {
        if (inputManager != null) {
            inputManager.unregisterInputDeviceListener(inputDeviceListener);
            inputManager = null;
        }
        hidePointPicker();
        hideSetupPanel();
        hideInputCapturePanel();
        hideKeyDiagnosticPanel();
        hideMotionDiagnosticPanel();
        hideControllerAnalyzerPanel();
        hideControllerDiagnosticPanel();
        hideAdbProbePanel();
        hideNoSavedPointPanel();
        hidePositionOverlay();
        hideTouchLockOverlay();
        hideRemoteTrapOverlay();
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    private void refreshSelectedControllerState() {
        String selectedDescriptor = MappingStore.selectedInputDeviceDescriptor(this);
        boolean deviceChanged = !selectedDescriptor.equals(activeControllerDescriptor);
        boolean wasConnected = selectedControllerConnected;
        InputDevice selectedDevice = findSelectedInputDevice();

        if (deviceChanged) {
            stopControllerFeatures();
            activeControllerDescriptor = selectedDescriptor;
        }

        selectedControllerConnected = !selectedDescriptor.isEmpty() && selectedDevice != null;
        if (!selectedControllerConnected) {
            if (wasConnected && !deviceChanged) {
                stopControllerFeatures();
                Toast.makeText(this, "컨트롤러 연결이 끊겨 기능을 중지했습니다.", Toast.LENGTH_SHORT).show();
            }
            updateMotionCapture();
            MainActivity.refreshIfVisible();
            return;
        }

        updateMotionCapture();
        boolean touchscreenController = (selectedDevice.getSources() & InputDevice.SOURCE_TOUCHSCREEN)
                == InputDevice.SOURCE_TOUCHSCREEN;
        if (touchscreenController && isSelectedDeviceMotionMode()) {
            rebuildSelectedControllerTrapZones();
            MappingStore.saveTrapMode(this, MappingStore.TRAP_MODE_AUTO);
            remoteTrapVisible = true;
            showRemoteTrapOverlay();
        } else if (!remoteTrapOverlays.isEmpty()) {
            remoteTrapVisible = false;
            hideRemoteTrapOverlay();
        }
        if (!wasConnected && !deviceChanged) {
            Toast.makeText(this, "컨트롤러가 다시 연결되었습니다.", Toast.LENGTH_SHORT).show();
        }
        MainActivity.refreshIfVisible();
    }

    private InputDevice findSelectedInputDevice() {
        if (!MappingStore.hasSelectedInputDevice(this)) {
            return null;
        }
        for (int deviceId : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (device != null && MappingStore.acceptsInputDevice(this, device.getDescriptor(), device.getName(),
                    device.getVendorId(), device.getProductId())) {
                return device;
            }
        }
        return null;
    }

    private void stopControllerFeatures() {
        selectedControllerConnected = false;
        remoteTrapVisible = false;
        touchLocked = false;
        positionsVisible = false;
        mainHandler.removeCallbacks(lockLongClickRunnable);
        hidePointPicker();
        hideSetupPanel();
        hideInputCapturePanel();
        hideKeyDiagnosticPanel();
        hideMotionDiagnosticPanel();
        hideControllerAnalyzerPanel();
        hideControllerDiagnosticPanel();
        hideAdbProbePanel();
        hideNoSavedPointPanel();
        hidePositionOverlay();
        hideTouchLockOverlay();
        hideRemoteTrapOverlay();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onMotionEvent(MotionEvent event) {
        if (adbProbeOverlay != null) {
            handleAdbProbeMotionEvent(event, "SVC");
            return;
        }
        if (controllerAnalyzerOverlay != null) {
            handleControllerAnalyzerMotionEvent(event, "SVC");
            return;
        }
        if (controllerDiagnosticOverlay != null) {
            handleControllerDiagnosticMotionEvent(event);
            return;
        }
        if (inputCaptureSlot >= 0) {
            handleInputCaptureMotionEvent(event);
            return;
        }
        if (motionDiagnosticOverlay != null) {
            handleMotionDiagnosticEvent(event, "SVC");
            return;
        }
        handleRemoteMouseGesture(event);
    }

    @Override
    public void onInterrupt() {
    }

    private boolean handleMappedKey(KeyEvent event, int slot, MappingStore.Mapping longMapping) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                heldKeyCode = event.getKeyCode();
                heldSlot = slot;
                heldLongSlot = holdActionSlot(slot, longMapping);
                longClickTriggered = false;
                if (heldLongSlot >= 0) {
                    mainHandler.postDelayed(lockLongClickRunnable, LOCK_LONG_CLICK_MS);
                }
            }
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_UP && heldKeyCode == event.getKeyCode()) {
            mainHandler.removeCallbacks(lockLongClickRunnable);
            int releasedSlot = heldSlot;
            heldKeyCode = KeyEvent.KEYCODE_UNKNOWN;
            heldSlot = -1;
            heldLongSlot = -1;

            if (!longClickTriggered) {
                executeSlot(releasedSlot);
            }
            longClickTriggered = false;
            return true;
        }

        return true;
    }

    /**
     * 이 키를 계속 누르고 있을 때 발동할 동작 슬롯. 없으면 -1.
     *
     * 명시적으로 등록된 롱 바인딩이 우선한다. 등록된 것이 없으면 버튼 1/4에 한해
     * 기존 암묵 동작(위치 표시 / 화면 잠금)을 유지한다. 키만 내보내는 HID 컨트롤러는
     * 남는 신호가 없어 이 암묵 경로에 의존한다.
     */
    private int holdActionSlot(int slot, MappingStore.Mapping longMapping) {
        if (longMapping != null) {
            return longMapping.slot;
        }
        if (slot == POSITION_TOGGLE_SLOT || slot == MappingStore.LOCK_SLOT) {
            return slot;
        }
        return -1;
    }

    private boolean handleInputCaptureKeyEvent(KeyEvent event) {
        if (!acceptsInputDevice(event.getDevice())) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            inputCaptureKeyCode = event.getKeyCode();
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (inputCaptureKeyCode == KeyEvent.KEYCODE_UNKNOWN
                    || inputCaptureKeyCode != event.getKeyCode()) {
                return true;
            }
            int keyCode = inputCaptureKeyCode;
            inputCaptureKeyCode = KeyEvent.KEYCODE_UNKNOWN;
            if (inputCaptureLongMode || !selectedDeviceSupportsMotionLearning()) {
                finishInputCaptureWithKey(keyCode);
                return true;
            }
            if (inputCaptureMotionObserved) {
                return true;
            }
            inputCapturePendingKeyCode = keyCode;
            mainHandler.removeCallbacks(finishPendingInputCaptureKeyRunnable);
            mainHandler.postDelayed(finishPendingInputCaptureKeyRunnable, INPUT_CAPTURE_KEY_GRACE_MS);
            updateInputCaptureStatus();
            return true;
        }

        return true;
    }

    private boolean handleInputCaptureMotionEvent(MotionEvent event) {
        if (inputCaptureSlot < 0) {
            return false;
        }
        if (!acceptsInputDevice(event.getDevice())) {
            return false;
        }

        boolean finished = inputCaptureBurst.record(event);
        if (!inputCaptureBurst.active && !finished) {
            return true;
        }
        if (!inputCaptureMotionObserved) {
            inputCaptureMotionObserved = true;
            inputCapturePendingKeyCode = KeyEvent.KEYCODE_UNKNOWN;
            mainHandler.removeCallbacks(finishPendingInputCaptureKeyRunnable);
            updateInputCaptureStatus();
        }
        if (!finished) {
            return true;
        }

        inputCaptureMotionSignature = motionBurstSignature(inputCaptureBurst);
        int direction = motionBurstDirection(inputCaptureBurst);
        int learnedIndex = findBestLearnedRemoteButton(inputCaptureMotionSignature);
        if (learnedIndex >= 0) {
            direction = MappingStore.learnedRemoteButtonDirection(this, learnedIndex);
            inputCaptureMotionSignature = MappingStore.learnedRemoteButtonSignature(this, learnedIndex);
        }
        if (inputCaptureBurst.hasAnchor) {
            inputCaptureAnchors.add(new float[]{inputCaptureBurst.anchorX, inputCaptureBurst.anchorY});
        }
        inputCaptureBurst.reset();
        recordInputCaptureSample(inputCaptureMotionSignature, direction, KeyEvent.KEYCODE_UNKNOWN);
        return true;
    }

    private void finishInputCaptureWithKey(int keyCode) {
        finishInputCaptureWithKey(keyCode, "");
    }

    private void finishInputCaptureWithKey(int keyCode, String signature) {
        if (inputCaptureSlot < 0) {
            return;
        }

        int slot = inputCaptureSlot;
        if (inputCaptureLongMode) {
            MappingStore.saveLongKeyCode(this, slot, keyCode, signature);
        } else {
            MappingStore.saveKeyCode(this, slot, keyCode, signature);
        }
        startPostCaptureCooldown();
        Toast.makeText(this, "버튼 " + (slot + 1) + " 입력 저장: "
                + MappingStore.keyDisplayLabel(keyCode), Toast.LENGTH_SHORT).show();
        hideInputCapturePanel();
        MainActivity.refreshIfVisible();
    }

    /**
     * 방금 등록한 신호의 잔여 반복을 흘려보낸다.
     *
     * 볼륨키는 한 번 눌러도 0.3~0.5초 간격으로 계속 들어온다. 등록은 첫 신호에서 끝나므로
     * 나머지가 일반 경로로 새어나가 방금 등록한 동작을 그대로 실행해버린다. 토글이 켜졌다
     * 꺼졌다 하고 그 토스트가 줄줄이 쌓여 뒤늦게 하나씩 뜬다.
     */
    private void startPostCaptureCooldown() {
        inputCaptureCooldownUntilMs = SystemClock.uptimeMillis() + POST_CAPTURE_COOLDOWN_MS;
    }

    private boolean inPostCaptureCooldown(long eventTime) {
        return eventTime < inputCaptureCooldownUntilMs;
    }

    private void finishInputCaptureWithMouseGesture(int direction) {
        finishInputCaptureWithMouseGesture(direction, inputCaptureMotionSignature);
    }

    private void finishInputCaptureWithMouseGesture(int direction, String signature) {
        if (inputCaptureSlot < 0) {
            return;
        }

        int slot = inputCaptureSlot;
        if (inputCaptureLongMode) {
            MappingStore.saveLongMouseGesture(this, slot, direction, signature);
        } else {
            MappingStore.saveMouseGesture(this, slot, direction, signature);
        }
        MappingStore.saveInputDeviceMode(this, MappingStore.DEVICE_MODE_MOTION);
        saveObservedAnchorRange(slot);
        if (selectedDeviceUsesTouchscreen()) {
            rebuildSelectedControllerTrapZones();
        }
        MappingStore.saveTrapMode(this, MappingStore.TRAP_MODE_AUTO);
        boolean needsTrapOverlay = Build.VERSION.SDK_INT < 34
                || selectedDeviceUsesTouchscreen();
        if (needsTrapOverlay && !remoteTrapVisible) {
            remoteTrapVisible = true;
        }
        if (needsTrapOverlay && remoteTrapVisible) {
            hideRemoteTrapOverlay();
            showRemoteTrapOverlay();
        }
        startPostCaptureCooldown();
        Toast.makeText(this, "버튼 " + (slot + 1) + " 입력 저장: 마우스 "
                + MappingStore.mouseDirectionDisplayLabel(direction), Toast.LENGTH_SHORT).show();
        hideInputCapturePanel();
        MainActivity.refreshIfVisible();
    }

    private void recordInputCaptureSample(String signature, int direction, int keyCode) {
        if (inputCaptureSlot < 0) {
            return;
        }

        inputCaptureSampleAttempts++;
        if (keyCode != KeyEvent.KEYCODE_UNKNOWN && inputCaptureSampleKeyCode == KeyEvent.KEYCODE_UNKNOWN) {
            inputCaptureSampleKeyCode = keyCode;
        }
        if (direction != MappingStore.TRIGGER_UNKNOWN) {
            inputCaptureSampleDirection = direction;
        }

        String cleanSignature = signature == null ? "" : signature.trim();
        if (!cleanSignature.isEmpty() && !inputCaptureSampleSignatures.contains(cleanSignature)) {
            inputCaptureSampleSignatures.add(cleanSignature);
        }

        updateInputCaptureStatus();

        if (inputCaptureSampleAttempts < inputCaptureSampleTarget) {
            return;
        }

        String groupedSignature = joinedInputCaptureSignatures();
        if (inputCaptureSampleKeyCode != KeyEvent.KEYCODE_UNKNOWN) {
            finishInputCaptureWithKey(inputCaptureSampleKeyCode, groupedSignature);
            return;
        }

        int finalDirection = inputCaptureSampleDirection;
        if (finalDirection == MappingStore.TRIGGER_UNKNOWN) {
            finalDirection = direction;
        }
        finishInputCaptureWithMouseGesture(finalDirection, groupedSignature);
    }

    private String joinedInputCaptureSignatures() {
        StringBuilder builder = new StringBuilder();
        for (String signature : inputCaptureSampleSignatures) {
            if (signature == null || signature.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(signature.trim());
        }
        return builder.toString();
    }

    private void updateInputCaptureStatus() {
        if (inputCaptureStatus == null) {
            return;
        }
        int unique = inputCaptureSampleSignatures.isEmpty() && inputCaptureSampleKeyCode != KeyEvent.KEYCODE_UNKNOWN
                ? 1
                : inputCaptureSampleSignatures.size();
        if (inputCapturePendingKeyCode != KeyEvent.KEYCODE_UNKNOWN && !inputCaptureMotionObserved) {
            inputCaptureStatus.setText("키 신호 감지 · 모션 신호를 잠시 확인하고 있습니다.");
        } else if (inputCaptureLongMode || !selectedDeviceSupportsMotionLearning()) {
            inputCaptureStatus.setText("버튼을 한 번 눌렀다가 떼세요.");
        } else if (inputCaptureSampleAttempts == 0) {
            inputCaptureStatus.setText("같은 버튼을 5번 눌러주세요.\n모션 샘플 0/5 · 고유 신호 0");
        } else {
            inputCaptureStatus.setText("모션 샘플 " + inputCaptureSampleAttempts + "/" + inputCaptureSampleTarget
                    + " · 고유 신호 " + unique);
        }
    }

    private boolean selectedDeviceSupportsMotionLearning() {
        InputDevice device = findSelectedInputDevice();
        if (device == null) {
            return false;
        }
        int sources = device.getSources();
        int motionSources = CONTROLLER_MOTION_SOURCES | InputDevice.SOURCE_TOUCHSCREEN;
        return (sources & motionSources) != 0;
    }

    private boolean handleKeyDiagnosticEvent(KeyEvent event, InputDevice inputDevice, String descriptor) {
        boolean selectedMode = MappingStore.hasSelectedInputDevice(this);
        boolean acceptedDevice = acceptsInputDevice(inputDevice);
        MappingStore.Mapping mapping = MappingStore.findByKeyCode(this, event.getKeyCode());
        boolean blocked = selectedMode && acceptedDevice;

        if (event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_UP) {
            addKeyDiagnosticLine(event, inputDevice, acceptedDevice, mapping, blocked);
        }

        return blocked;
    }

    private void addKeyDiagnosticLine(KeyEvent event, InputDevice inputDevice, boolean acceptedDevice,
                                      MappingStore.Mapping mapping, boolean blocked) {
        String action = event.getAction() == KeyEvent.ACTION_DOWN ? "DOWN" : "UP";
        String deviceName = inputDevice == null ? "알 수 없는 장치" : inputDevice.getName();
        String keyName = KeyEvent.keyCodeToString(event.getKeyCode());
        String selectedText = acceptedDevice ? "선택 장치" : "다른 장치";
        String mappedText = mapping == null ? "매핑 없음" : "버튼 " + (mapping.slot + 1) + " · " + mapping.name;
        String blockedText = blocked ? "차단됨" : "통과";
        String line = action + " · " + keyName + " (" + event.getKeyCode() + ")"
                + "\n" + deviceName
                + "\n" + selectedText + " · " + mappedText + " · " + blockedText;

        keyDiagnosticLines.add(0, line);
        while (keyDiagnosticLines.size() > 6) {
            keyDiagnosticLines.remove(keyDiagnosticLines.size() - 1);
        }
        updateKeyDiagnosticLog();
    }

    private void updateKeyDiagnosticLog() {
        if (keyDiagnosticLog == null) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        if (keyDiagnosticLines.isEmpty()) {
            builder.append("UGREEN LP910 버튼을 눌러보세요.\n")
                    .append("입력 장치를 선택해두면 해당 장치 키는 진단 중 차단됩니다.");
        } else {
            for (int index = 0; index < keyDiagnosticLines.size(); index++) {
                if (index > 0) {
                    builder.append("\n\n");
                }
                builder.append(keyDiagnosticLines.get(index));
            }
        }
        keyDiagnosticLog.setText(builder.toString());
    }

    private boolean handleMotionDiagnosticEvent(MotionEvent event, String route) {
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE
                || event.getActionMasked() == MotionEvent.ACTION_HOVER_MOVE) {
            long now = event.getEventTime();
            if (now - lastMotionDiagnosticMoveMs < 180L) {
                return true;
            }
            lastMotionDiagnosticMoveMs = now;
        }

        addMotionDiagnosticLine(event, route);
        return true;
    }

    private void addMotionDiagnosticLine(MotionEvent event, String route) {
        InputDevice inputDevice = event.getDevice();
        String deviceName = inputDevice == null ? "알 수 없는 장치" : inputDevice.getName();
        String action = MotionEvent.actionToString(event.getActionMasked());
        String source = motionSourceLabel(event.getSource());
        String tool = event.getPointerCount() > 0 ? toolTypeLabel(event.getToolType(0)) : "NONE";
        String line = route + " · " + action
                + "\n" + deviceName
                + "\nx " + Math.round(event.getRawX()) + ", y " + Math.round(event.getRawY())
                + " · " + source + " · " + tool
                + "\nscroll v " + formatAxis(event.getAxisValue(MotionEvent.AXIS_VSCROLL))
                + ", h " + formatAxis(event.getAxisValue(MotionEvent.AXIS_HSCROLL));

        motionDiagnosticLines.add(0, line);
        while (motionDiagnosticLines.size() > 7) {
            motionDiagnosticLines.remove(motionDiagnosticLines.size() - 1);
        }
        updateMotionDiagnosticLog();
    }

    private void updateMotionDiagnosticLog() {
        if (motionDiagnosticLog == null) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        if (motionDiagnosticLines.isEmpty()) {
            builder.append("UGREEN LP910 버튼을 눌러보세요.\n")
                    .append("스와이프/마우스/스크롤 입력이면 여기에 표시됩니다.\n")
                    .append("진단 중 화면 입력은 차단됩니다.");
        } else {
            for (int index = 0; index < motionDiagnosticLines.size(); index++) {
                if (index > 0) {
                    builder.append("\n\n");
                }
                builder.append(motionDiagnosticLines.get(index));
            }
        }
        motionDiagnosticLog.setText(builder.toString());
    }

    private boolean handleControllerAnalyzerKeyEvent(KeyEvent event, InputDevice inputDevice) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            hideControllerAnalyzerPanel();
            return true;
        }

        if (!controllerAnalyzerCapturing) {
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_UP) {
            controllerAnalyzerKeyEvents++;
            addControllerAnalyzerLine(formatAnalyzerKeyEvent(event, inputDevice));
        }
        return true;
    }

    private boolean handleControllerAnalyzerMotionEvent(MotionEvent event, String route) {
        if (!controllerAnalyzerCapturing) {
            return true;
        }

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_HOVER_MOVE) {
            long now = event.getEventTime();
            if (now - lastControllerAnalyzerMoveMs < 120L) {
                return true;
            }
            lastControllerAnalyzerMoveMs = now;
        }

        controllerAnalyzerMotionEvents++;
        addControllerAnalyzerLine(formatAnalyzerMotionEvent(event, route));
        return true;
    }

    private boolean handleAdbProbeKeyEvent(KeyEvent event, InputDevice inputDevice) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            hideAdbProbePanel();
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_UP) {
            addAdbProbeLine("B" + adbProbeCurrentButton + " " + formatProbeKeyEvent(event, inputDevice));
            MappingStore.saveInputDeviceMode(this, MappingStore.DEVICE_MODE_KEYBOARD);
            MainActivity.refreshIfVisible();
        }
        return true;
    }

    private boolean handleAdbProbeMotionEvent(MotionEvent event, String route) {
        adbProbeBurst.record(event);
        MappingStore.saveInputDeviceMode(this, MappingStore.DEVICE_MODE_MOTION);
        MainActivity.refreshIfVisible();
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_HOVER_MOVE) {
            long now = event.getEventTime();
            if (now - lastAdbProbeMoveMs < 60L) {
                return true;
            }
            lastAdbProbeMoveMs = now;
        }
        addAdbProbeLine("B" + adbProbeCurrentButton + " " + formatProbeMotionEvent(event, route));
        return true;
    }

    private boolean isVolumeKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
                || keyCode == KeyEvent.KEYCODE_MUTE;
    }

    /**
     * 볼륨키를 한 번 누르면 컨트롤러가 같은 신호를 0.3~0.5초 간격으로 계속 흘린다.
     * 토글 동작에 그대로 연결하면 켜졌다 꺼졌다를 반복하므로, 신호가 끊길 때까지를
     * 한 번의 누름으로 묶는다. 첫 신호만 통과시키고 나머지는 버린다.
     *
     * 마지막 신호 시각은 통과 여부와 상관없이 항상 갱신한다. 통과한 것만 갱신하면
     * 계속 누르고 있는 동안 고정 간격마다 한 번씩 다시 발동한다.
     */
    private boolean shouldSuppressRepeatedVolumeKey(KeyEvent event) {
        if (!isVolumeKey(event.getKeyCode()) || event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }

        long eventTime = event.getEventTime();
        boolean sameBurst = event.getKeyCode() == lastAcceptedVolumeKeyCode
                && eventTime - lastAcceptedVolumeKeyMs < VOLUME_KEY_BURST_GAP_MS;

        lastAcceptedVolumeKeyCode = event.getKeyCode();
        lastAcceptedVolumeKeyMs = eventTime;
        return sameBurst;
    }

    private boolean acceptsInputDevice(InputDevice inputDevice) {
        String descriptor = inputDevice == null ? "" : inputDevice.getDescriptor();
        String name = inputDevice == null ? "" : inputDevice.getName();
        int vendorId = inputDevice == null ? 0 : inputDevice.getVendorId();
        int productId = inputDevice == null ? 0 : inputDevice.getProductId();
        return MappingStore.acceptsInputDevice(this, descriptor, name, vendorId, productId);
    }

    private String formatProbeKeyEvent(KeyEvent event, InputDevice inputDevice) {
        String action = event.getAction() == KeyEvent.ACTION_DOWN ? "KEY_DOWN" : "KEY_UP";
        return adbProbeRelTime(event.getEventTime())
                + " " + action
                + " " + KeyEvent.keyCodeToString(event.getKeyCode())
                + " code=" + event.getKeyCode()
                + " repeat=" + event.getRepeatCount()
                + " scan=" + event.getScanCode()
                + "\n" + analyzerDeviceSummary(inputDevice);
    }

    private String formatProbeMotionEvent(MotionEvent event, String route) {
        InputDevice inputDevice = event.getDevice();
        String tool = event.getPointerCount() > 0 ? toolTypeLabel(event.getToolType(0)) : "NONE";
        return adbProbeRelTime(event.getEventTime())
                + " " + route
                + " " + MotionEvent.actionToString(event.getActionMasked())
                + "\n" + analyzerDeviceSummary(inputDevice)
                + "\nraw=" + Math.round(event.getRawX()) + "," + Math.round(event.getRawY())
                + " local=" + Math.round(event.getX()) + "," + Math.round(event.getY())
                + " source=" + motionSourceLabel(event.getSource())
                + "/0x" + Integer.toHexString(event.getSource())
                + " tool=" + tool
                + "\nbuttonState=" + event.getButtonState()
                + " actionButton=" + event.getActionButton()
                + " scrollV=" + formatAxis(event.getAxisValue(MotionEvent.AXIS_VSCROLL))
                + " scrollH=" + formatAxis(event.getAxisValue(MotionEvent.AXIS_HSCROLL))
                + " downDelta=" + (event.getEventTime() - event.getDownTime()) + "ms";
    }

    private String adbProbeRelTime(long eventTime) {
        if (adbProbeStartMs == 0L) {
            adbProbeStartMs = eventTime;
        }
        long base = adbProbeStartMs;
        return "+" + Math.max(0L, eventTime - base) + "ms";
    }

    private void addAdbProbeLine(String line) {
        Log.i(PROBE_TAG, line.replace('\n', ' '));
        adbProbeLines.add(0, line);
        while (adbProbeLines.size() > 10) {
            adbProbeLines.remove(adbProbeLines.size() - 1);
        }
        adbProbeButtonReport.append(line).append("\n\n");
        adbProbeReport.append(line).append("\n\n");
        updateAdbProbeLog();
    }

    private void updateAdbProbeLog() {
        if (adbProbeLog == null) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Button ")
                .append(adbProbeCurrentButton)
                .append(" / ")
                .append(adbProbeButtonCount)
                .append("\nSaved buttons: ")
                .append(MappingStore.learnedRemoteButtonCount(this))
                .append("\nPress the physical button, then tap Done.\n\n");
        if (adbProbeLines.isEmpty()) {
            builder.append("Waiting for input...");
        } else {
            for (int index = 0; index < adbProbeLines.size(); index++) {
                if (index > 0) {
                    builder.append("\n\n");
                }
                builder.append(adbProbeLines.get(index));
            }
        }
        adbProbeLog.setText(builder.toString());
    }

    private void completeAdbProbeButton() {
        String signature = motionBurstSignature(adbProbeBurst);
        int direction = motionBurstDirection(adbProbeBurst);
        if (!signature.isEmpty()) {
            MappingStore.saveLearnedRemoteButton(this, adbProbeCurrentButton - 1, direction, signature);
            adbProbeReport.append("SAVED BUTTON ")
                    .append(adbProbeCurrentButton)
                    .append(" ")
                    .append(signature)
                    .append("\n\n");
            Log.i(PROBE_TAG, "SAVED BUTTON " + adbProbeCurrentButton + " " + signature);
        }
        adbProbeBurst.reset();
        adbProbeReport.append("---- END BUTTON ")
                .append(adbProbeCurrentButton)
                .append(" ----\n\n");
        Log.i(PROBE_TAG, "---- END BUTTON " + adbProbeCurrentButton + " ----");
        if (adbProbeCurrentButton < adbProbeButtonCount) {
            adbProbeCurrentButton++;
            adbProbeLines.clear();
            adbProbeButtonReport.setLength(0);
            lastAdbProbeMoveMs = 0L;
            adbProbeReport.append("---- BEGIN BUTTON ")
                    .append(adbProbeCurrentButton)
                    .append(" ----\n\n");
            Log.i(PROBE_TAG, "---- BEGIN BUTTON " + adbProbeCurrentButton + " ----");
            updateAdbProbeLog();
            Toast.makeText(this, "Next: button " + adbProbeCurrentButton, Toast.LENGTH_SHORT).show();
            return;
        }
        MappingStore.saveTrapMode(this, MappingStore.TRAP_MODE_AUTO);
        boolean needsTrapOverlay = Build.VERSION.SDK_INT < 34 || selectedDeviceUsesTouchscreen();
        if (needsTrapOverlay && !remoteTrapVisible) {
            remoteTrapVisible = true;
        }
        if (needsTrapOverlay) {
            hideRemoteTrapOverlay();
            showRemoteTrapOverlay();
        } else {
            updateMotionCapture();
        }
        saveAdbProbeReport();
        Toast.makeText(this, "ADB probe complete", Toast.LENGTH_LONG).show();
        hideAdbProbePanel();
    }

    private void saveAdbProbeReport() {
        File dir = getExternalFilesDir(null);
        if (dir == null) {
            dir = getFilesDir();
        }
        File file = new File(dir, "adb-probe-" + System.currentTimeMillis() + ".txt");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(adbProbeReport.toString().getBytes(StandardCharsets.UTF_8));
            Log.i(PROBE_TAG, "Saved " + file.getAbsolutePath());
            Toast.makeText(this, "Saved: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException exception) {
            Log.e(PROBE_TAG, "Save failed", exception);
            Toast.makeText(this, "Save failed: " + exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String formatAnalyzerKeyEvent(KeyEvent event, InputDevice inputDevice) {
        String action = event.getAction() == KeyEvent.ACTION_DOWN ? "DOWN" : "UP";
        return relTime(event.getEventTime())
                + " KEY " + action
                + " " + KeyEvent.keyCodeToString(event.getKeyCode())
                + " (" + event.getKeyCode() + ")"
                + " repeat " + event.getRepeatCount()
                + "\n" + analyzerDeviceSummary(inputDevice)
                + "\nscan " + event.getScanCode()
                + " meta " + event.getMetaState()
                + " flags 0x" + Integer.toHexString(event.getFlags());
    }

    private String formatAnalyzerMotionEvent(MotionEvent event, String route) {
        InputDevice inputDevice = event.getDevice();
        String tool = event.getPointerCount() > 0 ? toolTypeLabel(event.getToolType(0)) : "NONE";
        return relTime(event.getEventTime())
                + " " + route + " " + MotionEvent.actionToString(event.getActionMasked())
                + "\n" + analyzerDeviceSummary(inputDevice)
                + "\nx " + Math.round(event.getRawX()) + ", y " + Math.round(event.getRawY())
                + " local " + Math.round(event.getX()) + "," + Math.round(event.getY())
                + " pointers " + event.getPointerCount()
                + "\nsource " + motionSourceLabel(event.getSource())
                + " 0x" + Integer.toHexString(event.getSource())
                + " tool " + tool
                + "\nbuttonState " + event.getButtonState()
                + " actionButton " + event.getActionButton()
                + " edgeFlags 0x" + Integer.toHexString(event.getEdgeFlags())
                + "\nscroll v " + formatAxis(event.getAxisValue(MotionEvent.AXIS_VSCROLL))
                + ", h " + formatAxis(event.getAxisValue(MotionEvent.AXIS_HSCROLL))
                + " down+" + (event.getEventTime() - event.getDownTime()) + "ms";
    }

    private String analyzerDeviceSummary(InputDevice inputDevice) {
        if (inputDevice == null) {
            return "device: none";
        }

        return inputDevice.getName()
                + "\nid " + inputDevice.getId()
                + " vendor " + inputDevice.getVendorId()
                + " product " + inputDevice.getProductId()
                + " sources 0x" + Integer.toHexString(inputDevice.getSources())
                + "\ndescriptor " + inputDevice.getDescriptor();
    }

    private String relTime(long eventTime) {
        long base = controllerAnalyzerStartMs == 0L ? eventTime : controllerAnalyzerStartMs;
        return "+" + Math.max(0L, eventTime - base) + "ms";
    }

    private void addControllerAnalyzerLine(String line) {
        controllerAnalyzerLines.add(0, line);
        while (controllerAnalyzerLines.size() > 18) {
            controllerAnalyzerLines.remove(controllerAnalyzerLines.size() - 1);
        }
        controllerAnalyzerReport.append(line).append("\n\n");
        updateControllerAnalyzerState();
        updateControllerAnalyzerLog();
    }

    private void updateControllerAnalyzerLog() {
        if (controllerAnalyzerLog == null) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        if (controllerAnalyzerLines.isEmpty()) {
            builder.append(controllerAnalyzerCapturing ? "Recording...\n" : "Tap Start first.\n")
                    .append("Then press each remote button once.\n")
                    .append("This analyzer records key, touch, hover, generic motion, source, device id, and coordinates.\n")
                    .append("Events are consumed while this screen is open.\n\n")
                    .append(MappingStore.lastControllerAnalysisSummary(this));
        } else {
            for (int index = 0; index < controllerAnalyzerLines.size(); index++) {
                if (index > 0) {
                    builder.append("\n\n");
                }
                builder.append(controllerAnalyzerLines.get(index));
            }
        }
        controllerAnalyzerLog.setText(builder.toString());
    }

    private void startControllerAnalyzerSession() {
        controllerAnalyzerLines.clear();
        controllerAnalyzerReport.setLength(0);
        controllerAnalyzerStartMs = System.currentTimeMillis();
        lastControllerAnalyzerMoveMs = 0L;
        controllerAnalyzerCapturing = false;
        controllerAnalyzerKeyEvents = 0;
        controllerAnalyzerMotionEvents = 0;
        controllerAnalyzerKeyEvents = 0;
        controllerAnalyzerMotionEvents = 0;
        controllerAnalyzerCapturing = true;
        controllerAnalyzerReport.append("Remote Analyzer Session\n")
                .append("startedAt ").append(System.currentTimeMillis()).append("\n")
                .append("selectedDevice ").append(MappingStore.selectedInputDeviceName(this)).append("\n")
                .append("selectedMode ").append(MappingStore.inputDeviceModeLabel(this)).append("\n\n");
        updateControllerAnalyzerState();
        updateControllerAnalyzerLog();
    }

    private void stopControllerAnalyzerSession() {
        controllerAnalyzerCapturing = false;
        updateControllerAnalyzerState();
        updateControllerAnalyzerLog();
    }

    private void updateControllerAnalyzerState() {
        if (controllerAnalyzerStartButton != null) {
            controllerAnalyzerStartButton.setText(controllerAnalyzerCapturing ? "Stop" : "Start");
            controllerAnalyzerStartButton.setTextColor(controllerAnalyzerCapturing ? 0xFFB42318 : 0xFF047857);
            controllerAnalyzerStartButton.setBackground(rounded(controllerAnalyzerCapturing ? 0xFFFFF1F2 : 0xFFECFDF3, dp(8)));
        }
        if (controllerAnalyzerStatus != null) {
            String state = controllerAnalyzerCapturing ? "Recording" : "Ready";
            controllerAnalyzerStatus.setText(state
                    + " | key " + controllerAnalyzerKeyEvents
                    + " | motion " + controllerAnalyzerMotionEvents);
        }
    }

    private void saveControllerAnalyzerReport() {
        if (controllerAnalyzerReport.length() == 0) {
            Toast.makeText(this, "No analyzer events yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        controllerAnalyzerCapturing = false;
        updateControllerAnalyzerState();
        MappingStore.saveControllerAnalysis(this,
                controllerAnalyzerReport.toString(),
                controllerAnalyzerKeyEvents,
                controllerAnalyzerMotionEvents);

        File dir = getExternalFilesDir(null);
        if (dir == null) {
            dir = getFilesDir();
        }
        File file = new File(dir, "controller-analyzer-" + System.currentTimeMillis() + ".txt");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(controllerAnalyzerReport.toString().getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "Saved: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException exception) {
            Toast.makeText(this, "Save failed: " + exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean handleControllerDiagnosticKeyEvent(KeyEvent event, InputDevice inputDevice, String descriptor) {
        if (!acceptsInputDevice(inputDevice)) {
            return false;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            controllerDiagnosticKeyCount++;
            boolean hasTouchProfile = !controllerDiagnosticZones.isEmpty()
                    || !MappingStore.trapZones(this).isEmpty()
                    || MappingStore.inputDeviceMode(this) == MappingStore.DEVICE_MODE_MOTION
                    || MappingStore.inputDeviceMode(this) == MappingStore.DEVICE_MODE_MIXED;
            if (hasTouchProfile) {
                MappingStore.saveInputDeviceMode(this, MappingStore.DEVICE_MODE_MIXED);
            } else {
                MappingStore.saveInputDeviceMode(this, MappingStore.DEVICE_MODE_KEYBOARD);
                MappingStore.clearTrapZones(this);
                if (remoteTrapVisible) {
                    hideRemoteTrapOverlay();
                    remoteTrapVisible = false;
                }
            }
            updateControllerDiagnosticLog("KEY " + KeyEvent.keyCodeToString(event.getKeyCode()));
            MainActivity.refreshIfVisible();
        }
        return true;
    }

    private boolean handleControllerDiagnosticMotionEvent(MotionEvent event) {
        InputDevice inputDevice = event.getDevice();
        String descriptor = inputDevice == null ? "" : inputDevice.getDescriptor();
        if (!acceptsInputDevice(inputDevice)) {
            return false;
        }

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_BUTTON_PRESS) {
            recordControllerDiagnosticPoint(event.getRawX(), event.getRawY());
            return true;
        }

        if (action == MotionEvent.ACTION_DOWN) {
            controllerDiagnosticMotionActive = true;
            controllerDiagnosticDownX = event.getRawX();
            controllerDiagnosticDownY = event.getRawY();
            return true;
        }

        boolean finished = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
        if (!finished || !controllerDiagnosticMotionActive) {
            return true;
        }

        controllerDiagnosticMotionActive = false;
        controllerDiagnosticMotionCount++;
        RectF zone = normalizedTrapZone(
                controllerDiagnosticDownX,
                controllerDiagnosticDownY,
                event.getRawX(),
                event.getRawY()
        );
        addOrMergeControllerZone(zone);
        saveControllerDiagnosticZones();
        if (controllerDiagnosticKeyCount > 0
                || MappingStore.inputDeviceMode(this) == MappingStore.DEVICE_MODE_KEYBOARD
                || MappingStore.inputDeviceMode(this) == MappingStore.DEVICE_MODE_MIXED) {
            MappingStore.saveInputDeviceMode(this, MappingStore.DEVICE_MODE_MIXED);
        } else {
            MappingStore.saveInputDeviceMode(this, MappingStore.DEVICE_MODE_MOTION);
        }

        if (remoteTrapVisible) {
            hideRemoteTrapOverlay();
            showRemoteTrapOverlay();
        }

        int direction = remoteGestureSlot(controllerDiagnosticDownX, controllerDiagnosticDownY, event.getRawX(), event.getRawY());
        String directionText = direction >= 0 ? MappingStore.mouseDirectionLabel(direction) : "Still";
        updateControllerDiagnosticLog("MOTION " + directionText);
        MainActivity.refreshIfVisible();
        return true;
    }

    private void recordControllerDiagnosticPoint(float x, float y) {
        controllerDiagnosticMotionCount++;
        RectF zone = normalizedTrapZone(x, y, x, y);
        addOrMergeControllerZone(zone);
        saveControllerDiagnosticZones();
        if (controllerDiagnosticKeyCount > 0
                || MappingStore.inputDeviceMode(this) == MappingStore.DEVICE_MODE_KEYBOARD
                || MappingStore.inputDeviceMode(this) == MappingStore.DEVICE_MODE_MIXED) {
            MappingStore.saveInputDeviceMode(this, MappingStore.DEVICE_MODE_MIXED);
        } else {
            MappingStore.saveInputDeviceMode(this, MappingStore.DEVICE_MODE_MOTION);
        }
        if (remoteTrapVisible) {
            hideRemoteTrapOverlay();
            showRemoteTrapOverlay();
        }
        updateControllerDiagnosticLog("MOTION point " + Math.round(x) + ", " + Math.round(y));
        MainActivity.refreshIfVisible();
    }

    private void showControllerDiagnosticPanel() {
        hideControllerDiagnosticPanel();
        controllerDiagnosticZones.clear();
        controllerDiagnosticKeyCount = 0;
        controllerDiagnosticMotionCount = 0;

        for (MappingStore.TrapZone zone : MappingStore.trapZones(this)) {
            controllerDiagnosticZones.add(new RectF(zone.x, zone.y, zone.x + zone.width, zone.y + zone.height));
        }

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0x26000000);
        overlay.setFocusable(true);
        overlay.setFocusableInTouchMode(true);
        overlay.setOnTouchListener((view, event) -> handleControllerDiagnosticMotionEvent(event));
        overlay.setOnGenericMotionListener((view, event) -> handleControllerDiagnosticMotionEvent(event));
        overlay.setOnHoverListener((view, event) -> handleControllerDiagnosticMotionEvent(event));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setBackground(roundedStroke(0xFAFFFFFF, dp(8), 0xFFE0E5EC, 1));
        panel.setElevation(dp(6));

        TextView title = new TextView(this);
        title.setText("Controller diagnosis");
        title.setTextColor(0xFF1F2933);
        title.setTextSize(15f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(8));
        panel.addView(title, panelParams());

        controllerDiagnosticLog = new TextView(this);
        controllerDiagnosticLog.setTextColor(0xFF1F2933);
        controllerDiagnosticLog.setTextSize(12f);
        controllerDiagnosticLog.setPadding(dp(10), dp(8), dp(10), dp(8));
        controllerDiagnosticLog.setBackground(rounded(0xFFF1F5F9, dp(8)));
        panel.addView(controllerDiagnosticLog, panelParams());
        updateControllerDiagnosticLog(null);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button resetButton = new Button(this);
        resetButton.setText("Reset");
        resetButton.setAllCaps(false);
        resetButton.setTextColor(0xFF2563EB);
        resetButton.setBackground(rounded(0xFFEAF2FF, dp(8)));
        resetButton.setOnClickListener(view -> {
            controllerDiagnosticZones.clear();
            controllerDiagnosticKeyCount = 0;
            controllerDiagnosticMotionCount = 0;
            MappingStore.saveInputDeviceMode(this, MappingStore.DEVICE_MODE_UNKNOWN);
            MappingStore.clearTrapZones(this);
            updateControllerDiagnosticLog("Reset complete");
            MainActivity.refreshIfVisible();
        });
        actions.addView(resetButton, panelHalfParams());

        Button closeButton = new Button(this);
        closeButton.setText("Close");
        closeButton.setAllCaps(false);
        closeButton.setTextColor(0xFF1F2933);
        closeButton.setBackground(roundedStroke(0xFFFFFFFF, dp(8), 0xFFE0E5EC, 1));
        closeButton.setOnClickListener(view -> hideControllerDiagnosticPanel());
        actions.addView(closeButton, panelHalfParams());
        panel.addView(actions, panelParams());

        FrameLayout.LayoutParams panelFrameParams = new FrameLayout.LayoutParams(
                dp(320),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL
        );
        panelFrameParams.setMargins(dp(12), dp(42), dp(12), 0);
        overlay.addView(panel, panelFrameParams);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;

        controllerDiagnosticOverlay = overlay;
        windowManager.addView(controllerDiagnosticOverlay, params);
        overlay.requestFocus();
    }

    private void updateControllerDiagnosticLog(String lastEvent) {
        if (controllerDiagnosticLog == null) {
            return;
        }

        String mode;
        switch (MappingStore.inputDeviceMode(this)) {
            case MappingStore.DEVICE_MODE_KEYBOARD:
                mode = "HID keyboard type";
                break;
            case MappingStore.DEVICE_MODE_MOTION:
                mode = "Motion / TikTok clicker type";
                break;
            case MappingStore.DEVICE_MODE_MIXED:
                mode = "Mixed keyboard + motion type";
                break;
            default:
                mode = "Waiting for input";
                break;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Selected: ").append(MappingStore.selectedInputDeviceName(this)).append("\n");
        builder.append("Detected: ").append(mode).append("\n");
        builder.append("Key events: ").append(controllerDiagnosticKeyCount)
                .append(" / Motion events: ").append(controllerDiagnosticMotionCount).append("\n");
        builder.append("Learned motion buttons: ").append(MappingStore.learnedRemoteButtonCount(this));
        if (lastEvent != null) {
            builder.append("\n\nLast: ").append(lastEvent);
        } else {
            builder.append("\n\nPress a controller button once. HID keyboard controllers can use normal key capture. Motion/TikTok clicker controllers should use Motion button learning.");
        }
        controllerDiagnosticLog.setText(builder.toString());
    }

    private RectF normalizedTrapZone(float startX, float startY, float endX, float endY) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        float margin = dp(84);
        float minSize = dp(128);
        float left = Math.min(startX, endX) - margin;
        float top = Math.min(startY, endY) - margin;
        float right = Math.max(startX, endX) + margin;
        float bottom = Math.max(startY, endY) + margin;

        if (right - left < minSize) {
            float center = (left + right) / 2f;
            left = center - minSize / 2f;
            right = center + minSize / 2f;
        }
        if (bottom - top < minSize) {
            float center = (top + bottom) / 2f;
            top = center - minSize / 2f;
            bottom = center + minSize / 2f;
        }

        left = Math.max(0f, left);
        top = Math.max(0f, top);
        right = Math.min(screenWidth, right);
        bottom = Math.min(screenHeight, bottom);
        return new RectF(left, top, Math.max(left + 1f, right), Math.max(top + 1f, bottom));
    }

    /**
     * 학습 중 관측한 DOWN 좌표의 범위를 저장한다. 트랩 존은 여기서 직접 만든다.
     *
     * 시그니처는 화면을 12x16으로 양자화한 값이라 좌표로 되돌리면 최대 반 칸이 어긋난다.
     * 원본을 갖고 있는 학습 시점에 그대로 남겨두면 역산할 이유가 없다.
     */
    private void saveObservedAnchorRange(int slot) {
        if (inputCaptureAnchors.isEmpty()) {
            return;
        }
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (float[] point : inputCaptureAnchors) {
            minX = Math.min(minX, point[0]);
            minY = Math.min(minY, point[1]);
            maxX = Math.max(maxX, point[0]);
            maxY = Math.max(maxY, point[1]);
        }
        MappingStore.saveObservedAnchor(this, slot, minX, minY, maxX, maxY);
    }

    private void rebuildSelectedControllerTrapZones() {
        List<RectF> anchorZones = new ArrayList<>();
        for (int slot = 0; slot < MappingStore.SLOT_COUNT; slot++) {
            MappingStore.Mapping mapping = MappingStore.get(this, slot);
            // 관측한 좌표가 있으면 그것을 쓴다. 없는 기존 프로필만 시그니처에서 역산한다.
            RectF observed = observedAnchorZone(slot);
            if (observed != null) {
                mergeAnchorZone(anchorZones, observed);
                continue;
            }
            if (mapping.triggerType == MappingStore.TRIGGER_MOUSE_GESTURE) {
                addSignatureAnchorZones(anchorZones, mapping.triggerSignature);
            }
            if (mapping.longTriggerType == MappingStore.TRIGGER_MOUSE_GESTURE) {
                addSignatureAnchorZones(anchorZones, mapping.longTriggerSignature);
            }
        }
        if (anchorZones.isEmpty()) {
            return;
        }

        List<MappingStore.TrapZone> zones = new ArrayList<>();
        for (RectF zone : anchorZones) {
            zones.add(new MappingStore.TrapZone(
                    Math.round(zone.left),
                    Math.round(zone.top),
                    Math.max(1, Math.round(zone.width())),
                    Math.max(1, Math.round(zone.height()))
            ));
        }
        MappingStore.saveTrapZones(this, zones);
    }

    /**
     * 관측된 DOWN 좌표 범위에 여백을 붙인 트랩 존.
     *
     * 스와이프를 주입하는 컨트롤러라도 DOWN을 받은 창이 제스처 전체를 가져가므로
     * 시작점만 덮으면 된다. 경로까지 덮을 필요가 없어 화면 점유가 작다.
     */
    private RectF observedAnchorZone(int slot) {
        float[] range = MappingStore.observedAnchor(this, slot);
        if (range == null) {
            return null;
        }
        float margin = dp(24);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        return new RectF(
                Math.max(0f, range[0] - margin),
                Math.max(0f, range[1] - margin),
                Math.min(screenWidth, range[2] + margin),
                Math.min(screenHeight, range[3] + margin));
    }

    private void mergeAnchorZone(List<RectF> zones, RectF zone) {
        for (RectF saved : zones) {
            if (RectF.intersects(saved, zone)) {
                saved.union(zone);
                return;
            }
        }
        if (zones.size() < MappingStore.MAX_TRAP_ZONES) {
            zones.add(zone);
        }
    }

    private void addSignatureAnchorZones(List<RectF> zones, String signatures) {
        if (signatures == null || signatures.trim().isEmpty()) {
            return;
        }
        for (String signature : signatures.split("\\n")) {
            RectF zone = anchorZoneFromSignature(signature);
            if (zone == null) {
                continue;
            }
            boolean merged = false;
            for (RectF saved : zones) {
                if (RectF.intersects(saved, zone)) {
                    saved.union(zone);
                    merged = true;
                    break;
                }
            }
            if (!merged && zones.size() < MappingStore.MAX_TRAP_ZONES) {
                zones.add(zone);
            }
        }
    }

    private RectF anchorZoneFromSignature(String signature) {
        int anchorStart = signature == null ? -1 : signature.indexOf("|a=");
        if (anchorStart < 0) {
            return null;
        }
        anchorStart += 3;
        int anchorEnd = signature.indexOf('|', anchorStart);
        String anchor = anchorEnd < 0
                ? signature.substring(anchorStart)
                : signature.substring(anchorStart, anchorEnd);
        String[] values = anchor.split(",", 2);
        if (values.length != 2) {
            return null;
        }

        int xBin;
        int yBin;
        try {
            xBin = Math.max(0, Math.min(11, Integer.parseInt(values[0].trim())));
            yBin = Math.max(0, Math.min(15, Integer.parseInt(values[1].trim())));
        } catch (NumberFormatException exception) {
            return null;
        }

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        float centerX = (xBin + 0.5f) * screenWidth / 12f;
        float centerY = (yBin + 0.5f) * screenHeight / 16f;
        float halfSize = dp(72);
        float left = Math.max(0f, centerX - halfSize);
        float top = Math.max(0f, centerY - halfSize);
        float right = Math.min(screenWidth, centerX + halfSize);
        float bottom = Math.min(screenHeight, centerY + halfSize);
        return new RectF(left, top, Math.max(left + 1f, right), Math.max(top + 1f, bottom));
    }

    private void addOrMergeControllerZone(RectF newZone) {
        float mergeMargin = dp(48);
        for (RectF zone : controllerDiagnosticZones) {
            RectF expanded = new RectF(zone);
            expanded.inset(-mergeMargin, -mergeMargin);
            if (RectF.intersects(expanded, newZone)) {
                zone.union(newZone);
                return;
            }
        }

        if (controllerDiagnosticZones.size() < MappingStore.MAX_TRAP_ZONES) {
            controllerDiagnosticZones.add(newZone);
        }
    }

    private void saveControllerDiagnosticZones() {
        List<MappingStore.TrapZone> zones = new ArrayList<>();
        for (RectF zone : controllerDiagnosticZones) {
            zones.add(new MappingStore.TrapZone(
                    Math.round(zone.left),
                    Math.round(zone.top),
                    Math.max(1, Math.round(zone.width())),
                    Math.max(1, Math.round(zone.height()))
            ));
        }
        MappingStore.saveTrapZones(this, zones);
    }

    private String motionSourceLabel(int source) {
        List<String> labels = new ArrayList<>();
        if ((source & InputDevice.SOURCE_TOUCHSCREEN) == InputDevice.SOURCE_TOUCHSCREEN) {
            labels.add("TOUCHSCREEN");
        }
        if ((source & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) {
            labels.add("MOUSE");
        }
        if ((source & InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD) {
            labels.add("TOUCHPAD");
        }
        if ((source & InputDevice.SOURCE_TRACKBALL) == InputDevice.SOURCE_TRACKBALL) {
            labels.add("TRACKBALL");
        }
        if ((source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            labels.add("JOYSTICK");
        }
        if (labels.isEmpty()) {
            labels.add("0x" + Integer.toHexString(source));
        }
        return joinLabels(labels);
    }

    private String joinLabels(List<String> labels) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < labels.size(); index++) {
            if (index > 0) {
                builder.append("/");
            }
            builder.append(labels.get(index));
        }
        return builder.toString();
    }

    private String toolTypeLabel(int toolType) {
        switch (toolType) {
            case MotionEvent.TOOL_TYPE_FINGER:
                return "FINGER";
            case MotionEvent.TOOL_TYPE_MOUSE:
                return "MOUSE";
            case MotionEvent.TOOL_TYPE_STYLUS:
                return "STYLUS";
            case MotionEvent.TOOL_TYPE_ERASER:
                return "ERASER";
            default:
                return "UNKNOWN";
        }
    }

    private String formatAxis(float value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private void executeSlot(int slot) {
        if (slot < 0) {
            return;
        }

        MappingStore.Mapping mapping = MappingStore.get(this, slot);
        if (mapping.canRun()) {
            tap(mapping.x, mapping.y);
        }
    }

    private void executeLongSlot(int slot) {
        if (slot == POSITION_TOGGLE_SLOT) {
            togglePositionOverlayFromButton();
            return;
        }
        if (slot == MappingStore.LOCK_SLOT) {
            toggleTouchLock();
            return;
        }
        executeSlot(slot);
    }

    private boolean handleRemoteMouseGesture(MotionEvent event) {
        if (!isAcceptedMouseEvent(event)) {
            return false;
        }

        if (inPostCaptureCooldown(event.getEventTime())) {
            remoteGestureBurst.reset();
            return true;
        }
        // 커서를 옮기는 이동 신호까지 볼륨키를 막으면 안 된다. LP-910은 버튼을 누를 때마다
        // 커서를 구석으로 보냈다 되돌리는 신호를 먼저 흘리는데, 그 직후에 오는 볼륨키가
        // 통째로 버려진다. 실제로 버튼이 눌린 이벤트에서만 짧게 막는다.
        if (isRemoteAnchorEvent(event)) {
            remoteVolumeSuppressUntilMs = Math.max(remoteVolumeSuppressUntilMs,
                    event.getEventTime() + REMOTE_VOLUME_SUPPRESS_MS);
        }

        boolean finished = remoteGestureBurst.record(event);
        if (!finished) {
            return true;
        }

        if (event.getEventTime() - lastRemoteGestureMs < REMOTE_GESTURE_COOLDOWN_MS) {
            remoteGestureBurst.reset();
            return true;
        }
        lastRemoteGestureMs = event.getEventTime();

        int direction = motionBurstDirection(remoteGestureBurst);
        String signature = motionBurstSignature(remoteGestureBurst);
        int learnedIndex = findBestLearnedRemoteButton(signature);
        if (learnedIndex >= 0) {
            direction = MappingStore.learnedRemoteButtonDirection(this, learnedIndex);
            signature = MappingStore.learnedRemoteButtonSignature(this, learnedIndex);
        }
        remoteGestureBurst.reset();
        recentMotionSignature = signature;
        recentMotionSignatureMs = event.getEventTime();

        MappingStore.Mapping mapping = MappingStore.findByMouseSignature(this, signature);
        if (mapping == null) {
            mapping = MappingStore.findByMouseSignature(this, direction, signature);
        }
        if (mapping == null) {
            mapping = findClosestMappedMouseGesture(direction, signature, false);
        }
        if (mapping == null) {
            mapping = MappingStore.findByMouseGesture(this, direction);
        }
        if (mapping != null) {
            executeSlot(mapping.slot);
        } else {
            MappingStore.Mapping longMapping = MappingStore.findByLongMouseSignature(this, signature);
            if (longMapping == null) {
                longMapping = MappingStore.findByLongMouseSignature(this, direction, signature);
            }
            if (longMapping == null) {
                longMapping = findClosestMappedMouseGesture(direction, signature, true);
            }
            if (longMapping == null) {
                longMapping = MappingStore.findByLongMouseGesture(this, direction);
            }
            if (longMapping != null) {
                executeLongSlot(longMapping.slot);
            }
        }
        return true;
    }

    private MappingStore.Mapping findClosestMappedMouseGesture(int direction, String signature, boolean longMode) {
        if (signature == null || signature.trim().isEmpty()) {
            return null;
        }

        MappingStore.Mapping best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int slot = 0; slot < MappingStore.SLOT_COUNT; slot++) {
            MappingStore.Mapping mapping = MappingStore.get(this, slot);
            int triggerType = longMode ? mapping.longTriggerType : mapping.triggerType;
            int triggerValue = longMode ? mapping.longTriggerValue : mapping.triggerValue;
            String savedSignatures = longMode ? mapping.longTriggerSignature : mapping.triggerSignature;
            if (triggerType != MappingStore.TRIGGER_MOUSE_GESTURE || triggerValue != direction) {
                continue;
            }

            for (String savedSignature : savedSignatures.split("\\n")) {
                int score = burstSignatureDistance(signature, savedSignature.trim());
                if (score >= 0 && score < bestScore) {
                    best = mapping;
                    bestScore = score;
                }
            }
        }
        return bestScore <= 5 ? best : null;
    }

    private MappingStore.Mapping findMouseMappingByAnchorSignature(int direction, String signature, boolean longMode) {
        MappingStore.Mapping exact = longMode
                ? MappingStore.findByLongMouseSignature(this, direction, signature)
                : MappingStore.findByMouseSignature(this, direction, signature);
        if (exact != null) {
            return exact;
        }

        int[] anchor = parseAnchorTarget(signature);
        if (anchor == null) {
            return direction >= 0
                    ? (longMode ? MappingStore.findByLongMouseGesture(this, direction) : MappingStore.findByMouseGesture(this, direction))
                    : null;
        }

        MappingStore.Mapping best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int slot = 0; slot < MappingStore.SLOT_COUNT; slot++) {
            MappingStore.Mapping mapping = MappingStore.get(this, slot);
            if (longMode) {
                if (mapping.longTriggerType != MappingStore.TRIGGER_MOUSE_GESTURE) {
                    continue;
                }
                if (mapping.longTriggerValue != direction) {
                    continue;
                }
                int distance = anchorTargetDistance(anchor, mapping.longTriggerSignature);
                if (distance >= 0 && distance < bestDistance) {
                    best = mapping;
                    bestDistance = distance;
                }
            } else {
                if (mapping.triggerType != MappingStore.TRIGGER_MOUSE_GESTURE) {
                    continue;
                }
                if (mapping.triggerValue != direction) {
                    continue;
                }
                int distance = anchorTargetDistance(anchor, mapping.triggerSignature);
                if (distance >= 0 && distance < bestDistance) {
                    best = mapping;
                    bestDistance = distance;
                }
            }
        }

        if (best != null && bestDistance <= 2) {
            return best;
        }

        return direction >= 0
                ? (longMode ? MappingStore.findByLongMouseGesture(this, direction) : MappingStore.findByMouseGesture(this, direction))
                : null;
    }

    private int anchorTargetDistance(int[] currentAnchor, String savedSignature) {
        int[] savedAnchor = parseAnchorTarget(savedSignature);
        if (currentAnchor == null || savedAnchor == null) {
            return -1;
        }
        return Math.abs(currentAnchor[0] - savedAnchor[0]) + Math.abs(currentAnchor[1] - savedAnchor[1]);
    }

    private int[] parseAnchorTarget(String signature) {
        if (signature == null || !signature.startsWith("A")) {
            return null;
        }
        int arrow = signature.indexOf('>');
        String target = arrow >= 0 ? signature.substring(arrow + 1) : signature.substring(1);
        int colon = target.indexOf(':');
        if (colon >= 0 && colon + 1 < target.length()) {
            target = target.substring(colon + 1);
        }
        if (target.isEmpty()) {
            return null;
        }
        String[] parts = target.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean isRemoteAnchorEvent(MotionEvent event) {
        int action = event.getActionMasked();
        return action == MotionEvent.ACTION_BUTTON_PRESS
                || (action == MotionEvent.ACTION_DOWN && event.getButtonState() != 0);
    }

    private boolean isAcceptedMouseEvent(MotionEvent event) {
        InputDevice inputDevice = event.getDevice();
        int source = event.getSource();
        boolean mouse = (source & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE;
        boolean relativeMouse = (source & InputDevice.SOURCE_MOUSE_RELATIVE) == InputDevice.SOURCE_MOUSE_RELATIVE;
        boolean touchpad = (source & InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD;
        boolean trackball = (source & InputDevice.SOURCE_TRACKBALL) == InputDevice.SOURCE_TRACKBALL;
        boolean joystick = (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
        boolean rotary = (source & InputDevice.SOURCE_ROTARY_ENCODER) == InputDevice.SOURCE_ROTARY_ENCODER;
        boolean touchscreen = (source & InputDevice.SOURCE_TOUCHSCREEN) == InputDevice.SOURCE_TOUCHSCREEN;
        boolean pointer = (source & InputDevice.SOURCE_CLASS_POINTER) == InputDevice.SOURCE_CLASS_POINTER;
        return (mouse || relativeMouse || touchpad || trackball || joystick || rotary || touchscreen || pointer)
                && acceptsInputDevice(inputDevice);
    }

    private boolean selectedDeviceUsesTouchscreen() {
        String selectedDescriptor = MappingStore.selectedInputDeviceDescriptor(this);
        if (selectedDescriptor.isEmpty()) {
            return false;
        }
        for (int deviceId : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (device == null || !selectedDescriptor.equals(device.getDescriptor())) {
                continue;
            }
            int sources = device.getSources();
            return (sources & InputDevice.SOURCE_TOUCHSCREEN) == InputDevice.SOURCE_TOUCHSCREEN;
        }
        return false;
    }

    private boolean isSelectedDeviceMotionMode() {
        int mode = MappingStore.inputDeviceMode(this);
        return mode == MappingStore.DEVICE_MODE_MOTION || mode == MappingStore.DEVICE_MODE_MIXED;
    }

    private int remoteGestureSlot(float startX, float startY, float endX, float endY) {
        float dx = endX - startX;
        float dy = endY - startY;
        float absX = Math.abs(dx);
        float absY = Math.abs(dy);
        float minDistance = dp(48);
        if (Math.max(absX, absY) < minDistance) {
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            float edgeSize = dp(72);
            if (Math.min(startY, endY) <= edgeSize) {
                return MappingStore.MOUSE_UP;
            }
            if (Math.max(startY, endY) >= screenHeight - edgeSize) {
                return MappingStore.MOUSE_DOWN;
            }
            if (Math.min(startX, endX) <= edgeSize) {
                return MappingStore.MOUSE_LEFT;
            }
            if (Math.max(startX, endX) >= screenWidth - edgeSize) {
                return MappingStore.MOUSE_RIGHT;
            }
            return -1;
        }

        if (absY >= absX) {
            return dy < 0 ? MappingStore.MOUSE_UP : MappingStore.MOUSE_DOWN;
        }
        return dx < 0 ? MappingStore.MOUSE_LEFT : MappingStore.MOUSE_RIGHT;
    }

    private String recentMotionSignatureForKey(long eventTime) {
        return eventTime - recentMotionSignatureMs <= 900L ? recentMotionSignature : "";
    }

    private String motionSignature(int direction, float startX, float startY, float endX, float endY) {
        int screenWidth = Math.max(1, getResources().getDisplayMetrics().widthPixels);
        int screenHeight = Math.max(1, getResources().getDisplayMetrics().heightPixels);
        int sx = quantize(startX, screenWidth, 8);
        int sy = quantize(startY, screenHeight, 12);
        int ex = quantize(endX, screenWidth, 8);
        int ey = quantize(endY, screenHeight, 12);
        return "S" + direction + ":" + sx + "," + sy + ">" + ex + "," + ey;
    }

    private String motionBurstSignature(MotionBurst burst) {
        if (burst == null || !burst.hasAnchor) {
            return "";
        }
        int screenWidth = Math.max(1, getResources().getDisplayMetrics().widthPixels);
        int screenHeight = Math.max(1, getResources().getDisplayMetrics().heightPixels);
        int ax = quantize(burst.anchorX, screenWidth, 12);
        int ay = quantize(burst.anchorY, screenHeight, 16);
        int ex = quantize(burst.endX, screenWidth, 12);
        int ey = quantize(burst.endY, screenHeight, 16);
        int direction = motionBurstDirection(burst);
        long duration = Math.max(0L, burst.endTime - burst.startTime);
        String timeBucket = duration < 80L ? "S" : duration < 260L ? "M" : "L";
        int moveBucket = Math.min(9, Math.max(0, burst.moveCount));
        return "B|a=" + ax + "," + ay
                + "|e=" + ex + "," + ey
                + "|d=" + direction
                + "|t=" + timeBucket
                + "|m=" + moveBucket;
    }

    private int motionBurstDirection(MotionBurst burst) {
        if (burst == null || !burst.hasAnchor) {
            return MappingStore.TRIGGER_UNKNOWN;
        }
        int screenWidth = Math.max(1, getResources().getDisplayMetrics().widthPixels);
        int screenHeight = Math.max(1, getResources().getDisplayMetrics().heightPixels);
        int ax = quantize(burst.anchorX, screenWidth, 12);
        int ay = quantize(burst.anchorY, screenHeight, 16);
        int ex = quantize(burst.endX, screenWidth, 12);
        int ey = quantize(burst.endY, screenHeight, 16);
        int dx = ex - ax;
        int dy = ey - ay;
        if (Math.max(Math.abs(dx), Math.abs(dy)) <= 1) {
            return MappingStore.MOUSE_STILL;
        }
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx < 0 ? MappingStore.MOUSE_LEFT : MappingStore.MOUSE_RIGHT;
        }
        return dy < 0 ? MappingStore.MOUSE_UP : MappingStore.MOUSE_DOWN;
    }

    private int findBestLearnedRemoteButton(String signature) {
        int exact = MappingStore.findLearnedRemoteButton(this, signature);
        if (exact >= 0) {
            return exact;
        }

        int bestIndex = -1;
        int bestScore = Integer.MAX_VALUE;
        int count = MappingStore.learnedRemoteButtonCount(this);
        for (int index = 0; index < count; index++) {
            String learned = MappingStore.learnedRemoteButtonSignature(this, index);
            int score = burstSignatureDistance(signature, learned);
            if (score >= 0 && score < bestScore) {
                bestScore = score;
                bestIndex = index;
            }
        }
        return bestScore <= 5 ? bestIndex : -1;
    }

    private int burstSignatureDistance(String current, String learned) {
        int[] currentAnchor = burstPoint(current, "a");
        int[] learnedAnchor = burstPoint(learned, "a");
        int[] currentEnd = burstPoint(current, "e");
        int[] learnedEnd = burstPoint(learned, "e");
        Integer currentDirection = burstInt(current, "d");
        Integer learnedDirection = burstInt(learned, "d");
        Integer currentMoves = burstInt(current, "m");
        Integer learnedMoves = burstInt(learned, "m");
        String currentTime = burstField(current, "t");
        String learnedTime = burstField(learned, "t");
        if (currentAnchor == null || learnedAnchor == null
                || currentEnd == null || learnedEnd == null
                || currentDirection == null || learnedDirection == null
                || currentMoves == null || learnedMoves == null
                || currentTime.isEmpty() || learnedTime.isEmpty()) {
            return -1;
        }

        int score = Math.abs(currentAnchor[0] - learnedAnchor[0])
                + Math.abs(currentAnchor[1] - learnedAnchor[1])
                + Math.abs(currentEnd[0] - learnedEnd[0])
                + Math.abs(currentEnd[1] - learnedEnd[1]);
        if (!currentDirection.equals(learnedDirection)) {
            score += 6;
        }
        if (!currentTime.equals(learnedTime)) {
            score += 2;
        }
        score += Math.abs(currentMoves - learnedMoves) * 2;
        return score;
    }

    private int[] burstPoint(String signature, String key) {
        String value = burstField(signature, key);
        if (value.isEmpty()) {
            return null;
        }
        String[] parts = value.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer burstInt(String signature, String key) {
        String value = burstField(signature, key);
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String burstField(String signature, String key) {
        if (signature == null || key == null) {
            return "";
        }
        String prefix = key + "=";
        String[] parts = signature.split("\\|");
        for (String part : parts) {
            if (part.startsWith(prefix)) {
                return part.substring(prefix.length());
            }
        }
        return "";
    }

    private String anchorMotionSignature(int direction, float previousX, float previousY, MotionEvent event) {
        int screenWidth = Math.max(1, getResources().getDisplayMetrics().widthPixels);
        int screenHeight = Math.max(1, getResources().getDisplayMetrics().heightPixels);
        int ax = quantize(event.getRawX(), screenWidth, 12);
        int ay = quantize(event.getRawY(), screenHeight, 16);
        return "A" + ax + "," + ay;
    }

    private int quantize(float value, int max, int bins) {
        int bin = (int) Math.floor((Math.max(0f, Math.min(value, max - 1f)) / max) * bins);
        return Math.max(0, Math.min(bins - 1, bin));
    }

    private void tap(float x, float y) {
        boolean restoreLockOverlay = touchLocked && touchLockOverlay != null;
        boolean restorePositionOverlay = positionsVisible && !positionMarkers.isEmpty();
        boolean restoreRemoteTrapOverlay = remoteTrapVisible && !remoteTrapOverlays.isEmpty();
        if (restoreLockOverlay || restorePositionOverlay || restoreRemoteTrapOverlay) {
            hideTouchLockOverlay();
            hidePositionOverlay();
            hideRemoteTrapOverlay();
            mainHandler.postDelayed(() -> performTap(x, y, restoreLockOverlay, restorePositionOverlay, restoreRemoteTrapOverlay), 90);
            return;
        }

        performTap(x, y, false, false, false);
    }

    private void performTap(float x, float y, boolean restoreLockOverlay, boolean restorePositionOverlay,
                            boolean restoreRemoteTrapOverlay) {
        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 80))
                .build();

        dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                restoreTemporaryOverlaysIfNeeded(restoreLockOverlay, restorePositionOverlay, restoreRemoteTrapOverlay);
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                restoreTemporaryOverlaysIfNeeded(restoreLockOverlay, restorePositionOverlay, restoreRemoteTrapOverlay);
            }
        }, mainHandler);
    }

    private void toggleTouchLock() {
        touchLocked = !touchLocked;
        if (touchLocked) {
            showTouchLockOverlay();
            Toast.makeText(this, "터치 잠금 ON", Toast.LENGTH_SHORT).show();
        } else {
            hideTouchLockOverlay();
            Toast.makeText(this, "터치 잠금 OFF", Toast.LENGTH_SHORT).show();
        }
    }

    private void togglePositionOverlayFromButton() {
        if (!positionsVisible && !hasAnySavedPoint()) {
            showNoSavedPointPanel();
            return;
        }

        positionsVisible = !positionsVisible;
        if (positionsVisible) {
            if (!configurationActive) {
                showPositionOverlay();
            }
            Toast.makeText(this, "위치 표시 ON", Toast.LENGTH_SHORT).show();
        } else {
            hidePositionOverlay();
            Toast.makeText(this, "위치 표시 OFF", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasAnySavedPoint() {
        return MappingStore.hasAnySavedPoint(this);
    }

    /**
     * 트랩 존은 가상 터치 컨트롤러가 주입하는 터치를 잡으려고 화면 일부를 덮는다.
     * 평상시 사용 중에만 필요하다. 앱 설정 화면이나 이 서비스의 패널이 떠 있는 동안
     * 남겨두면 그 화면의 버튼을 트랩이 먹어버린다.
     */
    private boolean shouldShowRemoteTrap() {
        return remoteTrapVisible && !configurationActive && !isAnyPanelOpen();
    }

    private boolean isAnyPanelOpen() {
        return setupOverlay != null
                || pickerOverlay != null
                || inputCaptureOverlay != null
                || noSavedPointOverlay != null
                || keyDiagnosticOverlay != null
                || motionDiagnosticOverlay != null
                || controllerAnalyzerOverlay != null
                || controllerDiagnosticOverlay != null
                || adbProbeOverlay != null;
    }

    /** 트랩을 지금 상태에 맞게 올리거나 내린다. 패널을 열고 닫을 때마다 호출한다. */
    private void refreshRemoteTrapOverlay() {
        if (shouldShowRemoteTrap()) {
            if (remoteTrapOverlays.isEmpty()) {
                showRemoteTrapOverlay();
            }
        } else if (!remoteTrapOverlays.isEmpty()) {
            hideRemoteTrapOverlay();
        }
    }

    /**
     * 저장된 좌표가 없는데 위치 표시를 켜려 할 때 띄우는 안내. 리모컨으로 눌렀을 때는
     * 앱 화면이 없으므로 서비스가 직접 오버레이로 보여준다.
     */
    private void showNoSavedPointPanel() {
        hideNoSavedPointPanel();
        if (windowManager == null) {
            return;
        }

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));
        panel.setBackground(roundedStroke(0xF5FFFFFF, dp(10), 0xFFE0E5EC, 1));
        panel.setElevation(dp(8));

        TextView title = new TextView(this);
        title.setText("설정된 좌표 없음");
        title.setTextColor(0xFF1F2933);
        title.setTextSize(16f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(6));
        panel.addView(title, panelParams());

        TextView message = new TextView(this);
        message.setText("좌표가 저장된 버튼이 없습니다.\n지금 위치를 설정하시겠습니까?");
        message.setTextColor(0xFF657282);
        message.setTextSize(13f);
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, 0, 0, dp(12));
        panel.addView(message, panelParams());

        Button setupButton = new Button(this);
        setupButton.setText("위치 설정");
        setupButton.setAllCaps(false);
        setupButton.setTextColor(0xFF2563EB);
        setupButton.setBackground(rounded(0xFFEAF2FF, dp(8)));
        setupButton.setOnClickListener(view -> {
            hideNoSavedPointPanel();
            showSetupPanel();
        });
        panel.addView(setupButton, panelParams());

        Button closeButton = new Button(this);
        closeButton.setText("닫기");
        closeButton.setAllCaps(false);
        closeButton.setTextColor(0xFF1F2933);
        closeButton.setBackground(roundedStroke(0xFFFFFFFF, dp(8), 0xFFE0E5EC, 1));
        closeButton.setOnClickListener(view -> hideNoSavedPointPanel());
        panel.addView(closeButton, panelParams());

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.CENTER;

        noSavedPointOverlay = panel;
        windowManager.addView(noSavedPointOverlay, params);
        refreshRemoteTrapOverlay();
    }

    private void hideNoSavedPointPanel() {
        if (noSavedPointOverlay == null || windowManager == null) {
            noSavedPointOverlay = null;
            return;
        }
        windowManager.removeView(noSavedPointOverlay);
        noSavedPointOverlay = null;
        refreshRemoteTrapOverlay();
    }

    private void restoreTemporaryOverlaysIfNeeded(boolean restoreLockOverlay, boolean restorePositionOverlay,
                                                  boolean restoreRemoteTrapOverlay) {
        mainHandler.postDelayed(() -> {
            if (restoreLockOverlay && touchLocked) {
                showTouchLockOverlay();
            }
            if (restoreRemoteTrapOverlay && remoteTrapVisible) {
                showRemoteTrapOverlay();
            }
            if (restorePositionOverlay && positionsVisible && !configurationActive) {
                showPositionOverlay();
            }
        }, 120);
    }

    private void vibrate(long durationMs) {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 장갑을 낀 채로도 느껴야 하므로 기본 세기 대신 최대 진폭을 쓴다.
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, MAX_VIBRATION_AMPLITUDE));
        } else {
            vibrator.vibrate(durationMs);
        }
    }

    private void showPointPicker(int slot) {
        hidePointPicker();
        hideSetupPanel();
        int targetSlot = Math.max(0, Math.min(MappingStore.SLOT_COUNT - 1, slot));

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0x77000000);

        TextView label = new TextView(this);
        label.setText("저장할 위치를 누르세요\n버튼 " + (targetSlot + 1) + " · " + MappingStore.buttonName(this, targetSlot));
        label.setTextColor(0xFFFFFFFF);
        label.setTextSize(20f);
        label.setGravity(Gravity.CENTER);
        label.setPadding(dp(24), dp(20), dp(24), dp(20));
        label.setBackground(rounded(0xCC1F2933, dp(8)));

        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        overlay.addView(label, labelParams);

        overlay.setOnTouchListener((View view, MotionEvent event) -> {
            if (event.getAction() != MotionEvent.ACTION_UP) {
                return true;
            }

            MappingStore.savePoint(this, targetSlot, event.getRawX(), event.getRawY());
            Toast.makeText(this, "버튼 " + (targetSlot + 1) + " 위치 저장 완료", Toast.LENGTH_SHORT).show();
            hidePointPicker();
            if (positionsVisible) {
                showPositionOverlay();
            }
            showSetupPanel();
            return true;
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;

        pickerOverlay = overlay;
        windowManager.addView(pickerOverlay, params);
        refreshRemoteTrapOverlay();
    }

    private void showSetupPanel() {
        hideSetupPanel();

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setBackground(roundedStroke(0xEEFFFFFF, dp(8), 0xFFE0E5EC, 1));
        panel.setElevation(dp(6));

        TextView title = new TextView(this);
        title.setText("위치 설정 · " + MappingStore.profileName(this));
        title.setTextColor(0xFF1F2933);
        title.setTextSize(15f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(8));
        title.setOnTouchListener(new DragMoveListener());
        panel.addView(title, panelParams());

        TextView help = new TextView(this);
        help.setText("이 영역을 드래그해서 이동\n원하는 장면에서 버튼을 누르세요.");
        help.setTextColor(0xFF657282);
        help.setTextSize(12f);
        help.setGravity(Gravity.CENTER);
        help.setPadding(dp(4), 0, dp(4), dp(8));
        help.setOnTouchListener(new DragMoveListener());
        panel.addView(help, panelParams());

        for (int slot = 0; slot < MappingStore.SLOT_COUNT; slot++) {
            final int targetSlot = slot;
            Button button = new Button(this);
            button.setText("버튼 " + (slot + 1) + " · " + MappingStore.buttonName(this, slot));
            button.setAllCaps(false);
            button.setTextColor(0xFF2563EB);
            button.setBackground(rounded(0xFFEAF2FF, dp(8)));
            button.setOnClickListener(view -> showPointPicker(targetSlot));
            panel.addView(button, panelParams());
        }

        Button closeButton = new Button(this);
        closeButton.setText("닫기");
        closeButton.setAllCaps(false);
        closeButton.setTextColor(0xFF1F2933);
        closeButton.setBackground(roundedStroke(0xFFFFFFFF, dp(8), 0xFFE0E5EC, 1));
        closeButton.setOnClickListener(view -> hideSetupPanel());
        panel.addView(closeButton, panelParams());

        setupOverlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        setupOverlayParams.gravity = Gravity.TOP | Gravity.START;
        setupOverlayParams.x = 18;
        setupOverlayParams.y = 120;

        setupOverlay = panel;
        windowManager.addView(setupOverlay, setupOverlayParams);
        // 좌표를 잡는 동안 이미 저장된 위치가 보여야 겹치지 않게 놓을 수 있다.
        // 설정을 마친 뒤 따로 위치 표시를 다시 켜야 하는 흐름은 번거롭다.
        positionsVisible = true;
        showPositionOverlay();
        refreshRemoteTrapOverlay();
    }

    private void showInputCapturePanel(int slot, boolean longMode) {
        hideInputCapturePanel();
        inputCaptureSlot = Math.max(0, Math.min(MappingStore.SLOT_COUNT - 1, slot));
        inputCaptureLongMode = longMode;
        inputCaptureKeyCode = KeyEvent.KEYCODE_UNKNOWN;
        inputCapturePendingKeyCode = KeyEvent.KEYCODE_UNKNOWN;
        inputCaptureMotionObserved = false;
        mainHandler.removeCallbacks(finishPendingInputCaptureKeyRunnable);
        inputCaptureMotionActive = false;
        inputCaptureMotionSignature = "";
        inputCaptureBurst.reset();
        inputCaptureSampleAttempts = 0;
        inputCaptureSampleTarget = INPUT_CAPTURE_SAMPLE_TARGET;
        inputCaptureSampleKeyCode = KeyEvent.KEYCODE_UNKNOWN;
        inputCaptureSampleDirection = MappingStore.TRIGGER_UNKNOWN;
        inputCaptureSampleSignatures.clear();
        inputCaptureAnchors.clear();
        inputCaptureStatus = null;
        updateMotionCapture();

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0x66000000);
        overlay.setFocusable(true);
        overlay.setFocusableInTouchMode(true);
        overlay.setOnTouchListener((view, event) -> handleInputCaptureMotionEvent(event));
        overlay.setOnGenericMotionListener((view, event) -> handleInputCaptureMotionEvent(event));
        overlay.setOnHoverListener((view, event) -> handleInputCaptureMotionEvent(event));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));
        panel.setBackground(roundedStroke(0xF8FFFFFF, dp(8), 0xFFE0E5EC, 1));
        panel.setElevation(dp(8));

        TextView title = new TextView(this);
        title.setText("버튼 " + (inputCaptureSlot + 1) + " 입력 대기");
        title.setTextColor(0xFF1F2933);
        title.setTextSize(17f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(8));
        panel.addView(title, panelParams());

        TextView help = new TextView(this);
        help.setText(!longMode && selectedDeviceSupportsMotionLearning()
                ? "같은 리모컨 버튼을 5번 눌렀다가 떼세요.\n서로 다른 모션 신호를 모두 한 버튼으로 저장합니다."
                : "리모컨 버튼을 한 번 눌렀다가 떼세요.\n키 입력은 DOWN부터 UP까지 한 세트로 저장합니다.");
        help.setTextColor(0xFF657282);
        help.setTextSize(13f);
        help.setGravity(Gravity.CENTER);
        help.setPadding(dp(4), 0, dp(4), dp(10));
        panel.addView(help, panelParams());

        inputCaptureStatus = new TextView(this);
        inputCaptureStatus.setTextColor(0xFF2563EB);
        inputCaptureStatus.setTextSize(13f);
        inputCaptureStatus.setGravity(Gravity.CENTER);
        inputCaptureStatus.setPadding(dp(4), 0, dp(4), dp(10));
        panel.addView(inputCaptureStatus, panelParams());
        updateInputCaptureStatus();

        Button cancelButton = new Button(this);
        cancelButton.setText("취소");
        cancelButton.setAllCaps(false);
        cancelButton.setTextColor(0xFF1F2933);
        cancelButton.setBackground(roundedStroke(0xFFFFFFFF, dp(8), 0xFFE0E5EC, 1));
        cancelButton.setOnClickListener(view -> hideInputCapturePanel());
        panel.addView(cancelButton, panelParams());

        FrameLayout.LayoutParams panelFrameParams = new FrameLayout.LayoutParams(
                dp(320),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        panelFrameParams.setMargins(dp(16), 0, dp(16), 0);
        overlay.addView(panel, panelFrameParams);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;

        inputCaptureOverlay = overlay;
        windowManager.addView(inputCaptureOverlay, params);
        overlay.requestFocus();
        refreshRemoteTrapOverlay();
    }

    private void showKeyDiagnosticPanel() {
        hideKeyDiagnosticPanel();

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setBackground(roundedStroke(0xF8FFFFFF, dp(8), 0xFFE0E5EC, 1));
        panel.setElevation(dp(6));

        TextView title = new TextView(this);
        title.setText("키 진단");
        title.setTextColor(0xFF1F2933);
        title.setTextSize(15f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(8));
        title.setOnTouchListener(new KeyDiagnosticDragMoveListener());
        panel.addView(title, panelParams());

        TextView help = new TextView(this);
        help.setText("리모컨 버튼을 누르면 장치와 키 코드가 표시됩니다.");
        help.setTextColor(0xFF657282);
        help.setTextSize(12f);
        help.setGravity(Gravity.CENTER);
        help.setPadding(dp(4), 0, dp(4), dp(8));
        help.setOnTouchListener(new KeyDiagnosticDragMoveListener());
        panel.addView(help, panelParams());

        keyDiagnosticLog = new TextView(this);
        keyDiagnosticLog.setTextColor(0xFF1F2933);
        keyDiagnosticLog.setTextSize(12f);
        keyDiagnosticLog.setPadding(dp(10), dp(8), dp(10), dp(8));
        keyDiagnosticLog.setBackground(rounded(0xFFF1F5F9, dp(8)));
        panel.addView(keyDiagnosticLog, panelParams());
        updateKeyDiagnosticLog();

        Button clearButton = new Button(this);
        clearButton.setText("기록 지우기");
        clearButton.setAllCaps(false);
        clearButton.setTextColor(0xFF2563EB);
        clearButton.setBackground(rounded(0xFFEAF2FF, dp(8)));
        clearButton.setOnClickListener(view -> {
            keyDiagnosticLines.clear();
            updateKeyDiagnosticLog();
        });
        panel.addView(clearButton, panelParams());

        Button closeButton = new Button(this);
        closeButton.setText("닫기");
        closeButton.setAllCaps(false);
        closeButton.setTextColor(0xFF1F2933);
        closeButton.setBackground(roundedStroke(0xFFFFFFFF, dp(8), 0xFFE0E5EC, 1));
        closeButton.setOnClickListener(view -> hideKeyDiagnosticPanel());
        panel.addView(closeButton, panelParams());

        keyDiagnosticOverlayParams = new WindowManager.LayoutParams(
                dp(280),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        keyDiagnosticOverlayParams.gravity = Gravity.TOP | Gravity.START;
        keyDiagnosticOverlayParams.x = 18;
        keyDiagnosticOverlayParams.y = 220;

        keyDiagnosticOverlay = panel;
        windowManager.addView(keyDiagnosticOverlay, keyDiagnosticOverlayParams);
    }

    private void showMotionDiagnosticPanel() {
        hideMotionDiagnosticPanel();

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0x22000000);
        overlay.setFocusable(true);
        overlay.setFocusableInTouchMode(true);
        overlay.setOnTouchListener((view, event) -> handleMotionDiagnosticEvent(event, "TOUCH"));
        overlay.setOnGenericMotionListener((view, event) -> handleMotionDiagnosticEvent(event, "GENERIC"));
        overlay.setOnHoverListener((view, event) -> handleMotionDiagnosticEvent(event, "HOVER"));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setBackground(roundedStroke(0xF8FFFFFF, dp(8), 0xFFE0E5EC, 1));
        panel.setElevation(dp(6));

        TextView title = new TextView(this);
        title.setText("터치 진단");
        title.setTextColor(0xFF1F2933);
        title.setTextSize(15f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(8));
        panel.addView(title, panelParams());

        motionDiagnosticLog = new TextView(this);
        motionDiagnosticLog.setTextColor(0xFF1F2933);
        motionDiagnosticLog.setTextSize(12f);
        motionDiagnosticLog.setPadding(dp(10), dp(8), dp(10), dp(8));
        motionDiagnosticLog.setBackground(rounded(0xFFF1F5F9, dp(8)));
        panel.addView(motionDiagnosticLog, panelParams());
        updateMotionDiagnosticLog();

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button clearButton = new Button(this);
        clearButton.setText("기록 지우기");
        clearButton.setAllCaps(false);
        clearButton.setTextColor(0xFF2563EB);
        clearButton.setBackground(rounded(0xFFEAF2FF, dp(8)));
        clearButton.setOnClickListener(view -> {
            motionDiagnosticLines.clear();
            updateMotionDiagnosticLog();
        });
        actions.addView(clearButton, panelHalfParams());

        Button closeButton = new Button(this);
        closeButton.setText("닫기");
        closeButton.setAllCaps(false);
        closeButton.setTextColor(0xFF1F2933);
        closeButton.setBackground(roundedStroke(0xFFFFFFFF, dp(8), 0xFFE0E5EC, 1));
        closeButton.setOnClickListener(view -> hideMotionDiagnosticPanel());
        actions.addView(closeButton, panelHalfParams());
        panel.addView(actions, panelParams());

        FrameLayout.LayoutParams panelFrameParams = new FrameLayout.LayoutParams(
                dp(310),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL
        );
        panelFrameParams.setMargins(dp(12), dp(42), dp(12), 0);
        overlay.addView(panel, panelFrameParams);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;

        motionDiagnosticOverlay = overlay;
        windowManager.addView(motionDiagnosticOverlay, params);
        overlay.requestFocus();
    }

    private void showControllerAnalyzerPanel() {
        hideControllerAnalyzerPanel();

        controllerAnalyzerLines.clear();
        controllerAnalyzerReport.setLength(0);
        controllerAnalyzerStartMs = System.currentTimeMillis();
        lastControllerAnalyzerMoveMs = 0L;

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0x18000000);
        overlay.setFocusable(true);
        overlay.setFocusableInTouchMode(true);
        overlay.setOnTouchListener((view, event) -> handleControllerAnalyzerMotionEvent(event, "TOUCH"));
        overlay.setOnGenericMotionListener((view, event) -> handleControllerAnalyzerMotionEvent(event, "GENERIC"));
        overlay.setOnHoverListener((view, event) -> handleControllerAnalyzerMotionEvent(event, "HOVER"));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setBackground(roundedStroke(0xFAFFFFFF, dp(8), 0xFFE0E5EC, 1));
        panel.setElevation(dp(8));

        TextView title = new TextView(this);
        title.setText("Remote Analyzer");
        title.setTextColor(0xFF1F2933);
        title.setTextSize(15f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(8));
        panel.addView(title, panelParams());

        TextView help = new TextView(this);
        help.setText("Test the controller here. Events are blocked while analyzer is open.");
        help.setTextColor(0xFF657282);
        help.setTextSize(11f);
        help.setGravity(Gravity.CENTER);
        help.setPadding(dp(4), 0, dp(4), dp(6));
        panel.addView(help, panelParams());

        controllerAnalyzerStatus = new TextView(this);
        controllerAnalyzerStatus.setTextColor(0xFF475569);
        controllerAnalyzerStatus.setTextSize(12f);
        controllerAnalyzerStatus.setGravity(Gravity.CENTER);
        controllerAnalyzerStatus.setPadding(dp(6), dp(4), dp(6), dp(4));
        controllerAnalyzerStatus.setBackground(rounded(0xFFF8FAFC, dp(8)));
        panel.addView(controllerAnalyzerStatus, panelParams());

        controllerAnalyzerLog = new TextView(this);
        controllerAnalyzerLog.setTextColor(0xFF1F2933);
        controllerAnalyzerLog.setTextSize(10.5f);
        controllerAnalyzerLog.setPadding(dp(10), dp(8), dp(10), dp(8));
        controllerAnalyzerLog.setBackground(rounded(0xFFF1F5F9, dp(8)));
        controllerAnalyzerLog.setMaxLines(22);
        panel.addView(controllerAnalyzerLog, panelParams());
        updateControllerAnalyzerLog();

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        controllerAnalyzerStartButton = new Button(this);
        controllerAnalyzerStartButton.setText("Start");
        controllerAnalyzerStartButton.setAllCaps(false);
        controllerAnalyzerStartButton.setOnClickListener(view -> {
            if (controllerAnalyzerCapturing) {
                stopControllerAnalyzerSession();
            } else {
                startControllerAnalyzerSession();
            }
        });
        actions.addView(controllerAnalyzerStartButton, panelHalfParams());

        Button saveButton = new Button(this);
        saveButton.setText("Save");
        saveButton.setAllCaps(false);
        saveButton.setTextColor(0xFFC2410C);
        saveButton.setBackground(rounded(0xFFFFF7ED, dp(8)));
        saveButton.setOnClickListener(view -> saveControllerAnalyzerReport());
        actions.addView(saveButton, panelHalfParams());

        Button closeButton = new Button(this);
        closeButton.setText("Close");
        closeButton.setAllCaps(false);
        closeButton.setTextColor(0xFF1F2933);
        closeButton.setBackground(roundedStroke(0xFFFFFFFF, dp(8), 0xFFE0E5EC, 1));
        closeButton.setOnClickListener(view -> hideControllerAnalyzerPanel());
        actions.addView(closeButton, panelHalfParams());
        panel.addView(actions, panelParams());
        updateControllerAnalyzerState();

        FrameLayout.LayoutParams panelFrameParams = new FrameLayout.LayoutParams(
                dp(340),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL
        );
        panelFrameParams.setMargins(dp(8), dp(54), dp(8), 0);
        overlay.addView(panel, panelFrameParams);

        Button floatingCloseButton = new Button(this);
        floatingCloseButton.setText("X");
        floatingCloseButton.setAllCaps(false);
        floatingCloseButton.setTextColor(0xFFFFFFFF);
        floatingCloseButton.setTextSize(14f);
        floatingCloseButton.setBackground(rounded(0xCC111827, dp(20)));
        floatingCloseButton.setOnClickListener(view -> hideControllerAnalyzerPanel());
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                dp(48),
                dp(42),
                Gravity.TOP | Gravity.END
        );
        closeParams.setMargins(0, dp(8), dp(8), 0);
        overlay.addView(floatingCloseButton, closeParams);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;

        controllerAnalyzerOverlay = overlay;
        windowManager.addView(controllerAnalyzerOverlay, params);
        overlay.requestFocus();
    }

    private void showAdbProbePanel(int buttonCount) {
        hideAdbProbePanel();

        adbProbeButtonCount = Math.max(1, buttonCount);
        adbProbeCurrentButton = 1;
        long wallStartedAt = System.currentTimeMillis();
        adbProbeStartMs = 0L;
        lastAdbProbeMoveMs = 0L;
        adbProbeBurst.reset();
        adbProbeLines.clear();
        adbProbeReport.setLength(0);
        adbProbeButtonReport.setLength(0);
        adbProbeReport.append("ADB Probe Session\n")
                .append("buttons=").append(adbProbeButtonCount).append("\n")
                .append("startedAt=").append(wallStartedAt).append("\n\n")
                .append("---- BEGIN BUTTON 1 ----\n\n");
        Log.i(PROBE_TAG, "ADB Probe start buttons=" + adbProbeButtonCount);

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0x18000000);
        overlay.setFocusable(true);
        overlay.setFocusableInTouchMode(true);
        overlay.setOnTouchListener((view, event) -> handleAdbProbeMotionEvent(event, "TOUCH"));
        overlay.setOnGenericMotionListener((view, event) -> handleAdbProbeMotionEvent(event, "GENERIC"));
        overlay.setOnHoverListener((view, event) -> handleAdbProbeMotionEvent(event, "HOVER"));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setBackground(roundedStroke(0xFAFFFFFF, dp(8), 0xFFE0E5EC, 1));
        panel.setElevation(dp(8));

        TextView title = new TextView(this);
        title.setText("Motion button learning");
        title.setTextColor(0xFF1F2933);
        title.setTextSize(15f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(8));
        panel.addView(title, panelParams());

        TextView help = new TextView(this);
        help.setText("Press the current physical button once, then tap Done. The Done tap is not recorded.");
        help.setTextColor(0xFF657282);
        help.setTextSize(11f);
        help.setGravity(Gravity.CENTER);
        help.setPadding(dp(4), 0, dp(4), dp(6));
        panel.addView(help, panelParams());

        adbProbeLog = new TextView(this);
        adbProbeLog.setTextColor(0xFF1F2933);
        adbProbeLog.setTextSize(10.5f);
        adbProbeLog.setPadding(dp(10), dp(8), dp(10), dp(8));
        adbProbeLog.setBackground(rounded(0xFFF1F5F9, dp(8)));
        adbProbeLog.setMaxLines(18);
        panel.addView(adbProbeLog, panelParams());
        updateAdbProbeLog();

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button doneButton = new Button(this);
        doneButton.setText("Done");
        doneButton.setAllCaps(false);
        doneButton.setTextColor(0xFF047857);
        doneButton.setBackground(rounded(0xFFECFDF3, dp(8)));
        doneButton.setOnClickListener(view -> completeAdbProbeButton());
        actions.addView(doneButton, panelHalfParams());

        Button closeButton = new Button(this);
        closeButton.setText("Close");
        closeButton.setAllCaps(false);
        closeButton.setTextColor(0xFF1F2933);
        closeButton.setBackground(roundedStroke(0xFFFFFFFF, dp(8), 0xFFE0E5EC, 1));
        closeButton.setOnClickListener(view -> hideAdbProbePanel());
        actions.addView(closeButton, panelHalfParams());
        panel.addView(actions, panelParams());

        FrameLayout.LayoutParams panelFrameParams = new FrameLayout.LayoutParams(
                dp(340),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL
        );
        panelFrameParams.setMargins(dp(8), dp(54), dp(8), 0);
        overlay.addView(panel, panelFrameParams);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;

        adbProbeOverlay = overlay;
        windowManager.addView(adbProbeOverlay, params);
        overlay.requestFocus();
        updateMotionCapture();
    }

    private LinearLayout.LayoutParams panelParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(3), 0, dp(3));
        return params;
    }

    private LinearLayout.LayoutParams panelHalfParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        return params;
    }

    private void hideSetupPanel() {
        if (setupOverlay == null || windowManager == null) {
            setupOverlay = null;
            return;
        }

        windowManager.removeView(setupOverlay);
        setupOverlay = null;
        setupOverlayParams = null;
        refreshRemoteTrapOverlay();
    }

    private void hideInputCapturePanel() {
        inputCaptureSlot = -1;
        inputCaptureLongMode = false;
        inputCaptureKeyCode = KeyEvent.KEYCODE_UNKNOWN;
        inputCapturePendingKeyCode = KeyEvent.KEYCODE_UNKNOWN;
        inputCaptureMotionObserved = false;
        mainHandler.removeCallbacks(finishPendingInputCaptureKeyRunnable);
        inputCaptureMotionActive = false;
        inputCaptureMotionSignature = "";
        inputCaptureBurst.reset();
        inputCaptureSampleAttempts = 0;
        inputCaptureSampleTarget = INPUT_CAPTURE_SAMPLE_TARGET;
        inputCaptureSampleKeyCode = KeyEvent.KEYCODE_UNKNOWN;
        inputCaptureSampleDirection = MappingStore.TRIGGER_UNKNOWN;
        inputCaptureSampleSignatures.clear();
        inputCaptureAnchors.clear();
        inputCaptureStatus = null;
        updateMotionCapture();
        if (inputCaptureOverlay == null || windowManager == null) {
            inputCaptureOverlay = null;
            return;
        }

        windowManager.removeView(inputCaptureOverlay);
        inputCaptureOverlay = null;
        refreshRemoteTrapOverlay();
    }

    private void hideKeyDiagnosticPanel() {
        if (keyDiagnosticOverlay == null || windowManager == null) {
            keyDiagnosticOverlay = null;
            keyDiagnosticOverlayParams = null;
            keyDiagnosticLog = null;
            return;
        }

        windowManager.removeView(keyDiagnosticOverlay);
        keyDiagnosticOverlay = null;
        keyDiagnosticOverlayParams = null;
        keyDiagnosticLog = null;
    }

    private void hideMotionDiagnosticPanel() {
        if (motionDiagnosticOverlay == null || windowManager == null) {
            motionDiagnosticOverlay = null;
            motionDiagnosticLog = null;
            return;
        }

        windowManager.removeView(motionDiagnosticOverlay);
        motionDiagnosticOverlay = null;
        motionDiagnosticLog = null;
    }

    private void hideControllerAnalyzerPanel() {
        controllerAnalyzerCapturing = false;
        if (controllerAnalyzerOverlay == null || windowManager == null) {
            controllerAnalyzerOverlay = null;
            controllerAnalyzerLog = null;
            controllerAnalyzerStatus = null;
            controllerAnalyzerStartButton = null;
            return;
        }

        windowManager.removeView(controllerAnalyzerOverlay);
        controllerAnalyzerOverlay = null;
        controllerAnalyzerLog = null;
        controllerAnalyzerStatus = null;
        controllerAnalyzerStartButton = null;
    }

    private void hideAdbProbePanel() {
        adbProbeBurst.reset();
        if (adbProbeOverlay == null || windowManager == null) {
            adbProbeOverlay = null;
            adbProbeLog = null;
            updateMotionCapture();
            return;
        }

        windowManager.removeView(adbProbeOverlay);
        adbProbeOverlay = null;
        adbProbeLog = null;
        updateMotionCapture();
    }

    private void hideControllerDiagnosticPanel() {
        controllerDiagnosticMotionActive = false;
        if (controllerDiagnosticOverlay == null || windowManager == null) {
            controllerDiagnosticOverlay = null;
            controllerDiagnosticLog = null;
            return;
        }

        windowManager.removeView(controllerDiagnosticOverlay);
        controllerDiagnosticOverlay = null;
        controllerDiagnosticLog = null;
    }

    private void showRemoteTrapOverlay() {
        if (!remoteTrapOverlays.isEmpty() || windowManager == null) {
            return;
        }

        if (MappingStore.inputDeviceMode(this) == MappingStore.DEVICE_MODE_KEYBOARD) {
            return;
        }

        int inputMode = MappingStore.inputDeviceMode(this);
        List<MappingStore.TrapZone> savedZones = MappingStore.trapZones(this);
        if ((inputMode == MappingStore.DEVICE_MODE_MOTION || inputMode == MappingStore.DEVICE_MODE_MIXED)
                && !savedZones.isEmpty()) {
            for (MappingStore.TrapZone zone : savedZones) {
                addRemoteTrapView(zone.width, zone.height, Gravity.TOP | Gravity.START, zone.x, zone.y);
            }
            updateMotionCapture();
            return;
        }

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int topWidth = Math.max(dp(120), screenWidth / 7);
        int topHeight = Math.max(dp(120), screenHeight / 8);
        int downWidth = Math.max(dp(220), screenWidth / 4);
        int downHeight = Math.max(dp(420), screenHeight / 3);
        int downX = Math.max(0, screenWidth / 5);

        addRemoteTrapView(topWidth, topHeight, Gravity.TOP | Gravity.END, 0, 0);
        addRemoteTrapView(downWidth, downHeight, Gravity.BOTTOM | Gravity.START, downX, 0);
        updateMotionCapture();
    }

    private void addRemoteTrapView(int width, int height, int gravity, int x, int y) {
        FrameLayout trap = new FrameLayout(this);
        trap.setBackgroundColor(0x01000000);
        trap.setPointerIcon(transparentPointerIcon());
        trap.setOnTouchListener((view, event) -> handleRemoteTrapEvent(event));
        trap.setOnGenericMotionListener((view, event) -> handleRemoteTrapEvent(event));
        trap.setOnHoverListener((view, event) -> handleRemoteTrapEvent(event));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = gravity;
        params.x = x;
        params.y = y;

        windowManager.addView(trap, params);
        remoteTrapOverlays.add(trap);
    }

    private PointerIcon transparentPointerIcon() {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0x00000000);
        return PointerIcon.create(bitmap, 0f, 0f);
    }

    private boolean handleRemoteTrapEvent(MotionEvent event) {
        if (!isAcceptedMouseEvent(event)) {
            if (MappingStore.isFullScreenTrapMode(this)
                    && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                temporarilyHideRemoteTrapForTouch();
            }
            return false;
        }
        return handleRemoteMouseGesture(event);
    }

    private void temporarilyHideRemoteTrapForTouch() {
        if (!remoteTrapVisible || remoteTrapOverlays.isEmpty()) {
            return;
        }
        hideRemoteTrapOverlay();
        mainHandler.postDelayed(() -> {
            if (remoteTrapVisible && remoteTrapOverlays.isEmpty()) {
                showRemoteTrapOverlay();
            }
        }, 850L);
    }

    private void hideRemoteTrapOverlay() {
        if (remoteTrapOverlays.isEmpty() || windowManager == null) {
            remoteTrapOverlays.clear();
            updateMotionCapture();
            return;
        }

        for (View overlay : new ArrayList<>(remoteTrapOverlays)) {
            windowManager.removeView(overlay);
        }
        remoteTrapOverlays.clear();
        updateMotionCapture();
    }

    private void showPositionOverlay() {
        hidePositionOverlay();

        for (int slot = 0; slot < MappingStore.SLOT_COUNT; slot++) {
            MappingStore.Mapping mapping = MappingStore.get(this, slot);
            if (!mapping.hasPoint()) {
                continue;
            }

            TextView marker = new TextView(this);
            marker.setText((slot + 1) + "\n" + mapping.name);
            marker.setTextColor(0xFFFFFFFF);
            marker.setTextSize(12f);
            marker.setGravity(Gravity.CENTER);
            marker.setPadding(dp(8), dp(5), dp(8), dp(5));
            marker.setBackground(rounded(0xDD2563EB, dp(8)));

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = Math.max(0, Math.round(mapping.x) - dp(28));
            params.y = Math.max(0, Math.round(mapping.y) - dp(16));

            marker.setOnTouchListener(new MarkerMoveListener(slot, params));
            windowManager.addView(marker, params);
            positionMarkers.add(marker);
        }
    }

    private void hidePositionOverlay() {
        if (positionMarkers.isEmpty() || windowManager == null) {
            positionMarkers.clear();
            return;
        }

        for (View marker : new ArrayList<>(positionMarkers)) {
            windowManager.removeView(marker);
        }
        positionMarkers.clear();
    }

    private final class MarkerMoveListener implements View.OnTouchListener {
        private final int slot;
        private final WindowManager.LayoutParams params;
        private int startX;
        private int startY;
        private float downRawX;
        private float downRawY;
        private boolean moving;
        private final Runnable startMovingRunnable = new Runnable() {
            @Override
            public void run() {
                moving = true;
                vibrate(180);
                Toast.makeText(TouchAccessibilityService.this, "위치 이동 모드", Toast.LENGTH_SHORT).show();
            }
        };

        MarkerMoveListener(int slot, WindowManager.LayoutParams params) {
            this.slot = slot;
            this.params = params;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (windowManager == null) {
                return true;
            }

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                startX = params.x;
                startY = params.y;
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                moving = false;
                mainHandler.postDelayed(startMovingRunnable, MOVE_MARKER_LONG_PRESS_MS);
                return true;
            }

            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                if (!moving) {
                    return true;
                }
                params.x = startX + Math.round(event.getRawX() - downRawX);
                params.y = startY + Math.round(event.getRawY() - downRawY);
                windowManager.updateViewLayout(view, params);
                return true;
            }

            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                mainHandler.removeCallbacks(startMovingRunnable);
                if (moving) {
                    float centerX = params.x + view.getWidth() / 2f;
                    float centerY = params.y + view.getHeight() / 2f;
                    MappingStore.savePoint(TouchAccessibilityService.this, slot, centerX, centerY);
                    vibrate(90);
                    Toast.makeText(TouchAccessibilityService.this, "버튼 " + (slot + 1) + " 위치 이동 완료", Toast.LENGTH_SHORT).show();
                }
                moving = false;
                return true;
            }

            return true;
        }
    }

    private final class DragMoveListener implements View.OnTouchListener {
        private int startX;
        private int startY;
        private float downRawX;
        private float downRawY;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (setupOverlay == null || setupOverlayParams == null || windowManager == null) {
                return false;
            }

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                startX = setupOverlayParams.x;
                startY = setupOverlayParams.y;
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                return true;
            }

            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                setupOverlayParams.x = startX + Math.round(event.getRawX() - downRawX);
                setupOverlayParams.y = startY + Math.round(event.getRawY() - downRawY);
                windowManager.updateViewLayout(setupOverlay, setupOverlayParams);
                return true;
            }

            return true;
        }
    }

    private final class KeyDiagnosticDragMoveListener implements View.OnTouchListener {
        private int startX;
        private int startY;
        private float downRawX;
        private float downRawY;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (keyDiagnosticOverlay == null || keyDiagnosticOverlayParams == null || windowManager == null) {
                return false;
            }

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                startX = keyDiagnosticOverlayParams.x;
                startY = keyDiagnosticOverlayParams.y;
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                return true;
            }

            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                keyDiagnosticOverlayParams.x = startX + Math.round(event.getRawX() - downRawX);
                keyDiagnosticOverlayParams.y = startY + Math.round(event.getRawY() - downRawY);
                windowManager.updateViewLayout(keyDiagnosticOverlay, keyDiagnosticOverlayParams);
                return true;
            }

            return true;
        }
    }

    private void hidePointPicker() {
        if (pickerOverlay == null || windowManager == null) {
            pickerOverlay = null;
            return;
        }

        windowManager.removeView(pickerOverlay);
        pickerOverlay = null;
        refreshRemoteTrapOverlay();
    }

    private void showTouchLockOverlay() {
        if (touchLockOverlay != null || windowManager == null) {
            return;
        }

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0x11000000);

        TextView label = new TextView(this);
        label.setText("터치 잠금");
        label.setTextColor(0xAAFFFFFF);
        label.setTextSize(14f);
        label.setGravity(Gravity.CENTER);
        label.setPadding(dp(14), dp(8), dp(14), dp(8));
        label.setBackground(rounded(0x88000000, dp(8)));

        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END
        );
        labelParams.setMargins(0, 36, 24, 0);
        overlay.addView(label, labelParams);

        overlay.setOnTouchListener((view, event) -> {
            handleRemoteMouseGesture(event);
            return true;
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;

        touchLockOverlay = overlay;
        windowManager.addView(touchLockOverlay, params);
    }

    private void hideTouchLockOverlay() {
        if (touchLockOverlay == null || windowManager == null) {
            touchLockOverlay = null;
            return;
        }

        windowManager.removeView(touchLockOverlay);
        touchLockOverlay = null;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable roundedStroke(int color, int radius, int strokeColor, int strokeDp) {
        GradientDrawable drawable = rounded(color, radius);
        if (strokeDp > 0) {
            drawable.setStroke(dp(strokeDp), strokeColor);
        }
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
