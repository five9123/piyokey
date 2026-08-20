# PIYOKEY / ピヨキー

일본어 사용자가 한국어 타이핑을 배우는 앱입니다. 현재 iOS 앱과 공용
한글 조합·덱 계약이 구현되어 있으며, Android와 웹은 같은 저장소에
추가합니다.

## 저장소 구조

```text
.
├── ios/                  # SwiftUI 앱과 Swift 공용 로직
├── android/              # M7에서 추가할 Kotlin/Compose 앱
├── web/                  # 웹 버전 착수 시 추가
├── shared/               # 스키마, 테스트 벡터, 목 카탈로그, 오프라인 음원
├── tools/                # 콘텐츠 생성·검증 도구
├── release/              # 현재 릴리스 메타데이터와 필수 스크린샷
├── PRD.md                # 제품·기능·AC 단일 소스 오브 트루스
├── DECISIONS.md          # PRD 밖의 기술·제품 결정 기록
└── AGENTS.md             # 사람과 AI agent가 반드시 지킬 작업 규칙
```

플랫폼은 UI나 런타임 구현을 공유하지 않습니다. 대신
`shared/test_vectors.json`, JSON Schema, 카탈로그 fixture를 공통 계약으로
사용해 Swift·Kotlin·TypeScript 구현의 동작을 맞춥니다.

## 시작하기

요구 환경은 Xcode와 Swift 5.10+, Python 3.11+입니다. iOS 앱은
`ios/Hanco/Hanco.xcodeproj`의 `Hanco` scheme으로 실행합니다.

저장소 루트에서 빠른 검증을 실행합니다.

```bash
python3 -m unittest discover -s tools/tests -p 'test_*.py'
python3 tools/release_preflight.py
(cd ios/HangulEngine && swift test)
```

전체 iOS 회귀는 마일스톤 종료나 출시 후보에서 실행합니다.

```bash
xcodebuild test \
  -project ios/Hanco/Hanco.xcodeproj \
  -scheme Hanco \
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5'
```

## 작업 시작 전

1. [AGENTS.md](AGENTS.md)와 작업에 해당하는 [PRD.md](PRD.md) 절을 읽습니다.
2. GitHub issue 하나를 branch 하나와 담당자/agent 하나에 연결합니다.
3. `main` 최신 상태에서 `codex/<issue>-<slug>` 같은 짧은 branch를 만듭니다.
4. 공용 계약을 바꾸면 영향을 받는 모든 플랫폼 테스트를 함께 갱신합니다.
5. 테스트 결과와 미실행 항목을 PR에 기록합니다.

자세한 내용은 [협업 가이드](CONTRIBUTING.md),
[아키텍처](docs/ARCHITECTURE.md), [저장소 정책](docs/REPOSITORY_POLICY.md)을
참조하세요.
