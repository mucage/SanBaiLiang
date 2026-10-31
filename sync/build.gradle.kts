plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    // 纯 Kotlin/JVM，无第三方依赖；JSON 编解码内置实现，供安卓端与桌面端共用
}

kotlin {
    jvmToolchain(17)
}
