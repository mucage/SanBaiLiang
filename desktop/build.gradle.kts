import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

dependencies {
    implementation(project(":sync"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
}

kotlin {
    jvmToolchain(17)
}

// 只打包 app-image，不需要 WiX（仅 Windows 安装包 MSI 才用），跳过联网下载以免卡住
rootProject.tasks.matching { it.name == "downloadWix" || it.name == "unzipWix" }.configureEach {
    enabled = false
}

compose.desktop {
    application {
        mainClass = "com.mucage.rmb.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.AppImage)
            packageName = "MucageLedger"
            packageVersion = "10.27.0"
            // jpackage 在中文 Windows 下读取 UTF-8 参数文件会乱码，描述保持纯 ASCII
            description = "Mucage Ledger - manual bill and balance sync between phone and PC"
            vendor = "mucage"
        }
    }
}
