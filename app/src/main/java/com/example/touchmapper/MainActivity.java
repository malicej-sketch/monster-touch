package com.example.touchmapper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
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
    /** 배터리 상태를 색으로 알린다. 주행 중에는 숫자보다 색이 먼저 읽힌다. */
    private static final int COLOR_OK = 0xFF0F7B3F;
    private static final int COLOR_WARN = 0xFFB45309;
    private static final int COLOR_ALERT = 0xFFB42318;

    private static MainActivity activeInstance;

    private LinearLayout mappingsContainer;
    private TextView statusTitle;
    private TextView statusSubtitle;
    private View statusDot;
    private android.widget.Switch positionSwitch;
    private android.widget.Switch lockSwitch;
    private android.widget.Switch doubleSwitch;
    private android.widget.Switch batterySwitch;
    private ImageView batteryIcon;
    private TextView batterySummary;
    private TextView batteryDetail;
    private TextView buttonCountBadge;
    private LinearLayout bannerSlot;
    /** 스위치를 코드로 되돌릴 때 리스너가 다시 도는 것을 막는다. */
    private boolean bindingSwitches;

    /** 전류는 계속 변한다. 화면에 떠 있는 동안은 살아 있는 값이어야 한다. */
    private static final long BATTERY_REFRESH_MS = 2000L;
    private final android.os.Handler batteryHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable batteryTick = new Runnable() {
        @Override
        public void run() {
            updateBatteryCard();
            batteryHandler.postDelayed(this, BATTERY_REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    /**
     * 선택된 컨트롤러가 주입하는 터치는 이 화면에서 무시한다.
     *
     * 터치스크린형 컨트롤러는 화면에 실제 터치를 넣는다. 설정 화면에서 트랩을 내려두면
     * 그 주입이 그대로 들어와 화면이 저절로 스크롤된다. 트랩으로 막으면 이번엔 트랩이
     * 이 화면의 버튼을 가린다. 창을 덮는 대신 이 화면이 직접 걸러내면 둘 다 피할 수 있다.
     * 손가락 터치는 기기가 다르므로 그대로 통과한다.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (isFromSelectedController(event)) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    private boolean isFromSelectedController(MotionEvent event) {
        if (!MappingStore.hasSelectedInputDevice(this)) {
            return false;
        }
        InputDevice device = event.getDevice();
        if (device == null) {
            return false;
        }
        return MappingStore.acceptsInputDevice(this, device.getDescriptor(), device.getName(),
                device.getVendorId(), device.getProductId());
    }

    @Override
    protected void onResume() {
        super.onResume();
        activeInstance = this;
        TouchAccessibilityService.setConfigurationActive(true);
        // 전용 제품 빌드는 쓸 수 있는 기기가 하나뿐이고 키도 정해져 있다. 물어보지 않는다.
        if (ControllerPolicy.autoSelect(this)) {
            TouchAccessibilityService.refreshMotionCapture();
        }
        ControllerPolicy.applyDefaultBindings(this);
        renderMappings();
        batteryHandler.removeCallbacks(batteryTick);
        batteryHandler.postDelayed(batteryTick, BATTERY_REFRESH_MS);
    }


    /** 충전 속도 단계별 색. */
    private static int speedColor(int level) {
        switch (level) {
            case BatteryReading.SPEED_DRAINING:
                return COLOR_ALERT;
            case BatteryReading.SPEED_SLOW:
                return COLOR_WARN;
            case BatteryReading.SPEED_NORMAL:
                // 일반 충전은 좋지도 나쁘지도 않다. 색으로 부르지 않는다.
                return COLOR_TEXT;
            case BatteryReading.SPEED_FAST:
                return COLOR_OK;
            case BatteryReading.SPEED_SUPER:
                return COLOR_PRIMARY;
            default:
                return COLOR_MUTED;
        }
    }

    private void updateBatteryCard() {
        if (batterySummary == null) {
            return;
        }
        BatteryReading reading = BatteryReading.read(this);

        String state = reading.charging ? "충전 중"
                : reading.isPluggedButIdle() ? "충전 멈춤" : "배터리 사용 중";
        batterySummary.setText(state + " · " + reading.temperatureText());
        batterySummary.setTextColor(reading.isHot() ? COLOR_ALERT : COLOR_TEXT);

        // 꽂혔으면 속도 단어와 전류, 아니면 방전으로 읽힌다.
        String speedWord = reading.isPluggedIn() ? reading.chargeSpeedLabel() : "방전 중";
        batteryDetail.setText(speedWord + " · " + reading.currentValueText());
        batteryDetail.setTextColor(reading.isPluggedIn()
                ? speedColor(reading.chargeSpeedLevel()) : COLOR_MUTED);

        if (batteryIcon != null) {
            int tint = reading.isHot() || reading.isDrainingWhilePlugged() ? COLOR_ALERT
                    : reading.charging ? COLOR_OK
                    : reading.isPluggedButIdle() ? COLOR_WARN : COLOR_MUTED;
            batteryIcon.setColorFilter(tint);
        }
    }

    @Override
    protected void onPause() {
        if (activeInstance == this) {
            activeInstance = null;
        }
        TouchAccessibilityService.setConfigurationActive(false);
        batteryHandler.removeCallbacks(batteryTick);
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
        applySystemBarInsets(scrollView);

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

        ImageView gear = new ImageView(this);
        gear.setImageResource(R.drawable.ic_gear);
        gear.setPadding(dp(8), dp(8), dp(8), dp(8));
        gear.setOnClickListener(view -> showSettingsDialog());
        LinearLayout.LayoutParams gearParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        gearParams.gravity = Gravity.CENTER_VERTICAL;
        brandHeader.addView(gear, gearParams);

        root.addView(buildStatusCard(), matchWidthParams());

        bannerSlot = new LinearLayout(this);
        bannerSlot.setOrientation(LinearLayout.VERTICAL);
        root.addView(bannerSlot, matchWidthParams());

        Button primaryCta = makeButton("위치 설정하기", COLOR_PRIMARY, 0xFFFFFFFF);
        primaryCta.setTextSize(17f);
        primaryCta.setTypeface(Typeface.DEFAULT_BOLD);
        primaryCta.setOnClickListener(view -> startPositionSetup());
        LinearLayout.LayoutParams ctaParams = matchWidthParams();
        ctaParams.setMargins(0, dp(14), 0, dp(22));
        root.addView(primaryCta, ctaParams);

        root.addView(sectionHeader("빠른 기능", null));
        root.addView(buildQuickActionsCard(), matchWidthParams());

        root.addView(sectionHeader("배터리", null));
        root.addView(buildBatteryCard(), matchWidthParams());

        buttonCountBadge = new TextView(this);
        root.addView(sectionHeader("버튼 설정", buttonCountBadge));


        mappingsContainer = new LinearLayout(this);
        mappingsContainer.setOrientation(LinearLayout.VERTICAL);
        mappingsContainer.setPadding(0, dp(12), 0, 0);
        root.addView(mappingsContainer);

        LinearLayout.LayoutParams toolsParams = matchWidthParams();
        toolsParams.setMargins(0, dp(4), 0, 0);
        root.addView(buildAdvancedToolsRow(), toolsParams);

        setContentView(scrollView);

        BannerLoader.load(this, this::showBanner);
    }

    /**
     * 상태 표시줄과 내비게이션 바 높이만큼 안쪽으로 밀어준다.
     *
     * targetSdk 35부터 화면이 시스템 바 아래까지 그려지고, 36부터는 이를 끄는 방법이 없다.
     * 테마의 statusBarColor / navigationBarColor는 무시된다. 직접 여백을 잡지 않으면
     * 제목이 시계에 겹치고 맨 아래 항목이 내비게이션 바에 가린다.
     */
    private void applySystemBarInsets(View view) {
        view.setOnApplyWindowInsetsListener((target, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            target.setPadding(target.getPaddingLeft(), top, target.getPaddingRight(), bottom);
            return insets;
        });
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
        bindingSwitches = true;

        boolean running = TouchAccessibilityService.isRunning();
        boolean deviceReady;
        String subtitle;
        if (ControllerPolicy.unrestricted()) {
            deviceReady = MappingStore.hasSelectedInputDevice(this);
            subtitle = deviceReady
                    ? "입력 장치: " + inputDeviceLabel()
                    : "눌러서 입력 장치를 선택해 주세요";
        } else {
            deviceReady = selectedInputDevice() != null;
            subtitle = deviceReady
                    ? "전용 컨트롤러 연결됨"
                    : "전용 컨트롤러가 연결되지 않았습니다";
        }
        if (statusTitle != null) {
            statusTitle.setText(running ? "접근성 연결됨" : "접근성이 꺼져 있습니다");
            statusSubtitle.setText(running ? subtitle : "눌러서 접근성 설정을 열어주세요");
            int dotColor = running && deviceReady ? COLOR_OK : running ? COLOR_WARN : COLOR_ALERT;
            statusDot.setBackground(rounded(dotColor, dp(6)));
        }

        if (positionSwitch != null) {
            positionSwitch.setChecked(TouchAccessibilityService.arePositionsVisible());
            lockSwitch.setChecked(TouchAccessibilityService.isTouchLocked());
            doubleSwitch.setChecked(MappingStore.doubleClickEnabled(this));
            batterySwitch.setChecked(TouchAccessibilityService.isBatteryOverlayVisible());
        }

        updateBatteryCard();

        int buttonCount = ControllerPolicy.visibleButtonCount();
        if (buttonCountBadge != null) {
            buttonCountBadge.setText(buttonCount + "개");
        }

        mappingsContainer.removeAllViews();
        for (int slot = 0; slot < buttonCount; slot++) {
            mappingsContainer.addView(mappingRow(slot));
        }

        bindingSwitches = false;
    }

    private TextView sectionTitle(String text, int topPadding) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(16f);
        title.setTextColor(COLOR_TEXT);
        title.setPadding(0, topPadding, 0, dp(12));
        return title;
    }

    private LinearLayout.LayoutParams halfWidthParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(0, 0, first ? dp(6) : 0, 0);
        if (!first) {
            params.setMargins(dp(6), 0, 0, 0);
        }
        return params;
    }

    /**
     * 같은 키의 길게가 무엇을 하는지.
     *
     * 슬롯 번호로 판단하면 안 된다. 하단 버튼은 짧게가 슬롯 2인데 화면 잠금은 슬롯 3의
     * 길게로 저장된다. 키를 기준으로 찾아야 맞다.
     */
    private String longRoleLabel(int keyCode) {
        if (keyCode == MappingStore.TRIGGER_UNKNOWN) {
            return "";
        }
        MappingStore.Mapping longMapping = MappingStore.findByLongKeyCode(this, keyCode);
        if (longMapping == null) {
            return "";
        }
        if (longMapping.slot == MappingStore.MARKER_TOGGLE_SLOT) {
            return "위치 표시";
        }
        if (longMapping.slot == MappingStore.LOCK_SLOT) {
            return "화면 잠금";
        }
        return "버튼 " + (longMapping.slot + 1) + " 탭";
    }

    private String doubleRoleLabel(int keyCode) {
        for (int index = 0; index < MappingStore.DOUBLE_BINDING_COUNT; index++) {
            if (MappingStore.doubleKeyCode(this, index) != keyCode
                    || keyCode == MappingStore.TRIGGER_UNKNOWN) {
                continue;
            }
            switch (MappingStore.doubleAction(this, index)) {
                case MappingStore.DOUBLE_ACTION_BACK:
                    return "뒤로 가기";
                case MappingStore.DOUBLE_ACTION_HOME:
                    return "홈";
                case MappingStore.DOUBLE_ACTION_RECENTS:
                    return "최근 앱";
                case MappingStore.DOUBLE_ACTION_CAMERA:
                    return "카메라";
                default:
                    return "";
            }
        }
        return "";
    }

    /** 버튼 하나가 지금 무엇을 하는지 한 줄로 다 보이게 한다. */
    private LinearLayout mappingRow(int slot) {
        MappingStore.Mapping mapping = MappingStore.get(this, slot);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        row.setBackground(roundedStroke(COLOR_SURFACE, dp(14), COLOR_BORDER, 1));
        row.setElevation(dp(1));

        TextView badge = new TextView(this);
        badge.setText(String.valueOf(slot + 1));
        badge.setTextColor(0xFFFFFFFF);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setTextSize(15f);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(COLOR_PRIMARY, dp(9)));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(30), dp(30));
        badgeParams.setMargins(0, 0, dp(12), 0);
        row.addView(badge, badgeParams);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText(slotTitle(slot, mapping));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(16f);
        title.setTextColor(COLOR_TEXT);
        texts.addView(title);

        TextView detail = new TextView(this);
        detail.setTextSize(13f);
        detail.setTextColor(COLOR_MUTED);
        detail.setPadding(0, dp(3), 0, 0);
        detail.setText(slotSummary(mapping));
        texts.addView(detail);

        row.addView(chevron());
        row.setOnClickListener(view -> showButtonOptions(slot));

        LinearLayout.LayoutParams rowParams = matchWidthParams();
        rowParams.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(rowParams);
        return row;
    }

    /** 키 · 위치 · 길게 · 더블을 점으로 이어 붙인다. 없는 것은 적지 않는다. */
    private String slotSummary(MappingStore.Mapping mapping) {
        List<String> parts = new ArrayList<>();

        String key = MappingStore.triggerDisplayLabel(mapping);
        if (!key.isEmpty()) {
            parts.add(key);
        }
        parts.add(pointLabel(mapping));

        int keyCode = mapping.triggerType == MappingStore.TRIGGER_KEY
                ? mapping.triggerValue : MappingStore.TRIGGER_UNKNOWN;
        String longLabel = longRoleLabel(keyCode);
        if (!longLabel.isEmpty()) {
            parts.add("길게: " + longLabel);
        }
        String doubleLabel = doubleRoleLabel(keyCode);
        if (!doubleLabel.isEmpty()) {
            parts.add("더블: " + doubleLabel
                    + (MappingStore.doubleClickEnabled(this) ? "" : " (꺼짐)"));
        }
        return String.join(" · ", parts);
    }

    /** 전용 빌드는 바꿀 것이 이름뿐이라 바로 연다. */
    private void showButtonOptions(int slot) {
        if (!ControllerPolicy.unrestricted()) {
            showButtonNameEditor(slot);
            return;
        }
        String[] items = {"이름 변경", "키 입력", "길게 누르기 키 입력"};
        new AlertDialog.Builder(this)
                .setTitle("버튼 " + (slot + 1))
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        showButtonNameEditor(slot);
                    } else if (which == 1) {
                        startInputCapture(slot, false);
                    } else {
                        startInputCapture(slot, true);
                    }
                })
                .show();
    }

    private void showNoSavedPointDialog() {
        new AlertDialog.Builder(this)
                .setTitle("설정된 좌표 없음")
                .setMessage("좌표가 저장된 버튼이 없습니다. 지금 위치를 설정하시겠습니까?")
                .setNegativeButton("닫기", null)
                .setPositiveButton("위치 설정", (dialog, which) -> startPositionSetup())
                .show();
    }

    private void showInputDeviceRequiredDialog() {
        new AlertDialog.Builder(this)
                .setTitle("입력 장치를 먼저 선택해주세요")
                .setMessage("어떤 컨트롤러의 신호를 학습할지 정해야 합니다. "
                        + "선택하지 않으면 다른 기기의 입력까지 섞여 들어옵니다.")
                .setNegativeButton("닫기", null)
                .setPositiveButton("입력 장치 선택", (dialog, which) -> showInputDevicePicker())
                .show();
    }

    private LinearLayout.LayoutParams rowButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(96), LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(8), 0, 0, 0);
        return params;
    }

    private String slotTitle(int slot, MappingStore.Mapping mapping) {
        String title = "버튼 " + (slot + 1);
        if (MappingStore.hasCustomButtonName(this, slot)) {
            title = title + " · " + mapping.name;
        }
        return title;
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
            showInputDeviceRequiredDialog();
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
        // 전용 제품 빌드는 우리 리모컨만 목록에 올린다.
        if (!ControllerPolicy.isAllowed(device)) {
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

    /** 흰 바탕 둥근 카드. 여러 화면 조각이 같은 껍데기를 쓴다. */
    private LinearLayout cardContainer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedStroke(COLOR_SURFACE, dp(14), COLOR_BORDER, 1));
        card.setElevation(dp(1));
        return card;
    }

    private View divider() {
        View line = new View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        params.setMargins(dp(16), 0, dp(16), 0);
        line.setLayoutParams(params);
        line.setBackgroundColor(COLOR_BORDER);
        return line;
    }

    private View sectionHeader(String text, TextView badge) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(20), dp(4), dp(10));

        TextView title = new TextView(this);
        title.setText(text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(17f);
        title.setTextColor(COLOR_TEXT);
        row.addView(title);

        if (badge != null) {
            badge.setTextSize(12f);
            badge.setTextColor(COLOR_MUTED);
            badge.setBackground(roundedStroke(COLOR_SURFACE, dp(10), COLOR_BORDER, 1));
            badge.setPadding(dp(9), dp(2), dp(9), dp(2));
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            badgeParams.setMargins(dp(8), 0, 0, 0);
            row.addView(badge, badgeParams);
        }
        return row;
    }

    private ImageView chevron() {
        ImageView view = new ImageView(this);
        view.setImageResource(R.drawable.ic_chevron);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(20), dp(20));
        params.gravity = Gravity.CENTER_VERTICAL;
        view.setLayoutParams(params);
        return view;
    }

    /** 지금 쓸 수 있는 상태인지 한눈에. 누르면 부족한 것을 채우러 간다. */
    private View buildStatusCard() {
        LinearLayout card = cardContainer();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(18), dp(16), dp(18));

        statusDot = new View(this);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(12), dp(12));
        dotParams.setMargins(0, 0, dp(12), 0);
        dotParams.gravity = Gravity.CENTER_VERTICAL;
        card.addView(statusDot, dotParams);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        card.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        statusTitle = new TextView(this);
        statusTitle.setTextSize(17f);
        statusTitle.setTypeface(Typeface.DEFAULT_BOLD);
        statusTitle.setTextColor(COLOR_TEXT);
        texts.addView(statusTitle);

        statusSubtitle = new TextView(this);
        statusSubtitle.setTextSize(13f);
        statusSubtitle.setTextColor(COLOR_MUTED);
        statusSubtitle.setPadding(0, dp(3), 0, 0);
        texts.addView(statusSubtitle);

        card.addView(chevron());

        card.setOnClickListener(view -> {
            if (!TouchAccessibilityService.isRunning()) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return;
            }
            if (ControllerPolicy.unrestricted()) {
                showInputDevicePicker();
                return;
            }
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        return card;
    }

    private LinearLayout quickRow(int iconRes, String title, String subtitle,
                                  android.widget.Switch toggle,
                                  java.util.function.Consumer<Boolean> onUserToggle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
        iconParams.setMargins(0, 0, dp(14), 0);
        row.addView(icon, iconParams);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(16f);
        titleView.setTextColor(COLOR_TEXT);
        texts.addView(titleView);

        if (subtitle != null) {
            TextView subtitleView = new TextView(this);
            subtitleView.setText(subtitle);
            subtitleView.setTextSize(12f);
            subtitleView.setTextColor(COLOR_MUTED);
            subtitleView.setPadding(0, dp(2), 0, 0);
            texts.addView(subtitleView);
        }

        toggle.setOnCheckedChangeListener((buttonView, checked) -> {
            if (!bindingSwitches) {
                onUserToggle.accept(checked);
            }
        });
        row.addView(toggle);
        return row;
    }

    private View buildQuickActionsCard() {
        LinearLayout card = cardContainer();
        positionSwitch = new android.widget.Switch(this);
        lockSwitch = new android.widget.Switch(this);
        doubleSwitch = new android.widget.Switch(this);
        card.addView(quickRow(R.drawable.ic_target, "위치 표시", null,
                positionSwitch, this::onPositionToggle));
        card.addView(divider());
        card.addView(quickRow(R.drawable.ic_lock, "화면 잠금", "1.5초 길게 누르기",
                lockSwitch, this::onLockToggle));
        card.addView(divider());
        card.addView(quickRow(R.drawable.ic_double_tap, "더블 클릭", null,
                doubleSwitch, this::onDoubleToggle));
        return card;
    }

    /**
     * 옵션끼리 앞뒤가 맞아야 한다.
     *
     * 위치 표시는 저장된 위치가 있어야 보여줄 것이 있고, 화면 잠금은 켜는 순간 손가락이
     * 막히므로 풀 수단(리모컨)이 붙어 있을 때만 받는다. 조건이 안 되면 스위치를 되돌리고
     * 무엇이 부족한지 알려준다.
     */
    private void onPositionToggle(boolean wantOn) {
        if (!TouchAccessibilityService.isRunning()) {
            Toast.makeText(this, "먼저 접근성 서비스를 켜주세요.", Toast.LENGTH_SHORT).show();
            renderMappings();
            return;
        }
        if (wantOn && !MappingStore.hasAnySavedPoint(this)) {
            renderMappings();
            showNoSavedPointDialog();
            return;
        }
        if (TouchAccessibilityService.arePositionsVisible() != wantOn) {
            TouchAccessibilityService.togglePositionOverlay();
        }
        renderMappings();
    }

    private void onLockToggle(boolean wantOn) {
        if (!TouchAccessibilityService.isRunning()) {
            Toast.makeText(this, "먼저 접근성 서비스를 켜주세요.", Toast.LENGTH_SHORT).show();
            renderMappings();
            return;
        }
        if (!wantOn) {
            if (TouchAccessibilityService.isTouchLocked()) {
                TouchAccessibilityService.toggleTouchLockFromApp();
            }
            renderMappings();
            return;
        }
        if (selectedInputDevice() == null) {
            // 잠그면 손가락이 막힌다. 풀 수 있는 리모컨이 없으면 잠그게 두면 안 된다.
            Toast.makeText(this, "리모컨이 연결돼 있어야 잠글 수 있습니다.", Toast.LENGTH_SHORT).show();
            renderMappings();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("화면 잠금")
                .setMessage("화면 터치가 모두 막힙니다.\n해제하려면 리모컨 하단 버튼을 1.5초 누르세요.")
                .setNegativeButton("취소", (dialog, which) -> renderMappings())
                .setOnCancelListener(dialog -> renderMappings())
                .setPositiveButton("잠금", (dialog, which) -> {
                    TouchAccessibilityService.toggleTouchLockFromApp();
                    renderMappings();
                })
                .show();
    }

    private void onDoubleToggle(boolean wantOn) {
        MappingStore.saveDoubleClickEnabled(this, wantOn);
        if (wantOn) {
            Toast.makeText(this, MappingStore.hasShutterPoint(this)
                    ? "더블이 걸린 버튼은 두 번째 입력을 잠깐 기다렸다 실행됩니다."
                    : "위치 설정에서 카메라 셔터 위치도 잡아주세요.", Toast.LENGTH_LONG).show();
        }
        renderMappings();
    }

    private void onBatteryToggle(boolean wantOn) {
        if (TouchAccessibilityService.isBatteryOverlayVisible() != wantOn) {
            if (!TouchAccessibilityService.toggleBatteryOverlay()) {
                Toast.makeText(this, "먼저 접근성 서비스를 켜주세요.", Toast.LENGTH_SHORT).show();
            }
        }
        renderMappings();
    }

    private View buildBatteryCard() {
        LinearLayout card = cardContainer();

        LinearLayout summaryRow = new LinearLayout(this);
        summaryRow.setOrientation(LinearLayout.HORIZONTAL);
        summaryRow.setGravity(Gravity.CENTER_VERTICAL);
        summaryRow.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.addView(summaryRow);

        batteryIcon = new ImageView(this);
        batteryIcon.setImageResource(R.drawable.ic_battery);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        iconParams.setMargins(0, 0, dp(14), 0);
        summaryRow.addView(batteryIcon, iconParams);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        summaryRow.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        batterySummary = new TextView(this);
        batterySummary.setTextSize(16f);
        batterySummary.setTypeface(Typeface.DEFAULT_BOLD);
        batterySummary.setTextColor(COLOR_TEXT);
        texts.addView(batterySummary);

        batteryDetail = new TextView(this);
        batteryDetail.setTextSize(13f);
        batteryDetail.setTextColor(COLOR_MUTED);
        batteryDetail.setPadding(0, dp(2), 0, 0);
        texts.addView(batteryDetail);

        summaryRow.addView(chevron());
        // 화면 위 표시의 진하기는 배터리 표시와 붙어 있어야 찾기 쉽다.
        summaryRow.setOnClickListener(view -> showOpacityDialog());

        card.addView(divider());

        LinearLayout toggleRow = new LinearLayout(this);
        toggleRow.setOrientation(LinearLayout.HORIZONTAL);
        toggleRow.setGravity(Gravity.CENTER_VERTICAL);
        toggleRow.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.addView(toggleRow);

        TextView toggleLabel = new TextView(this);
        toggleLabel.setText("화면 위에 배터리 표시");
        toggleLabel.setTextSize(15f);
        toggleLabel.setTextColor(COLOR_TEXT);
        toggleRow.addView(toggleLabel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        batterySwitch = new android.widget.Switch(this);
        batterySwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (!bindingSwitches) {
                onBatteryToggle(checked);
            }
        });
        toggleRow.addView(batterySwitch);

        return card;
    }

    private View buildAdvancedToolsRow() {
        LinearLayout card = cardContainer();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_wrench);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(22), dp(22));
        iconParams.setMargins(0, 0, dp(14), 0);
        card.addView(icon, iconParams);

        TextView label = new TextView(this);
        label.setText("고급 도구");
        label.setTextSize(16f);
        label.setTextColor(COLOR_TEXT);
        card.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        card.addView(chevron());
        card.setOnClickListener(view -> showAdvancedToolsDialog());
        return card;
    }

    /**
     * 앱을 내린 다음에 설정 오버레이를 띄운다.
     *
     * 잡아야 할 좌표는 배달 앱 위의 자리다. 이 화면 위에 찍으면 아무 의미가 없다.
     * 뒤로 물러난 뒤에 띄워야 실제로 쓸 화면이 배경이 된다.
     */
    private void startPositionSetup() {
        if (!TouchAccessibilityService.isRunning()) {
            Toast.makeText(this, "먼저 접근성 서비스를 켜주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        moveTaskToBack(true);
        // 화면이 실제로 내려간 뒤에 띄운다. 곱바로 띄우면 이 화면이 아직 위에 있다.
        batteryHandler.postDelayed(TouchAccessibilityService::showSetupOverlay, 300);
    }

    private void startInputCapture(int slot, boolean longMode) {
        if (!MappingStore.hasSelectedInputDevice(this)) {
            Toast.makeText(this, "먼저 입력 장치를 선택해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean started = TouchAccessibilityService.showInputCaptureOverlay(slot, longMode);
        if (started) {
            Toast.makeText(this, "학습 화면 안내에 따라 리모컨 버튼을 눌러주세요.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "먼저 접근성 서비스를 켜주세요.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 배너를 상태 카드 아래에 끼운다.
     *
     * 뒤에서 받아오므로 화면이 이미 떴 뒤에 불린다. 자리를 미리 비워두지 않고
     * 받았을 때만 늘린다 — 안 들어오면 없던 것처럼 보이게 하려는 것이다.
     */
    private void showBanner(android.graphics.Bitmap image, String targetUrl, String buttonText) {
        if (bannerSlot == null || image == null || isFinishing()) {
            return;
        }
        bannerSlot.removeAllViews();

        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.setBackground(rounded(0xFF111417, dp(14)));
        frame.setClipToOutline(true);
        frame.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(14));
            }
        });

        ImageView banner = new ImageView(this);
        banner.setImageBitmap(image);
        // 원본 비율을 그대로 따른다. 시안과 같은 2.9:1이 유지된다.
        banner.setAdjustViewBounds(true);
        banner.setScaleType(ImageView.ScaleType.FIT_CENTER);
        frame.addView(banner, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));

        // 광고라고 밝혀야 한다. 숫기면 안 된다.
        TextView adMark = new TextView(this);
        adMark.setText("광고");
        adMark.setTextSize(10f);
        adMark.setTextColor(0x99FFFFFF);
        adMark.setPadding(dp(8), dp(4), dp(10), dp(6));
        android.widget.FrameLayout.LayoutParams markParams = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        markParams.gravity = Gravity.BOTTOM | Gravity.END;
        frame.addView(adMark, markParams);

        if (targetUrl != null && !targetUrl.isEmpty()) {
            frame.setOnClickListener(view -> openLink(targetUrl));
        }

        LinearLayout.LayoutParams params = matchWidthParams();
        params.setMargins(0, dp(12), 0, 0);
        bannerSlot.addView(frame, params);
    }

    private void openLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception exception) {
            Toast.makeText(this, "열 수 있는 브라우저가 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showSettingsDialog() {
        String[] items = {"접근성 설정", "앱 설정", "화면 위 표시 진하기", "설정 초기화"};
        new AlertDialog.Builder(this)
                .setTitle("설정")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } else if (which == 1) {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } else if (which == 2) {
                        showOpacityDialog();
                    } else {
                        confirmReset();
                    }
                })
                .show();
    }

    /** 화면 위에 뜨는 표시의 진하기. 움직이는 동안 바로 반영된다. */
    private void showOpacityDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(16), dp(24), dp(4));

        TextView label = new TextView(this);
        label.setTextColor(COLOR_MUTED);
        label.setTextSize(14f);
        label.setText("화면 위 표시 진하기 " + MappingStore.overlayOpacity(this) + "%");
        box.addView(label);

        android.widget.SeekBar bar = new android.widget.SeekBar(this);
        bar.setMax(100 - MappingStore.MIN_OVERLAY_OPACITY);
        bar.setProgress(MappingStore.overlayOpacity(this) - MappingStore.MIN_OVERLAY_OPACITY);
        bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                int opacity = progress + MappingStore.MIN_OVERLAY_OPACITY;
                label.setText("화면 위 표시 진하기 " + opacity + "%");
                MappingStore.saveOverlayOpacity(MainActivity.this, opacity);
                TouchAccessibilityService.refreshOverlayOpacity(MainActivity.this);
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
            }
        });
        box.addView(bar);

        new AlertDialog.Builder(this)
                .setTitle("표시 진하기")
                .setView(box)
                .setPositiveButton("닫기", null)
                .show();
    }

    private void showAdvancedToolsDialog() {
        String[] items = {"키 진단", "터치 진단", "Remote Analyzer", "모션 버튼 학습"};
        new AlertDialog.Builder(this)
                .setTitle("고급 도구")
                .setItems(items, (dialog, which) -> {
                    boolean started;
                    if (which == 0) {
                        started = TouchAccessibilityService.showKeyDiagnosticOverlay();
                    } else if (which == 1) {
                        started = TouchAccessibilityService.showMotionDiagnosticOverlay();
                    } else if (which == 2) {
                        started = TouchAccessibilityService.toggleControllerAnalyzerOverlay();
                    } else {
                        showAdbProbeCountDialog();
                        return;
                    }
                    if (!started) {
                        Toast.makeText(this, "먼저 접근성 서비스를 켜주세요.", Toast.LENGTH_SHORT).show();
                    }
                })
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
