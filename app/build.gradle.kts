import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val barkVersionName = providers.gradleProperty("barkVersionName").get()
val barkVersionCode = providers.gradleProperty("barkVersionCode").get().toInt()

android {
    namespace = "com.lanbing.smsforwarder"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lanbing.smsforwarder"
        minSdk = 21
        targetSdk = 34

        // 1) 优先读取 Gradle property，再读取环境变量；没有时回退到默认值
        val versionNameFromProp = (project.findProperty("VERSION_NAME") ?: System.getenv("VERSION_NAME"))?.toString()
        val versionCodeFromProp = (project.findProperty("VERSION_CODE") ?: System.getenv("VERSION_CODE"))?.toString()

        versionName = versionNameFromProp ?: barkVersionName
        versionCode = (versionCodeFromProp?.toIntOrNull() ?: barkVersionCode)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appLabel"] = "短信转发助手"
    }

    // ---------- 新增：从环境变量或 gradle properties 加载签名信息 ----------
    // 优先级：ENV > project property > 本地 my-release-key.jks（便于本地测试）
    signingConfigs {
        create("bark") {
            val propertiesFile = rootProject.file("bark-signing.properties")
            val properties = Properties()
            if (propertiesFile.exists()) {
                propertiesFile.inputStream().use { properties.load(it) }
            }
            // CI uses environment variables; local builds can restore the private backup file.
            val keystorePath = System.getenv("KEYSTORE_FILE") ?: properties.getProperty("storeFile")
            if (!keystorePath.isNullOrBlank()) storeFile = rootProject.file(keystorePath)
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: properties.getProperty("storePassword")
            keyAlias = System.getenv("KEY_ALIAS") ?: properties.getProperty("keyAlias")
            keyPassword = System.getenv("KEY_PASSWORD") ?: properties.getProperty("keyPassword")
        }
        create("release") {
            val keystoreFileEnv = System.getenv("KEYSTORE_FILE")
            val keystoreFileProp = project.findProperty("KEYSTORE_FILE")?.toString()
            val keystoreFilePath = keystoreFileEnv ?: keystoreFileProp

            var hasStoreFile = false
            if (keystoreFilePath != null) {
                val keystoreFile = file(keystoreFilePath)
                if (keystoreFile.exists()) {
                    storeFile = keystoreFile
                    hasStoreFile = true
                }
            } else {
                val localKeystore = rootProject.file("my-release-key.jks")
                if (localKeystore.exists()) {
                    storeFile = localKeystore
                    hasStoreFile = true
                }
            }

            // 只有当 storeFile 存在时，才设置其他签名属性
            if (hasStoreFile) {
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: project.findProperty("KEYSTORE_PASSWORD")?.toString()
                keyAlias = System.getenv("KEY_ALIAS") ?: project.findProperty("KEY_ALIAS")?.toString()
                keyPassword = System.getenv("KEY_PASSWORD") ?: project.findProperty("KEY_PASSWORD")?.toString()
            }
        }
    }
    // ----------------------------------------------------------------------

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 只有当 release 签名配置完整时，debug 才使用相同签名
            val releaseConfig = signingConfigs.findByName("release")
            if (releaseConfig != null && releaseConfig.storeFile != null) {
                signingConfig = releaseConfig
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        create("bark") {
            initWith(getByName("release"))
            applicationIdSuffix = ".bark"
            versionNameSuffix = "-bark.$barkVersionCode"
            manifestPlaceholders["appLabel"] = "短信转发助手 Bark"
            signingConfig = signingConfigs.getByName("bark")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests.isIncludeAndroidResources = true }

    // 与下面的 Compose 版本保持一致
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.5"
    }
    // 排除冗余的 META-INF / license 文件，减小 APK 体积
    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/*.kotlin_module",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/license.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/versions/**",
                "/kotlin/**",
                "/kotlin-tooling-metadata.json"
            )
        }
    }
}

// Never silently make a replacement signing key for a Bark release.
tasks.matching { it.name == "preBarkBuild" || it.name == "validateSigningBark" }.configureEach {
    doFirst {
        val config = android.signingConfigs.getByName("bark")
        check(config.storeFile?.isFile == true && !config.storePassword.isNullOrBlank() &&
            !config.keyAlias.isNullOrBlank() && !config.keyPassword.isNullOrBlank()) {
            "Restore the original Bark signing backup locally, or configure the KEYSTORE_* environment variables in CI."
        }
    }
}

// 调试用：打印当前 versionName/versionCode
tasks.register("printVersion") {
    doLast {
        println("versionName = ${android.defaultConfig.versionName}")
        println("versionCode = ${android.defaultConfig.versionCode}")
    }
}

configurations.all {
    exclude(group = "com.infinitus", module = "inview")
    exclude(group = "com.inview", module = "*")
    exclude(group = "*", module = "inview")
    exclude(group = "*", module = "*inview*")
    exclude(group = "*inview*", module = "*")
}

dependencies {
    // 明确版本，避免 BOM 注入失败引起的问题
    val composeUiVersion = "1.6.1"
    val activityComposeVersion = "1.9.0"
    val material3Version = "1.2.1"
    val lifecycleRuntime = "2.7.0"
    val okhttpVersion = "4.12.0"

    // Compatible with this project's Compose 1.6.x; GPU backdrop blur on Android API32+.
    implementation("dev.chrisbanes.haze:haze:0.5.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleRuntime")
    implementation("androidx.activity:activity-compose:$activityComposeVersion")

    // Compose UI / graphics / tooling (明确 artifact 名称和版本)
    implementation("androidx.compose.ui:ui:$composeUiVersion")
    implementation("androidx.compose.ui:ui-graphics:$composeUiVersion")
    implementation("androidx.compose.ui:ui-tooling-preview:$composeUiVersion")
    debugImplementation("androidx.compose.ui:ui-tooling:$composeUiVersion")

    implementation("androidx.compose.material3:material3:$material3Version")

    // Icons (material icons extended) — 添加以解决 Icons.Default.History / Message 等引用
    implementation("androidx.compose.material:material-icons-extended:$composeUiVersion")

    // Fragment (required for ActivityResultLauncher)
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.compose.ui:ui-test-junit4:1.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.6.1")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:$okhttpVersion")


}
