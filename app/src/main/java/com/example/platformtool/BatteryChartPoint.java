package com.example.platformtool;

final class BatteryChartPoint {
    final long timestamp;
    final float level;
    final float current;
    final float voltage;
    final float temperature;

    BatteryChartPoint(long timestamp, float level, float current,
                      float voltage, float temperature) {
        this.timestamp = timestamp;
        this.level = level;
        this.current = current;
        this.voltage = voltage;
        this.temperature = temperature;
    }
}
