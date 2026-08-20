import SwiftUI

/// AppIcon과 앱 내부 로고 영역에서 함께 쓰는 단일 PIYOKEY 브랜드 이미지.
struct PiyokeyLogoMark: View {
  var body: some View {
    Image("PiyokeyLogo")
      .resizable()
      .scaledToFit()
      .aspectRatio(1, contentMode: .fit)
      .accessibilityElement(children: .ignore)
      .accessibilityLabel(Text("app.logo.accessibility_label"))
  }
}
