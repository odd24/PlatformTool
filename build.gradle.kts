plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

tasks.register("ciCheck") {
    group = "verification"
    description = "Builds the app and Android test APK, then runs JVM tests and lint."
    dependsOn(
        ":app:assembleDebug",
        ":app:assembleDebugAndroidTest",
        ":app:testDebugUnitTest",
        ":app:lintDebug"
    )
}
