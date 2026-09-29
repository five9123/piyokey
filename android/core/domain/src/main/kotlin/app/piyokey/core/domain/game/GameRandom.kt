package app.piyokey.core.domain.game

/**
 * iOS `ChoseongRandomNumberGenerator` (xorshift64) plus a bit-exact port of Swift's
 * `MutableCollection.shuffle(using:)` / `Int.random(in:using:)`, so a given seed produces the
 * same order on iOS and Android.
 */
class GameRandom(seed: ULong) {
  private var state: ULong = if (seed == 0uL) 0x9E37_79B9_7F4A_7C15uL else seed

  fun next(): ULong {
    state = state xor (state shl 13)
    state = state xor (state shr 7)
    state = state xor (state shl 17)
    return state
  }

  /** Swift `RandomNumberGenerator.next(upperBound:)` (Lemire's nearly-divisionless method). */
  fun next(upperBound: ULong): ULong {
    require(upperBound != 0uL) { "upperBound cannot be zero." }
    var random = next()
    var high = multiplyHigh(random, upperBound)
    var low = random * upperBound
    if (low < upperBound) {
      val t = (0uL - upperBound) % upperBound
      while (low < t) {
        random = next()
        high = multiplyHigh(random, upperBound)
        low = random * upperBound
      }
    }
    return high
  }

  /** Swift `Int.random(in: 0..<upperBound, using:)`. */
  fun nextInt(upperBound: Int): Int = next(upperBound.toULong()).toInt()

  companion object {
    /** High 64 bits of the unsigned 128-bit product (portable, no `Math.unsignedMultiplyHigh`). */
    internal fun multiplyHigh(a: ULong, b: ULong): ULong {
      val mask = 0xFFFF_FFFFuL
      val aLo = a and mask
      val aHi = a shr 32
      val bLo = b and mask
      val bHi = b shr 32
      val loLo = aLo * bLo
      val hiLo = aHi * bLo
      val loHi = aLo * bHi
      val hiHi = aHi * bHi
      val cross = (loLo shr 32) + (hiLo and mask) + loHi
      return hiHi + (hiLo shr 32) + (cross shr 32)
    }
  }
}

/** Swift `shuffle(using:)` (Fisher–Yates from the front). */
fun <T> MutableList<T>.swiftShuffle(generator: GameRandom) {
  if (size <= 1) return
  var amount = size
  var current = 0
  while (amount > 1) {
    val random = generator.nextInt(amount)
    amount -= 1
    val other = current + random
    val tmp = this[current]
    this[current] = this[other]
    this[other] = tmp
    current += 1
  }
}

fun <T> List<T>.swiftShuffled(generator: GameRandom): List<T> = toMutableList().also { it.swiftShuffle(generator) }

/** iOS `ChoseongQuizBuilder.seed(for:)`: FNV-1a 64 over UTF-8. */
object GameSeed {
  fun forValue(value: String): ULong {
    var hash = 1_469_598_103_934_665_603uL
    for (byte in value.encodeToByteArray()) {
      hash = (hash xor byte.toUByte().toULong()) * 1_099_511_628_211uL
    }
    return hash
  }
}
