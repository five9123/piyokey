import AppKit
import SwiftUI

private enum ConceptPalette {
  static let ink = Color(red: 0.20, green: 0.16, blue: 0.27)
  static let outline = Color(red: 0.91, green: 0.57, blue: 0.04)
  static let yellowTop = Color(red: 1.00, green: 0.91, blue: 0.54)
  static let yellowBottom = Color(red: 1.00, green: 0.72, blue: 0.20)
  static let orange = Color(red: 1.00, green: 0.48, blue: 0.16)
  static let pink = Color(red: 0.97, green: 0.28, blue: 0.55)
  static let palePink = Color(red: 1.00, green: 0.82, blue: 0.90)
  static let cream = Color(red: 1.00, green: 0.97, blue: 0.82)
  static let muted = Color(red: 0.46, green: 0.42, blue: 0.55)
}

private struct MascotConceptSheet: View {
  var body: some View {
    VStack(alignment: .leading, spacing: 26) {
      VStack(alignment: .leading, spacing: 8) {
        Text(verbatim: "PIYOKEY 병아리 디자인 3안")
          .font(.system(size: 42, weight: .black, design: .rounded))
          .foregroundStyle(ConceptPalette.ink)
        Text(verbatim: "모두 이미지 에셋 없이 SwiftUI 도형으로 구현하는 방향입니다.")
          .font(.system(size: 21, weight: .semibold, design: .rounded))
          .foregroundStyle(ConceptPalette.muted)
      }

      HStack(spacing: 28) {
        conceptCard(
          title: "A  말랑 키캡 피요",
          subtitle: "큰 반짝 눈 · 포근한 배 · 키캡을 꼭 안는 포즈",
          badge: "추천"
        ) {
          SoftKeycapChick()
        }

        conceptCard(
          title: "B  키캡 후드 피요",
          subtitle: "핑크 키보드 후드 · 강한 타이핑 브랜드 인상",
          badge: "브랜드형"
        ) {
          KeycapHoodChick()
        }

        conceptCard(
          title: "C  콩알 피요",
          subtitle: "작고 단순한 실루엣 · 점프와 표정 애니메이션 특화",
          badge: "모션형"
        ) {
          BeanChick()
        }
      }
    }
    .padding(48)
    .frame(width: 1_500, height: 760, alignment: .topLeading)
    .background(
      LinearGradient(
        colors: [Color(red: 1.00, green: 0.96, blue: 0.98), Color(red: 0.94, green: 0.96, blue: 1.00)],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
      )
    )
  }

  private func conceptCard<Art: View>(
    title: String,
    subtitle: String,
    badge: String,
    @ViewBuilder art: () -> Art
  ) -> some View {
    VStack(spacing: 16) {
      ZStack(alignment: .topTrailing) {
        RoundedRectangle(cornerRadius: 34, style: .continuous)
          .fill(.white.opacity(0.94))
          .shadow(color: ConceptPalette.ink.opacity(0.10), radius: 18, y: 10)

        Text(verbatim: badge)
          .font(.system(size: 16, weight: .black, design: .rounded))
          .foregroundStyle(.white)
          .padding(.horizontal, 15)
          .padding(.vertical, 8)
          .background(ConceptPalette.pink, in: Capsule())
          .padding(18)

        art()
          .frame(width: 300, height: 330)
          .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
          .padding(.top, 26)
      }
      .frame(height: 380)

      Text(verbatim: title)
        .font(.system(size: 27, weight: .black, design: .rounded))
        .foregroundStyle(ConceptPalette.ink)
      Text(verbatim: subtitle)
        .font(.system(size: 17, weight: .semibold, design: .rounded))
        .foregroundStyle(ConceptPalette.muted)
        .multilineTextAlignment(.center)
        .lineLimit(2)
        .frame(height: 48)
    }
    .frame(maxWidth: .infinity)
  }
}

