plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.valueerrorx.sleepr"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.valueerrorx.sleepr"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
    }

    // fixer Schlüssel im Repo, damit Updates ohne Deinstallation gehen (nicht für Play Store gedacht)
    signingConfigs {
        create("sleepr") {
            storeFile = file("sleepr.keystore")
            storePassword = "sleepr123"
            keyAlias = "sleepr"
            keyPassword = "sleepr123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sleepr")
        }
        debug {
            signingConfig = signingConfigs.getByName("sleepr")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}
