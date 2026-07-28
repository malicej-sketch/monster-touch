package com.example.touchmapper;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

final class MappingStore {
    static final int SLOT_COUNT = 4;
    static final int PROFILE_COUNT = 4;
    static final int LOCK_SLOT = 3;
    static final int MARKER_TOGGLE_SLOT = 0;
    static final int TRIGGER_KEY = 0;
    static final int TRIGGER_MOUSE_GESTURE = 1;
    static final int TRIGGER_UNKNOWN = -1;
    static final int MOUSE_UP = 0;
    static final int MOUSE_DOWN = 1;
    static final int MOUSE_LEFT = 2;
    static final int MOUSE_RIGHT = 3;
    static final int MOUSE_STILL = 4;
    static final int DEVICE_MODE_UNKNOWN = 0;
    static final int DEVICE_MODE_KEYBOARD = 1;
    static final int DEVICE_MODE_MOTION = 2;
    static final int DEVICE_MODE_MIXED = 3;
    static final int MAX_TRAP_ZONES = 8;
    static final int TRAP_MODE_AUTO = 0;
    static final int TRAP_MODE_FULL_SCREEN = 1;

    private static final String PREFS = "touch_mappings";
    private static final String CURRENT_PROFILE = "current_profile";
    private static final String PROFILE_NAME = "profile_name_";
    private static final String BUTTON_NAME = "button_name_";
    private static final String KEY_CODE = "key_code_";
    private static final String TRIGGER_TYPE = "trigger_type_";
    private static final String TRIGGER_VALUE = "trigger_value_";
    private static final String TRIGGER_SIGNATURE = "trigger_signature_";
    private static final String LONG_TRIGGER_TYPE = "long_trigger_type_";
    private static final String LONG_TRIGGER_VALUE = "long_trigger_value_";
    private static final String LONG_TRIGGER_SIGNATURE = "long_trigger_signature_";
    private static final String INPUT_DEVICE_DESCRIPTOR = "input_device_descriptor";
    private static final String INPUT_DEVICE_NAME = "input_device_name";
    private static final String INPUT_DEVICE_VENDOR_ID = "input_device_vendor_id";
    private static final String INPUT_DEVICE_PRODUCT_ID = "input_device_product_id";
    private static final String INPUT_DEVICE_MODE = "input_device_mode";
    private static final String INPUT_BINDINGS_MIGRATED = "input_bindings_migrated_v2";
    private static final String CONTROLLER_SETTINGS_MIGRATED = "controller_settings_migrated_v3";
    private static final String TRAP_MODE = "trap_mode";
    private static final String TRAP_ZONE_COUNT = "trap_zone_count";
    private static final String TRAP_ZONE_X = "trap_zone_x_";
    private static final String TRAP_ZONE_Y = "trap_zone_y_";
    private static final String TRAP_ZONE_W = "trap_zone_w_";
    private static final String TRAP_ZONE_H = "trap_zone_h_";
    private static final String CONTROLLER_ANALYSIS_REPORT = "controller_analysis_report";
    private static final String CONTROLLER_ANALYSIS_SAVED_AT = "controller_analysis_saved_at";
    private static final String CONTROLLER_ANALYSIS_KEY_EVENTS = "controller_analysis_key_events";
    private static final String CONTROLLER_ANALYSIS_MOTION_EVENTS = "controller_analysis_motion_events";
    private static final String LEARNED_REMOTE_BUTTON_COUNT = "learned_remote_button_count";
    private static final String LEARNED_REMOTE_BUTTON_SIGNATURE = "learned_remote_button_signature_";
    private static final String LEARNED_REMOTE_BUTTON_DIRECTION = "learned_remote_button_direction_";
    private static final String X = "x_";
    private static final String Y = "y_";
    private static final String[] DEFAULT_PROFILE_NAMES = {
            "Baemin",
            "Coupang Eats",
            "Yogiyo",
            "Custom"
    };

