# TYP-88 OS 한국어 10키 Debug probe

이 probe는 Apple 기기가 실제로 전달하는 `committed`·`marked` 중간 상태를
측정하기 위한 DEBUG 전용 화면이다. Release/TestFlight에서는 노출되지 않는다.
정민의 최종 결정(Buzz `cb3fc899`, `8c1a594e`)에 따라 TYP-88 production 구현 전
standalone 입력 요청은 종료됐다. 이 화면과 절차는 향후 진단 자산으로만 유지하며,
probe 생략을 production 수정의 실기기 검증 완료로 간주하지 않는다.
현재 설치된 TestFlight 1.1 (12)를 덮어쓰거나 재설치하지 않는다. probe는 별도 번들
ID `app.piyokey.Piyokey.TYP88Debug`와 홈 화면 이름 `typee TYP88`로 병렬 설치한다.
정민·니모가 별도로 기기 일정을 확정한 기기에서만 아래 절차를 수행한다.

## 실행

1. Xcode의 `Hanco` scheme Run configuration을 `Debug`로 두고 launch environment에
   `TYP88_IME_PROBE=1`만 추가한다. `TYP83_IME_PROBE`는 설정하지 않는다. 병렬 설치본은
   다음처럼 실행할 수도 있다.

   ```sh
   xcrun devicectl device process launch \
     --device DE770B66-ECC1-53AC-AEF9-B7E9A25F7F95 \
     --environment-variables '{"TYP88_IME_PROBE":"1"}' \
     --terminate-existing \
     app.piyokey.Piyokey.TYP88Debug
   ```
2. 예약된 iPhone 또는 iPad를 선택해 앱을 실행하고 iOS 입력 소스를 한국어 10키로
   전환한다. 붙여넣기나 내장 키보드는 사용하지 않는다.
3. 화면 순서대로 입력한다. 일반 target은 정확히 완성하면 자동으로 넘어간다. manual
   capture 시나리오는 지시된 순환 또는 negative 획을 끝까지 입력한 뒤
   `Captured; continue`를 누른다. TYP-88 raw capture는 판정기를 우회하므로 mismatch도
   rollback하지 않으며, 이 화면의 결과를 production 허용 규칙으로 간주하지 않는다.
4. Xcode console에서 `TYP-88 row 13`을 필터링하고 시작부터 `Capture complete`까지
   원문을 보존한다. 각 목표 header의 기기·OS와 모든 step의 Unicode scalar,
   `committed`, `marked`를 생략하지 않는다.

exact-match corpus 순서는 다음과 같다. 이 앞에 separator-free `학교` 순환 manual
capture가 한 번 실행되고, 아래 `학교`는 timeout/오른쪽 화살표로 경계를 확정한 성공
경로다.

```text
일해 · 말해 · 말했다 · 급해 · 입학 · 번째 · 각하
읽어 · 닭 · 삶 · 많이 · 앓다 · 읊다
앉아 · 없다 · 못해 · 위키백과 · 돼 · 과 · 웨 · 의
학교 · 요 · 여자 · 예 · 교 · 며칠 · 표 · 효
```

마지막에는 다음 negative raw-stroke vector를 각각 manual capture한다.

```text
target 요: ㅇ → dot → dot → vertical(ㅣ)      # expected final은 horizontal(ㅡ)
target 여: ㅇ → dot → dot → horizontal(ㅡ)    # expected final은 vertical(ㅣ)
target 교: ㄱ → dot → dot → vertical(ㅣ)      # expected final은 horizontal(ㅡ)
```

## 증빙 판독·보관

- Class A에서는 닫힌 앞 음절 뒤 다음 초성을 누르는 동안 원래 받침과 초성 recipe
  prefix가 병합되는지, 그 상태가 committed인지 marked인지 그대로 기록한다.
- Class B에서는 목표 겹받침의 첫째·둘째 성분 조립과 뒤 모음 입력 시 재분리되는 모든
  step을 보존한다. 특히 `삶`·`많이`에서 `살ㅇ`·`만ㅅ` 같은 dangling 표기가 실제로
  관찰되는지 명시한다.
- 관찰되지 않은 dangling 형태를 추론해 판정 예외로 추가하지 않는다. 관찰됐다면 해당
  원문 step과 기기·OS를 TYP-81에 첨부한 뒤 별도 구현 판단을 받는다.
- Class C 첫 시나리오에서는 구분자 없이 `학` 뒤 ㄱ 키를 연속 입력해
  `학 → 핰 → 핚 → 학` 한 순환의 각 `committed`·`marked` snapshot을 보존한다. 앱은
  여기서 음절 경계를 합성하거나 separator-free `학교`를 보장하지 않는다. 두 번째
  `학교`에서는 `학` 뒤 timeout 또는 OS 키보드 오른쪽 화살표로 경계를 확정하고,
  `학ㄱ → 학교` 성공까지 기록한다. 어떤 경계 확정 방식을 썼는지도 로그와 함께 적는다.
- Class D에서는 첫 dot, 두 번째 dot 직후 glyph, 완성 직전·직후의 Unicode scalar를
  생략하지 않는다. production 계약은 U+318D `ㆍ`와 U+119E `ᆞ`를 한 획,
  U+11A2 `ᆢ`를 두 획으로 해석한다. 특히 한 글자 double-dot glyph를 한 dot으로
  해석하지 않으며, single-dot scalar가 연속되면 각각 한 획으로 기록한다.
  negative vector는 raw 관찰 전용이며, target recipe와 다른 마지막 획이 production에서
  허용된다는 뜻이 아니다.
- iPhone과 iPad 로그는 구분해 TYP-81에 첨부한다. Debug probe 결과는 정확한 build 13
  TestFlight의 전체 corpus 실기기 통과를 대신하지 않는다.
