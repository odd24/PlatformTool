package com.example.platformtool;

import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BatteryChartActivity extends PlatformActivity {
    public static final String EXTRA_CSV_URI = "battery_csv_uri";

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private TextView statusView;
    private BatteryChartView chartView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battery_chart);
        setTitle("电池曲线");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        statusView = findViewById(R.id.batteryChartStatus);
        chartView = findViewById(R.id.batteryChartView);
        String uriText = getIntent().getStringExtra(EXTRA_CSV_URI);
        if (uriText == null || uriText.isEmpty()) {
            statusView.setText("没有可读取的 CSV 文件。");
            return;
        }
        Uri uri = Uri.parse(uriText);
        ioExecutor.execute(() -> loadCsv(uri));
    }

    private void loadCsv(Uri uri) {
        List<BatteryChartPoint> points = new ArrayList<>();
        String error = null;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("无法打开文件");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                boolean firstLine = true;
                SimpleDateFormat parser = new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
                parser.setLenient(false);
                while ((line = reader.readLine()) != null) {
                    if (firstLine) {
                        firstLine = false;
                        continue;
                    }
                    if (line.trim().isEmpty()) continue;
                    String[] columns = line.split(",", -1);
                    if (columns.length < 6) continue;
                    try {
                        Date date = parser.parse(removeBom(columns[0].trim()));
                        if (date == null) continue;
                        points.add(new BatteryChartPoint(date.getTime(),
                                parseFloat(columns[2]), parseFloat(columns[3]),
                                parseFloat(columns[4]), parseFloat(columns[5])));
                    } catch (ParseException | NumberFormatException ignored) {
                        // Skip a malformed record but continue drawing valid rows.
                    }
                }
            }
        } catch (IOException | SecurityException exception) {
            error = exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage();
        }
        String finalError = error;
        runOnUiThread(() -> {
            if (finalError != null) {
                statusView.setText("CSV 读取失败：" + finalError
                        + "。可返回后长按绘图按钮重新选择文件。");
            } else if (points.isEmpty()) {
                statusView.setText("CSV 中没有可绘制的有效记录。");
            } else {
                chartView.setPoints(points);
                statusView.setText(String.format(Locale.getDefault(),
                        "共 %d 条记录 · 横轴最小刻度 30 分钟 · 各曲线按自身数值范围缩放",
                        points.size()));
            }
        });
    }

    private static float parseFloat(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? Float.NaN : Float.parseFloat(trimmed);
    }

    private static String removeBom(String value) {
        return value.startsWith("\ufeff") ? value.substring(1) : value;
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
