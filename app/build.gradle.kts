import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    // alias(libs.plugins.kotlin.android) // No longer required in AGP 9.0+
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
        versionCode = 6
        versionName = "1.3.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        androidResources {
            localeFilters += listOf("en", "vi", "zh-rHK", "zh-rTW")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true   // 启用代码混淆
            isShrinkResources = true // 启用资源压缩
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
    
    // Kotlin options are now handled by AGP 9.0+ or via kotlin extension
    
    buildFeatures {
        dataBinding = true
    }

    lint {
        // 禁用 ExpiredTargetSdkVersion 检查
        disable.add("ExpiredTargetSdkVersion")
    }

    // 修复文件名修改 - TODO: Migrate to androidComponents API for AGP 9.0+
    /*
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
    */
}

// 获取时间戳的函数
fun getDateTime(): String {
    return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.material)
    implementation(libs.xlog)
}
