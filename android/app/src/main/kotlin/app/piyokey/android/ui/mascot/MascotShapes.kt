package app.piyokey.android.ui.mascot

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path

/*
 * The iOS `Shape`s from `ChickMascotView.swift`, normalised to a frame of `w × h` pixels with the
 * origin at the frame's top-left. Callers translate to the frame position.
 */
internal object MascotShapes {
  fun tuft(w: Float, h: Float) = Path().apply {
    moveTo(w * 0.45f, h)
    quadraticTo(w * 0.05f, h * 0.25f, w * 0.55f, 0f)
    moveTo(w * 0.55f, 0f)
    quadraticTo(w * 0.35f, h * 0.35f, w * 0.95f, h * 0.6f)
  }

  fun diamond(w: Float, h: Float) = Path().apply {
    moveTo(w / 2, 0f)
    lineTo(w, h / 2)
    lineTo(w / 2, h)
    lineTo(0f, h / 2)
    close()
  }

  fun heart(w: Float, h: Float) = Path().apply {
    moveTo(w * 0.5f, h * 0.28f)
    cubicTo(w * 0.36f, -h * 0.10f, -w * 0.04f, h * 0.10f, w * 0.06f, h * 0.42f)
    cubicTo(w * 0.12f, h * 0.72f, w * 0.38f, h * 0.86f, w * 0.5f, h)
    cubicTo(w * 0.62f, h * 0.86f, w * 0.88f, h * 0.72f, w * 0.94f, h * 0.42f)
    cubicTo(w * 1.04f, h * 0.10f, w * 0.64f, -h * 0.10f, w * 0.5f, h * 0.28f)
    close()
  }

  fun starburst(w: Float, h: Float): Path {
    val inset = 0.32f
    return Path().apply {
      moveTo(w * 0.5f, 0f)
      lineTo(w * (0.5f + inset / 2), h * (0.5f - inset / 2))
      lineTo(w, h * 0.5f)
      lineTo(w * (0.5f + inset / 2), h * (0.5f + inset / 2))
      lineTo(w * 0.5f, h)
      lineTo(w * (0.5f - inset / 2), h * (0.5f + inset / 2))
      lineTo(0f, h * 0.5f)
      lineTo(w * (0.5f - inset / 2), h * (0.5f - inset / 2))
      close()
    }
  }

  fun feet(w: Float, h: Float) = Path().apply {
    for (cx in floatArrayOf(w * 0.3f, w * 0.7f)) {
      moveTo(cx, 0f)
      lineTo(cx, h * 0.62f)
      moveTo(cx - w * 0.10f, h)
      lineTo(cx, h * 0.62f)
      lineTo(cx + w * 0.10f, h)
    }
  }

  fun egg(w: Float, h: Float) = Path().apply {
    moveTo(w * 0.5f, 0f)
    cubicTo(w * 0.78f, 0f, w, h * 0.25f, w, h * 0.55f)
    cubicTo(w, h * 0.82f, w * 0.78f, h, w * 0.5f, h)
    cubicTo(w * 0.22f, h, 0f, h * 0.82f, 0f, h * 0.55f)
    cubicTo(0f, h * 0.25f, w * 0.22f, 0f, w * 0.5f, 0f)
    close()
  }

  fun crack(w: Float, h: Float) = Path().apply {
    moveTo(0f, h * 0.2f)
    lineTo(w * 0.42f, h * 0.38f)
    lineTo(w * 0.16f, h * 0.6f)
    lineTo(w * 0.62f, h * 0.72f)
    lineTo(w * 0.44f, h)
  }

  fun shellCup(w: Float, h: Float): Path {
    val teeth = 7
    return Path().apply {
      moveTo(0f, h * 0.3f)
      for (i in 0 until teeth) {
        val x1 = w * (i + 0.5f) / teeth
        val x2 = w * (i + 1f) / teeth
        lineTo(x1, 0f)
        lineTo(x2, h * 0.3f)
      }
      lineTo(w, h * 0.62f)
      cubicTo(w, h * 0.9f, w * 0.78f, h, w * 0.5f, h)
      cubicTo(w * 0.22f, h, 0f, h * 0.9f, 0f, h * 0.62f)
      close()
    }
  }

  fun comb(w: Float, h: Float) = Path().apply {
    moveTo(0f, h)
    quadraticTo(w * 0.1f, -h * 0.2f, w * 0.3f, h * 0.85f)
    quadraticTo(w * 0.44f, -h * 0.35f, w * 0.62f, h * 0.8f)
    quadraticTo(w * 0.82f, -h * 0.1f, w, h)
    close()
  }

  fun brow(w: Float, h: Float) = Path().apply {
    moveTo(0f, 0f)
    lineTo(w, h)
  }

  fun sweatDrop(w: Float, h: Float) = Path().apply {
    moveTo(w * 0.5f, 0f)
    quadraticTo(w * 1.15f, h * 0.75f, w * 0.5f, h)
    quadraticTo(-w * 0.15f, h * 0.75f, w * 0.5f, 0f)
    close()
  }

  /**
   * SwiftUI `addArc(clockwise: false)` in a y-down space sweeps with increasing angle, which is
   * Compose's positive sweep. Radius is `min(w, h) / 2` around the frame centre.
   */
  fun arc(w: Float, h: Float, startDegrees: Float, endDegrees: Float): Path {
    val r = minOf(w, h) / 2
    val cx = w / 2
    val cy = h / 2
    var sweep = endDegrees - startDegrees
    if (sweep < 0) sweep += 360f
    return Path().apply {
      arcTo(Rect(cx - r, cy - r, cx + r, cy + r), startDegrees, sweep, forceMoveTo = true)
    }
  }

  /** Simple crown for the `crown.fill` badge (no Material equivalent). */
  fun crown(w: Float, h: Float) = Path().apply {
    moveTo(w * 0.08f, h * 0.30f)
    lineTo(w * 0.30f, h * 0.55f)
    lineTo(w * 0.50f, h * 0.18f)
    lineTo(w * 0.70f, h * 0.55f)
    lineTo(w * 0.92f, h * 0.30f)
    lineTo(w * 0.84f, h * 0.82f)
    lineTo(w * 0.16f, h * 0.82f)
    close()
  }
}
