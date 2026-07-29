/*
 * MON_TOUCH_RING — MONSTER Touch 시험용 리모컨
 *
 * ESP32-C3에서 BLE HID 키보드로 붙는다. 버튼 세 개가 각각 키 하나를 보낸다.
 *
 * 목적이 둘이다. 하나는 앱이 BLE HID 기기를 제대로 학습하는지 보는 것이고,
 * 다른 하나는 VID/PID로 "우리 리모컨만 동작" 잠금을 시험하는 것이다.
 */

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEHIDDevice.h>
#include <BLESecurity.h>
#include <BLEServer.h>
#include <BLEUtils.h>

// ── 제품 식별자 ──────────────────────────────────────────────────────────
// 앱은 이 값으로 우리 리모컨인지 판정한다.
//
// VID는 지어내면 안 된다. USB-IF와 Bluetooth SIG가 회사에 할당하는 번호라,
// 임의로 찍으면 남의 회사 번호와 충돌하고 그 상태로 팔면 그 회사 기기인 척하는
// 것이 된다. 그래서 이 칩의 실제 제조사인 Espressif 번호를 쓴다. 거짓이 아니다.
//
// PID는 Espressif가 자사 개발보드에 쓰는 낮은 대역(0x0000~0x1FFF)을 피해 잡았다.
// 양산할 때는 모듈 제조사에서 이 제품 전용 PID를 받아 바꾼다.
static const char *DEVICE_NAME = "MON_TOUCH_RING";
static const uint16_t VENDOR_ID = 0x303A;   // Espressif Systems
static const uint16_t PRODUCT_ID = 0x9E01;  // MON_TOUCH_RING v1
static const uint16_t VERSION_ID = 0x0100;

// ── 버튼 ────────────────────────────────────────────────────────────────
// GPIO0~5만 딥슬립에서 깨울 수 있고, GPIO2/8/9는 스트래핑 핀이라 부팅을
// 방해한다. 그래서 3, 4, 5를 쓴다.
struct Button {
    uint8_t pin;
    uint8_t usage;  // HID 키 코드
    const char *label;
    bool pressed;
    uint32_t changedMs;
};

// 물리 배치 그대로다. GP3이 위, GP4가 가운데, GP5가 아래.
//
// F5~F7을 쓴다. 안드로이드에서 아무 일도 하지 않는 키라서다. 화살표나 Enter를 쓰면
// 앱이 삼키지 못하는 순간마다 시스템이 받아버린다 — 접근성이 꺼져 있을 때, 장치를
// 선택하기 전, 페어링 직후. 화살표는 포커스를 옮기고 Enter는 포커스된 버튼을
// 실제로 누른다. 핸들바에 물린 폰에서 일어나면 안 되는 일이다.
static Button buttons[] = {
    {3, 0x3E, "TOP", false, 0},  // F5 -> KEYCODE_F5
    {4, 0x3F, "MID", false, 0},  // F6 -> KEYCODE_F6
    {5, 0x40, "BOT", false, 0},  // F7 -> KEYCODE_F7
};
static const size_t BUTTON_COUNT = sizeof(buttons) / sizeof(buttons[0]);

/** 접점이 떨리는 동안 여러 번 눌린 것으로 세지 않는다. */
static const uint32_t DEBOUNCE_MS = 25;

// ── 페어링 모드 ─────────────────────────────────────────────────────────
// GP4를 5초 이상 누르면 기존 등록을 지우고 새 폰을 받는다. 다른 폰에 물려
// 있으면 새 폰이 못 붙기 때문에, 짝을 바꾸려면 지우는 절차가 필요하다.
static const uint8_t PAIRING_BUTTON = 1;  // 가운데 버튼(GP4)
static const uint32_t PAIRING_HOLD_MS = 5000;
static bool pairingTriggered = false;

/** 이 보드는 GP10에 WS2812가 달려 있다. 페어링 대기 중인지 눈으로 봐야 한다. */
static const uint8_t LED_PIN = 10;

