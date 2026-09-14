plugins {
    id("com.android.application")
}

android {
    namespace = "ee.tonditare.shizukucopy"
    compileSdk = 35

    defaultConfig {
        applicationId = "ee.tonditare.shizukucopy"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "3.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
