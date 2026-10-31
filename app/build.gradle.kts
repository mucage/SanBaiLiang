import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// 签名信息从本机的 local.properties 读取（该文件不纳入版本库，仓库里不包含密钥与口令）
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val releaseKeystore: File? = localProps.getProperty("RELEASE_STORE_FILE")?.let { rootProject.file(it) }

android {
    namespace = "com.mucage.rmb"
    compileSdk = 35
    // 使用已安装的 build-tools，避免 AGP 自动下载默认版本被沙箱拦截
    buildToolsVersion = "35.0.0"

    signingConfigs {
        create("release") {
            storeFile = releaseKeystore
            storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
            keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS") ?: "mucage"
            keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
            // 启用 v2(APK) + v3(APK) 两种签名方案（minSdk 26 ≥ 24，无需 v1）
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    defaultConfig {
        applicationId = "com.mucage.rmb"
        minSdk = 26
        targetSdk = 35
        versionCode = 102700
        versionName = "102.7.0"
    }

    buildTypes {
        release {
            // 本机存在密钥时才启用正式签名，否则构建未签名发行包（便于他人 clone 后编译）
            if (releaseKeystore?.exists() == true) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // 两端通用的账本同步模型与 JSON 编解码
    implementation(project(":sync"))
}
