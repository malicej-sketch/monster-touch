package com.example.touchmapper;

import android.content.Context;
import android.view.InputDevice;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 이 빌드가 어떤 컨트롤러를 받아들이는지 정한다.
 *
 * 목록이 비어 있으면 아무 컨트롤러나 쓴다. 채워져 있으면 그 목록에 있는 것만 쓴다.
 * 값은 {@code BuildConfig.ALLOWED_CONTROLLERS}에서 오고 플레이버마다 다르다 —
 * 전용 제품으로 나가는 빌드는 잠그고, 범용 빌드는 열어둔다.
 *
 * 이건 보안이 아니다. VID/PID는 HID 디스크립터에 적힌 숫자일 뿐이라 같은 값을 구운
 * 기기는 통과한다. 아무 리모컨이나 사서 쓰는 것을 막는 제품 묶음 장치다.
 *
 * AGENTS.md 1항의 "모델별 허용목록 금지"와 층이 다르다. 그 규칙은 입력을 어떻게
 * 해석할지에 대한 것이고, 이건 어떤 제품과 함께 팔지에 대한 것이다. 입력을 읽고
 * 배우는 방식은 여기서도 그대로 관찰 기반이다.
 */
final class ControllerPolicy {

    private static final class Id {
        final int vendorId;
        final int productId;

        Id(int vendorId, int productId) {
            this.vendorId = vendorId;
            this.productId = productId;
        }
    }

    private static List<Id> allowed;

    private ControllerPolicy() {
    }

    /** 목록이 비어 있으면 제한이 없다. */
    static boolean unrestricted() {
        return ids().isEmpty();
    }

    static boolean isAllowed(InputDevice device) {
        if (unrestricted()) {
            return true;
        }
        if (device == null) {
            return false;
        }
        return isAllowed(device.getVendorId(), device.getProductId());
    }

    static boolean isAllowed(int vendorId, int productId) {
        if (unrestricted()) {
            return true;
        }
        for (Id id : ids()) {
            if (id.vendorId == vendorId && id.productId == productId) {
                return true;
            }
        }
        return false;
    }

    /**
     * 화면에 그릴 버튼 행 수.
     *
     * 전용 리모컨은 버튼이 셋이다. 넷째 행을 그려봐야 누를 방법이 없다. 화면 잠금은
     * 내부적으로 슬롯 3의 길게에 저장되지만 위쪽 전용 칸이 따로 보여주므로, 이 행을
     * 숨겨도 잃는 것이 없다.
     */
    static int visibleButtonCount() {
        return unrestricted() ? MappingStore.SLOT_COUNT : 3;
    }

    /** 잠긴 빌드에서 기기가 안 보일 때 사용자에게 무엇을 찾으라고 알려줄지. */
    static String requiredControllerLabel() {
        return BuildConfig.REQUIRED_CONTROLLER_NAME;
    }

    /**
     * 전용 리모컨이 붙어 있으면 말없이 고른다.
     *
     * 쓸 수 있는 기기가 하나뿐인데 사용자에게 고르라고 물을 이유가 없다.
     */
    static boolean autoSelect(Context context) {
        if (unrestricted() || MappingStore.hasSelectedInputDevice(context)) {
            return false;
        }
        for (int deviceId : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (device == null || device.isVirtual() || !isAllowed(device)) {
                continue;
            }
            MappingStore.saveInputDevice(context, device.getDescriptor(), device.getName(),
                    device.getVendorId(), device.getProductId());
            return true;
        }
        return false;
    }

    /**
     * 전용 리모컨은 어떤 키를 보내는지 우리가 이미 안다. 배우게 할 이유가 없다.
     *
     *   상단 F5  짧게 = 버튼 1,  길게 = 위치 표시
     *   중간 F6  짧게 = 버튼 2,  길게 = 쓰지 않는다
     *   하단 F7  짧게 = 버튼 3,  길게 = 화면 잠금
     *
     * **중간 버튼의 길게는 절대 쓰면 안 된다.** 리모컨 펌웨어가 이 버튼을 5초 이상
     * 붙들고 있으면 페어링 모드로 들어간다. 여기에 동작을 걸면 1.5초에 그 동작이 먼저
     * 터지고 5초에 페어링이 겹친다.
     *
     * 버튼 4는 짧게 누를 자리가 없다. 더블 클릭이 들어오면 그때 배정한다.
     *
     * 위치 표시는 슬롯 0의 길게, 화면 잠금은 슬롯 3의 길게로 저장된다. 그래서 하단
     * 버튼은 짧게와 길게가 서로 다른 슬롯에 들어간다 — 짧게는 슬롯 2, 길게는 슬롯 3이다.
     * 두 조회가 독립이라 문제없다.
     *
     * 한 번 깔린 뒤에 사용자가 바꾼 것은 되돌리지 않는다.
     */
    static void applyDefaultBindings(Context context) {
        if (unrestricted()) {
            return;
        }
        applyDefaultDoubleBindings(context);

        if (MappingStore.get(context, 0).triggerValue != MappingStore.TRIGGER_UNKNOWN) {
            return;
        }

        MappingStore.saveKeyCode(context, 0, KeyEvent.KEYCODE_F5);
        MappingStore.saveLongKeyCode(context, MappingStore.MARKER_TOGGLE_SLOT,
                KeyEvent.KEYCODE_F5);

        MappingStore.saveKeyCode(context, 1, KeyEvent.KEYCODE_F6);

        MappingStore.saveKeyCode(context, 2, KeyEvent.KEYCODE_F7);
        MappingStore.saveLongKeyCode(context, MappingStore.LOCK_SLOT, KeyEvent.KEYCODE_F7);

    }

    /**
     * 더블은 키 바인딩과 따로 깐다.
     *
     * 위의 이른 반환에 묶어두면 이미 설치된 폰에는 나중에 추가한 더블이 영영 안 들어간다.
     * 자리마다 비어 있을 때만 채우므로 사용자가 바꾼 것은 건드리지 않는다.
     */
    private static void applyDefaultDoubleBindings(Context context) {
        if (MappingStore.doubleAction(context, 0) == MappingStore.DOUBLE_ACTION_NONE) {
            MappingStore.saveDoubleBinding(context, 0, KeyEvent.KEYCODE_F5,
                    MappingStore.DOUBLE_ACTION_BACK);
        }
        if (MappingStore.doubleAction(context, 1) == MappingStore.DOUBLE_ACTION_NONE) {
            MappingStore.saveDoubleBinding(context, 1, KeyEvent.KEYCODE_F7,
                    MappingStore.DOUBLE_ACTION_CAMERA);
        }
    }

    /** "vid:pid" 를 쉼표로 나눈 목록. 16진수다. */
    private static synchronized List<Id> ids() {
        if (allowed != null) {
            return allowed;
        }
        allowed = new ArrayList<>();
        String raw = BuildConfig.ALLOWED_CONTROLLERS;
        if (raw == null || raw.trim().isEmpty()) {
            return allowed;
        }
        for (String entry : raw.split(",")) {
            String[] parts = entry.trim().split(":");
            if (parts.length != 2) {
                continue;
            }
            try {
                allowed.add(new Id(
                        Integer.parseInt(parts[0].trim(), 16),
                        Integer.parseInt(parts[1].trim(), 16)));
            } catch (NumberFormatException ignored) {
                // 설정이 잘못된 항목은 건너뛴다. 잠금이 통째로 풀리는 것보다 낫다.
            }
        }
        return allowed;
    }
}
