package com.example.platformtool;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseLongArray;
import android.view.Surface;
import android.widget.Switch;
import android.widget.TextView;


import java.util.Locale;

public class SensorActivity extends PlatformActivity implements SensorEventListener {
    private static final int UI_UPDATE_INTERVAL_MS = 100;
    private static final int[] SENSOR_TYPES = {
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_LIGHT,
            Sensor.TYPE_PROXIMITY
    };

    private SensorManager sensorManager;
    private final SparseArray<Sensor> sensors = new SparseArray<>();
    private final SparseArray<TextView> valueViews = new SparseArray<>();
    private final SparseArray<TextView> infoViews = new SparseArray<>();
    private final SparseArray<Switch> switches = new SparseArray<>();
    private final SparseBooleanArray registeredSensors = new SparseBooleanArray();
    private final SparseLongArray lastUpdates = new SparseLongArray();
    private TextView statusView;
    private TextView proximityRawView;
    private GravityBallView gravityBallView;
    private int availableSensorCount;
    private boolean activityResumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor);
        setTitle(R.string.sensor_title);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        bindViews();
        inspectSensors();
        bindSwitches();
        updateStatus();
    }

    private void bindViews() {
        statusView = findViewById(R.id.sensorStatusText);
        gravityBallView = findViewById(R.id.gravityBallView);
        proximityRawView = findViewById(R.id.proximityRawValue);
        valueViews.put(Sensor.TYPE_ACCELEROMETER, findViewById(R.id.accelerometerValue));
        valueViews.put(Sensor.TYPE_GYROSCOPE, findViewById(R.id.gyroscopeValue));
        valueViews.put(Sensor.TYPE_MAGNETIC_FIELD, findViewById(R.id.magneticValue));
        valueViews.put(Sensor.TYPE_LIGHT, findViewById(R.id.lightValue));
        valueViews.put(Sensor.TYPE_PROXIMITY, findViewById(R.id.proximityValue));

        infoViews.put(Sensor.TYPE_ACCELEROMETER, findViewById(R.id.accelerometerInfo));
        infoViews.put(Sensor.TYPE_GYROSCOPE, findViewById(R.id.gyroscopeInfo));
        infoViews.put(Sensor.TYPE_MAGNETIC_FIELD, findViewById(R.id.magneticInfo));
        infoViews.put(Sensor.TYPE_LIGHT, findViewById(R.id.lightInfo));
        infoViews.put(Sensor.TYPE_PROXIMITY, findViewById(R.id.proximityInfo));

        switches.put(Sensor.TYPE_ACCELEROMETER, findViewById(R.id.accelerometerSwitch));
        switches.put(Sensor.TYPE_GYROSCOPE, findViewById(R.id.gyroscopeSwitch));
        switches.put(Sensor.TYPE_MAGNETIC_FIELD, findViewById(R.id.magneticSwitch));
        switches.put(Sensor.TYPE_LIGHT, findViewById(R.id.lightSwitch));
        switches.put(Sensor.TYPE_PROXIMITY, findViewById(R.id.proximitySwitch));
    }

    private void inspectSensors() {
        availableSensorCount = 0;
        for (int type : SENSOR_TYPES) {
            Sensor sensor = sensorManager.getDefaultSensor(type);
            TextView valueView = valueViews.get(type);
            TextView infoView = infoViews.get(type);
            Switch sensorSwitch = switches.get(type);
            if (sensor == null) {
                valueView.setText("设备未配备此传感器");
                if (type == Sensor.TYPE_PROXIMITY) {
                    proximityRawView.setText("Raw：不可用");
                }
                infoView.setText("状态：不可用");
                sensorSwitch.setChecked(false);
                sensorSwitch.setEnabled(false);
                continue;
            }
            availableSensorCount++;
            sensors.put(type, sensor);
            valueView.setText("数据上报已关闭");
            if (type == Sensor.TYPE_PROXIMITY) {
                proximityRawView.setText("Raw 数据上报已关闭");
            }
            infoView.setText(String.format(Locale.getDefault(),
                    "%s · %s\n量程 %.3f · 分辨率 %.6f · 功耗 %.2f mA",
                    sensor.getName(), sensor.getVendor(), sensor.getMaximumRange(),
                    sensor.getResolution(), sensor.getPower()));
        }
    }

    private void bindSwitches() {
        for (int type : SENSOR_TYPES) {
            Switch sensorSwitch = switches.get(type);
            sensorSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                    handleSwitchChanged(type, isChecked));
        }
    }

    private void handleSwitchChanged(int type, boolean enabled) {
        Sensor sensor = sensors.get(type);
        if (sensor == null) return;
        if (enabled) {
            valueViews.get(type).setText("等待数据…");
            if (type == Sensor.TYPE_PROXIMITY) proximityRawView.setText("等待 Raw 数据…");
            if (type == Sensor.TYPE_ACCELEROMETER) gravityBallView.setSensorEnabled(true);
            if (activityResumed) registerSensor(type);
        } else {
            unregisterSensor(type);
            valueViews.get(type).setText("数据上报已关闭");
            if (type == Sensor.TYPE_PROXIMITY) {
                proximityRawView.setText("Raw 数据上报已关闭");
            }
            if (type == Sensor.TYPE_ACCELEROMETER) gravityBallView.setSensorEnabled(false);
        }
        updateStatus();
    }

    private void registerSensor(int type) {
        if (registeredSensors.get(type)) return;
        Sensor sensor = sensors.get(type);
        if (sensor == null || !switches.get(type).isChecked()) return;
        lastUpdates.delete(type);
        boolean success = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
        registeredSensors.put(type, success);
        if (!success) {
            valueViews.get(type).setText("传感器注册失败");
            if (type == Sensor.TYPE_PROXIMITY) proximityRawView.setText("Raw：注册失败");
        }
    }

    private void unregisterSensor(int type) {
        if (!registeredSensors.get(type)) return;
        Sensor sensor = sensors.get(type);
        if (sensor != null) sensorManager.unregisterListener(this, sensor);
        registeredSensors.delete(type);
        lastUpdates.delete(type);
    }

    private void unregisterAllSensors() {
        sensorManager.unregisterListener(this);
        registeredSensors.clear();
        lastUpdates.clear();
    }

    private void updateStatus() {
        int enabledCount = 0;
        for (int type : SENSOR_TYPES) {
            Switch sensorSwitch = switches.get(type);
            if (sensorSwitch != null && sensorSwitch.isEnabled() && sensorSwitch.isChecked()) enabledCount++;
        }
        statusView.setText(String.format(Locale.getDefault(),
                "检测到 %d / %d 类目标传感器，已开启 %d 个。各类型独立控制。",
                availableSensorCount, SENSOR_TYPES.length, enabledCount));
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        for (int type : SENSOR_TYPES) {
            Switch sensorSwitch = switches.get(type);
            if (sensorSwitch != null && sensorSwitch.isChecked()) {
                valueViews.get(type).setText("等待数据…");
                if (type == Sensor.TYPE_PROXIMITY) proximityRawView.setText("等待 Raw 数据…");
                if (type == Sensor.TYPE_ACCELEROMETER) gravityBallView.setSensorEnabled(true);
                registerSensor(type);
            }
        }
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        unregisterAllSensors();
        gravityBallView.setSensorEnabled(false);
        super.onPause();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();
        Switch sensorSwitch = switches.get(type);
        if (sensorSwitch == null || !sensorSwitch.isChecked() || !registeredSensors.get(type)) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastUpdates.get(type, 0) < UI_UPDATE_INTERVAL_MS) return;
        lastUpdates.put(type, now);

        TextView view = valueViews.get(type);
        switch (type) {
            case Sensor.TYPE_ACCELEROMETER:
                view.setText(formatVector(event.values, "m/s²"));
                updateGravityBall(event.values);
                break;
            case Sensor.TYPE_GYROSCOPE:
                view.setText(formatVector(event.values, "rad/s"));
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                view.setText(formatVector(event.values, "μT"));
                break;
            case Sensor.TYPE_LIGHT:
                view.setText(String.format(Locale.getDefault(), "照度  %.3f lx", event.values[0]));
                break;
            case Sensor.TYPE_PROXIMITY:
                if (event.values.length == 0) {
                    view.setText("数据格式异常：没有 values[0]");
                    proximityRawView.setText("Raw values = []");
                    break;
                }
                Sensor proximity = sensors.get(Sensor.TYPE_PROXIMITY);
                String state = event.values[0] < proximity.getMaximumRange() ? "靠近" : "远离";
                view.setText(String.format(Locale.getDefault(),
                        "状态  %s\n距离值  %.6f cm", state, event.values[0]));
                proximityRawView.setText(formatProximityRaw(event));
                break;
            default:
                break;
        }
    }

    private static String formatVector(float[] values, String unit) {
        if (values.length < 3) return "数据格式异常";
        return String.format(Locale.getDefault(),
                "X  %9.4f %s\nY  %9.4f %s\nZ  %9.4f %s",
                values[0], unit, values[1], unit, values[2], unit);
    }

    private static String formatProximityRaw(SensorEvent event) {
        StringBuilder builder = new StringBuilder("SensorEvent Raw");
        for (int index = 0; index < event.values.length; index++) {
            builder.append("\nvalues[").append(index).append("] = ")
                    .append(Float.toString(event.values[index]));
        }
        builder.append("\naccuracy = ").append(event.accuracy)
                .append("\ntimestamp = ").append(event.timestamp).append(" ns");
        return builder.toString();
    }

    private void updateGravityBall(float[] values) {
        if (values.length < 2) return;

        // Do not use SensorManager.remapCoordinateSystem() here. Some vendor Android 14
        // frameworks incorrectly treat a 3-value accelerometer vector as a 3x3 matrix,
        // which causes ArrayIndexOutOfBoundsException when the display is rotated.
        float screenX;
        float screenY;
        float deviceX = values[0];
        float deviceY = values[1];
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_90:
                screenX = deviceY;
                screenY = -deviceX;
                break;
            case Surface.ROTATION_180:
                screenX = -deviceX;
                screenY = -deviceY;
                break;
            case Surface.ROTATION_270:
                screenX = -deviceY;
                screenY = deviceX;
                break;
            default:
                screenX = deviceX;
                screenY = deviceY;
                break;
        }
        // Accelerometer output points opposite to physical gravity. Canvas Y grows downward.
        gravityBallView.setScreenAcceleration(-screenX, screenY);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    @Override public boolean onSupportNavigateUp() { finish(); return true; }
}
