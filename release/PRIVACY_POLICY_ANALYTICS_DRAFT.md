# PIYOKEY Privacy Policy — analytics update draft

> Publication gate: this is the approved source copy for the next analytics-enabled build. It is not effective until it replaces the live content at `https://typee.app/privacy`. Before publishing, confirm the processor names, production region, retention settings, and support contact. Keep Japanese first on the public page and provide an obvious language switch.

Effective date: 2026-08-26  
Operator: Jungmin Oh / PIYOKEY  
Contact: https://typee.app/support

## 日本語

### プライバシーポリシー

PIYOKEY（以下「本アプリ」）は、アカウント登録や広告なしで利用できる韓国語タイピング学習アプリです。本ポリシーは、本アプリと公式ウェブサイトにおける情報の取扱いを説明します。

#### 1. 任意のデータ共有

本アプリでは、次の2項目を個別に選べます。どちらも初期設定はオフです。共有しなくても、レッスン、ゲーム、ローカルデッキ、購入済み機能を含むすべての機能を利用できます。

- **匿名の利用状況分析**：利用された機能、オンボーディングやセッションの開始・完了、ゲーム種別・難易度・結果の範囲、デッキの取得や作成機能の操作、購入フローの段階、アプリのバージョン、OS、言語、ランダムなアプリ識別子。
- **クラッシュ診断**：クラッシュ・ANR・動作停止のログ、スタックトレース、発生時の限定されたアプリ状態、端末モデル、OS、アプリのバージョン、ランダムなインストール識別子。

本アプリは、入力中または確定した文字、回答、目標単語、検索語、デッキ・項目ID、ユーザーデッキの名前・内容・ファイル・パス、氏名、メールアドレス、広告ID、録音、画面録画、セッションリプレイを分析・診断データとして送信しません。IPアドレスから位置情報を推定する処理も無効にします。

#### 2. 利用目的と委託先

- 匿名の利用状況分析は、よく使われる機能の把握、オンボーディングや学習フローの改善、品質指標の集計に利用し、**PostHog Cloud EU**で処理します。
- クラッシュ診断は、クラッシュ・ANR・動作停止の原因調査と修正に利用し、**Google Firebase Crashlytics**で処理します。Firebase Analyticsは利用しません。

データを販売せず、広告、プロファイリング、または他社のアプリ・ウェブサイトをまたぐ追跡に利用しません。委託先は、各社の規約とセキュリティ措置に従ってデータを処理します。

#### 3. 保存期間

PostHogの製品イベントは本番プロジェクトで12か月に設定し、その後削除します。Firebase CrashlyticsのクラッシュレポートはFirebaseの現行保持期間（公開時点で90日）に従います。法令対応またはセキュリティ上必要な場合を除き、目的達成後に不要なデータを保持しません。

学習履歴、設定、ユーザーデッキ、`.typedeck`ファイルは原則として端末内に保存され、本アプリのサーバーへアップロードされません。アプリを削除すると端末上のローカルデータも削除されますが、iCloud Driveなど本アプリ外へ書き出したファイルは各サービスの設定に従います。

#### 4. 選択の変更・削除の申出

アプリ内の **設定 → プライバシー** から、いつでも各項目をオン・オフできます。オフにすると新たな送信を停止し、未送信のクラッシュレポートは削除します。オフにする操作だけでは、すでに送信済みのデータは直ちに削除されず、上記の保存期間に従って削除されます。

送信済みデータの削除を希望する場合は、サポートページからご連絡ください。本アプリにはアカウントがなく識別子もランダムであるため、特定の匿名レコードを利用者本人に結び付けられず、技術的に識別・削除できない場合があります。その場合は理由を説明します。識別可能な範囲では、委託先の機能を使って対応します。

#### 5. 子どものプライバシー

本アプリは子どもを対象とした広告や行動追跡を行いません。地域の法令上、保護者の同意が必要な年齢の利用者は、保護者と一緒にデータ共有の選択を確認してください。

#### 6. 変更とお問い合わせ

収集項目、目的、委託先に重要な変更がある場合は、本ポリシーを更新し、必要に応じてアプリ内で改めて選択を求めます。お問い合わせは https://typee.app/support からご連絡ください。

## English

### Privacy Policy

PIYOKEY is a Korean typing-learning app that can be used without an account or advertising. This policy explains how information is handled in the app and on the official website.

#### 1. Optional data sharing

You can choose the following two options independently. Both are off by default. Refusing does not limit lessons, games, local decks, or purchased features.

- **Anonymous usage analytics:** features used; onboarding and session starts/completions; game type, difficulty, and result buckets; deck download and creation-tool actions; purchase-flow stages; app version; OS; language; and a random app identifier.
- **Crash diagnostics:** crash, ANR, and hang logs; stack traces; limited relevant app state; device model; OS; app version; and a random installation identifier.

PIYOKEY does not send typed or composed text, answers, target words, searches, deck or item IDs, user-deck names or content, files or paths, names, email addresses, advertising IDs, recordings, screen recordings, or session replay as analytics or diagnostics. IP-based geolocation is disabled.

#### 2. Purposes and processors

- Anonymous usage analytics are used to understand feature preference, improve onboarding and learning flows, and measure product quality. They are processed by **PostHog Cloud EU**.
- Crash diagnostics are used to investigate and fix crashes, ANRs, and hangs. They are processed by **Google Firebase Crashlytics**. Firebase Analytics is not used.

We do not sell this data or use it for advertising, profiling, or tracking across other companies' apps or websites. Processors handle data under their terms and security safeguards.

