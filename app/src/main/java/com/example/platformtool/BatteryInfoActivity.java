package com.example.platformtool;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.content.pm.PackageManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class BatteryInfoActivity extends AppCompatActivity {
    private static final long REFRESH_INTERVAL_MS = 1_000L;
    private static final String PREFS_NAME = "battery_chart";
    private static final String PREF_LAST_CSV_URI = "last_csv_uri";
    private static final int MAX_VISIBLE_RECORDS = 500;

    private TextView chargeStateView;
    private TextView levelView;
    private TextView currentView;
    private TextView voltageView;
    private TextView temperatureView;
    private TextView powerSourceView;
    private TextView recordStatusView;
    private TextView lastRecordView;
    private TextView tableStatusView;
    private BatteryRecordTableView recordTableView;
    private ProgressBar levelProgress;
    private SwitchCompat recordSwitch;
    private Button chartButton;
    private BatteryManager batteryManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService tableExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger tableLoadGeneration = new AtomicInteger();

    private int levelPercent = -1;
    private int voltageMv = -1;
    private float temperatureC = Float.NaN;
    private Double currentMa;
    private boolean plugged;
    private boolean charging;
    private int pluggedType;
    private boolean hasSnapshot;
    private boolean receiverRegistered;
    private boolean changingRecordSwitch;
    private Uri lastCsvUri;
    private boolean pendingRecordRequest;
    private String loadedTableUri;
    private int loadedTableCount = Integer.MIN_VALUE;

    private final Runnable refreshCurrentRunnable = new Runnable() {
        @Override
        public void run() {
            currentMa = readCurrentMa();
            updateLiveViews();
            updateRecordingUi();
            handler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Boolean forcedPlugged = null;
            if (Intent.ACTION_POWER_CONNECTED.equals(action)) forcedPlugged = true;
            if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) forcedPlugged = false;
            Intent batteryIntent = Intent.ACTION_BATTERY_CHANGED.equals(action)
                    ? intent
                    : registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batteryIntent != null) updateSnapshot(batteryIntent, forcedPlugged);
        }
    };

    private final ActivityResultLauncher<String> createCsvFile = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/csv"), uri -> {
                if (uri == null) {
                    setRecordSwitch(false);
                    recordStatusView.setText("已取消选择 CSV 文件，记录未开始。");
                    return;
                }
                startRecording(uri);
            });

    private final ActivityResultLauncher<String[]> openCsvFile = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                rememberCsvUri(uri);
                openChart(uri);
            });

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!pendingRecordRequest) return;
                pendingRecordRequest = false;
                if (granted) {
                    chooseNewRecordingFile();
                } else {
                    setRecordSwitch(false);
                    recordStatusView.setText("未获得通知权限，无法启动可靠的后台记录服务。");
                    Toast.makeText(this, "请允许通知权限后再开始后台记录", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battery_info);
        setTitle("充电与电池信息");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        chargeStateView = findViewById(R.id.batteryChargeState);
        levelView = findViewById(R.id.batteryLevelValue);
        currentView = findViewById(R.id.batteryCurrentValue);
        voltageView = findViewById(R.id.batteryVoltageValue);
        temperatureView = findViewById(R.id.batteryTemperatureValue);
        powerSourceView = findViewById(R.id.batteryPowerSource);
        recordStatusView = findViewById(R.id.batteryRecordStatus);
        lastRecordView = findViewById(R.id.batteryLastRecord);
        tableStatusView = findViewById(R.id.batteryTableStatus);
        recordTableView = findViewById(R.id.batteryRecordTable);
        recordTableView.setShowHeader(false);
        levelProgress = findViewById(R.id.batteryLevelProgress);
        recordSwitch = findViewById(R.id.batteryRecordSwitch);
        chartButton = findViewById(R.id.batteryChartButton);
        batteryManager = (BatteryManager) getSystemService(BATTERY_SERVICE);
        String savedUri = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(PREF_LAST_CSV_URI, null);
        if (savedUri != null && !savedUri.isEmpty()) lastCsvUri = Uri.parse(savedUri);

        recordSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (changingRecordSwitch) return;
            if (checked) {
                setRecordSwitch(false);
                requestBackgroundRecording();
            } else if (BatteryLoggingService.isRecording(this)) {
                Intent stopIntent = new Intent(this, BatteryLoggingService.class)
                        .setAction(BatteryLoggingService.ACTION_STOP);
                startService(stopIntent);
                recordStatusView.setText("正在停止后台记录并保存 CSV…");
            }
        });
        chartButton.setOnClickListener(v -> openLatestChart());
        chartButton.setOnLongClickListener(v -> {
            chooseCsvForChart();
            return true;
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        Intent sticky = ContextCompat.registerReceiver(this, batteryReceiver, filter,
                ContextCompat.RECEIVER_EXPORTED);
        receiverRegistered = true;
        if (sticky != null) updateSnapshot(sticky, null);
        updateRecordingUi();
        handler.post(refreshCurrentRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (BatteryLoggingService.isRecording(this)) {
            Intent resumeIntent = new Intent(this, BatteryLoggingService.class)
                    .setAction(BatteryLoggingService.ACTION_RESUME);
            try {
                ContextCompat.startForegroundService(this, resumeIntent);
                recordStatusView.setText("正在检查并恢复后台记录服务…");
            } catch (RuntimeException exception) {
                recordStatusView.setText("后台记录服务恢复失败：" + exception.getMessage());
            }
        }
    }

    private void updateSnapshot(Intent intent, Boolean forcedPlugged) {
        int rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int newLevel = rawLevel >= 0 && scale > 0
                ? Math.round(rawLevel * 100f / scale) : levelPercent;
        int newVoltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, voltageMv);
        int rawTemperature = intent.getIntExtra(
                BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        int newPluggedType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, pluggedType);
        boolean newPlugged = forcedPlugged != null ? forcedPlugged : newPluggedType != 0;
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN);
        // Some vendor ROMs keep the old CHARGING status briefly after unplug.
        // Charging is only possible while an external power source is present.
        boolean newCharging = newPlugged
                && (status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL);

        levelPercent = newLevel;
        voltageMv = newVoltage;
        if (rawTemperature != Integer.MIN_VALUE) temperatureC = rawTemperature / 10f;
        if (forcedPlugged != null && forcedPlugged && newPluggedType == 0) {
            pluggedType = -1;
        } else {
            pluggedType = newPluggedType;
        }
        plugged = newPlugged;
        charging = newCharging;
        currentMa = readCurrentMa();
        hasSnapshot = true;
        updateLiveViews();

    }

    private void updateLiveViews() {
        if (levelPercent >= 0) {
            levelView.setText(String.format(Locale.getDefault(), "%d%%", levelPercent));
            levelProgress.setProgress(levelPercent);
        }
        currentView.setText(currentMa == null
                ? "实时电流：设备不支持"
                : String.format(Locale.getDefault(), "实时电流：%+.1f mA", currentMa));
        voltageView.setText(voltageMv < 0
                ? "电池电压：设备不支持"
                : String.format(Locale.getDefault(), "电池电压：%.3f V", voltageMv / 1000f));
        temperatureView.setText(Float.isNaN(temperatureC)
                ? "电池温度：设备不支持"
                : String.format(Locale.getDefault(), "电池温度：%.1f ℃", temperatureC));
        chargeStateView.setText(charging
                ? "正在充电"
                : (plugged ? "已连接电源（当前未充电）" : "未连接充电器"));
        powerSourceView.setText("供电来源：" + powerSourceName());
    }

    private Double readCurrentMa() {
        if (batteryManager != null) {
            long microamps = batteryManager.getLongProperty(
                    BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            if (microamps != Integer.MIN_VALUE && microamps != Long.MIN_VALUE) {
                return microamps / 1000.0;
            }
        }
        String[] paths = {
                "/sys/class/power_supply/battery/current_now",
                "/sys/class/power_supply/battery/current_avg"
        };
        for (String path : paths) {
            Long raw = readLongFile(path);
            if (raw != null) return Math.abs(raw) > 10_000 ? raw / 1000.0 : raw.doubleValue();
        }
        return null;
    }

    private static Long readLongFile(String path) {
        File file = new File(path);
        if (!file.isFile() || !file.canRead()) return null;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return Long.parseLong(reader.readLine().trim());
        } catch (IOException | NumberFormatException | NullPointerException ignored) {
            return null;
        }
    }

    private void startRecording(Uri uri) {
        try {
            rememberCsvUri(uri);
            Intent serviceIntent = new Intent(this, BatteryLoggingService.class)
                    .setAction(BatteryLoggingService.ACTION_START)
                    .putExtra(BatteryLoggingService.EXTRA_URI, uri.toString());
            ContextCompat.startForegroundService(this, serviceIntent);
            setRecordSwitch(true);
            recordStatusView.setText("正在启动后台记录服务…");
        } catch (RuntimeException exception) {
            setRecordSwitch(false);
            Toast.makeText(this, "无法启动后台记录：" + exception.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void openLatestChart() {
        if (lastCsvUri == null) {
            chooseCsvForChart();
        } else {
            openChart(lastCsvUri);
        }
    }

    private void chooseCsvForChart() {
        openCsvFile.launch(new String[]{"text/csv", "text/comma-separated-values", "text/plain"});
    }

    private void rememberCsvUri(Uri uri) {
        lastCsvUri = uri;
        loadedTableUri = null;
        loadedTableCount = Integer.MIN_VALUE;
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // The URI remains usable for the current app session on providers
            // that do not support persistable grants.
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(PREF_LAST_CSV_URI, uri.toString()).apply();
    }

    private void openChart(Uri uri) {
        Intent intent = new Intent(this, BatteryChartActivity.class);
        intent.putExtra(BatteryChartActivity.EXTRA_CSV_URI, uri.toString());
        startActivity(intent);
    }

    private void requestBackgroundRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            pendingRecordRequest = true;
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        chooseNewRecordingFile();
    }

    private void chooseNewRecordingFile() {
        String timestamp = new SimpleDateFormat(
                "yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        createCsvFile.launch("battery_curve_" + timestamp + ".csv");
    }

    private void updateRecordingUi() {
        android.content.SharedPreferences prefs = BatteryLoggingService.preferences(this);
        boolean active = prefs.getBoolean(BatteryLoggingService.KEY_ACTIVE, false);
        int count = prefs.getInt(BatteryLoggingService.KEY_COUNT, 0);
        String status = prefs.getString(BatteryLoggingService.KEY_STATUS, "记录已关闭");
        String lastRecord = prefs.getString(BatteryLoggingService.KEY_LAST_RECORD, null);
        String serviceUri = prefs.getString(BatteryLoggingService.KEY_URI, null);
        setRecordSwitch(active);
        if (active) {
            recordStatusView.setText(String.format(Locale.getDefault(),
                    "后台记录中，共 %d 条。关闭页面、切换应用或锁屏后仍会继续记录。",
                    count));
        } else {
            recordStatusView.setText(status + "。打开开关后请选择 CSV 保存位置。");
        }
        if (lastRecord != null && !lastRecord.isEmpty()) lastRecordView.setText(lastRecord);
        String tableUri = serviceUri != null ? serviceUri
                : (lastCsvUri == null ? null : lastCsvUri.toString());
        if (tableUri != null
                && (!tableUri.equals(loadedTableUri) || count != loadedTableCount)) {
            loadedTableUri = tableUri;
            loadedTableCount = count;
            loadRecentRecords(Uri.parse(tableUri), tableLoadGeneration.incrementAndGet());
        } else if (tableUri == null) {
            tableStatusView.setText("暂无可显示的 CSV 记录");
            recordTableView.setRows(new ArrayList<>());
        }
    }

    private void loadRecentRecords(Uri uri, int generation) {
        tableStatusView.setText("正在读取最近记录…");
        tableExecutor.execute(() -> {
            ArrayDeque<String[]> recent = new ArrayDeque<>(MAX_VISIBLE_RECORDS);
            String error = null;
            int total = 0;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException("无法打开 CSV 文件");
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    boolean firstLine = true;
                    while ((line = reader.readLine()) != null) {
                        if (firstLine) {
                            firstLine = false;
                            continue;
                        }
                        if (line.trim().isEmpty()) continue;
                        String[] columns = line.split(",", -1);
                        if (columns.length < 8) continue;
                        String[] row = {
                                removeBom(columns[0]), columns[1], columns[2], columns[3],
                                columns[4], columns[5], columns[6], columns[7]
                        };
                        if (recent.size() == MAX_VISIBLE_RECORDS) recent.removeFirst();
                        recent.addLast(row);
                        total++;
                    }
                }
            } catch (IOException | SecurityException exception) {
                error = exception.getMessage() == null
                        ? exception.getClass().getSimpleName() : exception.getMessage();
            }
            List<String[]> rows = new ArrayList<>(recent);
            // “最近记录”应当让用户第一眼看到最新状态。CSV 本身仍按时间正序
            // 保存，界面展示时再倒序，避免当前电量对应的记录藏在表格底部。
            Collections.reverse(rows);
            int finalTotal = total;
            String finalError = error;
            runOnUiThread(() -> {
                if (generation != tableLoadGeneration.get()) return;
                if (finalError != null) {
                    tableStatusView.setText("记录表格读取失败：" + finalError);
                    recordTableView.setRows(new ArrayList<>());
                } else {
                    recordTableView.setRows(rows);
                    tableStatusView.setText(finalTotal > MAX_VISIBLE_RECORDS
                            ? String.format(Locale.getDefault(),
                            "CSV 共 %d 条，显示最近 %d 条（最新在上）", finalTotal, MAX_VISIBLE_RECORDS)
                            : String.format(Locale.getDefault(), "共 %d 条记录（最新在上）", finalTotal));
                }
            });
        });
    }

    private static String removeBom(String value) {
        return value.startsWith("\ufeff") ? value.substring(1) : value;
    }

    private void setRecordSwitch(boolean checked) {
        changingRecordSwitch = true;
        recordSwitch.setChecked(checked);
        changingRecordSwitch = false;
    }

    private String powerSourceName() {
        if (!plugged) return "电池";
        if ((pluggedType & BatteryManager.BATTERY_PLUGGED_AC) != 0) return "交流充电器";
        if ((pluggedType & BatteryManager.BATTERY_PLUGGED_USB) != 0) return "USB";
        if ((pluggedType & BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0) return "无线充电";
        return "已连接（类型未知）";
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refreshCurrentRunnable);
        if (receiverRegistered) unregisterReceiver(batteryReceiver);
        tableLoadGeneration.incrementAndGet();
        tableExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
