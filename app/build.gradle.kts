plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.fl.pos"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fl.pos"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // 固定签名：仓库内带 app/debug.keystore，保证每次 CI 构建签名一致。
    // 否则 GitHub runner 每次随机生成 ~/.android/debug.keystore，
    // 导致新 APK 与旧版签名不匹配，老板更新时必须卸载重装 —— 订单数据全丢。
    //
    // 注意：不能只覆盖 getByName("debug")，AGP 8 里那个默认配置是懒创建的，
    // 覆盖它不一定能落到 buildType 上。这里新建一个独立配置 "fixed"，
    // 再由 buildTypes.debug 显式指向它，行为确定。
    signingConfigs {
        create("fixed") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            // 没有 release keystore，复用固定的 debug 签名，保证能覆盖安装
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
