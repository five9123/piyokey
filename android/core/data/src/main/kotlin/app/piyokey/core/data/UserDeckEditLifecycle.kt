package app.piyokey.core.data

sealed class UserDeckEditCommitException(message: String) : Exception(message) {
  data class SourceChanged(val expectedVersion: Int, val actualVersion: Int?) :
    UserDeckEditCommitException(
      "The source deck changed while editing (expected $expectedVersion, actual $actualVersion).",
    )

  data object IdentifierCollision :
    UserDeckEditCommitException("The generated user-deck identifier is already in use.")
}
