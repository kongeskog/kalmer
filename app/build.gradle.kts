import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.kongeskog.kalmer"
    compileSdk = 35
    buildToolsVersion = "34.0.0"
    defaultConfig {
        applicationId = "com.kongeskog.kalmer"
        minSdk = 31
        targetSdk = 31 // the Kompakt runs Android 12
        versionCode = 1
        versionName = "0.1.0"
    }
    // The release key stays out of the repository: its location and passwords come from keystore.properties
    // (see keystore.properties.example). Without that file, release builds are signed with the debug key.
    val keystore = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    signingConfigs {
        create("local") {
            if (keystore.isEmpty) {
                initWith(getByName("debug"))
            } else {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            // R8 matters here: unminified Compose is noticeably slow on this hardware.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("local")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint { disable += "ExpiredTargetSdkVersion" }
}

dependencies {
    implementation("com.mudita:MMD:1.0.2")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.3")
    implementation("com.caverock:androidsvg-aar:1.4") // Kalm's wallpapers are SVG
}
