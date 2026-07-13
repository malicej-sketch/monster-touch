package com.example.touchmapper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.GestureResultCallback;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class TouchAccessibilityService extends AccessibilityService {
    private static final long LOCK_LONG_CLICK_MS = 5000L;

    private static TouchAccessibilityService instance;
    private static boolean configurationActive;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private View pickerOverlay;
    private View setupOverlay;
    private WindowManager.LayoutParams setupOverlayParams;
    private View positionOverlay;
    private View touchLockOverlay;
    private boolean touchLocked;
    private boolean positionsVisible;
    private int heldKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private int heldSlot = -1;
    private boolean longClickTriggered;
    private final Runnable lockLongClickRunnable = new Runnable() {
        @Override
        public void run() {
            if (heldSlot == MappingStore.LOCK_SLOT) {
                longClickTriggered = true;
                toggleTouchLock();
            }
        }
    };

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
        } else {
            service.showPositionOverlay();
            service.positionsVisible = true;
        }
        return true;
    }

    static boolean arePositionsVisible() {
        TouchAccessibilityService service = instance;
        return service != null && service.positionsVisible;
    }

    static void refreshPositionOverlay() {
        TouchAccessibilityService service = instance;
        if (service != null && service.positionsVisible) {
            service.showPositionOverlay();
        }
    }

    static boolean isTouchLocked() {
        TouchAccessibilityService service = instance;
        return service != null && service.touchLocked;
    }

    static void setConfigurationActive(boolean active) {
        configurationActive = active;
    }

    static boolean showSetupOverlay() {
        TouchAccessibilityService service = instance;
        if (service == null) {
            return false;
        }

        service.showSetupPanel();
        return true;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (configurationActive) {
            return false;
        }

        MappingStore.Mapping mapping = MappingStore.findByKeyCode(this, event.getKeyCode());
        if (mapping == null) {
            return false;
        }

        return handleMappedKey(event, mapping.slot);
    }

    @Override
    public void onServiceConnected() {
        instance = this;
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    }

    @Override
    public void onDestroy() {
        hidePointPicker();
        hideSetupPanel();
        hidePositionOverlay();
        hideTouchLockOverlay();
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    private boolean handleMappedKey(KeyEvent event, int slot) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                heldKeyCode = event.getKeyCode();
                heldSlot = slot;
                longClickTriggered = false;
                if (slot == MappingStore.LOCK_SLOT) {
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

            if (!longClickTriggered) {
                executeSlot(releasedSlot);
            }
            longClickTriggered = false;
            return true;
        }

        return true;
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

    private void tap(float x, float y) {
        boolean restoreLockOverlay = touchLocked && touchLockOverlay != null;
        if (restoreLockOverlay) {
            hideTouchLockOverlay();
            mainHandler.postDelayed(() -> performTap(x, y, true), 90);
            return;
        }

        performTap(x, y, false);
    }

    private void performTap(float x, float y, boolean restoreLockOverlay) {
        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 80))
                .build();

        dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                restoreTouchLockOverlayIfNeeded(restoreLockOverlay);
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                restoreTouchLockOverlayIfNeeded(restoreLockOverlay);
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

    private void restoreTouchLockOverlayIfNeeded(boolean shouldRestore) {
        if (shouldRestore && touchLocked) {
            mainHandler.postDelayed(this::showTouchLockOverlay, 120);
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
    }

    private LinearLayout.LayoutParams panelParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(3), 0, dp(3));
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
    }

    private void showPositionOverlay() {
        hidePositionOverlay();

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0x00000000);

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

            FrameLayout.LayoutParams markerParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            );
            markerParams.leftMargin = Math.max(0, Math.round(mapping.x) - dp(28));
            markerParams.topMargin = Math.max(0, Math.round(mapping.y) - dp(16));
            overlay.addView(marker, markerParams);
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;

        positionOverlay = overlay;
        windowManager.addView(positionOverlay, params);
    }

    private void hidePositionOverlay() {
        if (positionOverlay == null || windowManager == null) {
            positionOverlay = null;
            return;
        }

        windowManager.removeView(positionOverlay);
        positionOverlay = null;
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

    private void hidePointPicker() {
        if (pickerOverlay == null || windowManager == null) {
            pickerOverlay = null;
            return;
        }

        windowManager.removeView(pickerOverlay);
        pickerOverlay = null;
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

        overlay.setOnTouchListener((view, event) -> true);

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
