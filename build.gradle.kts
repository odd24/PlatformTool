plugins {
    id("com.android.application") version "8.5.1" apply false
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
