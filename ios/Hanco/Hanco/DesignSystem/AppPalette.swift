import SwiftUI
import UIKit

enum AppPalette {
  static let backgroundTop = adaptive(
    light: UIColor(red: 1.00, green: 0.94, blue: 0.97, alpha: 1),
    dark: UIColor(red: 0.10, green: 0.08, blue: 0.15, alpha: 1)
  )
  static let backgroundBottom = adaptive(
    light: UIColor(red: 0.92, green: 0.95, blue: 1.00, alpha: 1),
    dark: UIColor(red: 0.08, green: 0.11, blue: 0.19, alpha: 1)
  )
  static let card = adaptive(
    light: UIColor(white: 1, alpha: 0.92),
    dark: UIColor(red: 0.16, green: 0.14, blue: 0.22, alpha: 0.96)
  )
  static let ink = adaptive(
    light: UIColor(red: 0.20, green: 0.17, blue: 0.28, alpha: 1),
    dark: UIColor(red: 0.96, green: 0.94, blue: 0.98, alpha: 1)
  )
  static let mutedInk = adaptive(
    light: UIColor(red: 0.46, green: 0.43, blue: 0.53, alpha: 1),
    dark: UIColor(red: 0.70, green: 0.67, blue: 0.77, alpha: 1)
  )
  static let accent = Color(red: 0.96, green: 0.31, blue: 0.55)
  /// High-contrast foreground for text and symbols placed on `accent`.
  static let onAccent = Color(red: 0.10, green: 0.03, blue: 0.07)
  static let accentSoft = adaptive(
    light: UIColor(red: 1.00, green: 0.79, blue: 0.87, alpha: 1),
    dark: UIColor(red: 0.39, green: 0.14, blue: 0.25, alpha: 1)
  )
  static let secondary = adaptive(
    light: UIColor(red: 0.39, green: 0.45, blue: 0.96, alpha: 1),
    dark: UIColor(red: 0.55, green: 0.61, blue: 1.00, alpha: 1)
  )
  static let success = Color(red: 0.17, green: 0.68, blue: 0.49)
  static let error = Color(red: 0.91, green: 0.25, blue: 0.34)
  static let successText = adaptive(
    light: UIColor(red: 0.04, green: 0.38, blue: 0.25, alpha: 1),
    dark: UIColor(red: 0.42, green: 0.92, blue: 0.70, alpha: 1)
  )
  static let errorText = adaptive(
    light: UIColor(red: 0.62, green: 0.06, blue: 0.12, alpha: 1),
    dark: UIColor(red: 1.00, green: 0.58, blue: 0.64, alpha: 1)
  )
  static let key = adaptive(
    light: UIColor.white,
    dark: UIColor(red: 0.22, green: 0.20, blue: 0.29, alpha: 1)
  )
  static let keyShadow = adaptive(
    light: UIColor(red: 0.45, green: 0.40, blue: 0.62, alpha: 0.18),
    dark: UIColor(white: 0, alpha: 0.42)
  )

  private static func adaptive(light: UIColor, dark: UIColor) -> Color {
    Color(
      uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark ? dark : light
      }
    )
  }
}
