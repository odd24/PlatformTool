package com.example.platformtool;

import android.os.Bundle;
import android.view.Choreographer;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;


import java.util.Locale;

public class FpsTestActivity extends PlatformActivity implements Choreographer.FrameCallback {
    private static final long SETTLE_DELAY_MS = 1_000L;
    private static final long MEASUREMENT_DURATION_NS = 3_000_000_000L;
    private static final long LIVE_UPDATE_INTERVAL_NS = 1_000_000_000L;
    private TextView fpsValue;
    private TextView displayInfo;
    private TextView sceneHint;
    private FpsSceneView sceneView;
    private RadioGroup sceneSelector;
    private Button measureButton;
    private boolean dynamicScene;
    private boolean dynamicRealtime;
    private boolean measurementActive;
    private boolean sampling;
    private long sampleStartNs;
    private long previousFrameNs;
    private int sampleFrames;
    private float animationPhase;
    private final Runnable beginSamplingRunnable = this::beginSampling;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fps_test);
        setTitle(R.string.fps_title);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        fpsValue = findViewById(R.id.fpsValue);
        displayInfo = findViewById(R.id.fpsDisplayInfo);
        sceneHint = findViewById(R.id.fpsSceneHint);
        sceneView = findViewById(R.id.fpsSceneView);
        sceneSelector = findViewById(R.id.fpsSceneSelector);
        measureButton = findViewById(R.id.fpsMeasureButton);
        sceneSelector.setOnCheckedChangeListener((group, checkedId) -> {
            stopFrameLoop();
            dynamicScene = checkedId == R.id.fpsDynamicScene;
            sceneView.setDynamic(dynamicScene);
            if (dynamicScene) {
                startDynamicRealtime();
            } else {
                showStaticMode();
            }
        });
        measureButton.setOnClickListener(v -> startMeasurement());
        dynamicScene = false;
        sceneView.setDynamic(false);
        resetResult();
        updateDisplayInfo();
    }

    @Override
    protected void onPause() {
        stopFrameLoop();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (dynamicScene && !dynamicRealtime) startDynamicRealtime();
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (dynamicRealtime) {
            updateDynamicFrame(frameTimeNanos);
            Choreographer.getInstance().postFrameCallback(this);
            return;
        }
        if (!sampling) return;
        if (sampleStartNs == 0L) sampleStartNs = frameTimeNanos;
        sampleFrames++;
        long elapsed = frameTimeNanos - sampleStartNs;
        previousFrameNs = frameTimeNanos;
        if (elapsed >= MEASUREMENT_DURATION_NS && sampleFrames > 1) {
            float fps = (sampleFrames - 1) * 1_000_000_000f / elapsed;
            float intervalMs = 1000f / Math.max(0.1f, fps);
            fpsValue.setText(String.format(Locale.getDefault(),
                    "%.1f FPS\n平均帧间隔 %.2f ms\n测量时长 %.1f 秒",
                    fps, intervalMs, elapsed / 1_000_000_000f));
            updateDisplayInfo();
            sampling = false;
            measurementActive = false;
            setControlsClickable(true);
            measureButton.setText("重新测量（静置1秒 + 测量3秒）");
            sceneHint.setText("静态画面测量完成，测量期间没有更新界面内容。");
            return;
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void startMeasurement() {
        if (measurementActive) return;
        measurementActive = true;
        setControlsClickable(false);
        // Do not change any visible property here. The one-second delay lets the
        // button press/ripple finish before the static-scene sampling begins.
        measureButton.postDelayed(beginSamplingRunnable, SETTLE_DELAY_MS);
    }

    private void beginSampling() {
        if (!measurementActive) return;
        sampling = true;
        sampleStartNs = 0L;
        previousFrameNs = 0L;
        sampleFrames = 0;
        animationPhase = 0f;
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void stopFrameLoop() {
        measurementActive = false;
        sampling = false;
        dynamicRealtime = false;
        measureButton.removeCallbacks(beginSamplingRunnable);
        Choreographer.getInstance().removeFrameCallback(this);
        setControlsClickable(true);
    }

    private void startDynamicRealtime() {
        dynamicRealtime = true;
        sampleStartNs = 0L;
        previousFrameNs = 0L;
        sampleFrames = 0;
        animationPhase = 0f;
        fpsValue.setText("正在实时测量动态画面 FPS…");
        sceneHint.setText("动态画面持续播放，FPS 每秒实时更新。");
        measureButton.setVisibility(View.GONE);
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void updateDynamicFrame(long frameTimeNanos) {
        if (sampleStartNs == 0L) sampleStartNs = frameTimeNanos;
        sampleFrames++;
        if (previousFrameNs != 0L) {
            float delta = Math.min(0.05f,
                    (frameTimeNanos - previousFrameNs) / 1_000_000_000f);
            animationPhase += delta * 2.2f;
            sceneView.setAnimationPhase(animationPhase);
        }
        previousFrameNs = frameTimeNanos;
        long elapsed = frameTimeNanos - sampleStartNs;
        if (elapsed >= LIVE_UPDATE_INTERVAL_NS && sampleFrames > 1) {
            float fps = (sampleFrames - 1) * 1_000_000_000f / elapsed;
            float intervalMs = 1000f / Math.max(0.1f, fps);
            fpsValue.setText(String.format(Locale.getDefault(),
                    "%.1f FPS\n实时平均帧间隔 %.2f ms", fps, intervalMs));
            sampleStartNs = frameTimeNanos;
            sampleFrames = 1;
        }
    }

    private void showStaticMode() {
        measureButton.setVisibility(View.VISIBLE);
        resetResult();
        sceneHint.setText("点击后静置1秒，再在界面完全不变的状态下测量3秒。");
    }

    private void resetResult() {
        fpsValue.setText("请选择画面后点击开始测量");
        measureButton.setText("开始测量（静置1秒 + 测量3秒）");
    }

    private void setControlsClickable(boolean clickable) {
        measureButton.setClickable(clickable);
        for (int i = 0; i < sceneSelector.getChildCount(); i++) {
            View child = sceneSelector.getChildAt(i);
            child.setClickable(clickable);
        }
    }

    private void updateDisplayInfo() {
        Display display = getWindowManager().getDefaultDisplay();
        float refreshRate = display.getRefreshRate();
        Display.Mode mode = display.getMode();
        displayInfo.setText(String.format(Locale.getDefault(),
                "系统报告刷新率：%.1f Hz   分辨率：%d × %d   模式 ID：%d",
                refreshRate, mode.getPhysicalWidth(), mode.getPhysicalHeight(), mode.getModeId()));
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }
}
