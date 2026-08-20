# Hanco iOS — 모바일 테스트 호스트

현재 화면은 M1 `HangulEngine`을 실제 iPhone 입력 UI에 연결한 M2 구현이다. 세 문제의 자모 순서, 다음 키 가이드, 조합 프리뷰, 오타 무시, 도깨비 이월, 자모 단위 백스페이스를 확인할 수 있다. 정타 시 자모 결합, 오타 시 셰이크·빨강 플래시, 단어 완성 시 8개 파티클·카드 팝이 재생된다. `ㅎ` 키캡을 든 아기 펭귄 마스코트는 설정 화면과 조합 상태에 맞춰 함께 반응한다. Debug 빌드에서는 상태 행에 최근 120회 입력의 터치 다운→다음 화면 프레임 p95도 표시한다.

키보드는 가이드, 로마자 힌트, 햅틱을 각각 끌 수 있는 영속 옵션을 지원하며 기본값은 모두 ON이다. 옵션은 연습 시작 전 설정 화면에서 바꾸며, 세션 중에는 설정 모달을 열지 않는다.

## Xcode에서 실행

1. `Hanco.xcodeproj`를 연다.
2. scheme `Hanco`과 설치된 iPhone Simulator를 선택한다.
3. Run(`⌘R`)을 누른다.

실제 iPhone에서는 Hanco 타깃의 Signing & Capabilities에서 자신의 Team을 선택하고, 필요하면 bundle identifier를 고유한 값으로 바꾼 뒤 기기를 실행 대상으로 선택한다.

## 실기기 F2 확인

1. 실제 iPhone을 Mac에 연결하고 위 절차로 Debug 앱을 실행한다.
2. 20자모 이상 입력한 뒤 `入力反映 p95`가 초록색이고 `50.0 ms` 이하인지 확인한다. 시뮬레이터 XCUITest 수치는 프로세스 간 입력 합성 지연이 섞이므로 성능 합격 기준으로 쓰지 않는다.
3. 롤오버는 첫 번째 키를 누른 손가락을 떼지 않은 상태에서 새로 하이라이트된 다음 키를 다른 손가락으로 누른다. 두 자모가 순서대로 반영되면 통과다.
4. 오타, Shift 된소리·이중모음, 백스페이스도 같은 방식으로 확인한다.

성능 회귀를 정식 측정할 때는 Xcode의 Instruments에서 Core Animation 템플릿을 실제 기기에 연결하고, 화면 p95와 프레임 드롭을 함께 기록한다.

## 명령줄 테스트

저장소 루트에서:

```bash
xcodebuild test \
  -project ios/Hanco/Hanco.xcodeproj \
  -scheme Hanco \
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5'
```

M1 공용 벡터와 DeckKit 테스트는 별도로 실행한다.

```bash
cd ios/HangulEngine
swift test
```

## 오프라인 목표 발음

앱이 제공하는 공식 덱·게임 프리셋·커리큘럼·데일리·기본 연습의 고정 한국어 581개는 `shared/mock_catalog/audio/`의 콘텐츠 주소형 gTTS MP3를 재생한다. 앱 런타임은 네트워크 TTS를 호출하지 않으며, 사용자 덱처럼 빌드 시 알 수 없는 문구와 파일 손상 때만 `AVSpeechSynthesizer(ko-KR)`로 대체한다.

콘텐츠를 바꾼 뒤에는 카탈로그와 음원을 순서대로 갱신한다.

```bash
python3 tools/gen_mock_catalog.py
python3 -m pip install -r tools/requirements-audio.txt
python3 tools/gen_gtts_audio.py --prune
```

생성기는 일반 덱과 5개 게임 하위 폴더를 재귀적으로 읽고 `pronunciation_prompts.json`의 커리큘럼·기본 연습 문구를 합친다. 각 MP3를 CoreAudio로 검증한 뒤 원자 교체하며, release preflight는 정확히 581개 MP3와 legacy 발음 CAF 0개를 요구한다.

