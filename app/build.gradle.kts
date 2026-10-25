plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

ksp {
    // Room schema 历史存档（app/schemas），供迁移测试与版本对比
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.balsam.timetable"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.balsam.timetable"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // 调试包保持全架构、不混淆，方便开发
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 用 debug 签名，release 包也可直接安装覆盖
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    // 按 CPU 架构拆分：手机装 arm64 包可省去其余三份 OCR 原生库（约省 30MB）；
    // 跑 instrumented 测试时可加 -PdisableSplits 规避模拟器 ABI 安装冲突
    splits {
        abi {
            isEnable = !project.hasProperty("disableSplits")
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }
    sourceSets {
        // Room 迁移测试：MigrationTestHelper 从 androidTest assets 读 schema 历史存档
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.material.icons.core)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    // 截图导入的 EXIF 方向读取（android.media 版在旧 API 上有历史 bug）
    implementation(libs.androidx.exifinterface)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // 每日自动备份当前学期 JSON 到本机 Documents
    implementation(libs.work.runtime.ktx)

    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.kotlinx.serialization.json)
    // 教务网导入：解析选课结果页 HTML
    implementation(libs.jsoup)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
