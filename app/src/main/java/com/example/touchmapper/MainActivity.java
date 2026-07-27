package com.example.touchmapper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.InputDevice;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int COLOR_PAGE = 0xFFF4F6F8;
    private static final int COLOR_SURFACE = 0xFFFFFFFF;
    private static final int COLOR_TEXT = 0xFF1F2933;
    private static final int COLOR_MUTED = 0xFF657282;
    private static final int COLOR_PRIMARY = 0xFF2563EB;
    private static final int COLOR_PRIMARY_SOFT = 0xFFEAF2FF;
    private static final int COLOR_BORDER = 0xFFE0E5EC;

    private static MainActivity activeInstance;

    private LinearLayout mappingsContainer;
    private TextView statusText;
    private Button inputDeviceButton;
    private TextView inputDeviceInfoText;
    private Button showPositionsButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        activeInstance = this;
        TouchAccessibilityService.setConfigurationActive(true);
        renderMappings();
    }

    @Override
    protected void onPause() {
        if (activeInstance == this) {
            activeInstance = null;
        }
        TouchAccessibilityService.setConfigurationActive(false);
        super.onPause();
    }

    static void refreshIfVisible() {
        MainActivity activity = activeInstance;
        if (activity != null) {
            activity.runOnUiThread(activity::renderMappings);
        }
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_PAGE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(28));
        scrollView.addView(root);

        LinearLayout brandHeader = new LinearLayout(this);
        brandHeader.setOrientation(LinearLayout.HORIZONTAL);
        brandHeader.setPadding(0, 0, 0, dp(16));
        root.addView(brandHeader, matchWidthParams());

        if (BuildConfig.SHOW_BRANDING) {
            ImageView logo = new ImageView(this);
            logo.setImageResource(R.drawable.monster_logo);
            logo.setAdjustViewBounds(true);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(148), dp(56));
            logoParams.setMargins(0, 0, dp(12), 0);
            brandHeader.addView(logo, logoParams);
        }

        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        brandText.setGravity(android.view.Gravity.CENTER_VERTICAL);
        brandHeader.addView(brandText, new LinearLayout.LayoutParams(0, dp(56), 1f));

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextSize(22f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(COLOR_TEXT);
        brandText.addView(title);

        if (BuildConfig.SHOW_BRANDING) {
            TextView subtitle = new TextView(this);
            subtitle.setText(getString(R.string.app_subtitle));
            subtitle.setTextSize(13f);
            subtitle.setTextColor(COLOR_MUTED);
            subtitle.setPadding(0, dp(2), 0, 0);
            brandText.addView(subtitle);
        }

        statusText = new TextView(this);
        statusText.setTextSize(15f);
        statusText.setTextColor(0xFF29415F);
        statusText.setPadding(dp(14), dp(12), dp(14), dp(12));
        statusText.setBackground(rounded(COLOR_PRIMARY_SOFT, dp(8)));
        LinearLayout.LayoutParams statusParams = matchWidthParams();
        statusParams.setMargins(0, 0, 0, dp(14));
        root.addView(statusText, statusParams);

        LinearLayout utilityActions = new LinearLayout(this);
        utilityActions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams utilityParams = matchWidthParams();
        utilityParams.setMargins(0, 0, 0, dp(10));
        root.addView(utilityActions, utilityParams);

        Button accessibilityButton = makeSmallButton("접근성", COLOR_PRIMARY, 0xFFFFFFFF);
        accessibilityButton.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        utilityActions.addView(accessibilityButton, utilityButtonParams());

        Button appSettingsButton = makeSmallButton("앱 설정", COLOR_SURFACE, COLOR_TEXT);
        appSettingsButton.setOnClickListener(view -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });
        utilityActions.addView(appSettingsButton, utilityButtonParams());

        Button resetButton = makeSmallButton("초기화", 0xFFFFF1F2, 0xFFB42318);
        resetButton.setOnClickListener(view -> confirmReset());
        utilityActions.addView(resetButton, utilityButtonParams());

        inputDeviceButton = makeButton("", 0xFFFFFFFF, COLOR_TEXT);
        inputDeviceButton.setOnClickListener(view -> showInputDevicePicker());
        root.addView(inputDeviceButton, matchWidthParams());

        inputDeviceInfoText = new TextView(this);
        inputDeviceInfoText.setTextSize(13f);
        inputDeviceInfoText.setTextColor(COLOR_MUTED);
        inputDeviceInfoText.setPadding(dp(12), dp(10), dp(12), dp(10));
        inputDeviceInfoText.setBackground(roundedStroke(COLOR_SURFACE, dp(8), COLOR_BORDER, 1));
        LinearLayout.LayoutParams deviceInfoParams = matchWidthParams();
        deviceInfoParams.setMargins(0, 0, 0, dp(12));
        root.addView(inputDeviceInfoText, deviceInfoParams);

        LinearLayout setupActions = new LinearLayout(this);
        setupActions.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(setupActions, matchWidthParams());

        Button setupPanelButton = makeButton("위치 설정", 0xFFEAF2FF, COLOR_PRIMARY);
        setupPanelButton.setOnClickListener(view -> {
            boolean started = TouchAccessibilityService.showSetupOverlay();
            if (!started) {
                Toast.makeText(this, "먼저 접근성 서비스를 켜주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            moveTaskToBack(true);
        });
        setupActions.addView(setupPanelButton, utilityButtonParams());

        showPositionsButton = makeButton(
                positionButtonLabel(),
                0xFFF1F5F9,
                COLOR_TEXT
        );
        showPositionsButton.setOnClickListener(view -> {
            boolean toggled = TouchAccessibilityService.togglePositionOverlay();
            if (!toggled) {
                Toast.makeText(this, "먼저 접근성 서비스를 켜주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            renderMappings();
        });
        setupActions.addView(showPositionsButton, utilityButtonParams());

        LinearLayout diagnosticActions = new LinearLayout(this);
        diagnosticActions.setOrientation(LinearLayout.HORIZONTAL);
        // Advanced diagnostics are available from the Remote Tools menu.

        Button keyDiagnosticButton = makeButton("키 진단", 0xFFFFFFFF, COLOR_TEXT);
        keyDiagnosticButton.setOnClickListener(view -> {
            boolean started = TouchAccessibilityService.showKeyDiagnosticOverlay();
            if (!started) {
                Toast.makeText(this, "먼저 접근성 서비스를 켜주세요.", Toast.LENGTH_SHORT).show();
            }
        });
        diagnosticActions.addView(keyDiagnosticButton, utilityButtonParams());

        Button motionDiagnosticButton = makeButton("터치 진단", 0xFFFFFFFF, COLOR_TEXT);
        motionDiagnosticButton.setOnClickListener(view -> {
            boolean started = TouchAccessibilityService.showMotionDiagnosticOverlay();
            if (!started) {
                Toast.makeText(this, "먼저 접근성 서비스를 켜주세요.", Toast.LENGTH_SHORT).show();
            }
        });
        diagnosticActions.addView(motionDiagnosticButton, utilityButtonParams());

        Button analyzerButton = makeButton("Remote Analyzer", 0xFFFFF7ED, 0xFFC2410C);
        analyzerButton.setOnClickListener(view -> {
            boolean started = TouchAccessibilityService.toggleControllerAnalyzerOverlay();
            if (!started) {
                Toast.makeText(this, "Accessibility service is not running.", Toast.LENGTH_SHORT).show();
            }
        });
        diagnosticActions.addView(analyzerButton, utilityButtonParams());

        TextView sectionTitle = new TextView(this);
        sectionTitle.setText("버튼 4개");
        sectionTitle.setTypeface(Typeface.DEFAULT_BOLD);
        sectionTitle.setTextSize(16f);
        sectionTitle.setTextColor(COLOR_TEXT);
        sectionTitle.setPadding(0, dp(16), 0, 0);
        root.addView(sectionTitle);

        mappingsContainer = new LinearLayout(this);
        mappingsContainer.setOrientation(LinearLayout.VERTICAL);
        mappingsContainer.setPadding(0, dp(12), 0, 0);
        root.addView(mappingsContainer);

        setContentView(scrollView);
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("설정 초기화")
                .setMessage("저장된 프로필, 버튼 이름, 키, 위치를 모두 지울까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("초기화", (dialog, which) -> {
                    MappingStore.reset(this);
                    renderMappings();
                    Toast.makeText(this, "초기화 완료", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showRemoteToolsDialog() {
        String[] items = {
                "Controller diagnosis",
                "Motion button learning"
        };
        new AlertDialog.Builder(this)
                .setTitle("Controller setup")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                    } else if (which == 1) {
                        showAdbProbeCountDialog();
                    }
                })
                .show();
    }

    private void showAdbProbeCountDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText("4");
        input.setSelectAllOnFocus(true);

        new AlertDialog.Builder(this)
                .setTitle("사용할 버튼 개수")
                .setView(input)
                .setNegativeButton("취소", null)
                .setPositiveButton("시작", (dialog, which) -> {
                    int count;
                    try {
                        count = Integer.parseInt(input.getText().toString().trim());
                    } catch (NumberFormatException exception) {
                        count = 4;
                    }
                    count = Math.max(1, Math.min(20, count));
                    boolean started = TouchAccessibilityService.startAdbProbeOverlay(count);
                    if (!started) {
                        Toast.makeText(this, "먼저 접근성 서비스를 켜주세요.", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void toggleRemoteAnalyzer() {
        boolean started = TouchAccessibilityService.toggleControllerAnalyzerOverlay();
        if (!started) {
            Toast.makeText(this, "먼저 접근성 서비스를 켜주세요.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showKeyDiagnostic() {
        boolean started = TouchAccessibilityService.showKeyDiagnosticOverlay();
        if (!started) {
            Toast.makeText(this, "먼저 접근성 서비스를 켜주세요.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showMotionDiagnostic() {
        boolean started = TouchAccessibilityService.showMotionDiagnosticOverlay();
        if (!started) {
            Toast.makeText(this, "먼저 접근성 서비스를 켜주세요.", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderMappings() {
        if (inputDeviceButton != null) {
            inputDeviceButton.setText("입력 장치: " + inputDeviceLabel());
        }
        if (inputDeviceInfoText != null) {
            inputDeviceInfoText.setText(inputDeviceInfo());
        }
        if (showPositionsButton != null) {
            showPositionsButton.setText(positionButtonLabel());
        }
        String lockText = TouchAccessibilityService.isTouchLocked() ? "터치 잠금 ON" : "터치 잠금 OFF";
        String positionText = TouchAccessibilityService.arePositionsVisible() ? "위치 표시 ON" : "위치 표시 OFF";
        statusText.setText(TouchAccessibilityService.isRunning()
                ? "접근성 ON · " + positionText + " · " + lockText + " · 2초 길게 누르기: 1 위치표시, 4 잠금"
                : "먼저 접근성 설정에서 " + getString(R.string.accessibility_service_label) + "를 켜주세요.");

        mappingsContainer.removeAllViews();
        for (int slot = 0; slot < MappingStore.SLOT_COUNT; slot++) {
            mappingsContainer.addView(mappingRow(slot));
        }
    }

    private LinearLayout mappingRow(int slot) {
        MappingStore.Mapping mapping = MappingStore.get(this, slot);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(16), dp(16), dp(16));
        row.setBackground(roundedStroke(COLOR_SURFACE, dp(8), COLOR_BORDER, 1));
        row.setElevation(dp(1));

        TextView title = new TextView(this);
        title.setText("버튼 " + (slot + 1) + " · " + mapping.name + (slot == MappingStore.LOCK_SLOT ? " · 잠금" : ""));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(18f);
        title.setTextColor(COLOR_TEXT);
        row.addView(title);

        TextView detail = new TextView(this);
        detail.setTextSize(14f);
        detail.setTextColor(COLOR_MUTED);
        detail.setPadding(0, dp(8), 0, dp(10));
        detail.setText("키: " + keyLabel(mapping) + "\n위치: " + pointLabel(mapping));
        row.addView(detail);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button captureButton = makeSmallButton("키 입력", 0xFFEAF2FF, COLOR_PRIMARY);
        captureButton.setOnClickListener(view -> {
            boolean started = TouchAccessibilityService.showInputCaptureOverlay(slot);
            if (started) {
                Toast.makeText(this, "학습 화면 안내에 따라 리모컨 버튼을 눌러주세요.", Toast.LENGTH_SHORT).show();
            } else {
                showLearningUnavailableDialog();
            }
        });
        actions.addView(captureButton, utilityButtonParams());

        Button longCaptureButton = makeSmallButton("Long", 0xFFFFF7ED, 0xFFC2410C);
        longCaptureButton.setOnClickListener(view -> {
            boolean started = TouchAccessibilityService.showInputCaptureOverlay(slot, true);
            if (started) {
                Toast.makeText(this, "Press and hold the remote button.", Toast.LENGTH_SHORT).show();
            } else {
                showLearningUnavailableDialog();
            }
        });
        actions.addView(longCaptureButton, utilityButtonParams());

        Button nameButton = makeSmallButton("이름 변경", 0xFFF1F5F9, COLOR_TEXT);
        nameButton.setOnClickListener(view -> showButtonNameEditor(slot));
        actions.addView(nameButton, utilityButtonParams());

        row.addView(actions, matchWidthParams());

        LinearLayout.LayoutParams rowParams = matchWidthParams();
        rowParams.setMargins(0, 0, 0, dp(12));
        row.setLayoutParams(rowParams);

        return row;
    }

    private String keyLabel(MappingStore.Mapping mapping) {
        if (!mapping.hasKey()) {
            return "미지정";
        }
        return MappingStore.triggerLabel(mapping);
    }

    private String pointLabel(MappingStore.Mapping mapping) {
        if (!mapping.hasPoint()) {
            return "미지정";
        }
        return Math.round(mapping.x) + ", " + Math.round(mapping.y);
    }

    private String positionButtonLabel() {
        return TouchAccessibilityService.arePositionsVisible() ? "위치 표시 켜짐" : "위치 표시 꺼짐";
    }

    private String inputDeviceLabel() {
        if (!MappingStore.hasSelectedInputDevice(this)) {
            return "선택 안 됨";
        }

        String selectedDescriptor = MappingStore.selectedInputDeviceDescriptor(this);
        for (InputDevice device : inputDevices()) {
            if (selectedDescriptor.equals(device.getDescriptor())) {
                return device.getName();
            }
        }
        return MappingStore.selectedInputDeviceName(this) + " (연결 안 됨)";
    }

    private String inputDeviceInfo() {
        if (!MappingStore.hasSelectedInputDevice(this)) {
            return "장치를 선택하면 안드로이드 입력 분류와 처리 방법을 표시합니다.";
        }

        InputDevice device = selectedInputDevice();
        if (device == null) {
            return "안드로이드 분류: 연결 후 확인\n처리 안내: 컨트롤러가 다시 연결될 때까지 모든 기능이 중지됩니다.";
        }

        return "안드로이드 분류: " + inputSourceLabel(device.getSources())
                + "\n처리 안내: " + inputStrategyLabel(device.getSources());
    }

    private void showProfilePicker() {
        String[] labels = new String[MappingStore.PROFILE_COUNT];
        for (int profile = 0; profile < MappingStore.PROFILE_COUNT; profile++) {
            labels[profile] = (profile + 1) + ". " + MappingStore.profileName(this, profile);
        }

        new AlertDialog.Builder(this)
                .setTitle("설정 선택")
                .setItems(labels, (dialog, which) -> {
                    MappingStore.setCurrentProfile(this, which);
                    TouchAccessibilityService.refreshPositionOverlay();
                    renderMappings();
                })
                .show();
    }

    private void showInputDevicePicker() {
        List<InputDevice> devices = inputDevices();
        if (devices.isEmpty()) {
            Toast.makeText(this, "연결된 컨트롤러를 찾지 못했습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = new String[devices.size()];
        for (int index = 0; index < devices.size(); index++) {
            InputDevice device = devices.get(index);
            labels[index] = device.getName() + "\n안드로이드 분류: " + inputSourceLabel(device.getSources());
        }

        new AlertDialog.Builder(this)
                .setTitle("입력 장치 선택")
                .setItems(labels, (dialog, which) -> {
                    InputDevice device = devices.get(which);
                    MappingStore.saveInputDevice(this, device.getDescriptor(), device.getName(),
                            device.getVendorId(), device.getProductId());
                    Toast.makeText(this, device.getName() + "만 사용합니다.", Toast.LENGTH_SHORT).show();
                    TouchAccessibilityService.refreshMotionCapture();
                    renderMappings();
                })
                .show();
    }

    private void startControllerDiagnostic() {
        if (!MappingStore.hasSelectedInputDevice(this)) {
            Toast.makeText(this, "Select the controller first.", Toast.LENGTH_SHORT).show();
            showInputDevicePicker();
            return;
        }

        boolean started = TouchAccessibilityService.showControllerDiagnosticOverlay();
        if (!started) {
            Toast.makeText(this, "먼저 접근성 서비스를 켜주세요.", Toast.LENGTH_SHORT).show();
        }
    }

    private List<InputDevice> inputDevices() {
        List<InputDevice> devices = new ArrayList<>();
        for (int deviceId : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (device != null && isUsableInputDevice(device)) {
                devices.add(device);
            }
        }
        return devices;
    }

    private InputDevice selectedInputDevice() {
        String selectedDescriptor = MappingStore.selectedInputDeviceDescriptor(this);
        if (selectedDescriptor.isEmpty()) {
            return null;
        }
        for (InputDevice device : inputDevices()) {
            if (MappingStore.acceptsInputDevice(this, device.getDescriptor(), device.getName(),
                    device.getVendorId(), device.getProductId())) {
                return device;
            }
        }
        return null;
    }

    private String inputSourceLabel(int sources) {
        List<String> labels = new ArrayList<>();
        addSourceLabel(labels, sources, InputDevice.SOURCE_KEYBOARD, "키보드");
        addSourceLabel(labels, sources, InputDevice.SOURCE_DPAD, "방향키");
        addSourceLabel(labels, sources, InputDevice.SOURCE_GAMEPAD, "게임패드");
        addSourceLabel(labels, sources, InputDevice.SOURCE_MOUSE, "마우스");
        addSourceLabel(labels, sources, InputDevice.SOURCE_MOUSE_RELATIVE, "상대 마우스");
        addSourceLabel(labels, sources, InputDevice.SOURCE_TOUCHPAD, "터치패드");
        addSourceLabel(labels, sources, InputDevice.SOURCE_TOUCHSCREEN, "터치스크린");
        addSourceLabel(labels, sources, InputDevice.SOURCE_TRACKBALL, "트랙볼");
        addSourceLabel(labels, sources, InputDevice.SOURCE_JOYSTICK, "조이스틱");
        addSourceLabel(labels, sources, InputDevice.SOURCE_ROTARY_ENCODER, "회전 입력");
        return labels.isEmpty() ? "기타 입력 (0x" + Integer.toHexString(sources) + ")"
                : String.join(" · ", labels);
    }

    private void addSourceLabel(List<String> labels, int sources, int source, String label) {
        if ((sources & source) == source) {
            labels.add(label);
        }
    }

    private String inputStrategyLabel(int sources) {
        boolean keyboard = (sources & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD;
        boolean touchscreen = (sources & InputDevice.SOURCE_TOUCHSCREEN) == InputDevice.SOURCE_TOUCHSCREEN;
        boolean capturableMotion = (sources & (InputDevice.SOURCE_MOUSE
                | InputDevice.SOURCE_MOUSE_RELATIVE
                | InputDevice.SOURCE_TOUCHPAD
                | InputDevice.SOURCE_TRACKBALL
                | InputDevice.SOURCE_JOYSTICK
                | InputDevice.SOURCE_ROTARY_ENCODER)) != 0;

        if (touchscreen) {
            return "부분 화면 트랩" + (keyboard ? " + 키 이벤트" : "")
                    + "\n안전: 전체 화면 터치는 차단하지 않습니다.";
        }
        if (capturableMotion) {
            return Build.VERSION.SDK_INT >= 34
                    ? "모션 캡처 (setMotionEventSources)" + (keyboard ? " + 키 이벤트" : "")
                    : "화면 트랩 방식" + (keyboard ? " + 키 이벤트" : "");
        }
        if (keyboard) {
            return "키 이벤트 (onKeyEvent)";
        }
        return "입력 학습 후 처리 방법 결정";
    }

    private boolean isUsableInputDevice(InputDevice device) {
        if (device.isVirtual()) {
            return false;
        }

        int sources = device.getSources();
        boolean keyboard = (sources & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD;
        boolean gamepad = (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;
        boolean dpad = (sources & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD;
        boolean joystick = (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
        boolean mouse = (sources & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE;
        boolean touchpad = (sources & InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD;
        boolean externalTouchscreen = device.isExternal()
                && (sources & InputDevice.SOURCE_TOUCHSCREEN) == InputDevice.SOURCE_TOUCHSCREEN;
        boolean trackball = (sources & InputDevice.SOURCE_TRACKBALL) == InputDevice.SOURCE_TRACKBALL;
        boolean rotary = (sources & InputDevice.SOURCE_ROTARY_ENCODER) == InputDevice.SOURCE_ROTARY_ENCODER;
        boolean button = (sources & InputDevice.SOURCE_CLASS_BUTTON) == InputDevice.SOURCE_CLASS_BUTTON;
        return keyboard || gamepad || dpad || joystick || mouse || touchpad || externalTouchscreen
                || trackball || rotary || button;
    }

    private void showProfileNameEditor() {
        showNameEditor("설정 이름", MappingStore.profileName(this), value -> {
            MappingStore.saveProfileName(this, value);
            renderMappings();
        });
    }

    private void showButtonNameEditor(int slot) {
        showNameEditor("버튼 " + (slot + 1) + " 이름", MappingStore.buttonName(this, slot), value -> {
            MappingStore.saveButtonName(this, slot, value);
            TouchAccessibilityService.refreshPositionOverlay();
            renderMappings();
        });
    }

    private void showLearningUnavailableDialog() {
        new AlertDialog.Builder(this)
                .setTitle("입력 학습을 시작할 수 없음")
                .setMessage("접근성 서비스가 실행 중인지, 선택한 컨트롤러가 연결되어 있는지 확인해주세요. 기존 설정은 변경되지 않았습니다.")
                .setNegativeButton("닫기", null)
                .setPositiveButton("접근성 설정", (dialog, which) ->
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .show();
    }

    private void showNameEditor(String title, String currentValue, NameSaveListener listener) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(currentValue);
        input.setSelectAllOnFocus(true);
        input.setPadding(dp(18), dp(8), dp(18), dp(8));

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", (dialog, which) -> listener.onSave(input.getText().toString()))
                .show();
    }

    private LinearLayout.LayoutParams matchWidthParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(10));
        return params;
    }

    private Button makeButton(String text, int backgroundColor, int textColor) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(textColor);
        button.setTextSize(14f);
        button.setMinHeight(dp(44));
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(roundedStroke(backgroundColor, dp(8), COLOR_BORDER, backgroundColor == COLOR_SURFACE ? 1 : 0));
        return button;
    }

    private Button makeSmallButton(String text, int backgroundColor, int textColor) {
        Button button = makeButton(text, backgroundColor, textColor);
        button.setTextSize(13f);
        button.setMinHeight(dp(38));
        button.setPadding(dp(8), 0, dp(8), 0);
        return button;
    }

    private LinearLayout.LayoutParams utilityButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
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

    private interface NameSaveListener {
        void onSave(String value);
    }
}
