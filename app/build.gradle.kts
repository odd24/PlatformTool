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
        versionCode = 33
        versionName = "1.32"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
