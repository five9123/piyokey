# Deck authoring overrides

`tools/deck_backoffice/server.py`가 저장한 덱 원본을 보관하는 폴더입니다.

- `main/*.json`: 발견 카탈로그에 들어가는 일반 덱
- `main/<game-mode>/*.json`: 게임 전용 덱

`tools/gen_mock_catalog.py`는 기존 내장 스펙으로 덱을 만든 뒤 여기의 같은 `deck_id`를 교체하거나 신규 덱을 추가합니다. 따라서 Deck Studio에서 수정한 텍스트는 생성기를 다시 실행해도 유지됩니다. JSON은 Deck Studio를 통해 수정하는 것을 권장합니다.
