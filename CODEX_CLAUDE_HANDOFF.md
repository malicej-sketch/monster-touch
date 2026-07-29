# CODEX / Claude 협업 핸드오프

작성일: 2026-07-27

## 목적

이 문서는 `monster-touch` 프로젝트를 여러 컴퓨터에서 이어서 작업하고, Codex와 Claude가 같은 맥락을 공유하기 위한 협업 기준 문서다.

핵심 목표는 다음과 같다.

- GitHub를 코드의 기준 저장소로 사용한다.
- Google Drive를 APK, 테스트 자료, 전달 문서 보관소로 사용한다.
- Claude는 설계 검토와 코드 리뷰를 맡는다.
- Codex는 실제 코드 수정, 빌드, 설치, GitHub 반영을 맡는다.

## 프로젝트 기본 정보

- 프로젝트명: `monster-touch`
- GitHub 저장소: `https://github.com/malicej-sketch/monster-touch`
- Google Drive 상위 폴더: `CODEX`
- Google Drive 프로젝트 폴더: `CODEX/monster-touch`
- Google Drive CODEX 폴더 링크: `https://drive.google.com/drive/folders/1FBYTd1PFPWyN2bOnLPi2XI531ki5YiWR`
- 현재 기준 앱 버전: `A-0.1.49`
- 현재 기준 versionCode: `57`
- 로컬 저장소 경로: `C:\Users\baboe\Documents\Codex\monster-touch`
- 저장소 공개 상태: **public** (2026-07-27 전환)

## 앱 개요

`monster-touch`는 Android 접근성 서비스를 이용하는 터치 매퍼 앱이다.

외부 리모컨, HID 키보드형 컨트롤러, TikTok 클릭커 계열 컨트롤러의 입력을 받아 사용자가 지정한 화면 좌표를 터치한다. 배달 기사처럼 비 오는 환경에서 손가락 터치 오작동을 줄이고, 물리 버튼으로 주요 앱 조작을 하는 것이 목표다.

현재 주요 기능:

- 기본 버튼 4개
- 버튼별 화면 좌표 터치
- 위치 설정 오버레이
- 위치 표시 오버레이
- 위치 표시 ON/OFF, 화면 터치 잠금 ON/OFF
  (코드상 슬롯 0 / 슬롯 3의 롱 트리거에 하드코딩돼 있으나, 실제 하드웨어에서는
  같은 버튼의 롱클릭이 아닌 별개 입력인 경우가 많다. 예: LP-910은 볼륨 ±로 나온다.
  이 결합을 푸는 리팩터가 예정돼 있다 — `CODEX_SESSION.md` 참고)
- 화면 잠금 중에도 리모컨 버튼 입력은 동작
- plain 버전과 monster 로고 버전 빌드 구조
- 기본 설치 대상은 plain 버전
- Android 14 이상에서 TikTok 클릭커/마우스/호버형 컨트롤러 대응
- 입력 등록 시 같은 버튼을 5회 눌러 신호를 수집하고, 중복 제거 후 하나의 입력 묶음으로 저장

## 역할 분담

### Codex 역할

Codex는 실제 작업자 역할을 맡는다.

- 코드 수정
- Android 프로젝트 빌드
- APK 생성
- ADB 설치
- GitHub commit/push
- Google Drive에 APK/문서 업로드
- 작업 세션 문서 갱신

### Claude 역할

Claude는 설계 검토자와 코드 리뷰어 역할을 맡는다.

- Android 접근성 정책 검토
- MotionEvent / KeyEvent 처리 구조 분석
- TikTok 클릭커 계열 컨트롤러 입력 안정화 아이디어 제안
- 복잡한 버그 원인 추론
- 코드 리뷰
- `AGENTS.md`, `CODEX_SESSION.md` 문서 검토

Claude가 제안한 내용을 바로 적용하지 말고, Codex가 코드베이스 맥락에 맞게 검토한 뒤 반영한다.

## 공유 방식

### GitHub

코드는 GitHub가 기준이다.

Claude에게 공유할 링크:

```text
https://github.com/malicej-sketch/monster-touch
```

저장소가 private이면 Claude가 접근할 수 있도록 다음 중 하나를 사용한다.

- 저장소를 public으로 전환
- Claude가 사용할 GitHub 계정을 collaborator로 추가
- 필요한 파일만 복사해서 Claude에게 전달

코드 수정은 GitHub 기준으로 진행한다. Google Drive에 있는 파일을 코드 기준으로 삼지 않는다.

### Google Drive

Google Drive는 코드 저장소가 아니라 산출물 보관소다.

공유할 링크:

```text
https://drive.google.com/drive/folders/1FBYTd1PFPWyN2bOnLPi2XI531ki5YiWR
```

권장 구조:

```text
CODEX/
  Little Weird Lab/
  monster-touch/
    01_APK/
    02_문서/
    03_테스트자료/
    04_전달용ZIP/
```

