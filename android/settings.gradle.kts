pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "PiyokeyAndroid"

include(":app")
include(":core:hangul")
include(":core:deckkit")
include(":core:piyodeck")
include(":core:session")
include(":core:data")
include(":core:game")
include(":core:retention")
include(":core:platform")
include(":core:settings")
include(":core:design")
include(":feature:practice")
include(":feature:discover")
include(":feature:game")
include(":feature:retention")
include(":feature:onboarding")
include(":feature:settings")
