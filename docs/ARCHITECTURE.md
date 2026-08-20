# 아키텍처와 플랫폼 경계

## 원칙

PIYOKEY는 하나의 source monorepo를 사용하지만, 플랫폼 앱을 하나의
cross-platform runtime으로 묶지 않습니다. iOS는 SwiftUI, Android는
Kotlin/Compose, 웹은 TypeScript 기반으로 각각 네이티브 구현합니다.

공유하는 것은 실행 코드가 아니라 검증 가능한 계약입니다.

```text
                  PRD.md / DECISIONS.md
                           │
             shared schemas · fixtures · vectors
                   ┌───────┼────────┐
                   │       │        │
              Swift/iOS Kotlin/Android TypeScript/Web
                   │       │        │
              native UI native UI  web UI
```

## 공용 계약

- `shared/test_vectors.json`: 한글 조합 상태 기계의 공용 입력·출력 벡터
- `shared/schema/`: 덱, 카탈로그, `.piyodeck` 형식의 JSON Schema
- `shared/mock_catalog/`: 정적 카탈로그와 오프라인 발음 자산 fixture
- `shared/piyodeck/`: 교환 형식 fixture와 호환성 자료
- `tools/`: 공용 콘텐츠를 생성·검증하는 재현 가능한 도구

공용 파일을 직접 손으로 파생 수정하지 않습니다. 생성기가 있는 파일은
생성기를 고치고 재생성하며, 각 플랫폼은 같은 fixture를 읽는 contract test를
둡니다.

## 플랫폼 경계

각 플랫폼이 소유하는 항목:

- 화면, navigation, 접근성, 입력 장치 연동
- 저장소와 migration 구현
- 오디오 session과 운영체제별 fallback
- 결제, Game Center/Play Games, 공유, 알림 같은 OS integration
- 한글 조합 엔진의 순수 함수 구현과 공용 벡터 test adapter

플랫폼 간 복사 가능한 항목은 알고리즘의 설명과 test vector이지, Swift 또는
Kotlin 런타임 바이너리가 아닙니다. 이 구조는 Android와 웹이 iOS release
cycle에 종속되는 것을 막으면서 결과의 일관성을 검사할 수 있게 합니다.

## 의존 방향

UI와 feature는 플랫폼 core를 의존할 수 있지만, 한글 조합 엔진과 DeckKit
같은 순수 core가 UI 또는 OS framework에 의존하면 안 됩니다. 카탈로그는
읽기 전용 정적 JSON이며 계정·쓰기 API를 추가하지 않습니다.

새 플랫폼을 시작할 때 첫 완료 조건은 화면 복제가 아니라 공용 schema와
`shared/test_vectors.json`을 모두 통과하는 독립 core 구현입니다.
