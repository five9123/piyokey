# M4 검증 산출물

- `game-selection-ja.png`: 설치 덱 선택·자동 코스 표시 기준 이미지
- `game-flow-ja.png`: 타이머·점수·콤보·컨베이어 카드·내장 키보드 기준 이미지
- `game-result-ja.png`: 랭크·점수·`NEW RECORD`·통계·일본어 코멘트·동일 태그 추천·재도전 기준 이미지
- `practice-result-common-ja.png`: 게임과 같은 Z1~Z5 구조·카운트업·메트릭 게이지를 사용하는 연습 결과 기준 이미지
- `m4-success-flow-ja.mp4`: 설정→공식 덱 설치→게임 정답·파티클→결과·신기록→1탭 재도전 흐름. iPhone 17 / iOS 26.5 Simulator, H.264, 1206×2622, 28.5초
- `m4-iphone15pro-frame-probe.png`: 실제 iPhone 15 Pro에서 연속 정타 2회·파티클 2회 직후 Debug 프레임 프로브와 점수 100·콤보 2를 함께 확인한 화면
- `hanco-iphone15pro-animation-hitches.trace`: 실제 iPhone 15 Pro Hanco 프로세스의 Animation Hitches 31.48초 trace
- `hanco-iphone15pro-game-performance-particles.trace`: Hanco 프로세스에 연결해 파티클을 포함한 Game Performance trace
- `hanco-iphone15pro-game-performance-all-processes.trace`: 연속 정타·파티클 2회를 포함한 all-processes Game Performance 21.23초 trace

## 현재 검증 상태

- iOS 앱 테스트: 65개 통과, 실패·스킵 0
- SwiftPM `HangulEngine`·`DeckKit`: 13개 통과
- generic iOS Simulator Release 빌드: 통과
- 게임 UI 회귀: 설치 덱 시작, 백그라운드 타이머 정지, 정답 50점·콤보 1, 시간 종료, 결과 100%·`NEW RECORD`, 1탭 재도전 통과
- Simulator 성공 흐름 녹화: 완료·장면별 검수 완료
- iPhone 15 Pro / iOS 26.5 Animation Hitches: Hanco potential hang 0건, hitch 0건, 측정 당시 thermal `Fair`
- iPhone 15 Pro / iOS 26.5 Game Performance: 연속 정타·파티클 2회 구간에서 Hanco hang 0건. all-processes trace의 유일한 591.92ms hang은 시스템 `adid` 프로세스이며 Hanco가 아니다. 반복 프로파일링으로 측정 당시 thermal `Serious`
- iPhone 15 Pro / iOS 26.5 Debug `CADisplayLink` 프레임 프로브: 점수 100·콤보 2·파티클 2회 직후 평균 59.8 FPS, p95 16.7ms, 20ms 초과 2프레임

Instruments의 iPhone Mirroring 녹화에서는 displayed-surface/FPS 테이블이 비어 정확한 앱 FPS를 직접 내보내지 못했다. 그래서 hang·hitch trace와 Release에 포함되지 않는 Debug `CADisplayLink` 프로브를 교차 확인했다. M4 기능과 현재 보유 기기의 보조 검증은 완료했다. 사용자의 명시적 승인으로 iPhone 12 기준 기기 측정은 M6 종료 전 출시 게이트로 이관했으며, 완료 전까지 미검증 상태로 유지한다. F7 복습 목록·복습 CTA는 M5, F9 공유 CTA는 M6에서 공통 결과 슬롯에 연결한다.

## iPhone 12 최종 게이트 실행

연결된 기기의 UDID는 `xcrun devicectl list devices`로 확인한다. 도구는 `iPhone 12`(`iPhone13,2`)만 PRD 기준 기기로 인정하며 다른 기기는 `--allow-proxy`를 명시하지 않으면 준비·녹화를 중단한다.

```sh
python3 tools/m4_ios_performance_gate.py inspect-device --device <UDID>
python3 tools/m4_ios_performance_gate.py prepare --device <UDID>
python3 tools/m4_ios_performance_gate.py record --device <UDID> --template animation-hitches --duration 30
python3 tools/m4_ios_performance_gate.py record --device <UDID> --template game-performance --duration 20
```

`prepare`는 실제 기기 Debug 빌드·설치·실행과 기기 메타데이터 저장을 담당한다. 각 `record` 명령의 카운트다운 뒤 공식 첫 덱에서 `회사`, `주말`을 연속 정타해 파티클을 두 번 발생시킨다. 도구는 `.trace`, `.toc.xml`, `.metadata.json`을 이 폴더에 함께 저장한다. 일반 Debug 플레이 화면에서는 성능 오버레이를 숨기며, 프레임 프로브 캡처가 필요한 측정 실행만 Scheme 환경 변수 `HANCO_SHOW_GAME_FPS=1`로 앱을 시작한다. 마지막으로 FPS·p95·20ms 초과 수와 Hanco hang·hitch 0건을 확인한다.
