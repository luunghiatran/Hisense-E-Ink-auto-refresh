import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.liziwa.hisense_autorefresh"
    compileSdk = 36

    signingConfigs {
        create("release") {
            storeFile = file("keystore.jks")
            storePassword = "liziwa"
            keyAlias = "android"
            keyPassword = "liziwa"
        }
    }

    defaultConfig {
        applicationId = "com.liziwa.hisense_autorefresh"
        minSdk = 28
        targetSdk = 30
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    dataBinding {
        enable = true
    }

    lint {
        // 禁用 ExpiredTargetSdkVersion 检查
        disable.add("ExpiredTargetSdkVersion")
    }

    // 修复文件名修改
    applicationVariants.all {
        val variant = this
        variant.outputs.forEach { output ->
            val outputImpl = output as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            outputImpl.outputFileName = buildString {
                append("${variant.applicationId}")
                append("_v${variant.versionName}")
                append("(${variant.versionCode})")
                append("_${variant.buildType.name}")
                append("_${getDateTime()}.apk")
            }
        }
    }
}

// 获取时间戳的函数
fun getDateTime(): String {
    return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.kotlinx.coroutines.android)
}