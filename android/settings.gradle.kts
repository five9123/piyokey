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
include(":feature:practice")
include(":feature:discover")
include(":feature:game")
