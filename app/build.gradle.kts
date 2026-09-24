import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.platformtool"
    compileSdk = 34

    flavorDimensions += "distribution"

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

    productFlavors {
        create("standard") {
            dimension = "distribution"
            buildConfigField("boolean", "ENGINEERING_FEATURES", "false")
        }
        create("engineering") {
            dimension = "distribution"
            applicationIdSuffix = ".engineering"
            versionNameSuffix = "-engineering"
            buildConfigField("boolean", "ENGINEERING_FEATURES", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.appcompat) {
        // Kotlin 1.8 merged the old jdk7/jdk8 artifacts into kotlin-stdlib.
        // Exclude legacy transitive jars to avoid duplicate classes on this SDK setup.
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
