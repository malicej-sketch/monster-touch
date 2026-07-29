package com.example.touchmapper;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/**
 * 홈 화면 1x1 위젯. 전원·전류·온도를 오버레이와 같은 규칙으로 보여준다.
 *
 * 위젯 자체 갱신 주기는 30분 아래로 못 내리므로 값은 접근성 서비스가 밀어넣는다
 * ({@link #refresh(Context)}). 서비스가 꺼져 있으면 30분마다 스스로 한 번씩 갱신된다.
 */
public class BatteryWidgetProvider extends AppWidgetProvider {

    /** 어두운 배경 위에 올라가므로 오버레이와 같은 밝은 색을 쓴다. */
    private static final int OK = 0xFF5BD98A;
    private static final int WARN = 0xFFFFC46B;
    private static final int ALERT = 0xFFFF6B6B;
    private static final int MUTED = 0xFFB9C2CC;
    private static final int FAST = 0xFF7AB8FF;
    private static final int PLAIN = 0xFFFFFFFF;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        render(context, manager, appWidgetIds);
    }

    /** 위젯이 홈 화면에 하나라도 놓여 있는지. 없으면 갱신을 돌릴 이유가 없다. */
    static boolean hasWidgets(Context context) {
        return widgetIds(context).length > 0;
    }

    /** 서비스가 값을 밀어넣는 통로. */
    static void refresh(Context context) {
        int[] ids = widgetIds(context);
        if (ids.length > 0) {
            render(context, AppWidgetManager.getInstance(context), ids);
        }
    }

    private static int[] widgetIds(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (manager == null) {
            return new int[0];
        }
        return manager.getAppWidgetIds(new ComponentName(context, BatteryWidgetProvider.class));
    }

    private static void render(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        BatteryReading reading = BatteryReading.read(context);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_battery);

        // 맨 위: 전류. 1x1에는 단위를 넣을 자리가 없어 숫자와 색만으로 읽게 한다.
        views.setTextViewText(R.id.widget_current, currentDigits(reading));
        views.setTextColor(R.id.widget_current, speedColor(reading.chargeSpeedLevel()));

        // 가운데: 배터리로 들어오는 중인지 나가는 중인지.
        views.setTextViewText(R.id.widget_state, stateText(reading));
        views.setTextColor(R.id.widget_state, stateColor(reading));

        views.setTextViewText(R.id.widget_temp, temperatureDigits(reading));
        views.setTextColor(R.id.widget_temp, reading.isHot() ? ALERT
                : reading.isWarm() ? WARN : OK);

        PendingIntent open = openApp(context);
        views.setOnClickPendingIntent(R.id.widget_current, open);
        views.setOnClickPendingIntent(R.id.widget_state, open);
        views.setOnClickPendingIntent(R.id.widget_temp, open);

        manager.updateAppWidget(appWidgetIds, views);
    }

    /** 부호가 곧 방향이다. 배터리로 들어오면 충전, 나가면 방전. */
    private static String stateText(BatteryReading reading) {
        if (reading.currentMa == Integer.MIN_VALUE) {
            return "—";
        }
        return reading.currentMa < 0 ? "방전중" : "충전중";
    }

    private static int stateColor(BatteryReading reading) {
        if (reading.currentMa == Integer.MIN_VALUE) {
            return MUTED;
        }
        if (reading.currentMa < 0) {
            // 꽂아놓고도 닳는 중이면 알려야 한다. 그냥 방전이면 놀랄 일이 아니다.
            return reading.isPluggedIn() ? ALERT : MUTED;
        }
        return OK;
    }

    private static String currentDigits(BatteryReading reading) {
        if (reading.currentMa == Integer.MIN_VALUE) {
            return "—";
        }
        return String.valueOf(reading.currentMa);
    }

    private static String temperatureDigits(BatteryReading reading) {
        if (Float.isNaN(reading.temperatureC)) {
            return "—";
        }
        return String.format(java.util.Locale.US, "%.1f°", reading.temperatureC);
    }

    private static int speedColor(int level) {
        switch (level) {
            case BatteryReading.SPEED_DRAINING:
                return ALERT;
            case BatteryReading.SPEED_SLOW:
                return WARN;
            case BatteryReading.SPEED_NORMAL:
                return PLAIN;
            case BatteryReading.SPEED_FAST:
                return OK;
            case BatteryReading.SPEED_SUPER:
                return FAST;
            default:
                return MUTED;
        }
    }

    private static PendingIntent openApp(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