private struct SoftKeycapChick: View {
  var body: some View {
    ZStack {
      feet.offset(y: 118)
      wing.rotationEffect(.degrees(18)).offset(x: -92, y: 30)
      wing.rotationEffect(.degrees(-18)).offset(x: 92, y: 30)

      Ellipse()
        .fill(
          LinearGradient(
            colors: [ConceptPalette.yellowTop, ConceptPalette.yellowBottom],
            startPoint: .top,
            endPoint: .bottom
          )
        )
        .overlay(Ellipse().stroke(ConceptPalette.outline, lineWidth: 5))
        .frame(width: 208, height: 224)
        .offset(y: 4)

      Ellipse()
        .fill(ConceptPalette.cream.opacity(0.72))
        .frame(width: 126, height: 112)
        .offset(y: 55)

      tuft
      glossyEye.offset(x: -45, y: -48)
      glossyEye.offset(x: 45, y: -48)
      cheek.offset(x: -75, y: -10)
      cheek.offset(x: 75, y: -10)
      ConceptDiamond()
        .fill(ConceptPalette.orange)
        .overlay(ConceptDiamond().stroke(ConceptPalette.outline, lineWidth: 3))
        .frame(width: 30, height: 22)
        .offset(y: -4)

      keycap(width: 126, height: 94).offset(y: 76)
      frontWing.rotationEffect(.degrees(-28)).offset(x: -71, y: 77)
      frontWing.rotationEffect(.degrees(28)).offset(x: 71, y: 77)
    }
  }

  private var tuft: some View {
    HStack(spacing: 0) {
      Capsule().rotationEffect(.degrees(-25))
      Capsule()
      Capsule().rotationEffect(.degrees(25))
    }
    .foregroundStyle(ConceptPalette.yellowTop)
    .overlay {
      HStack(spacing: 0) {
        Capsule().stroke(ConceptPalette.outline, lineWidth: 3).rotationEffect(.degrees(-25))
        Capsule().stroke(ConceptPalette.outline, lineWidth: 3)
        Capsule().stroke(ConceptPalette.outline, lineWidth: 3).rotationEffect(.degrees(25))
      }
    }
    .frame(width: 58, height: 44)
    .offset(y: -123)
  }

  private var glossyEye: some View {
    ZStack(alignment: .topLeading) {
      Circle().fill(ConceptPalette.ink).frame(width: 34, height: 40)
      Circle().fill(.white).frame(width: 11, height: 11).offset(x: 7, y: 6)
      Circle().fill(.white.opacity(0.82)).frame(width: 5, height: 5).offset(x: 21, y: 24)
    }
  }

  private var cheek: some View {
    Ellipse().fill(Color(red: 1.00, green: 0.45, blue: 0.45).opacity(0.62))
      .frame(width: 34, height: 19)
  }

  private var wing: some View {
    Ellipse()
      .fill(ConceptPalette.yellowBottom)
      .overlay(Ellipse().stroke(ConceptPalette.outline, lineWidth: 4))
      .frame(width: 42, height: 82)
  }

  private var frontWing: some View {
    Ellipse()
      .fill(ConceptPalette.yellowBottom)
      .overlay(Ellipse().stroke(ConceptPalette.outline, lineWidth: 4))
      .frame(width: 34, height: 65)
  }

  private var feet: some View {
    HStack(spacing: 30) {
      Capsule().fill(ConceptPalette.orange).frame(width: 38, height: 15)
      Capsule().fill(ConceptPalette.orange).frame(width: 38, height: 15)
    }
  }
}

