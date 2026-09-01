# 1.1 iPad 화면·가로 스토어 이미지 최종 QA — #79

검증일: 2026-08-29 JST. 소스·시뮬레이터·로컬 이미지 검증이며 출시 승인이 아니다.

## 배포 대상과 현재 상태

- 현재 업로드된 `1.1 (7)`은 `UIDeviceFamily [1, 2]`인 Universal 앱이다. iPhone과 iPad가 같은 앱/버전으로 배포된다.
- 빌드 7의 앱 소스는 PR #74 `9af01ef`이며 **이번 #79 수정은 포함하지 않는다**. #79 병합 뒤 새 빌드 번호로 RC/TestFlight를 만들어야 한다.
- 최종 앱 변경 `f674b57`, 검증 HEAD `1c7dc56`. 이후 변경은 테스트/기록뿐이며 앱·shared 소스 동일성을 확인했다.
- App Store Connect는 1.1 Prepare for Submission, 빌드 7 연결 상태다. 새 archive·TestFlight 업로드·심사 제출·공개 출시는 하지 않았다.
- 검수한 가로 이미지를 기존 영어(미국) iPad 초안에 등록하려 했으나 Chrome 파일 업로드가 `-32000 Not allowed`로 차단됐다. 슬롯 재조회는 `0 of 10`; 이미지 업로드/저장 완료가 아니다. 권한 제한을 우회하지 않았다.

## 최종 화면 변경

| 화면 | 변경 |
|---|---|
| 두벌식 키보드 | 820pt 폭 상한 제거, 실제 창 폭 활용, 방향/높이에 따른 키·글자 확대와 넓은 스페이스바 |
| 10키 키보드 | 3×4 구조 및 최대 600pt 중앙 정렬 유지, 태블릿 키 높이 확대 |
| 연습·단어·초성·받아쓰기 | **가로/세로 모두 문제 → 피요·타이핑 → 키보드 순서 유지**. 오른쪽 조합 열 제거 |
| 큰 iPad 학습 | 문제·조합 카드가 키보드 위 공간을 55/45로 나누고 목표 글자·피요·조합·안내 확대 |
| 작은 iPad 가로 | 카드 내부 간격 축소, 듣기 버튼 우상단 배치, 키보드 위 안내 문구의 안전 간격 확보 |
| 홈·발견·게임 선택 | 기존 #79의 넓은 창 카드/글자 확대, 홈 2열·게임 선택 최대 3열 유지 |
| 마이페이지·설정 | 마이페이지 1열, 창/시트 폭 기준 폰트 확대, 접근성 글자 크기 보존 |

600pt 미만 창은 기존 iPhone 밀도를 유지한다. 저장 schema·한글 엔진·판정·발음·결제 로직은 변경하지 않았다.

## 검증 결과

모두 iOS 26.5 **시뮬레이터 선택 회귀**이며 전체 UI suite나 실기기 검증을 뜻하지 않는다. 로그/xcresult는 `/private/tmp/piyokey-qa79-results`에 보존한다.

| 범위 | 결과·근거 |
|---|---|
| iPhone 17 | c49bc84에서 앱 단위 367개 + 관련 UI 4개 통과 (`iphone-stacked-final.xcresult`). 후속 f674b57은 작은 iPad 가로 퀴즈 크기만 변경하여 iPhone 경로 동일 |
| iPad mini A17 Pro | 최종 앱 f674b57에서 연습 회전/입력·진행 보존과 게임 6종 가로 UI 2개 통과 (`mini-clearance-final.xcresult`) |
| iPad Pro 13-inch M5 | HEAD에서 실제 창 너비>높이를 강제한 연습 회전/입력·진행 보존 및 게임 6종 가로 UI 2개 통과 (`pro-orientation-verified.xcresult`) |
| 접근성 큰 글자 | c49bc84 Pro 세로 설정·연습 도달 검사 통과 (`pro-stacked-final.xcresult`의 접근성 사례만 사용) |
| 스토어 촬영 | ja/en/es/de/fr UI 테스트 5개 통과, 실제 가로 원본 50장 (`capture-landscape-v3`) |
| Release Simulator | 최종 앱 Release 빌드 통과 (`release-stacked-clearance.log`), 서명 archive 검증은 아님 |
| Python | HEAD에서 97개 통과 (`python-final-head.log`) |
| Swift 공용 패키지 | 이전 45개 통과 재사용. 이번 UI 수정으로 공용 소스 변경 없음 |
| repository preflight | 일반 통과. strict는 기존 열린 출시 항목 38개로 실패 (`strict-final-head.log`) |

### 발견한 문제와 재검증

- mini 가로에서 타이핑/응원 문구가 키보드 배경에 닿는 문제를 발견했다. 아래쪽 여유를 확보하고 작은 가로 퀴즈의 조합 확대율을 제한한 f674b57에서 재검증했다. 최종 검사는 안내 문구가 첫 키보다 최소 14pt 위에 있는지 확인한다.
- 한 Pro 실행은 기기 방향 요청만 성공하고 실제 앱은 세로였다. 테스트 이름만으로 가로 통과로 보지 않고 실제 창 비율 검사를 추가했다. 해당 `pro-stacked-final`의 게임/회전 결과는 최종 가로 근거에서 제외한다. Pro 재시작 뒤 `pro-orientation-verified`에서 실제 가로 캡처와 검사를 모두 확인했다.
- 촬영 v1/v2는 위 방향 검사에서 실패했으며 최종 이미지에 포함하지 않았다. v3만 사용한다. 이전 실패 로그도 보존한다.
- 이전 Release 명령의 generic destination/-arch 조합 실패는 컴파일 전 명령 오류였다. 실제 Pro destination을 사용한 최종 Release 빌드가 통과했다.

