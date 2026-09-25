pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "My Application"
include(":app")
include(":baselineprofile")

val localProperties = java.util.Properties().apply {
    val file = file("local.properties")
    if (file.exists()) load(file.inputStream())
}
val useLocalFfmpegDecoder = localProperties.getProperty("USE_LOCAL_FFMPEG_DECODER")?.equals("true", ignoreCase = true) ?: false
if (useLocalFfmpegDecoder) {
    include(":ffmpeg-decoder-downmix")
}
