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
        return clampProfile(prefs(context).getInt(CURRENT_PROFILE, 0));
    }

    static void setCurrentProfile(Context context, int profile) {
        prefs(context).edit().putInt(CURRENT_PROFILE, clampProfile(profile)).apply();
    }

    static String profileName(Context context) {
        return profileName(context, currentProfile(context));
    }

    static String profileName(Context context, int profile) {
        int safeProfile = clampProfile(profile);
        return prefs(context).getString(PROFILE_NAME + safeProfile, DEFAULT_PROFILE_NAMES[safeProfile]);
    }

    static void saveProfileName(Context context, String name) {
        String cleanName = cleanName(name, profileName(context));
        prefs(context).edit().putString(PROFILE_NAME + currentProfile(context), cleanName).apply();
    }

    static String buttonName(Context context, int slot) {
        return prefs(context).getString(profileKey(BUTTON_NAME, context, slot), "Button " + (slot + 1));
    }

    static void saveButtonName(Context context, int slot, String name) {
        String cleanName = cleanName(name, "Button " + (slot + 1));
        prefs(context).edit().putString(profileKey(BUTTON_NAME, context, slot), cleanName).apply();
    }

    static Mapping get(Context context, int slot) {
        SharedPreferences prefs = prefs(context);
        int safeSlot = clampSlot(slot);
        int keyCode = prefs.getInt(profileKey(KEY_CODE, context, safeSlot),
                prefs.getInt(KEY_CODE + safeSlot, KeyEvent.KEYCODE_UNKNOWN));
        int triggerType = prefs.getInt(profileKey(TRIGGER_TYPE, context, safeSlot),
                keyCode == KeyEvent.KEYCODE_UNKNOWN ? TRIGGER_UNKNOWN : TRIGGER_KEY);
        int triggerValue = prefs.getInt(profileKey(TRIGGER_VALUE, context, safeSlot),
                keyCode == KeyEvent.KEYCODE_UNKNOWN ? TRIGGER_UNKNOWN : keyCode);
        String triggerSignature = prefs.getString(profileKey(TRIGGER_SIGNATURE, context, safeSlot), "");
        int longTriggerType = prefs.getInt(profileKey(LONG_TRIGGER_TYPE, context, safeSlot), TRIGGER_UNKNOWN);
        int longTriggerValue = prefs.getInt(profileKey(LONG_TRIGGER_VALUE, context, safeSlot), TRIGGER_UNKNOWN);
        String longTriggerSignature = prefs.getString(profileKey(LONG_TRIGGER_SIGNATURE, context, safeSlot), "");
        float x = prefs.getFloat(profileKey(X, context, safeSlot),
                prefs.getFloat(legacyTriggerKey(X, safeSlot), prefs.getFloat(X + safeSlot, -1f)));
        float y = prefs.getFloat(profileKey(Y, context, safeSlot),
                prefs.getFloat(legacyTriggerKey(Y, safeSlot), prefs.getFloat(Y + safeSlot, -1f)));
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
        int safeSlot = clampSlot(slot);
        prefs(context).edit()
                .putInt(profileKey(KEY_CODE, context, safeSlot), keyCode)
                .putInt(profileKey(TRIGGER_TYPE, context, safeSlot), TRIGGER_KEY)
                .putInt(profileKey(TRIGGER_VALUE, context, safeSlot), keyCode)
                .putString(profileKey(TRIGGER_SIGNATURE, context, safeSlot), cleanName(signature, ""))
                .apply();
    }

    static void saveLongKeyCode(Context context, int slot, int keyCode) {
        saveLongKeyCode(context, slot, keyCode, "");
    }

    static void saveLongKeyCode(Context context, int slot, int keyCode, String signature) {
        int safeSlot = clampSlot(slot);
        prefs(context).edit()
                .putInt(profileKey(LONG_TRIGGER_TYPE, context, safeSlot), TRIGGER_KEY)
                .putInt(profileKey(LONG_TRIGGER_VALUE, context, safeSlot), keyCode)
                .putString(profileKey(LONG_TRIGGER_SIGNATURE, context, safeSlot), cleanName(signature, ""))
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
        int safeSlot = clampSlot(slot);
        prefs(context).edit()
                .putInt(profileKey(KEY_CODE, context, safeSlot), KeyEvent.KEYCODE_UNKNOWN)
                .putInt(profileKey(TRIGGER_TYPE, context, safeSlot), TRIGGER_MOUSE_GESTURE)
                .putInt(profileKey(TRIGGER_VALUE, context, safeSlot), direction)
                .putString(profileKey(TRIGGER_SIGNATURE, context, safeSlot), cleanName(signature, ""))
                .apply();
    }

    static void saveLongMouseGesture(Context context, int slot, int direction) {
        saveLongMouseGesture(context, slot, direction, "");
    }

    static void saveLongMouseGesture(Context context, int slot, int direction, String signature) {
        int safeSlot = clampSlot(slot);
        prefs(context).edit()
                .putInt(profileKey(LONG_TRIGGER_TYPE, context, safeSlot), TRIGGER_MOUSE_GESTURE)
                .putInt(profileKey(LONG_TRIGGER_VALUE, context, safeSlot), direction)
                .putString(profileKey(LONG_TRIGGER_SIGNATURE, context, safeSlot), cleanName(signature, ""))
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

    static void saveLearnedRemoteButton(Context context, int index, int direction, String signature) {
        if (isBlank(signature)) {
            return;
        }
        int safeIndex = Math.max(0, index);
        SharedPreferences.Editor editor = prefs(context).edit()
                .putString(LEARNED_REMOTE_BUTTON_SIGNATURE + safeIndex, signature)
                .putInt(LEARNED_REMOTE_BUTTON_DIRECTION + safeIndex, direction);
        int count = Math.max(learnedRemoteButtonCount(context), safeIndex + 1);
        editor.putInt(LEARNED_REMOTE_BUTTON_COUNT, count).apply();
    }

    static int learnedRemoteButtonCount(Context context) {
        return Math.max(0, prefs(context).getInt(LEARNED_REMOTE_BUTTON_COUNT, 0));
    }

    static String learnedRemoteButtonSignature(Context context, int index) {
        return prefs(context).getString(LEARNED_REMOTE_BUTTON_SIGNATURE + Math.max(0, index), "");
    }

    static int learnedRemoteButtonDirection(Context context, int index) {
        return prefs(context).getInt(LEARNED_REMOTE_BUTTON_DIRECTION + Math.max(0, index), TRIGGER_UNKNOWN);
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
        prefs(context).edit()
                .putString(INPUT_DEVICE_DESCRIPTOR, cleanName(descriptor, ""))
                .putString(INPUT_DEVICE_NAME, cleanName(name, "Selected device"))
                .putInt(INPUT_DEVICE_VENDOR_ID, Math.max(0, vendorId))
                .putInt(INPUT_DEVICE_PRODUCT_ID, Math.max(0, productId))
                .putInt(INPUT_DEVICE_MODE, DEVICE_MODE_UNKNOWN)
                .putInt(TRAP_MODE, TRAP_MODE_FULL_SCREEN)
                .remove(TRAP_ZONE_COUNT)
                .apply();
    }

    static void saveInputDeviceMode(Context context, int mode) {
        prefs(context).edit().putInt(INPUT_DEVICE_MODE, mode).apply();
    }

    static int inputDeviceMode(Context context) {
        return prefs(context).getInt(INPUT_DEVICE_MODE, DEVICE_MODE_UNKNOWN);
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
        return prefs(context).getInt(TRAP_MODE, TRAP_MODE_FULL_SCREEN);
    }

    static void saveTrapMode(Context context, int mode) {
        prefs(context).edit().putInt(TRAP_MODE, mode).apply();
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
        return selectedDescriptor.isEmpty() || selectedDescriptor.equals(descriptor);
    }

    static boolean acceptsInputDevice(Context context, String descriptor, String name, int vendorId, int productId) {
        String selectedDescriptor = selectedInputDeviceDescriptor(context);
        if (selectedDescriptor.isEmpty() || selectedDescriptor.equals(cleanName(descriptor, ""))) {
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
        prefs(context).edit()
                .putString(CONTROLLER_ANALYSIS_REPORT, cleanName(report, ""))
                .putLong(CONTROLLER_ANALYSIS_SAVED_AT, System.currentTimeMillis())
                .putInt(CONTROLLER_ANALYSIS_KEY_EVENTS, Math.max(0, keyEvents))
                .putInt(CONTROLLER_ANALYSIS_MOTION_EVENTS, Math.max(0, motionEvents))
                .apply();
    }

    static String lastControllerAnalysisSummary(Context context) {
        SharedPreferences prefs = prefs(context);
        long savedAt = prefs.getLong(CONTROLLER_ANALYSIS_SAVED_AT, 0L);
        if (savedAt <= 0L) {
            return "No saved analyzer session.";
        }

        return "Last saved: " + savedAt
                + "\nkey events " + prefs.getInt(CONTROLLER_ANALYSIS_KEY_EVENTS, 0)
                + ", motion events " + prefs.getInt(CONTROLLER_ANALYSIS_MOTION_EVENTS, 0);
    }

    static void clearTrapZones(Context context) {
        SharedPreferences.Editor editor = prefs(context).edit().putInt(TRAP_ZONE_COUNT, 0);
        for (int index = 0; index < MAX_TRAP_ZONES; index++) {
            editor.remove(TRAP_ZONE_X + index)
                    .remove(TRAP_ZONE_Y + index)
                    .remove(TRAP_ZONE_W + index)
                    .remove(TRAP_ZONE_H + index);
        }
        editor.apply();
    }

    static void saveTrapZones(Context context, List<TrapZone> zones) {
        SharedPreferences.Editor editor = prefs(context).edit();
        int count = Math.min(MAX_TRAP_ZONES, zones == null ? 0 : zones.size());
        editor.putInt(TRAP_ZONE_COUNT, count);
        for (int index = 0; index < MAX_TRAP_ZONES; index++) {
            if (index < count) {
                TrapZone zone = zones.get(index);
                editor.putInt(TRAP_ZONE_X + index, zone.x)
                        .putInt(TRAP_ZONE_Y + index, zone.y)
                        .putInt(TRAP_ZONE_W + index, zone.width)
                        .putInt(TRAP_ZONE_H + index, zone.height);
            } else {
                editor.remove(TRAP_ZONE_X + index)
                        .remove(TRAP_ZONE_Y + index)
                        .remove(TRAP_ZONE_W + index)
                        .remove(TRAP_ZONE_H + index);
            }
        }
        editor.apply();
    }

    static List<TrapZone> trapZones(Context context) {
        SharedPreferences prefs = prefs(context);
        int count = Math.max(0, Math.min(MAX_TRAP_ZONES, prefs.getInt(TRAP_ZONE_COUNT, 0)));
        List<TrapZone> zones = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int width = prefs.getInt(TRAP_ZONE_W + index, 0);
            int height = prefs.getInt(TRAP_ZONE_H + index, 0);
            if (width <= 0 || height <= 0) {
                continue;
            }
            zones.add(new TrapZone(
                    prefs.getInt(TRAP_ZONE_X + index, 0),
                    prefs.getInt(TRAP_ZONE_Y + index, 0),
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
        return "p" + currentProfile(context) + "_" + prefix + slot;
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