    private MappingStore() {
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void reset(Context context) {
        prefs(context).edit().clear().apply();
    }

    static int currentProfile(Context context) {
        ensureControllerSettingsMigrated(context);
        return clampProfile(prefs(context).getInt(devicePreferenceKey(CURRENT_PROFILE, context), 0));
    }

    static void setCurrentProfile(Context context, int profile) {
        ensureControllerSettingsMigrated(context);
        prefs(context).edit()
                .putInt(devicePreferenceKey(CURRENT_PROFILE, context), clampProfile(profile))
                .apply();
    }

    static String profileName(Context context) {
        return profileName(context, currentProfile(context));
    }

    static String profileName(Context context, int profile) {
        ensureControllerSettingsMigrated(context);
        int safeProfile = clampProfile(profile);
        return prefs(context).getString(
                devicePreferenceKey(PROFILE_NAME + safeProfile, context), DEFAULT_PROFILE_NAMES[safeProfile]);
    }

    static void saveProfileName(Context context, String name) {
        String cleanName = cleanName(name, profileName(context));
        prefs(context).edit()
                .putString(devicePreferenceKey(PROFILE_NAME + currentProfile(context), context), cleanName)
                .apply();
    }

    static String buttonName(Context context, int slot) {
        return prefs(context).getString(profileKey(BUTTON_NAME, context, slot), "Button " + (slot + 1));
    }

    static void saveButtonName(Context context, int slot, String name) {
        String cleanName = cleanName(name, "Button " + (slot + 1));
        prefs(context).edit().putString(profileKey(BUTTON_NAME, context, slot), cleanName).apply();
    }

    static Mapping get(Context context, int slot) {
        ensureInputBindingsMigrated(context);
        SharedPreferences prefs = prefs(context);
        int safeSlot = clampSlot(slot);
        int keyCode = prefs.getInt(inputProfileKey(KEY_CODE, context, safeSlot), KeyEvent.KEYCODE_UNKNOWN);
        int triggerType = prefs.getInt(inputProfileKey(TRIGGER_TYPE, context, safeSlot),
                keyCode == KeyEvent.KEYCODE_UNKNOWN ? TRIGGER_UNKNOWN : TRIGGER_KEY);
        int triggerValue = prefs.getInt(inputProfileKey(TRIGGER_VALUE, context, safeSlot),
                keyCode == KeyEvent.KEYCODE_UNKNOWN ? TRIGGER_UNKNOWN : keyCode);
        String triggerSignature = prefs.getString(inputProfileKey(TRIGGER_SIGNATURE, context, safeSlot), "");
        int longTriggerType = prefs.getInt(inputProfileKey(LONG_TRIGGER_TYPE, context, safeSlot), TRIGGER_UNKNOWN);
        int longTriggerValue = prefs.getInt(inputProfileKey(LONG_TRIGGER_VALUE, context, safeSlot), TRIGGER_UNKNOWN);
        String longTriggerSignature = prefs.getString(inputProfileKey(LONG_TRIGGER_SIGNATURE, context, safeSlot), "");
        float x = prefs.getFloat(profileKey(X, context, safeSlot), -1f);
        float y = prefs.getFloat(profileKey(Y, context, safeSlot), -1f);
        return new Mapping(safeSlot, keyCode, triggerType, triggerValue,
                triggerSignature, longTriggerType, longTriggerValue, longTriggerSignature,
                buttonName(context, safeSlot), x, y);
    }

    static Mapping findByKeyCode(Context context, int keyCode) {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            Mapping mapping = get(context, slot);
            if (mapping.triggerType == TRIGGER_KEY
                    && mapping.triggerValue == keyCode
                    && isBlank(mapping.triggerSignature)) {
                return mapping;
            }
        }
        return null;
    }