Simulator UI 테스트는 p95 표본 생성까지 검증하지만 `50ms` 합격 판정은 하지 않는다. 성능 판정은 위 실기기 절차를 따른다.

## M3 발견 탭

하단 `さがす` 탭은 `shared/mock_catalog/catalog.json`을 앱 리소스로 직접 포함하고 `DeckKit`으로 디코딩·검증한다. 출시 fixture는 키보드 입문·자음·모음·조합·받침·기초 어휘·일상·여행·TOPIK·요즘 한국어, 창작 K-POP·K드라마, 여행·연애·친구·최애 라이브 상황별 재미 표현 콘텐츠의 공식 덱 26개로 구성되며, 이름·태그·제작자 검색과 타입·레벨 필터를 지원한다. 실제 집계가 없는 인기·급상승 수치는 노출하지 않는다.

덱 카드를 누르면 상세 화면에서 최대 10개 문장과 동일 태그 추천을 확인할 수 있다. `ダウンロード` 뒤 `練習する`를 누르면 저장된 키보드 설정을 사용해 바로 전체 덱 연습을 시작하며, 이 흐름은 발견 카드부터 세 번의 탭 이내다. 설치된 전체 덱 JSON과 버전형 인덱스는 Application Support에 저장되므로 앱을 다시 실행하거나 네트워크가 없어도 `マイデッキ`에서 연습할 수 있다. 카드의 더보기 메뉴에서는 덱을 삭제할 수 있고, 카탈로그 버전이 높아지면 업데이트 CTA가 표시된다.

앱 시작 시 검증된 카탈로그 캐시가 있으면 이를, 없으면 번들 카탈로그를 즉시 표시한 뒤 백그라운드에서 조건부 GET을 수행한다. 캐시에는 `schema_version`, `catalog_version`, `fetched_at`, ETag, Last-Modified와 원본 JSON을 저장한다. `304 Not Modified`는 확인 시각만 갱신하고, 오프라인·5xx·잘못된 JSON은 현재 발견 화면을 막거나 기존 캐시를 덮어쓰지 않는다.

운영 정적 호스트는 앱 타깃의 `HANCO_CATALOG_URL` 빌드 설정에 `https://.../catalog.json` 형식으로 지정한다. Debug에서는 scheme의 `HANCO_CATALOG_URL` 환경 변수로 덮어쓸 수도 있다. URL이 비어 있으면 개발용 공용 fixture로 동작한다. 원격 URL이 설정되어도 공식 덱은 오프라인 사용을 위해 번들을 우선하고, 버전이 높아진 덱만 상대 `decks/*.json` 경로에서 HTTPS로 갱신한다.

`shared/mock_catalog/updates/`는 카탈로그 v9와 `official_daily_words` v4를 포함하는 완전한 정적 호스트 fixture다. 기본 카탈로그 v8 설치 후 이 카탈로그를 받으면 발견·내 덱에 `更新あり`가 표시되고, 내 덱 더보기 메뉴에서 한 번에 갱신할 수 있다.

연습 홈의 `あなたへのおすすめ`는 다운로드했던 덱의 태그 일치 점수를 합산하고, 미설치 덱만 인기순 타이브레이커로 최대 3개 표시한다. 다운로드 이력은 덱을 삭제해도 설치 인덱스에 남는다. 이력이 없는 첫 실행에서는 featured·인기순으로 시작할 공식/인기 덱을 제안한다.

설치 덱 연습을 끝내면 정확도·미스·완료 항목을 보여주는 결과 화면으로 이동할 수 있다. 현재 덱과 같은 태그의 덱 2개를 미설치 우선으로 추천하며 `もう一回`은 한 번의 탭으로 같은 세션을 초기화한다. 점수·속도·복습 덱·공유와 전체 F12 연출은 각 후속 마일스톤에서 공통 결과 컴포넌트로 확장한다.

M2 시각 기준 이미지와 Simulator 성공 흐름 녹화는 저장소 루트의 `artifacts/m2/`에 있다.
M3 발견·상세·내 덱·홈 추천·결과 추천 기준 이미지는 `artifacts/m3/`에 있다.
