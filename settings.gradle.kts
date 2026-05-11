pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
    }
    plugins {
        id("com.android.application") version "9.2.0"
        id("org.jetbrains.kotlin.android") version "2.3.21"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
        id("org.jetbrains.kotlin.kapt") version "2.3.21"
        id("com.google.dagger.hilt.android") version "2.59.2"
        id("androidx.room") version "2.8.4"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.aliyun.com/repository/public")
    }
}

rootProject.name = "companion"
include(":app")