#### 3. Retention

PostHog product events are configured for deletion after 12 months. Firebase Crashlytics reports follow Firebase's current retention period (90 days at publication). We do not keep data longer than necessary except where required for legal or security purposes.

Learning history, settings, user decks, and `.typedeck` files normally stay on the device and are not uploaded to a PIYOKEY server. Deleting the app removes its local on-device data; files exported to iCloud Drive or another service remain subject to that service's settings.

#### 4. Changing choices and deletion requests

Change either choice anytime in **Settings → Privacy**. Turning a choice off stops future transmission and deletes unsent crash reports. It does not immediately erase records already sent; those records expire under the retention periods above.

To request deletion of already-sent data, contact us through the support page. Because PIYOKEY has no account and uses random identifiers, we may be unable to link a particular anonymous record to you and therefore may be technically unable to identify or delete it. We will explain when this applies and use processor deletion tools where a record can be identified.

#### 5. Children's privacy

PIYOKEY does not provide child-directed advertising or behavioral tracking. Users below the age at which local law requires parental consent should review the data-sharing choices with a parent or guardian.

#### 6. Changes and contact

If collection, purposes, or processors materially change, we will update this policy and request a new in-app choice where required. Contact us at https://typee.app/support.

## 한국어

### 개인정보처리방침

PIYOKEY(피요키)는 계정이나 광고 없이 이용할 수 있는 한국어 타이핑 학습 앱입니다. 이 방침은 앱과 공식 웹사이트에서 정보를 처리하는 방법을 설명합니다.

#### 1. 선택형 데이터 공유

다음 두 항목을 각각 선택할 수 있으며 기본값은 모두 꺼짐입니다. 공유하지 않아도 레슨, 게임, 로컬 덱과 구매한 기능을 제한 없이 이용할 수 있습니다.

- **익명 사용 분석:** 사용한 기능, 온보딩·세션 시작과 완료, 게임 종류·난이도·결과 구간, 덱 다운로드와 생성 도구 동작, 구매 흐름 단계, 앱 버전, OS, 언어, 무작위 앱 식별자.
- **크래시 진단:** 크래시·ANR·앱 멈춤 로그, 스택 추적, 발생 당시의 제한된 앱 상태, 기기 모델, OS, 앱 버전, 무작위 설치 식별자.

피요키는 입력 중이거나 확정된 글자, 정답, 목표 단어, 검색어, 덱·항목 ID, 사용자 덱 이름·내용·파일·경로, 이름, 이메일 주소, 광고 ID, 녹음, 화면 녹화, 세션 리플레이를 분석·진단 데이터로 전송하지 않습니다. IP 기반 위치 추정도 비활성화합니다.

#### 2. 이용 목적과 처리업체

- 익명 사용 분석은 선호 기능 파악, 온보딩·학습 흐름 개선과 품질 지표 집계에 이용하며 **PostHog Cloud EU**에서 처리합니다.
- 크래시 진단은 크래시·ANR·앱 멈춤의 원인 조사와 수정에 이용하며 **Google Firebase Crashlytics**에서 처리합니다. Firebase Analytics는 사용하지 않습니다.

데이터를 판매하지 않으며 광고, 프로파일링 또는 다른 회사의 앱·웹사이트를 연결한 추적에 사용하지 않습니다. 처리업체는 각 서비스 약관과 보안조치에 따라 데이터를 처리합니다.

#### 3. 보관 기간

PostHog 제품 이벤트는 운영 프로젝트에서 12개월 뒤 삭제되도록 설정합니다. Firebase Crashlytics 보고서는 Firebase의 현행 보관 기간(게시 시점 기준 90일)을 따릅니다. 법률 또는 보안을 위해 필요한 경우를 제외하고 목적 달성 후 불필요한 데이터를 보관하지 않습니다.

학습 기록, 설정, 사용자 덱과 `.typedeck` 파일은 원칙적으로 기기에 저장되며 피요키 서버로 업로드되지 않습니다. 앱을 삭제하면 앱의 기기 내 데이터도 삭제되지만, iCloud Drive 등 외부 서비스로 내보낸 파일은 해당 서비스 설정을 따릅니다.

#### 4. 선택 변경과 삭제 요청

앱의 **설정 → 개인정보**에서 언제든 각 항목을 켜거나 끌 수 있습니다. 끄면 앞으로의 전송을 중단하고 전송되지 않은 크래시 보고서를 삭제합니다. 이미 전송된 기록은 즉시 삭제되지 않으며 위 보관 기간에 따라 만료됩니다.

이미 전송된 데이터의 삭제를 원하면 지원 페이지로 요청해 주세요. 피요키에는 계정이 없고 무작위 식별자를 사용하므로 특정 익명 기록을 이용자와 연결할 수 없어 기술적으로 식별·삭제할 수 없는 경우가 있습니다. 이 경우 그 이유를 설명하며, 식별 가능한 범위에서는 처리업체의 삭제 기능을 사용해 처리합니다.

#### 5. 아동의 개인정보

피요키는 아동 대상 광고나 행동 추적을 제공하지 않습니다. 거주 지역 법률상 보호자 동의가 필요한 연령의 이용자는 보호자와 함께 데이터 공유 선택을 확인해 주세요.

#### 6. 변경과 문의

수집 항목, 목적 또는 처리업체에 중요한 변경이 생기면 이 방침을 갱신하고 필요한 경우 앱에서 다시 선택을 요청합니다. 문의는 https://typee.app/support 에서 접수해 주세요.
