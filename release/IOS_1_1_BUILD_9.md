# iOS 1.1 (9) — archive / App Store Connect record

상태: **local archive and Distribution export verified; server build identity blocked**

2026-08-31 JST에 PR #139를 포함한 정확한 `origin/main`에서 build 9를 archive하고
Apple Distribution IPA로 export했다. 업로드는 App Store Connect가 이미 사용된 build
9라고 거부했다. 서버에 존재하는 build 9의 source SHA·processing 상태·version 연결
상태는 인증된 live console과 원 archive 증빙 없이 추정하지 않는다.

## Source and automated verification

- Main source SHA: `51a558ce756c9a4f5ab2e370d1d145a53dcab8ed`
- Git tree SHA: `15c004c98c0081fde8527acb7ca0ff60c9a516a8`
- PR #139 merge commit: `51a558ce756c9a4f5ab2e370d1d145a53dcab8ed`
- Python: `3.12.13`
- `workspace_doctor.py --strict --require-origin-main`: pass
- `release_preflight.py`: pass
- `release_preflight.py --strict`: fail closed, 38 manual/external gates open
- `HancoTests`: 386 passed, 0 failed, 0 skipped
- `HancoTests/OSIMEInputResetTests`: 7 passed, 0 failed
- `HancoUITests/HancoUITests/testAcidRainOSIMECanClearAnyVisibleFallingWord`: 1 passed, 0 failed

일반 필터 없는 Hanco test와 AppStoreScreenshot, ScreenshotCapture, AppPreview,
마케팅 캡처 테스트는 실행하지 않았다.

## Archive and signing

- Archive: `PIYOKEY-1.1-9.xcarchive`
- Archive executable SHA-256: `100658d19c295edd62ed11a9bfcae1aef51e2389b414478e55eb0c8482e97b02`
- Final archive tree SHA-256: `cde6f6c25a577b89cd2958912b839cf0395fd64dd5b36d95365bb15ae79ddcf8`
- Archive zip SHA-256: `4a6591a2ba6ebc343c2a7c8bdaaec8fdc04a81a2aabf405928e61c1da3557799`
- Distribution IPA: `typee.ipa`
- IPA SHA-256: `33fa2a26dc5394280803a03a7911ba46de51dfdea962abfeafca120735f46df9`
- Bundle / version / build: `app.piyokey.Piyokey` / `1.1` / `9`
- Team: `X44BQNTAH9`
- Signing identity: `Apple Distribution: Kihyun Ju (X44BQNTAH9)`
- Distribution entitlements: `get-task-allow=false`, `beta-reports-active=true`, Game Center enabled
- `codesign --verify --deep --strict`: pass

로컬 산출물과 로그는 `/Users/jungminoh/Documents/hanco/outputs/appstore-1.1-9-20260831/`에
보관한다. 저장소 SHA 증빙은
`release/evidence/51a558ce756c9a4f5ab2e370d1d145a53dcab8ed.json`이다.

## Upload outcome and blocker

업로드 export는 다음 App Store Connect 응답으로 실패했다.

> The bundle version must be higher than the previously uploaded version: ‘9’.

- Error ID: `c64dc4a9-a755-4cc1-9f8d-b24eb9878997`
- 이 실행에서 새 build를 업로드하지 않음
- 서버 build 9의 exact source SHA: 미확인
- 서버 build 9의 processing / TestFlight / version 연결 상태: 미확인
- App Review submission / public release / Linear Done: 수행하지 않음

blocker owner는 기존 build 9를 업로드한 Apple Developer Team `X44BQNTAH9`의
App Store Connect 운영자 또는 Account Holder다. 인증된 App Store Connect에서 build
9의 build ID·업로드 시각·processing·연결 상태를 확인하고, 원 archive/manifest가 위
source SHA와 tree SHA에서 생성됐다는 증빙을 제공해야 한다. 일치하지 않거나 증빙할 수
없으면 제품 소유자가 build number 10 변경을 명시적으로 승인한 뒤 새 exact-main RC를
만들어야 한다. 서버 build 9 삭제나 임의의 build number 증가는 수행하지 않았다.

TYP-68 iPhone·iPad 물리 기기의 두벌식·천지인 5개 직접 입력 게임 연속 10단어와
IAP, 미디어, 현지어, account, rights, privacy, regional gate는 모두 미완료다.
