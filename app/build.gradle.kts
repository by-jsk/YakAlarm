plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yakalarm.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yakalarm.app"
        minSdk = 26
        targetSdk = 35
        // GitHub에서 빌드할 때마다 버전 번호가 자동으로 올라감
        val build = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionCode = build
        versionName = "1.$build"
    }

    // 항상 같은 서명 키를 써야 폰에서 덮어쓰기 업데이트가 됨
    // 키와 비밀번호는 GitHub 비밀 설정(Secrets)에서 받아옴
    val ksFile = file("yakalarm.keystore")
    val ksPass = System.getenv("KEYSTORE_PASSWORD")
    val hasKey = ksFile.exists() && !ksPass.isNullOrEmpty()
    signingConfigs {
        create("fixed") {
            if (hasKey) {
                storeFile = ksFile
                storePassword = ksPass
                keyAlias = "yakalarm"
                keyPassword = ksPass
            }
        }
    }

    buildTypes {
        debug {
            if (hasKey) signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            if (hasKey) signingConfig = signingConfigs.getByName("fixed")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
}
