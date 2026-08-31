# iOS 1.1 (10) — archive / TestFlight record

상태: **uploaded to App Store Connect (Xcode Organizer, 2026-08-31 21:39 KST); processing/TestFlight 확인 대기**

build 10은 TYP-71(PR #140, 온보딩 알림 권한 1회 → 개인정보 두 버튼 안내)을 포함한
최신 `origin/main`에서 만드는 1.1 RC다. build 9는 App Store Connect에 이미 존재하고
TYP-71을 포함하지 않아 RC로 사용하지 않는다(`release/IOS_1_1_BUILD_9.md`).
이 파일의 항목은 archive·서명·업로드를 실제 확인한 뒤에만 갱신한다.

- Main source SHA: `abb8d241551554f09db6fe0906be6a415691151e` (PR #141 squash; includes TYP-71 #140 `17fb367`)
- Git tree SHA: `6fa4b98fb53df14f5dcd8f3c9cd7ea0341e4590c`
- `workspace_doctor.py --strict --require-origin-main`: pass (2026-08-31 09:02 KST)
- `HancoTests` (iPhone 17, iOS 26.5): 389 passed, 0 failed, 0 skipped — same tree
- Archive: `PIYOKEY-1.1-10.xcarchive` — `app.piyokey.Piyokey` / `1.1` / `10`, team `X44BQNTAH9`
- Archive executable SHA-256: `80f60937b935efd2b123f1ddc51197d067632e0689fe0b13d8189597a8f63169`
- Archive tree SHA-256: `ceec90d92884790457f0cdb85fc2e2d9b8be83d8469558e0c552af66c85fc92f`
- Distribution IPA: `typee.ipa` — SHA-256 `6e4c3f18a5292e24921d8d73b9325ba6b3a7eabec7d3f38567202bfb2a0ff9ae` (`release/ExportOptions.plist`, Apple Distribution X44BQNTAH9, `codesign --verify --deep --strict` pass)
- Upload: `xcodebuild -exportArchive`(ExportOptionsUpload.plist) 4회 모두 `Failed to find an account with App Store Connect access for team X44BQNTAH9` (에이전트 프로세스에서 Xcode 26 계정 세션 미인식). 같은 archive를 `~/Library/Developer/Xcode/Archives/2026-08-31/`에 두고 제품 소유자가 Xcode Organizer → Distribute App → App Store Connect → Upload로 업로드, Organizer 상태 `Uploaded to Apple` (2026-08-31 21:39 KST)
- Apple build ID / processing status: 기록 대기 (ASC 라이브 콘솔 확인 필요)
- TestFlight installation: 미실행
- App Review submission / public release: 수행하지 않음

로컬 산출물·로그: `outputs/appstore-1.1-10-20260831/` (archive.log, export.log, upload.log, unit_tests.log).

Account Holder 계약·은행·세금, 정확한 build 10 실기기·IAP·미디어·현지어,
권리·개인정보 gate는 별도이며 archive 성공만으로 완료 처리하지 않는다.
