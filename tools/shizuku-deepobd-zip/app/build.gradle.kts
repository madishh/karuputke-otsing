plugins { id("com.android.application") }

android {
    namespace = "ee.tonditare.shizukucopy"
    compileSdk = 35

    signingConfigs {
        create("release") {
            storeFile = file("deepobd-release.jks")
            storePassword = "deepobd2026"
            keyAlias = "deepobd"
            keyPassword = "deepobd2026"
        }
    }

    defaultConfig {
        applicationId = "ee.tonditare.shizukucopy"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "6.0"
    }

    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("release") }
        getByName("release") { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("release") }
    }

    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { aidl = true; buildConfig = true }
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
