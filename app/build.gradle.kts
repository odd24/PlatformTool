plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.platformtool"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.platformtool"
        minSdk = 23
        targetSdk = 34
        versionCode = 30
        versionName = "1.29"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0") {
        // Kotlin 1.8 merged the old jdk7/jdk8 artifacts into kotlin-stdlib.
        // Exclude legacy transitive jars to avoid duplicate classes on this SDK setup.
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }
}
