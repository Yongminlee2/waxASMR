import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

/**
 * 출시 서명 키. 루트의 `keystore.properties` 가 있을 때만 읽는다.
 *
 * 키 파일과 비밀번호는 저장소에 넣지 않는다(.gitignore). 새어 나가면 남이 내 앱
 * 이름으로 업데이트를 올릴 수 있고, 잃어버리면 내가 다시는 업데이트를 못 올린다.
 */
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "com.waxball.asmr"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.waxball.asmr"
        minSdk = 26
        targetSdk = 36
        // 1번은 인터넷 권한이 남아 있던 빌드로 콘솔에 이미 올라갔다. 콘솔은 같은
        // 번호를 두 번 받지 않으므로, 내용을 고쳐 다시 올릴 때마다 이 값을 올린다.
        versionCode = 2
        versionName = "1.0"

        // 손 인식 라이브러리가 아키텍처마다 네이티브 코드를 싣는다. 전부 담으면
        // x86 20.5MB, armeabi-v7a 8.1MB가 그냥 따라와 APK가 60MB를 넘는다.
        // x86은 에뮬레이터 전용이고, 요즘 폰은 전부 arm64다.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 코드 축소는 꺼 둔다. APK 57MB 중 34MB가 소리·모델 자산이라 줄여 봐야
            // 얼마 안 되는데, MediaPipe가 리플렉션으로 부르는 클래스가 지워지면
            // 손 인식이 조용히 죽는다. 그건 설치해 보기 전에는 모른다.
            isMinifyEnabled = false
            // 키가 없으면 서명을 붙이지 않는다. 디버그 키로 서명하면 겉보기에는
            // 릴리즈 빌드가 되지만 스토어가 받아 주지 않는다.
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else null
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
