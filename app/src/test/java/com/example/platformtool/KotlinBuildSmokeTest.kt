package com.example.platformtool

import org.junit.Assert.assertEquals
import org.junit.Test

class KotlinBuildSmokeTest {
    @Test
    fun kotlinCompilerIsAvailable() {
        assertEquals("PlatformTool", listOf("Platform", "Tool").joinToString(separator = ""))
    }
}