Claude에게는 기본적으로 viewer 권한만 준다. 문서 수정이 필요할 때만 editor 권한을 준다.

## 여러 컴퓨터에서 작업하는 방식

새 컴퓨터에서 시작할 때:

```cmd
cd D:\개발\CODEX
"C:\Program Files\GitHub CLI\gh.exe" repo clone malicej-sketch/monster-touch
cd monster-touch
```

이미 clone되어 있으면 작업 시작 전에:

```cmd
"C:\Program Files\Git\cmd\git.exe" pull
```

작업 종료 시:

```cmd
"C:\Program Files\Git\cmd\git.exe" status
"C:\Program Files\Git\cmd\git.exe" add .
"C:\Program Files\Git\cmd\git.exe" commit -m "Update Monster Touch"
"C:\Program Files\Git\cmd\git.exe" push
```

GitHub CLI 인증 확인:

```cmd
"C:\Program Files\GitHub CLI\gh.exe" auth status
```

정상 상태 예:

```text
Logged in to github.com account malicej-sketch
Git operations protocol: https
Token scopes: 'gist', 'read:org', 'repo', 'workflow'
```

## Codex에게 새 세션에서 줄 첫 지시

다른 컴퓨터나 새 Codex 세션에서 이어서 작업할 때는 이렇게 시작한다.

```text
이 저장소의 AGENTS.md, CODEX_SESSION.md, CODEX_CLAUDE_HANDOFF.md를 읽고 현재 상태를 파악한 뒤 이어서 작업해줘.
기본 설치는 plain 버전만 해줘.
버전명은 반드시 명확히 올려줘.
```

`AGENTS.md`와 `CODEX_SESSION.md`가 아직 없다면 먼저 작성한다.

## Claude에게 줄 첫 지시

Claude에게는 다음 내용을 전달한다.

```text
우리는 Android용 Touch Mapper 앱(monster-touch)을 만들고 있습니다.

목표:
외부 리모컨/버튼 입력으로 화면의 지정 좌표를 터치하고, 배달 기사처럼 비 오는 환경에서도 터치 오조작을 줄이는 앱입니다.

저장소: https://github.com/malicej-sketch/monster-touch (public)
현재 버전: A-0.1.49 / versionCode 57

먼저 저장소의 AGENTS.md와 CODEX_SESSION.md를 읽어주세요. 프로젝트 규칙, 현재 상태,
지금까지 확인된 하드웨어 사실, 진행 중인 설계가 전부 거기에 있습니다.

앱은 기능적으로는 잘 동작하는 편입니다. 지금 하려는 것은 두 가지입니다.

1. UI/UX 정리 — 기능을 늘리지 말고 배치와 표현을 개선
2. 액션/슬롯 분리 리팩터 — "위치 표시"와 "화면 잠금"이 슬롯 번호에 하드코딩된 것을 해소

협업 방식:
Claude는 설계 검토와 코드 리뷰를 담당하고, Codex는 실제 코드 수정/빌드/설치/GitHub 반영을 담당합니다.
```

이미 검토가 끝난 항목은 다시 묻지 않는다. `CODEX_SESSION.md`에 정리돼 있다.

- LP-910의 홈잉 시퀀스와 버튼별 패턴 (커널 캡처로 확인)
- 커서가 화면을 가로지르는 것은 기기 동작 원리라 제거 불가
- `VOLUME_KEY_DEBOUNCE_MS = 1000`은 의도된 장치 — 건드리지 말 것
- 볼륨키 1.8초 억제는 실사용에서 문제 없음 — 수정 보류

## 작업 완료 시 GitHub 반영 (필수)

**빌드해서 기기에 설치한 작업은 그날 안에 GitHub에 push한다. 예외 없다.**

로컬에만 있는 작업은 없는 것과 같다. 다른 컴퓨터에서 이어갈 수 없고, 컴퓨터가
고장나면 사라진다.

```cmd
"C:\Program Files\Git\cmd\git.exe" add .
"C:\Program Files\Git\cmd\git.exe" commit -m "A-0.1.N: 요약"
"C:\Program Files\Git\cmd\git.exe" push
```

push 후 다음을 확인한다.

- `git status`가 `## main...origin/main` 으로 끝나는지 (ahead 표시가 없어야 함)
- GitHub의 `app/build.gradle` 버전이 방금 올린 값과 같은지

세션을 끝내기 전에 이 두 가지를 확인하지 않았다면 작업은 끝난 것이 아니다.

> **2026-07-27 실제 사고**
> 저장소는 `A-0.1.38`에 멈춰 있는데 폰에는 `A-0.1.49`가 설치돼 있었다.
> `.39`~`.49` 소스가 GitHub에 없어서, 다른 컴퓨터에서 ZIP을 받아 복구해야 했다.
> 11개 버전 분량의 작업이 사라질 뻔했다. 이 규칙은 그 사고에서 나왔다.

