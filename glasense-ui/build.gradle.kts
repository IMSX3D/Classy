// Glasense UI —— 来自 Cresto（https://github.com/Nevodev/Cresto）的独立设计系统模块，Apache-2.0。
// 为接入本工程所做的改动：去掉版本目录（libs.*）改用显式坐标、minSdk 对齐到 26、移除测试配置。
// 玻璃效果（RenderEffect / RuntimeShader）原模块已按 SDK≥33 做守卫，低版本自动降级为普通材质。
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nevoit.glasense"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// 与原模块一致：本设计系统不依赖 material3（避免 M3 主题渗入 Glasense 视觉）
configurations.configureEach {
    exclude(group = "androidx.compose.material3")
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    // 平滑圆角与毛玻璃底层（与原项目同版本）
    implementation("io.github.kyant0:shapes:1.2.0")
    implementation("io.github.kyant0:backdrop:2.0.0")
}
