# iOS 1.1 (8) — superseded archive / TestFlight record

이 문서는 2026-08-29 생성·업로드한 build 8의 역사적 증빙이다. build 8은 iPad
변경까지 포함하지만 PR #96의 Pro 덱 언어 retag 이전 소스이므로 최종 1.1 RC로
사용하지 않는다.

| 항목 | 값 |
|---|---|
| Bundle / version / build | `app.piyokey.Piyokey` / `1.1` / `8` |
| `origin/main` | `a678e0f7bc3618eb131d7b742322dbddb0ad96a8` |
| archive checkout | `42f11157629e5272031a8860720124666441a368` |
| 동일 Git tree | `d5a8294ab2072c9c180ab7fb241637b187faf97c` |
| IPA SHA-256 | `758ed15e7aaa246a50c2f0724da7f212fccce86c3fd08a2ed368c5c6c7bcd581` |
| Apple build ID | `a4bf197a-930e-4b6b-9469-e0757eea8b30` |
| 처리 상태 | Build 8 Complete / Ready to Submit |

- Universal iPhone+iPad, minimum iOS 16.0을 확인했다.
- archive·App Store IPA export·Apple Distribution 서명 검증과 동일 archive 업로드가
  성공했다. 재빌드하지 않았다.
- iPhone 15 Pro JM 업데이트 설치는 성공했지만 잠금 때문에 실행 확인이 막혔다.
- Jungmin’s iPad에서 업데이트 설치·실행과 `1.1 (8)` inventory를 확인했다.
- App Store 1.1 version record는 build 7 연결 상태를 유지했다. build 8로 바꾸거나
  App Review를 제출하지 않았다.
- 정확한 TestFlight smoke, IAP, 미디어, 권리·개인정보, Account Holder gate는
  완료하지 않았다.

원본 archive·IPA·로그·전체 manifest는 로컬
`outputs/appstore-1.1-8-20260829/`에 보존되어 있다. 이 기록의 값은 해당
`BUILD_8.md`, `artifact-manifest.json`, GitHub Issue #77 증빙과 일치한다.
