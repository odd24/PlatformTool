plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

tasks.register("ciCheck") {
    group = "verification"
    description = "Builds both product flavors and test APKs, then runs JVM tests and lint."
    dependsOn(
        ":app:assembleStandardDebug",
        ":app:assembleEngineeringDebug",
        ":app:assembleStandardDebugAndroidTest",
        ":app:assembleEngineeringDebugAndroidTest",
        ":app:testStandardDebugUnitTest",
        ":app:testEngineeringDebugUnitTest",
        ":app:lintStandardDebug",
        ":app:lintEngineeringDebug"
    )
}
