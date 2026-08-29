# 역사 기록: Claude Code M1 킥오프 프롬프트

이 문서는 프로젝트 초기 M1 시작용 프롬프트의 역사 기록이다. 현재 작업 지침이나
상태로 사용하지 않는다. 새 작업은 저장소 루트의 `AGENTS.md`, `PRD.md`,
`PROJECT_STATUS.md`, `ROADMAP.md`를 따른다.

## 당시 원문

터미널에서 이 폴더로 이동 후 `claude` 실행, 아래를 붙여넣으세요.

---

CLAUDE.md와 PRD.md를 읽고 M1을 시작해줘.

M1 범위 (PRD §13, §6, §8):
1. Swift 패키지 `HangulEngine` 생성 (ios/ 하위, 플랫폼 독립 순수 로직)
   - 두벌식 조합 상태 기계 (PRD §6.2): EMPTY→CHO→CHOJUNG→CHOJUNGJONG, 복합모음/복합종성 결합, 도깨비 이월, 백스페이스 자모 단위 해체
   - 자모 시퀀스 판정기 (PRD §6.3): 목표 문자열 → 기대 자모 시퀀스 분해, 실시간 정타/오타 판정, Shift 자모 1자모 취급
   - `shared/test_vectors.json`의 composition_cases 15개 + backspace_cases 6개 전부 통과하는 XCTest 작성. 테스트는 JSON 파일을 직접 읽을 것 (하드코딩 금지)
2. 덱/카탈로그 JSON Schema (`shared/schema/`) + Swift 디코딩 모델 + 검증기 (모든 ko 필드가 조합 엔진으로 분해 가능한지 확인)
3. `tools/gen_mock_catalog.py`: PRD §8.2 스키마의 목 카탈로그 생성 (시드 30덱 = 공식 6 + 가상 커뮤니티 24, 팬덤 태그 다양하게, preview_items 포함) → `shared/mock_catalog/`에 출력
4. DECISIONS.md에 결정 사항 기록

완료 후: 테스트 결과 요약과 함께 M2 착수 계획을 보여줘. Xcode 프로젝트(앱 타깃)는 M2에서 만든다 — M1은 SPM 패키지와 테스트만.

---

### 당시 패키지 메모

- `PRD.md` v1.3 (전체 스펙)
- `CLAUDE.md` (당시 작업 규칙·마일스톤·철칙)
- `shared/test_vectors.json` (당시 검증된 21케이스 — 직접 수정 금지)
- `tools/gen_test_vectors.py` (벡터 생성기)
- `DECISIONS.md` (결정 기록)
