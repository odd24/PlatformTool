package com.example.platformtool

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductFlavorBoundaryTest {
    @Test
    fun engineeringFlagMatchesFlavor() {
        assertEquals(
            BuildConfig.FLAVOR == "engineering",
            BuildConfig.ENGINEERING_FEATURES,
        )
    }
}
