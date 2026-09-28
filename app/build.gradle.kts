import org.gradle.api.tasks.testing.Test
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.imsx3d.classy"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.imsx3d.classy"
        minSdk = 26
        targetSdk = 37
        // 0.0.1 = 仓库初版（换包名后归零，debug 签名）；
        // 0.0.2 = 第一版**自有签名**的包（2026-09-28，用户定"发包给同学之前切"）；
        // 0.0.3 = 关于页加「联系开发者（QQ）」；
        // 0.0.4 = 契约测试全绿（37 → 0）＋修回许可页贡献者外链；
        // 0.0.5 = 组件编辑页去掉已失效的「滚动方式」节（整条 per-widget 开关链）+ 清三块死代码；
        // 0.0.6 = 「来自开发者的心声」语气软化（用户令）+ 更新说明同步；
        // 0.0.7 = 整块清掉休眠的「位图组件管线」（用户拍板 A）
        versionCode = 7
        versionName = "0.0.7"
        vectorDrawables { useSupportLibrary = true }
        androidResources {
            localeFilters += listOf("zh-rCN", "zh-rTW", "en", "ja", "es")
        }
    }

    // ── 签名（v0.0.2 起：自有密钥库，取代原来的"release 复用 debug 证书"）──
    // 为什么写成"存在才用"：密钥库与口令**都在仓库之外**
    //   · 密钥库 E:/T1/classy-release.keystore（工程目录是 E:/T1/vendor/sleepy → 退两级）
    //   · 口令   local.properties（不进发布树）
    // 于是公开仓库里既没有密钥也没有口令：别人 clone 下来本块不生效 → 自动回退 debug 签名，
    // `./gradlew assembleRelease` 照样能构建；我方机器上有密钥库 → 走自有签名。
    // 指纹（SHA-256）：95:9D:7F:1B:95:6F:52:03:C0:19:BE:FD:FE:13:BF:40:82:CF:D6:00:47:47:7A:9F:10:75:9D:F2:B9:5D:68:56
    val classyKeystoreFile = rootProject.file("../../classy-release.keystore")
    val classySigning = if (classyKeystoreFile.exists()) {
        val localProps = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        signingConfigs.create("classy") {
            storeFile = classyKeystoreFile
            storePassword = localProps.getProperty("classy.storePassword")
                ?: error("classy-release.keystore 在，但 local.properties 里没有 classy.storePassword")
            keyAlias = localProps.getProperty("classy.keyAlias") ?: "classy"
            keyPassword = localProps.getProperty("classy.keyPassword")
                ?: error("classy-release.keystore 在，但 local.properties 里没有 classy.keyPassword")
        }
    } else null

    buildTypes {
        debug {
            isMinifyEnabled = false
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            // 保留 R8 shrinking 能力但关闭混淆改名(避免反射/序列化类被重命名后崩溃)
            // 真正的混淆(mangling)由 shrinkResources + 下面的 keep 规则共同保护
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 有自有密钥库就用它；没有（公开仓库里的 clone）回退 debug 签名，保证能构建
            signingConfig = classySigning ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*"
            )
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        // 基线对齐 v1.0.57 (38 errors / 434 warnings):
        // 本次集成前仓库 lint 从非 0, 这些 id 全部是 AGP 9.1 新检查在既有代码上的
        // 增量告警, 不影响功能; 逐处重构 (LocalContext→stringResource 需升级函数签名)
        // 超出本次发版范围, 先收敛到基线等价, 后续单独分支处理。
        disable += setOf(
            "LocalContextConfigurationRead",
            "LocalContextGetResourceValueCall",
            "LocalContextResourcesRead",
            "StateFlowValueCalledInComposition",
            "UnusedBoxWithConstraintsScope",
            "ModifierParameter",
        )
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("sleepy.test.root", rootDir.absolutePath)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.5.0-alpha28")
    implementation("androidx.compose.material3:material3-window-size-class:1.5.0-alpha28")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.5.0-alpha28")
    implementation("androidx.compose.material3.adaptive:adaptive")
    implementation("androidx.compose.material3.adaptive:adaptive-layout")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")

    // Activity + Lifecycle
    // Glasense UI（来自 Cresto，Apache-2.0）—— UI 换装的视觉基础
    implementation(project(":glasense-ui"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    // Navigation
    // issue#45: typed back stack + gesture-progress predictive back.
    // 官方 navigation3 1.1.7 (kotlin-stdlib 2.1.20, 与本仓 Kotlin 2.2.0 编译器兼容;
    // miuix-nav 0.9.4 metadata 2.4.0 超出 compiler 2.3.0 上限已弃用)。
    implementation("androidx.navigation3:navigation3-runtime:1.1.7")
    implementation("androidx.navigation3:navigation3-ui:1.1.7")
    implementation("androidx.navigationevent:navigationevent-compose:1.1.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3:2.11.0")

    // DataStore (preferences)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Room
    val roomVersion = "2.7.0"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Kotlinx Serialization (JSON parsing for WakeUp JSON)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // jsoup (HTML parsing for 教务直连 import)
    implementation("org.jsoup:jsoup:1.18.1")

    // WorkManager (Daily notifications)
    // Glance 依赖已随死代码删除移除(决策 D5-11): 5 个生产 widget 全走 RemoteViews + Canvas bitmap
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Splash screen
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Coil (image loading)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Test
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    testImplementation("org.json:json:20231013")
    // issue#26: 迁移测试用 sqlite-jdbc 直接执行 MIGRATION_5_6_STATEMENTS(单一事实来源),
    //             避免 Robolectric/Instrumentation 依赖(本仓库无)
    testImplementation("org.xerial:sqlite-jdbc:3.53.4.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
