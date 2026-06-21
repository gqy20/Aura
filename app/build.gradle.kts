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

// ---------------------------------------------------------------------------
// Brand single source of truth — see brand.properties at repo root.
// CLI override: -PBRAND_NAME=... or env var BRAND_NAME=...
// ---------------------------------------------------------------------------
val brandProps = Properties().apply {
    val f = rootProject.file("brand.properties")
    if (f.exists()) {
        f.inputStream().use { load(it) }
        logger.lifecycle("Loaded brand.properties from ${f.absolutePath}")
    } else {
        logger.warn("brand.properties not found at ${f.absolutePath}; falling back to inline defaults.")
    }
}
fun brand(key: String, default: String): String =
    providers.gradleProperty("BRAND_${key}").orNull
        ?: brandProps.getProperty("BRAND_${key}")
        ?: default

val brandName: String = brand("NAME", "Aura")
val brandDisplayName: String = brand("DISPLAY_NAME", brandName)
val brandPackage: String = brand("PACKAGE", "com.xiaoqi.companion")
val brandVersionName: String = brand("VERSION_NAME", "0.1.3")
val brandVersionCode: String = brand("VERSION_CODE", "4")

// ---------------------------------------------------------------------------
// Local dev/.env config — debug-only BuildConfig injection.
// .env is gitignored; copy .env.example -> .env and fill your keys.
// 消费链：.env -> envProps -> BuildConfig.ENV_* -> DebugConfigSeeder 预填 DataStore
// ---------------------------------------------------------------------------
val envProps = Properties().apply {
    val f = rootProject.file(".env")
    if (f.exists()) {
        // Properties.load(InputStream) 默认 ISO-8859-1，会让 UTF-8 中文（如"麦当劳""瑞幸"）乱码；
        // 显式用 UTF-8 Reader 读，保证 server 名等中文正确注入 BuildConfig。
        f.inputStream().reader(Charsets.UTF_8).use { load(it) }
        logger.lifecycle("Loaded .env from ${f.absolutePath}")
    } else {
        logger.warn(".env not found at ${f.absolutePath}; debug BuildConfig.ENV_* will be empty.")
    }
}
fun env(key: String, default: String = ""): String =
    providers.environmentVariable(key).orNull ?: envProps.getProperty(key) ?: default

// buildConfigField String 值需要把 \ 和 " 转义，避免 API key / URL 里的特殊字符破坏生成代码
fun escForBuildConfig(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")

val auraMnnHomeProvider = providers
    .gradleProperty("auraMnnHome")
    .orElse(providers.environmentVariable("AURA_MNN_HOME"))
    // 本机默认 MNN 仓库位置(Windows 资源管理器视角是大写 MNN,NTFS 大小写不敏感,小写也能用)。
    // 显式 > 隐式,被前面两个 source 覆盖。
    .orElse("D:/C/Desktop/ai/MNN")

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
    namespace = brandPackage
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = brandPackage
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = brandVersionCode.toInt()
        versionName = brandVersionName

        // Override launcher label so brand.properties is the single source of truth.
        resValue("string", "app_name", brandDisplayName)

        // Expose brand metadata to runtime code (see com.xiaoqi.companion.BuildConfig).
        buildConfigField("String", "BRAND_NAME", "\"$brandDisplayName\"")
        buildConfigField("String", "BRAND_PACKAGE", "\"$brandPackage\"")
        buildConfigField("String", "BRAND_VERSION_NAME", "\"$brandVersionName\"")
        buildConfigField("int",    "BRAND_VERSION_CODE", brandVersionCode)

        // .env -> BuildConfig.ENV_*：defaultConfig 给空占位，保证 main 源码（DebugConfigSeeder）
        // 在 release 编译也能找到符号；debug buildType 用 .env 真实值覆盖。release 不含敏感值。
        buildConfigField("boolean", "ENV_FORCE_SEED", "false")
        buildConfigField("String", "ENV_LLM_PROVIDER", "\"GLM\"")
        buildConfigField("String", "ENV_LLM_API_KEY", "\"\"")
        buildConfigField("String", "ENV_LLM_MODEL", "\"\"")
        buildConfigField("String", "ENV_LOCAL_QWEN_MODEL", "\"\"")
        buildConfigField("String", "ENV_MCP_AMAP_KEY", "\"\"")
        for (i in 1..6) {
            buildConfigField("String", "ENV_MCP_CUSTOM_${i}_NAME", "\"\"")
            buildConfigField("String", "ENV_MCP_CUSTOM_${i}_URL", "\"\"")
            buildConfigField("String", "ENV_MCP_CUSTOM_${i}_TOKEN", "\"\"")
        }

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

            // .env -> BuildConfig.ENV_*（仅 debug；release 不注入敏感配置）
            val forceSeed = env("ENV_FORCE_SEED", "false").trim().equals("true", ignoreCase = true)
            buildConfigField("boolean", "ENV_FORCE_SEED", forceSeed.toString())
            buildConfigField("String", "ENV_LLM_PROVIDER", "\"${escForBuildConfig(env("LLM_PROVIDER", "GLM"))}\"")
            buildConfigField("String", "ENV_LLM_API_KEY", "\"${escForBuildConfig(env("LLM_API_KEY"))}\"")
            buildConfigField("String", "ENV_LLM_MODEL", "\"${escForBuildConfig(env("LLM_MODEL"))}\"")
            buildConfigField("String", "ENV_LOCAL_QWEN_MODEL", "\"${escForBuildConfig(env("LOCAL_QWEN_MODEL"))}\"")
            buildConfigField("String", "ENV_MCP_AMAP_KEY", "\"${escForBuildConfig(env("MCP_AMAP_API_KEY"))}\"")
            for (i in 1..6) {
                buildConfigField("String", "ENV_MCP_CUSTOM_${i}_NAME", "\"${escForBuildConfig(env("MCP_CUSTOM_${i}_NAME"))}\"")
                buildConfigField("String", "ENV_MCP_CUSTOM_${i}_URL", "\"${escForBuildConfig(env("MCP_CUSTOM_${i}_URL"))}\"")
                buildConfigField("String", "ENV_MCP_CUSTOM_${i}_TOKEN", "\"${escForBuildConfig(env("MCP_CUSTOM_${i}_TOKEN"))}\"")
            }
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // 让未被 mock 的 Android API 返默认值(0/false/null) 而不是抛 "not mocked!"
            // 这样纯 JVM 测试不需要为一句 Context.getString 付出 Robolectric 启动成本
            isReturnDefaultValues = true

            // 并行跑测试类 —— 每类独立 JVM fork,避免 Robolectric static state 互相干扰
            // CI 跟本地开发都能提速(本机 4 workers,上限于 gradle.workers.max)
            all {
                it.maxParallelForks = Runtime.getRuntime().availableProcessors().coerceAtMost(4)
                it.forkEvery = 0  // 同一 fork 复用 JVM,减少启动次数
                it.systemProperty("robolectric.invokedynamic", "true")
            }
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
        resValues = true
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
    implementation(libs.androidx.sqlite.bundled)
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
    androidTestImplementation(libs.androidx.sqlite.bundled)
}
