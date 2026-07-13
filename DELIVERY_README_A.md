# MONSTER Touch A-0.1.0 전달 안내

## 구성

- `apk/MONSTER-Touch-A-0.1.0-debug.apk`
  - 안드로이드 기기에 설치해서 테스트할 수 있는 APK입니다.
- `source/`
  - Android Studio 또는 Codex에서 이어서 작업할 수 있는 프로젝트 소스입니다.
- `docs/CODEX_HANDOFF_A.md`
  - Codex에게 이어서 작업을 맡길 때 필요한 인수인계 문서입니다.

## 설치 방법

1. Android 기기에 APK를 복사합니다.
2. 알 수 없는 앱 설치를 허용합니다.
3. APK를 설치합니다.
4. 앱을 열고 `접근성` 버튼을 누릅니다.
5. Android 접근성 설정에서 `MONSTER Touch Controller`를 켭니다.
6. 앱으로 돌아와 버튼별 키와 위치를 설정합니다.

## 현재 A 버전 기능

- 버튼 4개 고정
- 각 버튼은 한 번 클릭으로 저장된 위치를 터치
- 버튼별 이름 입력 가능
- 설정 프로필 4개
  - 배달의민족
  - 쿠팡이츠
  - 요기요
  - 직접 입력
- 프로필별로 버튼 이름, 키, 위치가 따로 저장됨
- 버튼 4번을 5초 이상 누르면 터치 잠금/해제
- 터치 잠금 상태에서도 하드웨어 버튼 조작은 동작

## 개발 환경

- Android Studio에서 `source/` 폴더를 열면 됩니다.
- 집이나 다른 PC에서는 `local.properties`가 새로 생성되거나 SDK 경로를 다시 잡아야 할 수 있습니다.
- Gradle 빌드:

```powershell
.\gradlew.bat assembleDebug
```

빌드 결과는 보통 아래에 생성됩니다.

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 주의

- 이 앱은 Android AccessibilityService를 사용합니다.
- 사용자가 직접 접근성 권한을 켜야 합니다.
- 일부 기기, 제조사 OS, 보안 정책, 특정 앱에서는 터치 제스처가 제한될 수 있습니다.
- Play Store 배포용이 아니라 테스트용 debug APK입니다.
