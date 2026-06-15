import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room)
}

val auraMnnHomeProvider = providers
    .gradleProperty("auraMnnHome")
    .orElse(providers.environmentVariable("AURA_MNN_HOME"))

val auraMnnHomePath = auraMnnHomeProvider.orNull
val auraMnnHomeDir = auraMnnHomePath
    ?.takeIf { it.isNotBlank() }
    ?.let(::File)

val generatedMnnJniLibsDir = layout.buildDirectory.dir("generated/mnnJniLibs")
val generatedMnnJniLibsPath = File(layout.buildDirectory.asFile.get(), "generated/mnnJniLibs")
val auraMnnLibDir = auraMnnHomeDir?.let { mnnHome ->
    listOf(
        File(mnnHome, "project/android/build_64/lib"),
        File(mnnHome, "project/android/build_64"),
        File(mnnHome, "build_64/lib"),
        File(mnnHome, "build_64"),
    ).firstOrNull { File(it, "libMNN.so").isFile }
}

when {
    auraMnnHomeDir == null -> logger.lifecycle("AURA_MNN_HOME/auraMnnHome is not set; packaging Aura MNN stub only.")
    auraMnnLibDir == null -> logger.warn(
        "AURA_MNN_HOME/auraMnnHome points to ${auraMnnHomeDir.absolutePath}, " +
            "but no libMNN.so was found under project/android/build_64."
    )
    else -> logger.lifecycle("Packaging Aura MNN native libs from ${auraMnnLibDir.absolutePath}")
}

room {
    schemaDirectory("$projectDir/schemas")
}

val syncAuraMnnNativeLibs by tasks.registering(Sync::class) {
    auraMnnLibDir?.let { libDir ->
        from(libDir.absolutePath) {
            include("*.so")
        }
    }
    into(generatedMnnJniLibsDir.map { it.dir("arm64-v8a") })
}

android {
    namespace = "com.xiaoqi.companion"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.xiaoqi.companion"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 4
        versionName = "0.1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // LLM secrets are user-configured at runtime and must not be embedded in BuildConfig.
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
                auraMnnHomePath?.let { mnnHome ->
                    arguments += "-DAURA_MNN_HOME=$mnnHome"
                }
            }
        }
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // Signing: use release keystore if keystore.properties exists, otherwise fallback to debug
    val keystoreFile = rootProject.file("keystore.properties")
    if (keystoreFile.exists()) {
        val keystoreProps = Properties().apply { load(keystoreFile.inputStream()) }
        signingConfigs {
            create("release") {
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
                storeFile = file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/INDEX.LIST",
                "/META-INF/io.netty.versions.properties",
                "/META-INF/DEPENDENCIES"
            )
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.setSrcDirs(listOf("src/main/jniLibs", generatedMnnJniLibsPath))
        }
    }
}

tasks.matching {
    it.name in setOf(
        "mergeDebugJniLibFolders",
        "mergeReleaseJniLibFolders",
        "mergeDebugNativeLibs",
        "mergeReleaseNativeLibs",
    )
}
    .configureEach {
        dependsOn(syncAuraMnnNativeLibs)
    }

dependencies {
    implementation(libs.androidx.core.ktx)

    // Compose UI
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.material3)
    implementation(libs.compose.navigation)
    implementation(libs.compose.viewmodel)
    implementation(libs.compose.activity)
    implementation(libs.compose.material.icons.extended)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    // Health Connect M7: ProcessLifecycleOwner 用于"回到前台自动同步"
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    // Coroutines
    implementation(libs.coroutines.android)

    // DI
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.compiler.androidx)

    // Database
    implementation(libs.datastore.preferences)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Background
    implementation(libs.work.runtime.ktx)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Media / Animation
    implementation(libs.lottie.compose)
    implementation(libs.coil.compose)

    // Camera
    implementation(libs.bundles.camera)

    // Logging
    implementation(libs.timber)

    // Networking
    implementation(libs.okhttp)

    // Agent Framework: Koog
    implementation(libs.koog.agents)

    // Health Connect: 接小米运动健康国内版(用户已实测国内版支持 HC,详见 docs/research/health-connect-mi-fitness.md)
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.room.testing)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.compose.ui.test)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
