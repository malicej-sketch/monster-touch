package com.example.touchmapper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int COLOR_PAGE = 0xFFF4F6F8;
    private static final int COLOR_SURFACE = 0xFFFFFFFF;
    private static final int COLOR_TEXT = 0xFF1F2933;
    private static final int COLOR_MUTED = 0xFF657282;
    private static final int COLOR_PRIMARY = 0xFF2563EB;
    private static final int COLOR_PRIMARY_SOFT = 0xFFEAF2FF;
    private static final int COLOR_BORDER = 0xFFE0E5EC;

    private int captureSlot = -1;
    private LinearLayout mappingsContainer;
    private TextView statusText;
    private Button profileButton;
    private Button showPositionsButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        TouchAccessibilityService.setConfigurationActive(true);
        renderMappings();
    }

    @Override
    protected void onPause() {
        TouchAccessibilityService.setConfigurationActive(false);
        super.onPause();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (captureSlot < 0 || event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }

        MappingStore.saveKeyCode(this, captureSlot, event.getKeyCode());
        Toast.makeText(this, "버튼 " + (captureSlot + 1) + " 키 저장: " + KeyEvent.keyCodeToString(event.getKeyCode()), Toast.LENGTH_SHORT).show();
        captureSlot = -1;
        renderMappings();
        return true;
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

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.monster_logo);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(148), dp(56));
        logoParams.setMargins(0, 0, dp(12), 0);
        brandHeader.addView(logo, logoParams);

        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        brandText.setGravity(android.view.Gravity.CENTER_VERTICAL);
        brandHeader.addView(brandText, new LinearLayout.LayoutParams(0, dp(56), 1f));

        TextView title = new TextView(this);
        title.setText("MONSTER Touch");
        title.setTextSize(22f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(COLOR_TEXT);
        brandText.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("시선은 도로에, 조작은 버튼으로");
        subtitle.setTextSize(13f);
        subtitle.setTextColor(COLOR_MUTED);
        subtitle.setPadding(0, dp(2), 0, 0);
        brandText.addView(subtitle);

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

        profileButton = makeButton("", 0xFFFFFFFF, COLOR_TEXT);
        profileButton.setOnClickListener(view -> showProfilePicker());
        root.addView(profileButton, matchWidthParams());

        Button renameProfileButton = makeButton("현재 설정 이름 변경", 0xFFF1F5F9, COLOR_TEXT);
        renameProfileButton.setOnClickListener(view -> showProfileNameEditor());
        root.addView(renameProfileButton, matchWidthParams());

        LinearLayout setupActions = new LinearLayout(this);
        setupActions.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(setupActions, matchWidthParams());

        Button setupPanelButton = makeButton("위치 설정", 0xFFEAF2FF, COLOR_PRIMARY);
        setupPanelButton.setOnClickListener(view -> {
            boolean started = TouchAccessibilityService.showSetupOverlay();
            if (!started) {
                Toast.makeText(this, "먼저 접근성 서비스를 켜주세요.", Toast.LENGTH_SHORT).show();
            }
        });
        setupActions.addView(setupPanelButton, utilityButtonParams());

        showPositionsButton = makeButton(
                TouchAccessibilityService.arePositionsVisible() ? "위치 숨김" : "위치 표시",
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
                    captureSlot = -1;
                    renderMappings();
                    Toast.makeText(this, "초기화 완료", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void renderMappings() {
        if (profileButton != null) {
            profileButton.setText("현재 설정: " + MappingStore.profileName(this));
        }
        if (showPositionsButton != null) {
            showPositionsButton.setText(TouchAccessibilityService.arePositionsVisible() ? "위치 숨김" : "위치 표시");
        }

        String lockText = TouchAccessibilityService.isTouchLocked() ? "터치 잠금 ON" : "터치 잠금 OFF";
        statusText.setText(TouchAccessibilityService.isRunning()
                ? "접근성 ON · " + lockText + " · 버튼 4를 5초 누르면 잠금/해제"
                : "먼저 접근성 설정에서 MONSTER Touch Controller를 켜주세요.");

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

        Button captureButton = makeSmallButton(captureSlot == slot ? "키를 누르세요" : "키 입력", 0xFFEAF2FF, COLOR_PRIMARY);
        captureButton.setOnClickListener(view -> {
            captureSlot = slot;
            renderMappings();
        });
        actions.addView(captureButton, utilityButtonParams());

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
        return KeyEvent.keyCodeToString(mapping.keyCode);
    }

    private String pointLabel(MappingStore.Mapping mapping) {
        if (!mapping.hasPoint()) {
            return "미지정";
        }
        return Math.round(mapping.x) + ", " + Math.round(mapping.y);
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
