# Codex 인수인계: MONSTER Touch A-0.1.0

## 목표

이 프로젝트는 배달 기사 등 현장 사용자가 물리 버튼으로 화면의 지정 위치를 터치할 수 있게 하는 Android 앱입니다. 현재 A 버전은 단순 납품형으로, 복잡한 기능을 빼고 안정적인 4버튼 터치 매퍼에 집중합니다.

## 현재 버전

- 앱 이름: `MONSTER Touch`
- 패키지: `com.example.touchmapper`
- 버전명: `A-0.1.0`
- versionCode: `8`
- minSdk: `26`
- targetSdk: `35`

## 핵심 파일

- `app/src/main/java/com/example/touchmapper/MainActivity.java`
  - 앱 설정 화면
  - 프로필 선택/이름 변경
  - 버튼 이름 변경
  - 키 입력 설정
- `app/src/main/java/com/example/touchmapper/MappingStore.java`
  - SharedPreferences 저장소
  - 프로필별 버튼 이름, 키, 좌표 저장
- `app/src/main/java/com/example/touchmapper/TouchAccessibilityService.java`
  - 접근성 서비스
  - 하드웨어 키 감지
  - 저장 좌표 터치 실행
  - 위치 설정 오버레이
  - 터치 잠금 오버레이
- `app/build.gradle`
  - 버전 정보

## 현재 기능 정의

- 버튼은 4개 고정입니다.
- 버튼 1~4는 기본적으로 짧게 누르면 저장된 좌표를 터치합니다.
- 앱 화면에서 각 버튼마다 연결할 하드웨어 키를 지정합니다.
- `위치 설정`을 누르면 접근성 오버레이가 뜹니다.
- 오버레이에서 버튼 1~4 중 하나를 선택하고 원하는 화면 위치를 터치하면 좌표가 저장됩니다.
- 버튼 4번은 예외적으로 5초 이상 길게 누르면 터치 잠금/해제가 됩니다.
- 터치 잠금 상태에서는 손가락 터치는 막고, 설정된 하드웨어 버튼 동작은 계속 허용합니다.
- 설정 프로필은 4개입니다.
  - 배달의민족
  - 쿠팡이츠
  - 요기요
  - 직접 입력
- 각 프로필마다 버튼 이름, 키, 좌표를 따로 저장합니다.

## 의도적으로 제외한 B 버전 기능

A 버전에서는 아래 기능을 일부러 제외했습니다.

- 더블클릭
- 일반 롱클릭 동작 선택
- 줌인/줌아웃
- 앱 실행
- 볼륨/음악/전화/카메라 기능
- 버튼 추가
- 동작 종류 변경 UI

이 기능들은 복잡도가 올라가므로 확장형 B 버전 쪽 기능으로 분리하는 것이 좋습니다.

## 빌드 방법

Windows PowerShell 기준:

```powershell
.\gradlew.bat assembleDebug
```

빌드 APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

현재 전달용 APK는 아래 이름으로 복사되어 있습니다.

```text
outputs/MONSTER-Touch-A-0.1.0-debug.apk
outputs/MONSTER-Touch-A-debug.apk
outputs/MONSTER-Touch-debug.apk
```

## Codex에게 이어서 작업시킬 때 첫 메시지 예시

```text
이 프로젝트는 MONSTER Touch A-0.1.0이야.
CODEX_HANDOFF_A.md를 먼저 읽고 현재 구조를 파악해줘.
A 버전은 4버튼 단순 터치 매퍼이고, 버튼 4 롱클릭 터치 잠금과 프로필 저장 기능만 유지해야 해.
불필요하게 B 버전 기능(더블클릭, 앱 실행, 줌, 볼륨 등)을 다시 넣지 말고, 내가 요청한 부분만 수정해줘.
```

## 다음에 개선하기 좋은 작업

- 설정 내보내기/가져오기
- 프로필 복사 기능
- 버튼 이름을 위치 설정 오버레이에 더 크게 표시
- 접근성 권한이 꺼졌을 때 안내 화면 강화
- 좌표 저장 후 진동/소리 피드백
- 세로 화면 고정 여부 검토

## 주의 사항

- Android 접근성 권한은 사용자가 직접 켜야 합니다.
- 특정 앱이나 일부 제조사 OS에서는 접근성 제스처가 제한될 수 있습니다.
- debug APK는 테스트용입니다. 실제 배포용이면 release signing과 개인정보/접근성 권한 고지 정리가 필요합니다.