private struct KeycapHoodChick: View {
  var body: some View {
    ZStack {
      HStack(spacing: 70) {
        Capsule().fill(ConceptPalette.orange).frame(width: 34, height: 14)
        Capsule().fill(ConceptPalette.orange).frame(width: 34, height: 14)
      }
      .offset(y: 121)

      RoundedRectangle(cornerRadius: 68, style: .continuous)
        .fill(
          LinearGradient(
            colors: [Color(red: 1.00, green: 0.67, blue: 0.81), ConceptPalette.pink],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
          )
        )
        .overlay(RoundedRectangle(cornerRadius: 68).stroke(Color(red: 0.88, green: 0.20, blue: 0.44), lineWidth: 5))
        .frame(width: 220, height: 218)
        .offset(y: 21)

      Circle()
        .fill(ConceptPalette.yellowTop)
        .overlay(Circle().stroke(ConceptPalette.outline, lineWidth: 5))
        .frame(width: 174, height: 174)
        .offset(y: -34)

      Capsule().fill(ConceptPalette.yellowTop).frame(width: 26, height: 60)
        .rotationEffect(.degrees(-24), anchor: .bottom).offset(x: -18, y: -126)
      Capsule().fill(ConceptPalette.yellowTop).frame(width: 26, height: 65)
        .offset(x: 14, y: -131)

      eye.offset(x: -39, y: -55)
      eye.offset(x: 39, y: -55)
      ConceptDiamond().fill(ConceptPalette.orange).frame(width: 31, height: 23).offset(y: -15)
      Ellipse().fill(Color.red.opacity(0.28)).frame(width: 34, height: 18).offset(x: -64, y: -18)
      Ellipse().fill(Color.red.opacity(0.28)).frame(width: 34, height: 18).offset(x: 64, y: -18)

      keycap(width: 116, height: 88).offset(y: 74)
      hoodWing.offset(x: -94, y: 64).rotationEffect(.degrees(-18))
      hoodWing.offset(x: 94, y: 64).rotationEffect(.degrees(18))
    }
  }

  private var eye: some View {
    ZStack(alignment: .topLeading) {
      Circle().fill(ConceptPalette.ink).frame(width: 30, height: 34)
      Circle().fill(.white).frame(width: 9, height: 9).offset(x: 6, y: 5)
    }
  }

  private var hoodWing: some View {
    Capsule()
      .fill(Color(red: 1.00, green: 0.70, blue: 0.82))
      .overlay(Capsule().stroke(Color(red: 0.88, green: 0.20, blue: 0.44), lineWidth: 4))
      .frame(width: 38, height: 75)
  }
}

private struct BeanChick: View {
  var body: some View {
    ZStack {
      HStack(spacing: 52) {
        Capsule().fill(ConceptPalette.orange).frame(width: 35, height: 14)
        Capsule().fill(ConceptPalette.orange).frame(width: 35, height: 14)
      }
      .offset(y: 116)

      ConceptBeanShape()
        .fill(
          LinearGradient(
            colors: [ConceptPalette.yellowTop, ConceptPalette.yellowBottom],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
          )
        )
        .overlay(ConceptBeanShape().stroke(ConceptPalette.outline, lineWidth: 5))
        .frame(width: 210, height: 235)

      Capsule().fill(ConceptPalette.yellowTop).frame(width: 25, height: 62)
        .rotationEffect(.degrees(-23), anchor: .bottom).offset(x: -14, y: -127)
      Capsule().fill(ConceptPalette.yellowTop).frame(width: 25, height: 68)
        .rotationEffect(.degrees(10), anchor: .bottom).offset(x: 18, y: -132)

      happyEye.offset(x: -43, y: -48)
      happyEye.offset(x: 43, y: -48)
      ConceptDiamond().fill(ConceptPalette.orange).frame(width: 29, height: 21).offset(y: -10)
      Ellipse().fill(Color.red.opacity(0.30)).frame(width: 35, height: 18).offset(x: -71, y: -11)
      Ellipse().fill(Color.red.opacity(0.30)).frame(width: 35, height: 18).offset(x: 71, y: -11)

      Ellipse()
        .fill(ConceptPalette.yellowBottom)
        .overlay(Ellipse().stroke(ConceptPalette.outline, lineWidth: 4))
        .frame(width: 41, height: 73)
        .rotationEffect(.degrees(-48))
        .offset(x: -93, y: 29)

      keycap(width: 102, height: 78)
        .rotationEffect(.degrees(4))
        .offset(x: 35, y: 70)
      Ellipse()
        .fill(ConceptPalette.yellowBottom)
        .overlay(Ellipse().stroke(ConceptPalette.outline, lineWidth: 4))
        .frame(width: 37, height: 66)
        .rotationEffect(.degrees(31))
        .offset(x: 94, y: 54)
    }
    .rotationEffect(.degrees(-2))
  }

