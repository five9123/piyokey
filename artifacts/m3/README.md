# M3 검증 산출물

- `discover-search-twice-ja.png`: 발견 탭 검색과 팬덤 카탈로그 기준 이미지
- `deck-detail-twice-ja.png`: 덱 상세·미리보기·추천 기준 이미지
- `my-decks-installed-ja.png`: 설치된 덱의 오프라인 내 덱 기준 이미지
- `home-recommendations-ja.png`: 다운로드 태그 이력 기반 홈 추천 기준 이미지
- `practice-result-recommendations-ja.png`: 100% 연습 결과·동일 태그 추천·원탭 재도전 기준 이미지
- `m3-success-flow-ja.mp4`: iPhone 17 / iOS 26.5 Simulator에서 실행한 발견→다운로드→플레이→결과 추천→재도전 성공 흐름. H.264, 1206×2622, 19초

## 종료 게이트

- iOS 앱 테스트: 40개 통과, 실패·스킵 0
- SwiftPM `HangulEngine`·`DeckKit`: 13개 통과
- generic iOS Release 빌드: 통과
- 영상 무결성: AVFoundation 재생시간·주요 프레임 확인 완료

M3는 PRD §13에 따라 `shared/mock_catalog/`의 v1·v2 fixture를 대상으로 검증했다. 운영 `HANCO_CATALOG_URL` smoke test는 정적 호스트가 배정된 배포 환경에서 수행한다.
