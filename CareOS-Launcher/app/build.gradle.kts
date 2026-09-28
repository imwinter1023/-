plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.careos.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.careos.launcher"
        // 8.0 起：mipmap-anydpi-v26 自适应图标可用，老机型覆盖率高
        minSdk = 26
        // 34 而不是 35：全屏来电 Intent、状态栏着色的行为更可控；升 35 需要改成强制边到边布局
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 骨架阶段不混淆，避免把 RemoteViews / PendingIntent 相关类名的排查成本抬高
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
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // 桌面组件（App Widget）用 RemoteViews，不需要额外依赖
    // 联系人读取用 ContactsContract，属于平台 API，不需要额外依赖
}