  private var happyEye: some View {
    ConceptSmileArc()
      .stroke(ConceptPalette.ink, style: StrokeStyle(lineWidth: 6, lineCap: .round))
      .frame(width: 30, height: 19)
  }
}

private func keycap(width: CGFloat, height: CGFloat) -> some View {
  RoundedRectangle(cornerRadius: height * 0.23, style: .continuous)
    .fill(.white)
    .shadow(color: ConceptPalette.ink.opacity(0.18), radius: 8, y: 6)
    .overlay {
      RoundedRectangle(cornerRadius: height * 0.23, style: .continuous)
        .stroke(ConceptPalette.palePink, lineWidth: 6)
    }
    .frame(width: width, height: height)
    .overlay {
      Text(verbatim: "ㅎ")
        .font(.system(size: height * 0.52, weight: .black, design: .rounded))
        .foregroundStyle(ConceptPalette.ink)
    }
}

private struct ConceptDiamond: Shape {
  func path(in rect: CGRect) -> Path {
    var path = Path()
    path.move(to: CGPoint(x: rect.midX, y: rect.minY))
    path.addLine(to: CGPoint(x: rect.maxX, y: rect.midY))
    path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
    path.addLine(to: CGPoint(x: rect.minX, y: rect.midY))
    path.closeSubpath()
    return path
  }
}

private struct ConceptBeanShape: Shape {
  func path(in rect: CGRect) -> Path {
    var path = Path()
    path.move(to: CGPoint(x: rect.midX, y: rect.minY))
    path.addCurve(
      to: CGPoint(x: rect.maxX, y: rect.height * 0.52),
      control1: CGPoint(x: rect.width * 0.82, y: rect.minY),
      control2: CGPoint(x: rect.maxX, y: rect.height * 0.24)
    )
    path.addCurve(
      to: CGPoint(x: rect.width * 0.56, y: rect.maxY),
      control1: CGPoint(x: rect.maxX, y: rect.height * 0.84),
      control2: CGPoint(x: rect.width * 0.83, y: rect.maxY)
    )
    path.addCurve(
      to: CGPoint(x: rect.minX, y: rect.height * 0.50),
      control1: CGPoint(x: rect.width * 0.20, y: rect.maxY),
      control2: CGPoint(x: rect.minX, y: rect.height * 0.80)
    )
    path.addCurve(
      to: CGPoint(x: rect.midX, y: rect.minY),
      control1: CGPoint(x: rect.minX, y: rect.height * 0.18),
      control2: CGPoint(x: rect.width * 0.24, y: rect.minY)
    )
    path.closeSubpath()
    return path
  }
}

private struct ConceptSmileArc: Shape {
  func path(in rect: CGRect) -> Path {
    var path = Path()
    path.move(to: CGPoint(x: rect.minX, y: rect.height * 0.36))
    path.addQuadCurve(
      to: CGPoint(x: rect.maxX, y: rect.height * 0.36),
      control: CGPoint(x: rect.midX, y: rect.maxY)
    )
    return path
  }
}

@main
@MainActor
private struct RenderPiyokeyMascotConcepts {
  static func main() throws {
    let output = CommandLine.arguments.dropFirst().first
      ?? "artifacts/m6/piyokey-mascot-concepts.png"
    let renderer = ImageRenderer(content: MascotConceptSheet())
    renderer.scale = 1
    renderer.proposedSize = ProposedViewSize(width: 1_500, height: 760)

    guard let image = renderer.cgImage else {
      throw RenderError.renderFailed
    }
    let bitmap = NSBitmapImageRep(cgImage: image)
    guard let data = bitmap.representation(using: .png, properties: [:]) else {
      throw RenderError.encodingFailed
    }
    try data.write(to: URL(fileURLWithPath: output), options: .atomic)
    print(output)
  }

  private enum RenderError: Error {
    case renderFailed
    case encodingFailed
  }
}