// ── HID ─────────────────────────────────────────────────────────────────
/** 표준 키보드 리포트. 수정자 1바이트, 예약 1바이트, 키 6바이트. */
static const uint8_t REPORT_MAP[] = {
    0x05, 0x01,  // Usage Page (Generic Desktop)
    0x09, 0x06,  // Usage (Keyboard)
    0xA1, 0x01,  // Collection (Application)
    0x85, 0x01,  //   Report ID (1)
    0x05, 0x07,  //   Usage Page (Keyboard)
    0x19, 0xE0,  //   Usage Minimum (224)
    0x29, 0xE7,  //   Usage Maximum (231)
    0x15, 0x00,  //   Logical Minimum (0)
    0x25, 0x01,  //   Logical Maximum (1)
    0x75, 0x01,  //   Report Size (1)
    0x95, 0x08,  //   Report Count (8)
    0x81, 0x02,  //   Input (Data, Variable, Absolute)
    0x95, 0x01,  //   Report Count (1)
    0x75, 0x08,  //   Report Size (8)
    0x81, 0x01,  //   Input (Constant)
    0x95, 0x06,  //   Report Count (6)
    0x75, 0x08,  //   Report Size (8)
    0x15, 0x00,  //   Logical Minimum (0)
    0x25, 0x65,  //   Logical Maximum (101)
    0x05, 0x07,  //   Usage Page (Keyboard)
    0x19, 0x00,  //   Usage Minimum (0)
    0x29, 0x65,  //   Usage Maximum (101)
    0x81, 0x00,  //   Input (Data, Array)
    0xC0         // End Collection
};

static BLEHIDDevice *hid = nullptr;
static BLECharacteristic *input = nullptr;
static BLEServer *server = nullptr;
static volatile bool connected = false;
static volatile uint16_t connId = 0;

/**
 * 안드로이드가 요구하는 본딩에 응답한다.
 *
 * 이걸 달지 않으면 붙자마자 끊어진다. 연결은 되는데 암호화 협상에서 아무 답도
 * 못 하기 때문이다. 화면도 키패드도 없는 기기라 PIN 없이(Just Works) 맺는다.
 */
class SecurityCallbacks : public BLESecurityCallbacks {
    uint32_t onPassKeyRequest() override { return 0; }
    void onPassKeyNotify(uint32_t passKey) override {}
    bool onConfirmPIN(uint32_t pin) override { return true; }
    bool onSecurityRequest() override { return true; }

    void onAuthenticationComplete(esp_ble_auth_cmpl_t complete) override {
        Serial.printf("[BLE] auth %s\n", complete.success ? "OK" : "FAIL");
    }
};

class ServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer *s, esp_ble_gatts_cb_param_t *param) override {
        connected = true;
        connId = param->connect.conn_id;
        Serial.println("[BLE] connected");
    }

    void onDisconnect(BLEServer *s) override {
        connected = false;
        Serial.println("[BLE] disconnected - advertising again");
        BLEDevice::startAdvertising();
    }
};

/** 등록된 폰을 모두 지우고 새로 광고한다. */
static void enterPairingMode() {
    Serial.println("[BLE] pairing mode - clearing bonds");

    if (connected && server != nullptr) {
        server->disconnect(connId);
        delay(200);
    }

    int count = esp_ble_get_bond_device_num();
    if (count > 0) {
        esp_ble_bond_dev_t *list =
            (esp_ble_bond_dev_t *)malloc(sizeof(esp_ble_bond_dev_t) * count);
        if (list != nullptr) {
            esp_ble_get_bond_device_list(&count, list);
            for (int i = 0; i < count; i++) {
                esp_ble_remove_bond_device(list[i].bd_addr);
            }
            free(list);
        }
    }
    Serial.printf("[BLE] cleared %d bond(s)\n", count);

    BLEDevice::startAdvertising();

    // 지웠다는 것을 눈으로 알린다. 파랑은 연결됨이므로 흰색을 쓴다.
    for (int i = 0; i < 6; i++) {
        neopixelWrite(LED_PIN, 50, 50, 50);
        delay(80);
        neopixelWrite(LED_PIN, 0, 0, 0);
        delay(80);
    }
}

/** 지금 눌려 있는 버튼을 모두 담아 한 번에 보낸다. */
static void sendCurrentReport() {
    if (!connected || input == nullptr) {
        return;
    }
    uint8_t report[8] = {0};
    size_t slot = 2;
    for (size_t i = 0; i < BUTTON_COUNT && slot < sizeof(report); i++) {
        if (buttons[i].pressed) {
            report[slot++] = buttons[i].usage;
        }
    }
    input->setValue(report, sizeof(report));
    input->notify();
}

/**
 * 상태를 색으로 알린다. 버튼이 눌린 순간이 가장 급하므로 맨 위에 둔다.
 *
 *   빨강      버튼 눌림
 *   파랑      연결됨
 *   초록 깜빡  짝을 기다리는 중
 *
 * 밝기를 낮게 잡았다. 이 LED는 켜 있는 동안 계속 전류를 먹어서, 배터리로 도는
 * 리모컨에서는 밝기가 그대로 사용 시간이 된다.
 */
