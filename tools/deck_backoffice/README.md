# PIYOKEY Deck Studio

PIYOKEY의 정적 덱 JSON을 로컬 브라우저에서 만들고 편집하는 운영 도구입니다. 앱이나 원격 서버에 쓰기 API를 추가하지 않습니다.

## 실행

저장소 루트에서 다음 명령을 실행합니다.

```bash
python3 tools/deck_backoffice/server.py
```

브라우저가 `http://127.0.0.1:8765`로 자동으로 열립니다. 종료할 때는 터미널에서 `Ctrl+C`를 누릅니다.

자동으로 브라우저를 열지 않으려면 다음처럼 실행합니다.

```bash
python3 tools/deck_backoffice/server.py --no-open
```

## 제공 기능

- 현재 `shared/mock_catalog/decks/`의 일반·게임 전용 덱 검색 및 편집
- 신규 덱 생성, 기존 덱 복제, JSON 가져오기/내보내기
- 행 단위 추가·복제·삭제와 스프레드시트 TSV 일괄 붙여넣기
- PRD §8.1의 필드 제한, ID, 날짜, 한국어 10자, 조합 가능 문자, 중복 항목 실시간 검사
- 저장 시 덱 파일과 `catalog.json`의 미리보기·바이트 크기·태그 수 자동 동기화
- 선택적인 버전 증가와 이전 버전 파일 보존
- `shared/deck_overrides/main/`에 생성기 재실행용 원본 자동 저장
- 항목별 `✦ AI` 버튼으로 가타카나 읽기·일본어 뜻·gTTS 한국어 MP3 생성
- 한 번에 최대 25개씩 비어 있는 필드만 채우는 `빈칸 AI 채우기`

오디오가 `null`인 항목은 앱에서 기기 TTS로 대체됩니다. 공식 오프라인 음원이 필요한 덱은 저장 후 기존 음원 생성 워크플로를 실행해 `audio` 경로를 채워야 합니다.

## AI 연결

가타카나 읽기와 일본어 뜻 생성에는 OpenAI API 키가 필요합니다. 예제 설정을 복사한 뒤 키를 입력하고 Deck Studio를 다시 실행합니다.

```bash
cp tools/deck_backoffice/config.example.env tools/deck_backoffice/.env
```

```dotenv
OPENAI_API_KEY=여기에_발급받은_API_키
```

`.env`는 Git에서 제외되며 API 키는 브라우저로 전달되지 않습니다. 텍스트 요청은 Responses API의 구조화 출력과 `store: false`를 사용합니다. 기본 모델은 짧은 구조화 추출에 맞춘 `gpt-5.6-luna`이고 `PIYOKEY_OPENAI_MODEL`로 바꿀 수 있습니다.

한국어 MP3 생성에는 저장소에 고정된 gTTS 2.5.4가 필요합니다.

```bash
python3 -m pip install -r tools/requirements-audio.txt
```

항목의 `✦ AI`를 누르면 생성 결과를 먼저 보여 줍니다. 체크한 값만 편집 행에 적용되며, 실제 덱 파일에는 상단 `저장`을 눌러야 반영됩니다. 오디오는 공용 생성기와 같은 gTTS `lang=ko`, `tld=com`, 보통 속도로 만들며 `shared/mock_catalog/audio/ko_<한국어 UTF-8 SHA-256 앞 20자리>.mp3`에 원자적으로 저장합니다. 동일 문구는 기존 MP3를 검증해 재사용합니다.

gTTS는 실행 시 네트워크를 사용하며 한국어 문구를 gTTS가 사용하는 Google Translate 음성 서비스로 전송합니다. OpenAI에는 오디오 생성 요청을 보내지 않습니다. gTTS 라이브러리는 지연 로드되므로 설치되지 않았거나 버전이 2.5.4와 다르면 Deck Studio의 텍스트 편집은 계속 사용할 수 있고 오디오 생성만 설치 안내와 함께 중단됩니다.

## 안전 범위

서버는 `127.0.0.1`에만 바인딩하고, `shared/mock_catalog/decks/` 밖의 파일 경로는 거부합니다. 저장 전 변경은 브라우저 로컬 저장소에 임시 복구본으로 남고, 실제 파일은 저장 버튼을 누를 때만 원자적으로 갱신됩니다.
