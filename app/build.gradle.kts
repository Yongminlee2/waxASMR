plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.waxball.asmr"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.waxball.asmr"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // 손 인식 라이브러리가 아키텍처마다 네이티브 코드를 싣는다. 전부 담으면
        // x86 20.5MB, armeabi-v7a 8.1MB가 그냥 따라와 APK가 60MB를 넘는다.
        // x86은 에뮬레이터 전용이고, 요즘 폰은 전부 arm64다.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mediapipe.tasks.vision)
    testImplementation(libs.junit)
}
