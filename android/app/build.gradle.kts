// Kanji Kartları – uygulama modülü
// Android Studio şablonundaki "plugins" ve SDK satırları farklı yazılmışsa (ör. compileSdk { version = release(36) })
// şablondakini koruyabilirsin; önemli olan değerlerin aşağıdakiyle aynı olması.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.ugur.kanjikart"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ugur.kanjikart"
        minSdk = 24
        targetSdk = 36
        // Play'e her yeni yüklemede versionCode'u 1 artır.
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
    }
}

// Harici kütüphane kullanılmıyor; şablondan gelen "dependencies" bloğunu silebilirsin.
