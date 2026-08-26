# Google Play 출시 준비

이 문서는 Android `1.1.0 (8)` 소스 후보를 Google Play 배포 후보로 전환할 때 사용하는 체크리스트다. `release/google_play_metadata.json`이 스토어 문구·자산·열린 gate의 기계 판독 기준이다.

## 저장소에서 완료하는 항목

- [x] en-US·ja·ko 앱 이름, 짧은 설명, 전체 설명 초안
- [x] 30/80/4,000자 제한과 금지성 홍보 문구 자동 검증
- [x] shared 브랜드 원본 기반 512×512 Play 아이콘
- [x] 1024×500 무알파 피처 그래픽과 140자 이하 대체 텍스트
- [x] target SDK 36과 Android 16 제출 기준 일치
- [ ] 최종 signed AAB와 동일 commit·콘텐츠로 1080×1920 전화 스크린샷 4장씩(en-US·ja·ko) 캡처

스크린샷 순서는 홈·데일리, 조합이 보이는 연습, 6개 게임 허브/플레이, 성장·복습·기록으로 고정한다. 실제 앱 UI를 우선하고 추가 문구를 넣더라도 화면의 20% 이하로 제한한다. 통신사·알림·개인정보·디버그 표시·기기 프레임·스토어 배지를 포함하지 않는다.

## Play Console 외부 gate

- [ ] `app.piyokey.piyokey` 소유권 확인과 앱 생성
- [ ] Play App Signing 및 별도 upload key 확정
- [ ] Education 카테고리, 앱/게임 분류, 광고 없음, 연락처 입력
- [ ] 개인정보처리방침·Data safety·콘텐츠 등급·대상 연령 작성
- [ ] `app.piyokey.deckmaker.lifetime` 일회성 상품과 license tester 구성
- [ ] Play Games project, leaderboard 15개, achievement 5개 구성
- [ ] 운영 catalog URL과 콘텐츠·고정 발음 권리 승인
- [ ] en-US·ja·ko listing과 그래픽·스크린샷 업로드
- [ ] 내부 테스트 트랙에 최종 signed AAB 업로드
- [ ] 같은 AAB로 Issue #19 Galaxy 통합 QA 완료

외부 gate가 하나라도 열려 있으면 일반 `bundleRelease` 산출물을 업로드하지 않는다. 모든 비공개 설정을 주입한 `bundleDistributionRelease --no-configuration-cache`만 업로드 후보로 사용한다.

## 공식 요구사항 기준

- 앱 이름 30자, 짧은 설명 80자, 전체 설명 4,000자 이하
- Play 아이콘 512×512 32-bit PNG, 피처 그래픽 1024×500 JPEG 또는 24-bit 무알파 PNG
- 전화 스크린샷 최소 2장, 권장 4장 이상 9:16·최소 1080×1920
- 2026-08-31 이후 새 앱·업데이트는 Android 16(API 36) 이상 target

참고: Google Play Console Help의 [Create and set up your app](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en), [Add preview assets to showcase your app](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en), [Target API level requirements for Google Play apps](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)을 2026-08-27에 확인했다.