    static Mapping findByKeySignature(Context context, int keyCode, String signature) {
        if (isBlank(signature)) {
            return null;
        }
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            Mapping mapping = get(context, slot);
            if (mapping.triggerType == TRIGGER_KEY
                    && mapping.triggerValue == keyCode
                    && signatureMatches(mapping.triggerSignature, signature)) {
                return mapping;
            }
        }
        return null;
    }

    static Mapping findByLongKeyCode(Context context, int keyCode) {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            Mapping mapping = get(context, slot);
            if (mapping.longTriggerType == TRIGGER_KEY
                    && mapping.longTriggerValue == keyCode
                    && isBlank(mapping.longTriggerSignature)) {
                return mapping;
            }
        }
        return null;
    }

    static Mapping findByLongKeySignature(Context context, int keyCode, String signature) {
        if (isBlank(signature)) {
            return null;
        }
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            Mapping mapping = get(context, slot);
            if (mapping.longTriggerType == TRIGGER_KEY
                    && mapping.longTriggerValue == keyCode
                    && signatureMatches(mapping.longTriggerSignature, signature)) {
                return mapping;
            }
        }
        return null;
    }

    static void saveKeyCode(Context context, int slot, int keyCode) {
        saveKeyCode(context, slot, keyCode, "");
    }

    static void saveKeyCode(Context context, int slot, int keyCode, String signature) {
        ensureInputBindingsMigrated(context);
        int safeSlot = clampSlot(slot);
        prefs(context).edit()
                .putInt(inputProfileKey(KEY_CODE, context, safeSlot), keyCode)
                .putInt(inputProfileKey(TRIGGER_TYPE, context, safeSlot), TRIGGER_KEY)
                .putInt(inputProfileKey(TRIGGER_VALUE, context, safeSlot), keyCode)
                .putString(inputProfileKey(TRIGGER_SIGNATURE, context, safeSlot), cleanName(signature, ""))
                .apply();
    }

    static void saveLongKeyCode(Context context, int slot, int keyCode) {
        saveLongKeyCode(context, slot, keyCode, "");
    }

    static void saveLongKeyCode(Context context, int slot, int keyCode, String signature) {
        ensureInputBindingsMigrated(context);
        int safeSlot = clampSlot(slot);
        prefs(context).edit()
                .putInt(inputProfileKey(LONG_TRIGGER_TYPE, context, safeSlot), TRIGGER_KEY)
                .putInt(inputProfileKey(LONG_TRIGGER_VALUE, context, safeSlot), keyCode)
                .putString(inputProfileKey(LONG_TRIGGER_SIGNATURE, context, safeSlot), cleanName(signature, ""))
                .apply();
    }

    static Mapping findByMouseGesture(Context context, int direction) {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            Mapping mapping = get(context, slot);
            if (mapping.triggerType == TRIGGER_MOUSE_GESTURE
                    && mapping.triggerValue == direction
                    && isBlank(mapping.triggerSignature)) {
                return mapping;
            }
        }
        return null;
    }

    static Mapping findByMouseSignature(Context context, int direction, String signature) {
        if (isBlank(signature)) {
            return null;
        }
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            Mapping mapping = get(context, slot);
            if (mapping.triggerType == TRIGGER_MOUSE_GESTURE
                    && mapping.triggerValue == direction
                    && signatureMatches(mapping.triggerSignature, signature)) {
                return mapping;
            }
        }
        return null;
    }

    static Mapping findByMouseSignature(Context context, String signature) {
        if (isBlank(signature)) {
            return null;
        }
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            Mapping mapping = get(context, slot);
            if (mapping.triggerType == TRIGGER_MOUSE_GESTURE
                    && signatureMatches(mapping.triggerSignature, signature)) {
                return mapping;
            }
        }
        return null;
    }

    static Mapping findByLongMouseGesture(Context context, int direction) {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            Mapping mapping = get(context, slot);
            if (mapping.longTriggerType == TRIGGER_MOUSE_GESTURE
                    && mapping.longTriggerValue == direction
                    && isBlank(mapping.longTriggerSignature)) {
                return mapping;
            }
        }
        return null;
    }

    static Mapping findByLongMouseSignature(Context context, int direction, String signature) {
        if (isBlank(signature)) {
            return null;
        }
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            Mapping mapping = get(context, slot);
            if (mapping.longTriggerType == TRIGGER_MOUSE_GESTURE
                    && mapping.longTriggerValue == direction
                    && signatureMatches(mapping.longTriggerSignature, signature)) {
                return mapping;
            }
        }
        return null;
    }

    static Mapping findByLongMouseSignature(Context context, String signature) {
        if (isBlank(signature)) {
            return null;
        }
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            Mapping mapping = get(context, slot);
            if (mapping.longTriggerType == TRIGGER_MOUSE_GESTURE
                    && signatureMatches(mapping.longTriggerSignature, signature)) {
                return mapping;
            }
        }
        return null;
    }

    static void saveMouseGesture(Context context, int slot, int direction) {
        saveMouseGesture(context, slot, direction, "");
    }

    static void saveMouseGesture(Context context, int slot, int direction, String signature) {
        ensureInputBindingsMigrated(context);
        int safeSlot = clampSlot(slot);
        prefs(context).edit()
                .putInt(inputProfileKey(KEY_CODE, context, safeSlot), KeyEvent.KEYCODE_UNKNOWN)
                .putInt(inputProfileKey(TRIGGER_TYPE, context, safeSlot), TRIGGER_MOUSE_GESTURE)
                .putInt(inputProfileKey(TRIGGER_VALUE, context, safeSlot), direction)
                .putString(inputProfileKey(TRIGGER_SIGNATURE, context, safeSlot), cleanName(signature, ""))
                .apply();
    }

    static void saveLongMouseGesture(Context context, int slot, int direction) {
        saveLongMouseGesture(context, slot, direction, "");
    }

    static void saveLongMouseGesture(Context context, int slot, int direction, String signature) {
        ensureInputBindingsMigrated(context);
        int safeSlot = clampSlot(slot);
        prefs(context).edit()
                .putInt(inputProfileKey(LONG_TRIGGER_TYPE, context, safeSlot), TRIGGER_MOUSE_GESTURE)
                .putInt(inputProfileKey(LONG_TRIGGER_VALUE, context, safeSlot), direction)
                .putString(inputProfileKey(LONG_TRIGGER_SIGNATURE, context, safeSlot), cleanName(signature, ""))
                .apply();
    }

    static String triggerLabel(Mapping mapping) {
        return triggerLabel(mapping.triggerType, mapping.triggerValue, mapping.triggerSignature);
    }

    static String longTriggerLabel(Mapping mapping) {
        return triggerLabel(mapping.longTriggerType, mapping.longTriggerValue, mapping.longTriggerSignature);
    }

    private static String triggerLabel(int triggerType, int triggerValue, String signature) {
        String suffix = isBlank(signature) ? "" : " " + signatureSummary(signature);
        if (triggerType == TRIGGER_KEY && triggerValue != TRIGGER_UNKNOWN) {
            return KeyEvent.keyCodeToString(triggerValue) + suffix;
        }
        if (triggerType == TRIGGER_MOUSE_GESTURE) {
            return "Mouse " + mouseDirectionLabel(triggerValue) + suffix;
        }
        return "Not set";
    }

    static String mouseDirectionLabel(int direction) {
        switch (direction) {
            case MOUSE_UP:
                return "Up";
            case MOUSE_DOWN:
                return "Down";
            case MOUSE_LEFT:
                return "Left";
            case MOUSE_RIGHT:
                return "Right";
            case MOUSE_STILL:
                return "Still";
            default:
                return "Unknown";
        }
    }

    static String triggerDisplayLabel(Mapping mapping) {
        return triggerDisplayLabel(mapping.triggerType, mapping.triggerValue);
    }

    static String longTriggerDisplayLabel(Mapping mapping) {
        return triggerDisplayLabel(mapping.longTriggerType, mapping.longTriggerValue);
    }

    private static String triggerDisplayLabel(int triggerType, int triggerValue) {
        if (triggerType == TRIGGER_KEY && triggerValue != TRIGGER_UNKNOWN) {
            return keyDisplayLabel(triggerValue);
        }
        if (triggerType == TRIGGER_MOUSE_GESTURE) {
            return "마우스 " + mouseDirectionDisplayLabel(triggerValue);
        }
        return "미지정";
    }

    static String mouseDirectionDisplayLabel(int direction) {
        switch (direction) {
            case MOUSE_UP:
                return "위";
            case MOUSE_DOWN:
                return "아래";
            case MOUSE_LEFT:
                return "왼쪽";
            case MOUSE_RIGHT:
                return "오른쪽";
            case MOUSE_STILL:
                return "제자리";
            default:
                return "알 수 없음";
        }
    }

    static String keyDisplayLabel(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                return "볼륨 위";
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                return "볼륨 아래";
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_MUTE:
                return "음소거";
            default:
                break;
        }
        String name = KeyEvent.keyCodeToString(keyCode);
        return name.startsWith("KEYCODE_") ? name.substring("KEYCODE_".length()) : name;
    }

    static String defaultButtonName(int slot) {
        return "Button " + (clampSlot(slot) + 1);
    }

    static boolean hasCustomButtonName(Context context, int slot) {
        return !defaultButtonName(slot).equals(buttonName(context, slot));
    }

    static void saveLearnedRemoteButton(Context context, int index, int direction, String signature) {
        if (isBlank(signature)) {
            return;
        }
        ensureInputBindingsMigrated(context);
        int safeIndex = Math.max(0, index);
        SharedPreferences.Editor editor = prefs(context).edit()
                .putString(devicePreferenceKey(LEARNED_REMOTE_BUTTON_SIGNATURE + safeIndex, context), signature)
                .putInt(devicePreferenceKey(LEARNED_REMOTE_BUTTON_DIRECTION + safeIndex, context), direction);
        int count = Math.max(learnedRemoteButtonCount(context), safeIndex + 1);
        editor.putInt(devicePreferenceKey(LEARNED_REMOTE_BUTTON_COUNT, context), count).apply();
    }

    static int learnedRemoteButtonCount(Context context) {
        ensureInputBindingsMigrated(context);
        return Math.max(0, prefs(context).getInt(devicePreferenceKey(LEARNED_REMOTE_BUTTON_COUNT, context), 0));
    }

    static String learnedRemoteButtonSignature(Context context, int index) {
        ensureInputBindingsMigrated(context);
        return prefs(context).getString(
                devicePreferenceKey(LEARNED_REMOTE_BUTTON_SIGNATURE + Math.max(0, index), context), "");
    }

    static int learnedRemoteButtonDirection(Context context, int index) {
        ensureInputBindingsMigrated(context);
        return prefs(context).getInt(
                devicePreferenceKey(LEARNED_REMOTE_BUTTON_DIRECTION + Math.max(0, index), context), TRIGGER_UNKNOWN);
    }

    static int findLearnedRemoteButton(Context context, String signature) {
        if (isBlank(signature)) {
            return -1;
        }
        int count = learnedRemoteButtonCount(context);
        for (int index = 0; index < count; index++) {
            if (signature.equals(learnedRemoteButtonSignature(context, index))) {
                return index;
            }
        }
        return -1;
    }

    static boolean hasLearnedRemoteButtons(Context context) {
        return learnedRemoteButtonCount(context) > 0;
    }

    static void selectAllInputDevices(Context context) {
        ensureInputBindingsMigrated(context);
        prefs(context).edit()
                .remove(INPUT_DEVICE_DESCRIPTOR)
                .remove(INPUT_DEVICE_NAME)
                .remove(INPUT_DEVICE_VENDOR_ID)
                .remove(INPUT_DEVICE_PRODUCT_ID)
                .remove(INPUT_DEVICE_MODE)
                .remove(TRAP_MODE)
                .remove(TRAP_ZONE_COUNT)
                .apply();
    }

    static void saveInputDevice(Context context, String descriptor, String name) {
        saveInputDevice(context, descriptor, name, 0, 0);
    }

    static void saveInputDevice(Context context, String descriptor, String name, int vendorId, int productId) {
        if (hasSelectedInputDevice(context)) {
            ensureInputBindingsMigrated(context);
            ensureControllerSettingsMigrated(context);
        }
        prefs(context).edit()
                .putString(INPUT_DEVICE_DESCRIPTOR, cleanName(descriptor, ""))
                .putString(INPUT_DEVICE_NAME, cleanName(name, "Selected device"))
                .putInt(INPUT_DEVICE_VENDOR_ID, Math.max(0, vendorId))
                .putInt(INPUT_DEVICE_PRODUCT_ID, Math.max(0, productId))
                .apply();
        ensureInputBindingsMigrated(context);
        ensureControllerSettingsMigrated(context);
    }

    static void saveInputDeviceMode(Context context, int mode) {
        ensureControllerSettingsMigrated(context);
        prefs(context).edit().putInt(devicePreferenceKey(INPUT_DEVICE_MODE, context), mode).apply();
    }

    static int inputDeviceMode(Context context) {
        ensureControllerSettingsMigrated(context);
        return prefs(context).getInt(devicePreferenceKey(INPUT_DEVICE_MODE, context), DEVICE_MODE_UNKNOWN);
    }

    static String inputDeviceModeLabel(Context context) {
        switch (inputDeviceMode(context)) {
            case DEVICE_MODE_KEYBOARD:
                return "KEY";
            case DEVICE_MODE_MOTION:
                return "TOUCH";
            case DEVICE_MODE_MIXED:
                return "TOUCH+KEY";
            default:
                return "UNKNOWN";
        }
    }

    static int trapMode(Context context) {
        ensureControllerSettingsMigrated(context);
        return prefs(context).getInt(devicePreferenceKey(TRAP_MODE, context), TRAP_MODE_AUTO);
    }

    static void saveTrapMode(Context context, int mode) {
        ensureControllerSettingsMigrated(context);
        prefs(context).edit().putInt(devicePreferenceKey(TRAP_MODE, context), mode).apply();
    }

    static boolean isFullScreenTrapMode(Context context) {
        return trapMode(context) == TRAP_MODE_FULL_SCREEN;
    }

    static boolean hasSelectedInputDevice(Context context) {
        return !selectedInputDeviceDescriptor(context).isEmpty();
    }

    static String selectedInputDeviceDescriptor(Context context) {
        return prefs(context).getString(INPUT_DEVICE_DESCRIPTOR, "");
    }

    static String selectedInputDeviceName(Context context) {
        return prefs(context).getString(INPUT_DEVICE_NAME, "All devices");
    }

    static int selectedInputDeviceVendorId(Context context) {
        return prefs(context).getInt(INPUT_DEVICE_VENDOR_ID, 0);
    }

    static int selectedInputDeviceProductId(Context context) {
        return prefs(context).getInt(INPUT_DEVICE_PRODUCT_ID, 0);
    }

    static boolean acceptsInputDevice(Context context, String descriptor) {
        String selectedDescriptor = selectedInputDeviceDescriptor(context);
        return !selectedDescriptor.isEmpty() && selectedDescriptor.equals(descriptor);
    }

    static boolean acceptsInputDevice(Context context, String descriptor, String name, int vendorId, int productId) {
        String selectedDescriptor = selectedInputDeviceDescriptor(context);
        if (selectedDescriptor.isEmpty()) {
            return false;
        }
        if (selectedDescriptor.equals(cleanName(descriptor, ""))) {
            return true;
        }

        int selectedVendor = selectedInputDeviceVendorId(context);
        int selectedProduct = selectedInputDeviceProductId(context);
        if (selectedVendor > 0 && selectedProduct > 0
                && selectedVendor == vendorId && selectedProduct == productId) {
            return true;
        }

        String selectedName = selectedInputDeviceName(context);
        String cleanSelectedName = cleanName(selectedName, "");
        String cleanDeviceName = cleanName(name, "");
        return !cleanSelectedName.isEmpty()
                && !cleanDeviceName.isEmpty()
                && cleanDeviceName.equals(cleanSelectedName);
    }

    static void saveControllerAnalysis(Context context, String report, int keyEvents, int motionEvents) {
        ensureControllerSettingsMigrated(context);
        prefs(context).edit()
                .putString(devicePreferenceKey(CONTROLLER_ANALYSIS_REPORT, context), cleanName(report, ""))
                .putLong(devicePreferenceKey(CONTROLLER_ANALYSIS_SAVED_AT, context), System.currentTimeMillis())
                .putInt(devicePreferenceKey(CONTROLLER_ANALYSIS_KEY_EVENTS, context), Math.max(0, keyEvents))
                .putInt(devicePreferenceKey(CONTROLLER_ANALYSIS_MOTION_EVENTS, context), Math.max(0, motionEvents))
                .apply();
    }

    static String lastControllerAnalysisSummary(Context context) {
        ensureControllerSettingsMigrated(context);
        SharedPreferences prefs = prefs(context);
        long savedAt = prefs.getLong(devicePreferenceKey(CONTROLLER_ANALYSIS_SAVED_AT, context), 0L);
        if (savedAt <= 0L) {
            return "No saved analyzer session.";
        }

        return "Last saved: " + savedAt
                + "\nkey events " + prefs.getInt(devicePreferenceKey(CONTROLLER_ANALYSIS_KEY_EVENTS, context), 0)
                + ", motion events "
                + prefs.getInt(devicePreferenceKey(CONTROLLER_ANALYSIS_MOTION_EVENTS, context), 0);
    }

    static void clearTrapZones(Context context) {
        ensureControllerSettingsMigrated(context);
        SharedPreferences.Editor editor = prefs(context).edit()
                .putInt(devicePreferenceKey(TRAP_ZONE_COUNT, context), 0);
        for (int index = 0; index < MAX_TRAP_ZONES; index++) {
            editor.remove(devicePreferenceKey(TRAP_ZONE_X + index, context))
                    .remove(devicePreferenceKey(TRAP_ZONE_Y + index, context))
                    .remove(devicePreferenceKey(TRAP_ZONE_W + index, context))
                    .remove(devicePreferenceKey(TRAP_ZONE_H + index, context));
        }
        editor.apply();
    }

    static void saveTrapZones(Context context, List<TrapZone> zones) {
        ensureControllerSettingsMigrated(context);
        SharedPreferences.Editor editor = prefs(context).edit();
        int count = Math.min(MAX_TRAP_ZONES, zones == null ? 0 : zones.size());
        editor.putInt(devicePreferenceKey(TRAP_ZONE_COUNT, context), count);
        for (int index = 0; index < MAX_TRAP_ZONES; index++) {
            if (index < count) {
                TrapZone zone = zones.get(index);
                editor.putInt(devicePreferenceKey(TRAP_ZONE_X + index, context), zone.x)
                        .putInt(devicePreferenceKey(TRAP_ZONE_Y + index, context), zone.y)
                        .putInt(devicePreferenceKey(TRAP_ZONE_W + index, context), zone.width)
                        .putInt(devicePreferenceKey(TRAP_ZONE_H + index, context), zone.height);
            } else {
                editor.remove(devicePreferenceKey(TRAP_ZONE_X + index, context))
                        .remove(devicePreferenceKey(TRAP_ZONE_Y + index, context))
                        .remove(devicePreferenceKey(TRAP_ZONE_W + index, context))
                        .remove(devicePreferenceKey(TRAP_ZONE_H + index, context));
            }
        }
        editor.apply();
    }

    static List<TrapZone> trapZones(Context context) {
        ensureControllerSettingsMigrated(context);
        SharedPreferences prefs = prefs(context);
        int count = Math.max(0, Math.min(MAX_TRAP_ZONES,
                prefs.getInt(devicePreferenceKey(TRAP_ZONE_COUNT, context), 0)));
        List<TrapZone> zones = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int width = prefs.getInt(devicePreferenceKey(TRAP_ZONE_W + index, context), 0);
            int height = prefs.getInt(devicePreferenceKey(TRAP_ZONE_H + index, context), 0);
            if (width <= 0 || height <= 0) {
                continue;
            }
            zones.add(new TrapZone(
                    prefs.getInt(devicePreferenceKey(TRAP_ZONE_X + index, context), 0),
                    prefs.getInt(devicePreferenceKey(TRAP_ZONE_Y + index, context), 0),
                    width,
                    height
            ));
        }
        return zones;
    }

    static void savePoint(Context context, int slot, float x, float y) {
        int safeSlot = clampSlot(slot);
        prefs(context).edit()
                .putFloat(profileKey(X, context, safeSlot), x)
                .putFloat(profileKey(Y, context, safeSlot), y)
                .apply();
    }

    private static String profileKey(String prefix, Context context, int slot) {
        return devicePreferenceKey("p" + currentProfile(context) + "_" + prefix + slot, context);
    }

    private static String inputProfileKey(String prefix, Context context, int slot) {
        return profileKey(prefix, context, slot);
    }

    private static String devicePreferenceKey(String key, Context context) {
        return "d" + Integer.toHexString(deviceIdentity(context).hashCode()) + "_" + key;
    }

    private static String deviceIdentity(Context context) {
        SharedPreferences prefs = prefs(context);
        String descriptor = prefs.getString(INPUT_DEVICE_DESCRIPTOR, "");
        if (!isBlank(descriptor)) {
            return "descriptor:" + descriptor.trim();
        }

        int vendorId = prefs.getInt(INPUT_DEVICE_VENDOR_ID, 0);
        int productId = prefs.getInt(INPUT_DEVICE_PRODUCT_ID, 0);
        String name = prefs.getString(INPUT_DEVICE_NAME, "");
        if (vendorId > 0 || productId > 0 || !isBlank(name)) {
            return "hardware:" + vendorId + ":" + productId + ":" + cleanName(name, "unknown");
        }
        return "all-devices";
    }

    private static void ensureInputBindingsMigrated(Context context) {
        SharedPreferences prefs = prefs(context);
        if (prefs.getBoolean(INPUT_BINDINGS_MIGRATED, false)) {
            return;
        }

        SharedPreferences.Editor editor = prefs.edit();
        String[] intPrefixes = {
                KEY_CODE, TRIGGER_TYPE, TRIGGER_VALUE, LONG_TRIGGER_TYPE, LONG_TRIGGER_VALUE
        };
        String[] stringPrefixes = {TRIGGER_SIGNATURE, LONG_TRIGGER_SIGNATURE};
        for (int profile = 0; profile < PROFILE_COUNT; profile++) {
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                for (String prefix : intPrefixes) {
                    String legacyKey = "p" + profile + "_" + prefix + slot;
                    if (prefs.contains(legacyKey)) {
                        editor.putInt(devicePreferenceKey(legacyKey, context),
                                prefs.getInt(legacyKey, TRIGGER_UNKNOWN));
                    }
                }
                for (String prefix : stringPrefixes) {
                    String legacyKey = "p" + profile + "_" + prefix + slot;
                    if (prefs.contains(legacyKey)) {
                        editor.putString(devicePreferenceKey(legacyKey, context),
                                prefs.getString(legacyKey, ""));
                    }
                }
            }
        }

        int learnedCount = Math.max(0, prefs.getInt(LEARNED_REMOTE_BUTTON_COUNT, 0));
        if (learnedCount > 0) {
            editor.putInt(devicePreferenceKey(LEARNED_REMOTE_BUTTON_COUNT, context), learnedCount);
            for (int index = 0; index < learnedCount; index++) {
                editor.putString(devicePreferenceKey(LEARNED_REMOTE_BUTTON_SIGNATURE + index, context),
                        prefs.getString(LEARNED_REMOTE_BUTTON_SIGNATURE + index, ""));
                editor.putInt(devicePreferenceKey(LEARNED_REMOTE_BUTTON_DIRECTION + index, context),
                        prefs.getInt(LEARNED_REMOTE_BUTTON_DIRECTION + index, TRIGGER_UNKNOWN));
            }
        }
        editor.putBoolean(INPUT_BINDINGS_MIGRATED, true).apply();
    }

    private static void ensureControllerSettingsMigrated(Context context) {
        SharedPreferences prefs = prefs(context);
        if (prefs.getBoolean(CONTROLLER_SETTINGS_MIGRATED, false)
                || isBlank(prefs.getString(INPUT_DEVICE_DESCRIPTOR, ""))) {
            return;
        }

        SharedPreferences.Editor editor = prefs.edit();
        int currentProfile = clampProfile(prefs.getInt(CURRENT_PROFILE, 0));
        editor.putInt(devicePreferenceKey(CURRENT_PROFILE, context), currentProfile);

        for (int profile = 0; profile < PROFILE_COUNT; profile++) {
            copyStringPreference(prefs, editor, PROFILE_NAME + profile,
                    devicePreferenceKey(PROFILE_NAME + profile, context));
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                String profilePrefix = "p" + profile + "_";
                copyStringPreference(prefs, editor, profilePrefix + BUTTON_NAME + slot,
                        devicePreferenceKey(profilePrefix + BUTTON_NAME + slot, context));
                copyLegacyPointPreference(prefs, editor, profilePrefix + X + slot,
                        legacyTriggerKey(X, slot), X + slot,
                        devicePreferenceKey(profilePrefix + X + slot, context));
                copyLegacyPointPreference(prefs, editor, profilePrefix + Y + slot,
                        legacyTriggerKey(Y, slot), Y + slot,
                        devicePreferenceKey(profilePrefix + Y + slot, context));
            }
        }

        copyIntPreference(prefs, editor, INPUT_DEVICE_MODE, devicePreferenceKey(INPUT_DEVICE_MODE, context));
        copyIntPreference(prefs, editor, TRAP_MODE, devicePreferenceKey(TRAP_MODE, context));
        copyIntPreference(prefs, editor, TRAP_ZONE_COUNT, devicePreferenceKey(TRAP_ZONE_COUNT, context));
        for (int index = 0; index < MAX_TRAP_ZONES; index++) {
            copyIntPreference(prefs, editor, TRAP_ZONE_X + index,
                    devicePreferenceKey(TRAP_ZONE_X + index, context));
            copyIntPreference(prefs, editor, TRAP_ZONE_Y + index,
                    devicePreferenceKey(TRAP_ZONE_Y + index, context));
            copyIntPreference(prefs, editor, TRAP_ZONE_W + index,
                    devicePreferenceKey(TRAP_ZONE_W + index, context));
            copyIntPreference(prefs, editor, TRAP_ZONE_H + index,
                    devicePreferenceKey(TRAP_ZONE_H + index, context));
        }

        copyStringPreference(prefs, editor, CONTROLLER_ANALYSIS_REPORT,
                devicePreferenceKey(CONTROLLER_ANALYSIS_REPORT, context));
        copyLongPreference(prefs, editor, CONTROLLER_ANALYSIS_SAVED_AT,
                devicePreferenceKey(CONTROLLER_ANALYSIS_SAVED_AT, context));
        copyIntPreference(prefs, editor, CONTROLLER_ANALYSIS_KEY_EVENTS,
                devicePreferenceKey(CONTROLLER_ANALYSIS_KEY_EVENTS, context));
        copyIntPreference(prefs, editor, CONTROLLER_ANALYSIS_MOTION_EVENTS,
                devicePreferenceKey(CONTROLLER_ANALYSIS_MOTION_EVENTS, context));

        editor.putBoolean(CONTROLLER_SETTINGS_MIGRATED, true).apply();
    }

    private static void copyStringPreference(SharedPreferences prefs, SharedPreferences.Editor editor,
            String source, String target) {
        if (prefs.contains(source) && !prefs.contains(target)) {
            editor.putString(target, prefs.getString(source, ""));
        }
    }

    private static void copyIntPreference(SharedPreferences prefs, SharedPreferences.Editor editor,
            String source, String target) {
        if (prefs.contains(source) && !prefs.contains(target)) {
            editor.putInt(target, prefs.getInt(source, 0));
        }
    }

    private static void copyLongPreference(SharedPreferences prefs, SharedPreferences.Editor editor,
            String source, String target) {
        if (prefs.contains(source) && !prefs.contains(target)) {
            editor.putLong(target, prefs.getLong(source, 0L));
        }
    }

    private static void copyLegacyPointPreference(SharedPreferences prefs, SharedPreferences.Editor editor,
            String profileSource, String legacySource, String oldestSource, String target) {
        if (prefs.contains(target)) {
            return;
        }
        if (prefs.contains(profileSource)) {
            editor.putFloat(target, prefs.getFloat(profileSource, -1f));
        } else if (prefs.contains(legacySource)) {
            editor.putFloat(target, prefs.getFloat(legacySource, -1f));
        } else if (prefs.contains(oldestSource)) {
            editor.putFloat(target, prefs.getFloat(oldestSource, -1f));
        }
    }

    private static String legacyTriggerKey(String prefix, int slot) {
        return prefix + slot + "_0";
    }

    private static int clampProfile(int profile) {
        return Math.max(0, Math.min(PROFILE_COUNT - 1, profile));
    }

    private static int clampSlot(int slot) {
        return Math.max(0, Math.min(SLOT_COUNT - 1, slot));
    }

    private static String cleanName(String name, String fallback) {
        String trimmed = name == null ? "" : name.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean signatureMatches(String savedSignature, String currentSignature) {
        if (isBlank(savedSignature) || isBlank(currentSignature)) {
            return false;
        }
        String current = currentSignature.trim();
        for (String saved : savedSignature.split("\\n")) {
            if (current.equals(saved.trim())) {
                return true;
            }
        }
        return false;
    }

    private static String signatureSummary(String signature) {
        int count = signatureCount(signature);
        if (count > 1) {
            return "samples:" + count;
        }
        return signature.trim();
    }

    private static int signatureCount(String signature) {
        if (isBlank(signature)) {
            return 0;
        }
        int count = 0;
        for (String saved : signature.split("\\n")) {
            if (!isBlank(saved)) {
                count++;
            }
        }
        return count;
    }

    static final class Mapping {
        final int slot;
        final int keyCode;
        final int triggerType;
        final int triggerValue;
        final String triggerSignature;
        final int longTriggerType;
        final int longTriggerValue;
        final String longTriggerSignature;
        final String name;
        final float x;
        final float y;

        Mapping(int slot, int keyCode, int triggerType, int triggerValue,
                String triggerSignature, int longTriggerType, int longTriggerValue,
                String longTriggerSignature, String name, float x, float y) {
            this.slot = slot;
            this.keyCode = keyCode;
            this.triggerType = triggerType;
            this.triggerValue = triggerValue;
            this.triggerSignature = triggerSignature;
            this.longTriggerType = longTriggerType;
            this.longTriggerValue = longTriggerValue;
            this.longTriggerSignature = longTriggerSignature;
            this.name = name;
            this.x = x;
            this.y = y;
        }

        boolean hasKey() {
            return triggerType != TRIGGER_UNKNOWN && triggerValue != TRIGGER_UNKNOWN;
        }

        boolean hasLongKey() {
            return longTriggerType != TRIGGER_UNKNOWN && longTriggerValue != TRIGGER_UNKNOWN;
        }

        boolean hasPoint() {
            return x >= 0f && y >= 0f;
        }

        boolean canRun() {
            return hasPoint();
        }
    }

    static final class TrapZone {
        final int x;
        final int y;
        final int width;
        final int height;

        TrapZone(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
