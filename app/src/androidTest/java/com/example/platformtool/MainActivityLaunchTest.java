package com.example.platformtool;

import static org.junit.Assert.assertFalse;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class MainActivityLaunchTest {

    @Rule
    public final ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void launcherActivityStarts() {
        activityRule.getScenario().onActivity(activity -> assertFalse(activity.isFinishing()));
    }
}
