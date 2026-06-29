pluginManagement {
    repositories {
        maven ("https://maven.fabricmc.net/")
        maven ("https://maven.architectury.dev/")
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0")
}

include("common")
include("fabric")
//include("forge")

rootProject.name = "Railway"
