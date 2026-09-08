package com.example.platformtool;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(R.string.app_name);

        View.OnClickListener openAudio = v ->
                startActivity(new Intent(this, AudioPlayerActivity.class));
        View.OnClickListener openVideo = v ->
                startActivity(new Intent(this, VideoLibraryActivity.class));
        View.OnClickListener openSerial = v ->
                startActivity(new Intent(this, SerialPortActivity.class));
        View.OnClickListener openSensors = v ->
                startActivity(new Intent(this, SensorActivity.class));
        View.OnClickListener openLogs = v ->
                startActivity(new Intent(this, RootLogActivity.class));
        View.OnClickListener openFps = v ->
                startActivity(new Intent(this, FpsTestActivity.class));
        View.OnClickListener openQuickTools = v ->
                startActivity(new Intent(this, QuickToolsActivity.class));
        View.OnClickListener openBatteryInfo = v ->
                startActivity(new Intent(this, BatteryInfoActivity.class));
        View.OnClickListener openConsole = v ->
                startActivity(new Intent(this, ConsoleActivity.class));

        findViewById(R.id.audioCard).setOnClickListener(openAudio);
        findViewById(R.id.openAudioButton).setOnClickListener(openAudio);
        findViewById(R.id.videoCard).setOnClickListener(openVideo);
        findViewById(R.id.openVideoButton).setOnClickListener(openVideo);
        findViewById(R.id.serialCard).setOnClickListener(openSerial);
        findViewById(R.id.openSerialButton).setOnClickListener(openSerial);
        findViewById(R.id.sensorCard).setOnClickListener(openSensors);
        findViewById(R.id.openSensorButton).setOnClickListener(openSensors);
        findViewById(R.id.logCard).setOnClickListener(openLogs);
        findViewById(R.id.openLogButton).setOnClickListener(openLogs);
        findViewById(R.id.fpsCard).setOnClickListener(openFps);
        findViewById(R.id.openFpsButton).setOnClickListener(openFps);
        findViewById(R.id.quickToolsCard).setOnClickListener(openQuickTools);
        findViewById(R.id.openQuickToolsButton).setOnClickListener(openQuickTools);
        findViewById(R.id.batteryCard).setOnClickListener(openBatteryInfo);
        findViewById(R.id.openBatteryButton).setOnClickListener(openBatteryInfo);
        findViewById(R.id.consoleCard).setOnClickListener(openConsole);
        findViewById(R.id.openConsoleButton).setOnClickListener(openConsole);
    }
}