## 스토어 이미지

- 사용자 요청대로 **모든 기본 이미지를 가로 2752×2064 RGB PNG**로 제작했다. 10개 시장 × 10장 = **100장**, 세로/가로 대체본 0장.
- 시장: ja, en-US, ko, zh-Hans, zh-Hant, de-DE, fr-FR, es-ES, pt-BR, id. 실제 UI는 ja/en/es/de/fr이며 한국·중국어·브라질·인도네시아용은 영어 UI다.
- 기존 iPhone 마케팅 디자인·문구를 재사용하고 수정된 실제 iPad 화면을 비율 왜곡 없이 배치했다. 유료 덱 편집의 Pro 필요 안내를 유지한다. DEBUG 촬영 진행 상태는 구매 성공 증빙이 아니다.
- [Apple 스크린샷 규격](https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications/)의 iPad 13-inch 가로 규격을 확인했다.
- 원본 50장+출력 100장의 크기/PNG decode, 출력 RGB, 소스·출력·ZIP의 SHA-256 160개, ZIP 10개의 CRC/파일 수/순서를 검증했다. 10시장 contact sheet 전체와 일본어·영어·독일어·프랑스어 대표 전체 크기를 시각 검수했다. 일본어 최종본은 먼저 검수한 미리보기와 바이트 동일하다.
- 촬영 commit `5b5ab82`, 최종 후보 `1c7dc56`; 앱 소스 차이 0개. 실행 파일 SHA-256: `7314bce8d272f4ec6f1b555d163550b01bb954152057120b8bc8a9f658b07e88`.
- 전달 폴더: `/Users/jungminoh/Documents/hanco/outputs/ipad-1.1-landscape-store-20260829`. `index.html` 전체 갤러리, `zip/` 언어별 10장 ZIP, `manifest.json` 검증/출처, `qa/` 근거.
- 이전 `/outputs/ipad-1.1-store-20260829`의 세로 세트는 보존하되 최종 업로드용으로 사용하지 않는다.

### 재현

```sh
python3 tools/capture_global_store_assets.py --screenshots-only --derived-data /private/tmp/piyokey-qa79-results/Stacked --device <iPad-13-UDID> --languages ja en es de fr --output <새-촬영-폴더>
swiftc tools/generate_app_store_marketing_screenshots.swift -o <renderer>
python3 tools/build_ipad_store_assets.py --capture <촬영-폴더> --output <새-출력-폴더> --renderer <renderer> --capture-source-ref <촬영-SHA>
```

Pillow 사용. 촬영 언어별 실제 가로 비율과 출처를 검증한다. 같은 Pro에서 방향 테스트/촬영을 동시에 실행하지 않는다.
기본 명령은 기존처럼 모든 스토어 로케일을 생성한다. TYP-78의 일본어 10장만 만들 때는
명시적으로 `--locale ja --issue 78`을 추가한다. 기본 issue 79는 이 문서의 GitHub #79
all-locale 계약을 보존하며, 단일 locale 옵션은 정확히 하나의 일치 로케일이 없으면 실패한다.

## 스토어 등록·출시 전 남은 항목

1. PR #80 CI·별도 리뷰: Actions `enabled=false`, 리뷰/체크 없음. 과거 PR의 예외는 재사용하지 않는다. 정상 gate 또는 이번 PR에 대한 명시적 예외 승인 후 병합한다.
2. 병합 뒤 fetch 및 `workspace_doctor.py --strict --require-origin-main` 통과, 새 signed RC/TestFlight 제작. 실제 iPhone/iPad 업데이트·진행/덱/설정/구매 권한 보존, 가로/세로·Split View/Stage Manager·물리/OS 키보드 검증.
3. 파일 업로드 권한 복구 또는 제공 파일의 수동 등록. 현재 기존 로케일은 일본어·영어 4개 지역·한국어 6개다. 나머지 7시장 신규 메타데이터와 일본어 설명의 오래된 지원 언어 표기 수정은 공개 문구 변경 확인 후 진행한다. 기존 iPhone 이미지는 변경하지 않았다.
4. Paid Apps Agreement Pending User Info; 은행·한국 세금 양식 미완료. 미국 Foreign Status 및 W-8BEN은 Active를 확인했다. Account Holder가 직접 완료해야 하며 은행 입력 중인 다른 탭은 건드리지 않았다.
5. IAP 심사 자료/버전 연결, Sandbox 구매·취소·pending·복원·환불, 오디오·Files/iCloud/AirDrop·1,000항목·Game Center/60fps·알림·개인정보/권리·DSA의 열린 gate.
6. 최종 RC/이미지 일치와 현지어 사람 검수, 스토어 저장 후 재조회, 그 뒤 App Review 제출. 승인 후 자동·전 사용자 즉시 출시 설정은 유지했다.

증빙: `release/evidence/1c7dc566e0ebd3302f7a35a2a563735ebc21a6dc.json`. 종합 결과는 strict 출시 gate 및 파일 업로드 제한으로 `fail`이며, 개별 소스/선택 UI/이미지 통과와 구분한다.
