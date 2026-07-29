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

    /**
     * 충전 중에 이보다 작으면 밀리암페어로 본다.
     *
     * 마이크로암페어로 읽으면 10 mA인데, 충전 중에 그렇게 낮을 수 없다. 크기만으로
     * 가르려 하면 안 된다 — 마이크로암페어 기기도 부하가 낮을 때는 수만 단위를 낸다.
     * 실측: Galaxy S25는 방전 중 -22,656 µA(= -22.6 mA)를 낸다.
     */
    private static final int MILLI_AMP_CEILING_WHILE_CHARGING = 10_000;

    /**
     * 이 기기가 밀리암페어로 보고한다고 판단됐는지. 충전 중에 한 번 확인되면 방전 중에도
     * 같은 단위로 본다. 방전 값만으로는 단위를 알아낼 방법이 없기 때문이다.
     */
    private static boolean reportsMilliAmps;

    /**
     * 부호 규칙이 뒤집힌 기기인지. 충전기를 뽑은 상태에서는 배터리가 반드시 닳으므로 값이
     * 음수여야 한다. 그때 양수가 계속 나오면 이 기기는 규칙이 반대다.
     *
     * 충전 상태를 보고 부호를 강제하면 안 된다. 충전기를 꽂고도 부하가 커서 실제로 닳는
     * 경우가 있고, 거치대에서는 그게 정확히 알려줘야 할 상황이다.
     */
    private static boolean invertedSign;

    /** 뽑힌 상태에서 본 부호의 누적. 순간적으로 튀는 값 하나에 판정이 흔들리지 않게 한다. */
    private static int unpluggedSignTally;

    private static final int SIGN_TALLY_LIMIT = 5;
    private static final int SIGN_TALLY_DECISION = 3;

    final boolean charging;
    /** {@link BatteryManager#EXTRA_PLUGGED} 원본. 꽂혀 있지 않으면 0. */
    final int plugged;
    /** 섭씨. 읽지 못하면 {@link Float#NaN}. */
    final float temperatureC;
    /** 밀리암페어. 배터리로 들어오면 양수, 나가면 음수. 읽지 못하면 {@link Integer#MIN_VALUE}. */
    final int currentMa;

    private BatteryReading(boolean charging, int plugged, float temperatureC, int currentMa) {
        this.charging = charging;
        this.plugged = plugged;
        this.temperatureC = temperatureC;
        this.currentMa = currentMa;
    }

    static BatteryReading read(Context context) {
        Intent status = context.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        boolean charging = false;
        int plugged = 0;
        float temperatureC = Float.NaN;
        if (status != null) {
            int state = status.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            charging = state == BatteryManager.BATTERY_STATUS_CHARGING
                    || state == BatteryManager.BATTERY_STATUS_FULL;

            plugged = status.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);

            int tenths = status.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (tenths != Integer.MIN_VALUE) {
                temperatureC = tenths / 10f;
            }
        }

        return new BatteryReading(charging, plugged, temperatureC, readCurrentMa(context, charging));
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

        // 순간값을 먼저 쓴다. 전류계 앱들이 보여주는 값과 같아야 사용자가 대조할 수 있다.
        // 평균값은 순간값을 주지 않는 기기를 위한 대비책이다.
        int raw = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        if (raw == Integer.MIN_VALUE || raw == 0) {
            raw = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
        }
        if (raw == Integer.MIN_VALUE || raw == 0) {
            return raw == 0 ? 0 : Integer.MIN_VALUE;
        }

        // 명세는 마이크로암페어다. 그것을 기본으로 두고, 충전 중인데 값이 말이 안 되게
        // 작은 기기만 밀리암페어로 판정한다. 한 번 판정되면 계속 유지한다.
        if (charging && Math.abs(raw) < MILLI_AMP_CEILING_WHILE_CHARGING) {
            reportsMilliAmps = true;
        }

        int milliAmps = reportsMilliAmps ? raw : raw / 1000;

        // 뽑힌 상태에서는 반드시 닳는다. 거기서 부호를 배운다.
        if (!charging) {
            unpluggedSignTally += raw > 0 ? 1 : -1;
            unpluggedSignTally = Math.max(-SIGN_TALLY_LIMIT,
                    Math.min(SIGN_TALLY_LIMIT, unpluggedSignTally));
            if (unpluggedSignTally >= SIGN_TALLY_DECISION) {
                invertedSign = true;
            } else if (unpluggedSignTally <= -SIGN_TALLY_DECISION) {
                invertedSign = false;
            }
        }

        return invertedSign ? -milliAmps : milliAmps;
    }

    String temperatureText() {
        if (Float.isNaN(temperatureC)) {
            return "온도 --";
        }
        return String.format(java.util.Locale.US, "%.1f°C", temperatureC);
    }

    boolean isWireless() {
        return plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
    }

    static final int SPEED_NONE = 0;
    static final int SPEED_DRAINING = 1;
    static final int SPEED_SLOW = 2;
    static final int SPEED_NORMAL = 3;
    static final int SPEED_FAST = 4;
    static final int SPEED_SUPER = 5;

    /**
     * 충전 속도 경계. 배터리 단자 기준 전류이고, 유선과 무선이 다르다.
     *
     * 단자 전압은 3.7~4.4V로 거의 일정하므로 밀리암페어를 속도로 읽어도 된다. 다만
     * 무선은 손실이 30%까지 나서 유선과 같은 등급이라도 배터리에 훨씬 적게 들어간다.
     * 유선 기준을 무선에 그대로 쓰면 무엇을 올려도 "일반" 아래로만 보인다.
     *
     * 삼성 분류는 유선 5W 일반 / 15W 고속 / 25W 초고속이고, 무선은 5W 일반 /
     * 9~15W 고속이다. 3.9V로 환산해 경계를 잡았다.
     */
    private static final int[] WIRED_BOUNDS_MA = {500, 1500, 3500};
    private static final int[] WIRELESS_BOUNDS_MA = {300, 900, 2000};
    /** 음수는 배터리에서 전류가 나가는 것이다. 크기와 무관하게 방전이다. */
    private static final int DRAIN_FLOOR_MA = 0;

    int chargeSpeedLevel() {
        if (!isPluggedIn() || currentMa == Integer.MIN_VALUE) {
            return SPEED_NONE;
        }
        if (currentMa < DRAIN_FLOOR_MA) {
            return SPEED_DRAINING;
        }
        int[] bounds = isWireless() ? WIRELESS_BOUNDS_MA : WIRED_BOUNDS_MA;
        if (currentMa < bounds[0]) {
            return SPEED_SLOW;
        }
        if (currentMa < bounds[1]) {
            return SPEED_NORMAL;
        }
        if (currentMa < bounds[2]) {
            return SPEED_FAST;
        }
        return SPEED_SUPER;
    }

    /** 속도를 한 단어로. 꽂혀 있지 않으면 그냥 항목 이름이 된다. */
    String chargeSpeedLabel() {
        switch (chargeSpeedLevel()) {
            case SPEED_DRAINING:
                return "방전 중";
            case SPEED_SLOW:
                return "저속";
            case SPEED_NORMAL:
                return "일반";
            case SPEED_FAST:
                return "고속";
            case SPEED_SUPER:
                return "초고속";
            default:
                return "전류";
        }
    }

    /** 전류만. 충전 방식은 {@link #chargerLabel()}이 따로 보여준다. */
    String currentValueText() {
        if (currentMa == Integer.MIN_VALUE) {
            return "--";
        }
        // 꽂혀 있어도 부하가 크면 실제로는 닳는다. 그 사실을 감추지 않는다.
        return (currentMa > 0 ? "+" : "") + currentMa + " mA";
    }

    /**
     * 화면에 그대로 쓸 수 있는 충전 방식.
     *
     * 꽂혀 있는데 충전이 멈춘 상태가 따로 있다. 배터리 보호 한도에 닿거나 너무 뜨거우면
     * 그렇게 된다. 이때 "없음"이라고 하면 거짓말이 되므로 방식은 그대로 보여주고,
     * 멈췄다는 사실은 색으로 알린다.
     */
    String chargerLabel() {
        if (!isPluggedIn()) {
            return "방전";
        }
        // AC든 USB든 도크든 케이블로 들어오는 것은 하나로 묶는다. 속도 경계가 같다.
        return isWireless() ? "무선" : "유선";
    }

    boolean isPluggedIn() {
        return plugged != 0;
    }

    /** 꽂혀 있는데 충전이 멈춘 상태. */
    boolean isPluggedButIdle() {
        return isPluggedIn() && !charging;
    }

    /** 충전기를 꽂았는데도 배터리가 닳는 중. 거치 상태에서 알아야 할 상황이다. */
    boolean isDrainingWhilePlugged() {
        return isPluggedIn() && currentMa != Integer.MIN_VALUE && currentMa < DRAIN_FLOOR_MA;
    }

    /** 여름철 거치 상태에서 과열이 시작되는 지점. 색을 바꿔 알린다. */
    boolean isHot() {
        return !Float.isNaN(temperatureC) && temperatureC >= 40f;
    }

    /** 아직 위험하진 않지만 올라가는 중. */
    boolean isWarm() {
        return !Float.isNaN(temperatureC) && temperatureC >= 35f;
    }
}
