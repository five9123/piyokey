# PIYOKEY Android

Android M7의 A0 골격과 M1 공용 코어 기준선이다. 앱 UI를 복제하기 전에 한글 입력, 덱, `.piyodeck` 계약을 순수 Kotlin으로 고정했다.

## 고정 도구 체인

- Gradle Wrapper 9.5.0
- Android Gradle Plugin 9.3.1
- Kotlin 및 Compose Compiler plugin 2.3.21
- Compose BOM 2026.08.00
- `minSdk 26`, `compileSdk 37`, `targetSdk 36`
- Java/Kotlin bytecode 17

AGP 9의 built-in Kotlin을 사용하므로 `:app`에는 `org.jetbrains.kotlin.android` plugin을 적용하지 않는다. 순수 JVM 모듈인 `:core:hangul`만 `org.jetbrains.kotlin.jvm`을 적용한다.

로컬에는 Homebrew JDK 21을 Gradle 실행용으로 사용할 수 있다.

```sh
cd android
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew \
  :core:hangul:jacocoTestCoverageVerification \
  :core:deckkit:test \
  :core:piyodeck:test
```

CI의 권장 Gradle 실행 JDK는 17이다. 생성되는 bytecode도 17로 고정한다.

첫 앱 빌드는 SDK 경로가 설정되지 않아 차단됐다. `ANDROID_HOME`을 지정한 검증에서는 기존에 수락되어 있던 라이선스를 Gradle이 발견해 Android API 37을 자동 설치했으며, 별도의 라이선스 수락 명령은 실행하지 않았다. 현재 이 기기에서는 앱 빌드가 통과한다. 새 환경에 API 37 또는 수락된 라이선스가 없으면 빌드가 차단되며, 외부 SDK 라이선스는 개발자 본인이 내용을 확인한 뒤 수락해야 한다.

```sh
cd android
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew :app:assembleDebug
```

## 모듈 경계

- `:app`: Compose single-activity 진입점과 플랫폼 연결. 현재 화면은 세션이 아닌 개발 준비 상태만 보여 준다.
- `:core:hangul`: Android 및 Compose에 의존하지 않는 순수 Kotlin 상태 기계. `shared/test_vectors.json`의 15개 조합, 10개 백스페이스 계약을 직접 읽어 검증한다.
- `:core:deckkit`: 덱·카탈로그 모델, strict codec, 공용 JSON Schema와 의미·bundle 검증. 공식 26덱, 게임 프리셋 15덱, v10→v11 snapshot을 검증한다.
- `:core:piyodeck`: STORE-only ZIP, strict UTF-8 JSON, manifest/hash/metadata, 사용자 덱 정책과 결정적 writer. `core:deckkit`에만 단방향 의존한다.

후보 `applicationId`와 namespace는 `app.piyokey.piyokey`다. Google Play Console에서 패키지 소유권과 최종 식별자를 확인하기 전까지 출시 계약으로 확정하지 않는다.

로컬-only·비공개 사용자 덱이 플랫폼 자동 백업으로 복제되지 않도록 A0 앱은 `android:allowBackup="false"`로 시작한다. 사용자 데이터의 백업 및 명시적 내보내기 정책은 M1 데이터 설계에서 경로별로 확정한다.

## M1 검증 결과

- Kotlin tests: Hangul 11, DeckKit 8, PiyoDeck 15, 실패 0
- Hangul JaCoCo: line 99.68%, branch 95.78%
- cross-platform golden: canonical 1,109 bytes, SHA-256 `025efa7a0584509fd892221a01c4b3cdf828472c3ffeb13a7eec420102061c31`
- 순수 core의 Android/Compose import: 0

M2 기능 단계는 이 작업에서 시작하지 않았다. 현재 변경을 중앙 저장소의 전용 브랜치로 선별 이전하고 기준선을 review한 뒤에만 이어간다.
