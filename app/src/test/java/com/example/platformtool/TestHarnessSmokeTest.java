package com.example.platformtool;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public final class TestHarnessSmokeTest {

    @Test
    public void javaRuntimeIsAvailable() {
        assertNotNull(System.getProperty("java.version"));
    }
}