`AGENTS.md` §10은 "요청 없이 commit/push 하지 말 것"이라고 되어 있는데, 이는 작업 도중
임의로 올리지 말라는 뜻이다. **작업 세션을 마무리할 때의 push는 이 문서로 이미 요청된
것으로 본다.** 다만 실제 push 직전에 무엇을 올리는지 한 번 보고한다.

## 화면 위에 뜨는 것들의 규칙

**MONSTER Touch 화면이 앞에 있는 동안에는 우리가 띄운 것이 하나도 보이면 안 된다.**
배터리 계기판, 위치 표시 마커, 트랩 존 전부 해당한다. 앱을 닫으면 다시 나타난다.
바탕화면이든 배달 앱 위든 상관없다 — 기준은 "우리 화면이 앞에 있는가" 하나다.

이유가 둘이다. 설정 화면 위에 계기판이 떠 있으면 자기 UI를 자기가 가린다. 그리고 트랩
존을 남겨두면 그 트랩이 설정 화면의 버튼을 먹는다.

`setConfigurationActive(true/false)`가 이 일을 한다. `MainActivity.onResume`에서 켜고
`onPause`에서 끈다. **오버레이를 새로 만들 때는 반드시 이 경로에 물려야 한다.**

잠금화면도 같은 규칙이다. 화면이 꺼지면 내리고, 잠금이 풀린 뒤에 되살린다. 켜둔 상태는
기억하므로 사용자가 다시 켤 필요는 없다.

## 위치 설정은 앱을 내린 다음에 띄운다

`위치 설정하기`를 누르면 **`moveTaskToBack()`으로 앱을 먼저 내리고** 300ms 뒤에 설정
오버레이를 띄운다.

잡아야 할 좌표는 배달 앱 위의 자리다. 우리 화면이 앞에 있는 채로 좌표를 찍으면 그 좌표는
아무 의미가 없다. 처음에는 곧바로 오버레이를 띄웠는데, 그러면 자기 화면 위에 좌표를 찍게
된다. **이 순서를 바꾸면 기능이 통째로 무의미해진다.**

## 설정을 어디에 저장할지

`devicePreferenceKey()`는 키 앞에 **선택된 리모컨의 식별자 해시**를 붙인다. 리모컨마다
따로 기억해야 하는 것만 이걸 쓴다 — 버튼 이름, 키 바인딩, 좌표, 트랩 존, 프로필.

**앱 전체 취향은 여기에 넣으면 안 된다.** 리모컨 선택이 풀리는 순간 다른 키를 읽어서,
사용자가 끈 적 없는 설정이 꺼진 것처럼 보인다. 실제로 그 버그를 겪었다 — 더블 클릭을
켜뒀는데 재설치 후 접근성이 꺼진 상태로 앱을 열자 스위치가 꺼져 보였다.

공용으로 저장하는 것들:

- `double_click_enabled` — 더블 클릭 사용 여부
- `overlay_opacity` — 화면 위 표시 진하기
- `double_trigger_*` — 더블에 걸린 동작
- `shutter_x` / `shutter_y` — 카메라 셔터 좌표

판단 기준은 하나다. **"리모컨을 바꾸면 이 값도 달라져야 하나?"** 아니라면 공용이다.

## 중요한 개발 규칙

- 기본 설치는 plain 버전만 한다.
- 버전명은 매번 명확히 올린다.
- **작업이 끝나면 GitHub에 올린다** (위 항목 참고).
- TikTok 클릭커/마우스/호버형 컨트롤러 지원은 Android 14 이상 기준으로 본다.
- Android 13 이하 호환성 때문에 핵심 구조를 복잡하게 만들지 않는다.
- 코드는 GitHub 기준으로 관리한다.
- APK와 테스트 자료는 Google Drive에 보관한다.
- Google Drive 동기화 폴더에서 Android 프로젝트를 직접 작업하지 않는다.
- Claude 제안은 바로 적용하지 말고 Codex가 코드베이스와 현재 버전 기준으로 검토한 뒤 반영한다.

## 남은 정리 작업

- [x] `AGENTS.md` — 저장소 루트에 있음 (커밋 `c3e7dd6`)
- [x] README 버전 정보 갱신 — `A-0.1.49` / `57`
- [x] 저장소를 public으로 전환
- [ ] **이 문서와 `CODEX_SESSION.md`를 저장소에 커밋** — 아직 로컬에만 있다.
      저장소 경로는 `C:\Users\baboe\Documents\Codex\monster-touch`
- [ ] `CODEX_HANDOFF_A.md`의 오래된 내용 확인
- [ ] Google Drive `CODEX/monster-touch` 하위 폴더 정리
- [ ] 액션/슬롯 분리 리팩터 (`CODEX_SESSION.md` 참고)

