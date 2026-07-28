package com.example.touchmapper;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

/**
 * 배터리 온도와 충전 전류를 읽는다.
 *
 * 온도는 표준이 명확해서 그대로 쓸 수 있다. 전류는 제조사마다 단위와 부호가 달라서
 * 값을 보고 판단한다. 어느 기기가 어떤 규칙인지 목록으로 들고 있을 수 없기 때문이다.
 */
final class BatteryReading {

    /** 이 크기를 넘으면 마이크로암페어로 본다. 밀리암페어라면 이만큼 나올 수 없다. */
    private static final int MICRO_AMP_THRESHOLD = 100_000;

    final boolean charging;
    /** 섭씨. 읽지 못하면 {@link Float#NaN}. */
    final float temperatureC;
    /** 밀리암페어. 충전 중이면 양수. 읽지 못하면 {@link Integer#MIN_VALUE}. */
    final int currentMa;

    private BatteryReading(boolean charging, float temperatureC, int currentMa) {
        this.charging = charging;
        this.temperatureC = temperatureC;
        this.currentMa = currentMa;
    }

    static BatteryReading read(Context context) {
        Intent status = context.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        boolean charging = false;
        float temperatureC = Float.NaN;
        if (status != null) {
            int plugged = status.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            charging = plugged == BatteryManager.BATTERY_STATUS_CHARGING
                    || plugged == BatteryManager.BATTERY_STATUS_FULL;

            int tenths = status.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (tenths != Integer.MIN_VALUE) {
                temperatureC = tenths / 10f;
            }
        }

        return new BatteryReading(charging, temperatureC, readCurrentMa(context, charging));
    }

    /**
     * 충전 전류를 밀리암페어로. 부호는 충전 중일 때 양수가 되도록 맞춘다.
     *
     * 문서상 단위는 마이크로암페어지만 밀리암페어로 주는 기기가 있고, 충전 중에 음수를
     * 주는 기기도 있다. 크기와 충전 상태를 보고 맞춘다.
     */
    private static int readCurrentMa(Context context, boolean charging) {
        BatteryManager manager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (manager == null) {
            return Integer.MIN_VALUE;
        }

        int raw = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        if (raw == Integer.MIN_VALUE || raw == 0) {
            return raw == 0 ? 0 : Integer.MIN_VALUE;
        }

        int milliAmps = Math.abs(raw) > MICRO_AMP_THRESHOLD ? raw / 1000 : raw;

        // 충전 중인데 음수면 그 기기는 부호 규칙이 반대다.
        if (charging && milliAmps < 0) {
            milliAmps = -milliAmps;
        } else if (!charging && milliAmps > 0) {
            milliAmps = -milliAmps;
        }
        return milliAmps;
    }

    String temperatureText() {
        if (Float.isNaN(temperatureC)) {
            return "온도 --";
        }
        return String.format(java.util.Locale.US, "%.1f°C", temperatureC);
    }

    String currentText() {
        if (currentMa == Integer.MIN_VALUE) {
            return "전류 --";
        }
        if (!charging) {
            return "충전 안 함";
        }
        return currentMa + " mA";
    }

    /** 여름철 거치 상태에서 과열이 시작되는 지점. 색을 바꿔 알린다. */
    boolean isHot() {
        return !Float.isNaN(temperatureC) && temperatureC >= 40f;
    }
}