static void updateLed(uint32_t now) {
    static int lastR = -1, lastG = -1, lastB = -1;
    static bool blinkOn = false;
    static uint32_t blinkMs = 0;

    uint8_t r = 0, g = 0, b = 0;

    bool anyPressed = false;
    for (size_t i = 0; i < BUTTON_COUNT; i++) {
        if (buttons[i].pressed) {
            anyPressed = true;
            break;
        }
    }

    if (anyPressed) {
        r = 60;
    } else if (connected) {
        b = 40;
    } else {
        if (now - blinkMs >= 900) {
            blinkMs = now;
            blinkOn = !blinkOn;
        }
        if (blinkOn) {
            g = 25;
        }
    }

    // 값이 바뀔 때만 쓴다. WS2812는 쓸 때마다 인터럽트를 잠깐 막는다.
    if (r != lastR || g != lastG || b != lastB) {
        neopixelWrite(LED_PIN, r, g, b);
        lastR = r;
        lastG = g;
        lastB = b;
    }
}

void setup() {
    Serial.begin(115200);
    delay(300);
    Serial.println();
    Serial.printf("MON_TOUCH_RING  VID=0x%04X PID=0x%04X\n", VENDOR_ID, PRODUCT_ID);

    for (size_t i = 0; i < BUTTON_COUNT; i++) {
        pinMode(buttons[i].pin, INPUT_PULLUP);
    }

    BLEDevice::init(DEVICE_NAME);
    server = BLEDevice::createServer();
    server->setCallbacks(new ServerCallbacks());

    hid = new BLEHIDDevice(server);
    input = hid->inputReport(1);

    hid->manufacturer()->setValue("MONSTER");
    hid->pnp(0x02, VENDOR_ID, PRODUCT_ID, VERSION_ID);
    hid->hidInfo(0x00, 0x01);

    // 위 pnp()는 VID/PID를 빅엔디안으로 쓴다. PnP ID 특성(0x2A50) 규격은
    // 리틀엔디안이라, 그대로 두면 안드로이드가 0x303A를 0x3A30으로 읽는다.
    // 실제로 그렇게 올라오는 것을 확인했다. 규격대로 다시 쓴다.
    BLECharacteristic *pnpChar =
        hid->deviceInfo()->getCharacteristic(BLEUUID((uint16_t)0x2A50));
    if (pnpChar != nullptr) {
        uint8_t pnp[] = {
            0x02,  // Vendor ID Source: USB-IF
            (uint8_t)(VENDOR_ID & 0xFF), (uint8_t)(VENDOR_ID >> 8),
            (uint8_t)(PRODUCT_ID & 0xFF), (uint8_t)(PRODUCT_ID >> 8),
            (uint8_t)(VERSION_ID & 0xFF), (uint8_t)(VERSION_ID >> 8),
        };
        pnpChar->setValue(pnp, sizeof(pnp));
    } else {
        Serial.println("[BLE] PnP characteristic not found");
    }

    BLEDevice::setSecurityCallbacks(new SecurityCallbacks());
    BLESecurity *security = new BLESecurity();
    security->setAuthenticationMode(ESP_LE_AUTH_REQ_SC_BOND);
    security->setCapability(ESP_IO_CAP_NONE);  // 화면도 키패드도 없다
    security->setInitEncryptionKey(ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK);
    security->setRespEncryptionKey(ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK);

    hid->reportMap((uint8_t *)REPORT_MAP, sizeof(REPORT_MAP));
    hid->startServices();
    hid->setBatteryLevel(100);

    BLEAdvertising *advertising = BLEDevice::getAdvertising();
    advertising->setAppearance(961);  // HID 키보드
    advertising->addServiceUUID(hid->hidService()->getUUID());
    advertising->start();

    Serial.println("[BLE] 광고 시작 — 폰에서 MON_TOUCH_RING 페어링");
}

void loop() {
    uint32_t now = millis();
    bool changed = false;

    for (size_t i = 0; i < BUTTON_COUNT; i++) {
        Button &button = buttons[i];
        bool down = digitalRead(button.pin) == LOW;  // 눌리면 GND로 떨어진다
        if (down == button.pressed) {
            continue;
        }
        if (now - button.changedMs < DEBOUNCE_MS) {
            continue;
        }
        button.pressed = down;
        button.changedMs = now;
        changed = true;
        Serial.printf("[KEY] %s %s\n", button.label, down ? "누름" : "뗌");
    }

    if (changed) {
        sendCurrentReport();
    }

    // GP4를 5초 넘게 붙들고 있으면 페어링 모드. 한 번 누른 동안 한 번만 걸린다.
    Button &pairing = buttons[PAIRING_BUTTON];
    if (pairing.pressed && !pairingTriggered
            && now - pairing.changedMs >= PAIRING_HOLD_MS) {
        pairingTriggered = true;
        enterPairingMode();
    } else if (!pairing.pressed) {
        pairingTriggered = false;
    }

    updateLed(now);
    delay(5);
}
