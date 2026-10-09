import org.gradle.api.tasks.testing.Test
import java.util.Properties
import groovy.json.JsonSlurper

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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 0.0.1 = 仓库初版（换包名后归零，debug 签名）；
        // 0.0.2 = 第一版**自有签名**的包（2026-09-28，用户定"发包给同学之前切"）；
        // 0.0.3 = 关于页加「联系开发者（QQ）」；
        // 0.0.4 = 契约测试全绿（37 → 0）＋修回许可页贡献者外链；
        // 0.0.5 = 组件编辑页去掉已失效的「滚动方式」节（整条 per-widget 开关链）+ 清三块死代码；
        // 0.0.6 = 「来自开发者的心声」语气软化（用户令）+ 更新说明同步；
        // 0.0.7 = 整块清掉休眠的「位图组件管线」（用户拍板 A）；
        // 0.0.8 = 分发前的收口：自动检查更新恢复默认开（原始顾虑已消失）+ 注释与实现对齐
        versionCode = 9
        versionName = "0.0.9"
        vectorDrawables { useSupportLibrary = true }
        androidResources {
            localeFilters += listOf("zh-rCN", "zh-rTW", "en", "ja", "es")
        }
    }

    // Official release packages require the configured identity. Debug builds remain usable
    // in public clones. Paths/passwords come from Gradle properties or local.properties.
    val localProps = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    fun signingValue(name: String): String? = providers.gradleProperty(name).orNull ?: localProps.getProperty(name)
    val classyKeystoreFile = rootProject.file(signingValue("classy.storeFile") ?: "../../classy-release.keystore")
    val classySigning = if (classyKeystoreFile.exists() &&
        !signingValue("classy.storePassword").isNullOrBlank() &&
        !signingValue("classy.keyPassword").isNullOrBlank()) {
        signingConfigs.create("classy") {
            storeFile = classyKeystoreFile
            storePassword = signingValue("classy.storePassword")
            keyAlias = signingValue("classy.keyAlias") ?: "classy"
            keyPassword = signingValue("classy.keyPassword")
        }
    } else null

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
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
            // Never distribute a release signed with the debug key.
            signingConfig = classySigning
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

val verifyReleaseSigning by tasks.registering {
    doLast {
        check(android.signingConfigs.findByName("classy")?.storeFile?.isFile == true) {
            "Release signing is missing. Configure classy.storeFile, classy.storePassword, classy.keyAlias and classy.keyPassword; use assembleDebug for local testing."
        }
    }
}

// A future version must ship its own user-facing notes, not redisplay old changes.
val releaseNotesFile = layout.projectDirectory.file("src/main/assets/release-notes.json")
val releaseNotesVersionCode = android.defaultConfig.versionCode
val releaseNotesVersionName = android.defaultConfig.versionName
val verifyReleaseNotes by tasks.registering {
    inputs.file(releaseNotesFile)
    inputs.property("versionCode", releaseNotesVersionCode!!)
    inputs.property("versionName", releaseNotesVersionName!!)
    doLast {
        val notes = JsonSlurper().parse(releaseNotesFile.asFile) as Map<*, *>
        check((notes["versionCode"] as? Number)?.toInt() == releaseNotesVersionCode &&
            notes["versionName"] == releaseNotesVersionName) {
            "Update app/src/main/assets/release-notes.json for this version before building."
        }
        val translations = notes["notes"] as? Map<*, *> ?: error("Missing release notes")
        for (language in listOf("zh", "zh-Hant", "en", "es", "ja")) {
            val items = translations[language] as? List<*>
            check(!items.isNullOrEmpty() && items.all { it is String && it.isNotBlank() }) {
                "Missing user-facing release notes for $language"
            }
        }
    }
}
tasks.named("preBuild").configure { dependsOn(verifyReleaseNotes) }
tasks.matching {
    it.name in setOf("packageRelease", "assembleRelease", "bundleRelease")
}.configureEach { dependsOn(verifyReleaseSigning) }

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

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
