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
        // 콘솔은 같은 번호를 두 번 받지 않는다. 내용을 고쳐 다시 올릴 때마다 올린다.
        // 1번 인터넷 권한이 남아 있던 빌드 (콘솔이 받아 감)
        // 2번 권한을 걷어낸 빌드 (비공개 테스트로 출시됨)
        // 3번 쥐는 손맛 재조정 + 최대 광각 카메라 (16KB 미지원으로 반려)
        // 4번 16KB 메모리 페이지 대응 (CameraX·MediaPipe 올림)
        // 5번 광고를 보고 볼 해금 (처음 3개만 무료)
        // 6번 실제 광고 ID, 배너·전면 광고 추가
        versionCode = 6
        versionName = "1.0"

        // 광고 ID. 기본은 구글이 공개한 테스트 ID 이고 release 만 실제 ID 로 덮어쓴다.
        // 개발 중에 실제 광고를 누르면 애드몹이 무효 클릭으로 보고 계정을 정지할 수 있다.
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
        buildConfigField("String", "AD_BANNER", "\"ca-app-pub-3940256099942544/9214589741\"")
        buildConfigField("String", "AD_INTERSTITIAL", "\"ca-app-pub-3940256099942544/1033173712\"")
        buildConfigField("String", "AD_REWARDED", "\"ca-app-pub-3940256099942544/5224354917\"")

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

            // 실제 광고 ID (애드몹 콘솔의 왁뿌볼 앱)
            manifestPlaceholders["admobAppId"] = "ca-app-pub-6583185616347720~5243185363"
            buildConfigField("String", "AD_BANNER", "\"ca-app-pub-6583185616347720/5801674286\"")
            buildConfigField("String", "AD_INTERSTITIAL", "\"ca-app-pub-6583185616347720/2960811966\"")
            buildConfigField("String", "AD_REWARDED", "\"ca-app-pub-6583185616347720/1647730295\"")

            // 콘솔이 "네이티브 디버그 기호가 없다"고 경고하지만 없앨 수 없다.
            // debugSymbolLevel 을 켜 봐도 아무것도 안 실린다 — 구글이 배포하는
            // libmediapipe_tasks_vision_jni.so 가 이미 스트립되어 .symtab 도
            // 디버그 섹션도 없기 때문이다. 우리가 짠 네이티브 코드는 없다.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
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
    implementation(libs.play.services.ads)
    testImplementation(libs.junit)
}
