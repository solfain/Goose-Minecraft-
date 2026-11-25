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

rootProject.name = "XrtcfChess"

include(":app")
include(":chess-core")
include(":chess-engine")
include(":chess-analysis")
include(":chess-ui")
include(":chess-training")
