plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.com.controleparental"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.com.controleparental"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Para gerar um APK de release instalável, configure uma assinatura
            // (Build > Generate Signed App Bundle / APK no Android Studio).
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

// Sem dependências externas: o app usa apenas o framework Android.
