package com.example.touchmapper;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

final class MappingStore {
    static final int SLOT_COUNT = 4;
    static final int PROFILE_COUNT = 4;
    static final int LOCK_SLOT = 3;

    private static final String PREFS = "touch_mappings";
    private static final String CURRENT_PROFILE = "current_profile";
    private static final String PROFILE_NAME = "profile_name_";
    private static final String BUTTON_NAME = "button_name_";
    private static final String KEY_CODE = "key_code_";
    private static final String X = "x_";
    private static final String Y = "y_";
    private static final String[] DEFAULT_PROFILE_NAMES = {
            "배달의민족",
            "쿠팡이츠",
            "요기요",
            "직접 입력"
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
        return prefs(context).getString(profileKey(BUTTON_NAME, context, slot), "버튼 " + (slot + 1));
    }

    static void saveButtonName(Context context, int slot, String name) {
        String cleanName = cleanName(name, "버튼 " + (slot + 1));
        prefs(context).edit().putString(profileKey(BUTTON_NAME, context, slot), cleanName).apply();
    }

    static Mapping get(Context context, int slot) {
        SharedPreferences prefs = prefs(context);
        int safeSlot = clampSlot(slot);
        int keyCode = prefs.getInt(profileKey(KEY_CODE, context, safeSlot),
                prefs.getInt(KEY_CODE + safeSlot, KeyEvent.KEYCODE_UNKNOWN));
        float x = prefs.getFloat(profileKey(X, context, safeSlot),
                prefs.getFloat(legacyTriggerKey(X, safeSlot), prefs.getFloat(X + safeSlot, -1f)));
        float y = prefs.getFloat(profileKey(Y, context, safeSlot),
                prefs.getFloat(legacyTriggerKey(Y, safeSlot), prefs.getFloat(Y + safeSlot, -1f)));
        return new Mapping(safeSlot, keyCode, buttonName(context, safeSlot), x, y);
    }

    static Mapping findByKeyCode(Context context, int keyCode) {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            Mapping mapping = get(context, slot);
            if (mapping.keyCode == keyCode) {
                return mapping;
            }
        }
        return null;
    }

    static void saveKeyCode(Context context, int slot, int keyCode) {
        prefs(context).edit().putInt(profileKey(KEY_CODE, context, clampSlot(slot)), keyCode).apply();
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

    static final class Mapping {
        final int slot;
        final int keyCode;
        final String name;
        final float x;
        final float y;

        Mapping(int slot, int keyCode, String name, float x, float y) {
            this.slot = slot;
            this.keyCode = keyCode;
            this.name = name;
            this.x = x;
            this.y = y;
        }

        boolean hasKey() {
            return keyCode != KeyEvent.KEYCODE_UNKNOWN;
        }

        boolean hasPoint() {
            return x >= 0f && y >= 0f;
        }

        boolean canRun() {
            return hasPoint();
        }
    }
}
