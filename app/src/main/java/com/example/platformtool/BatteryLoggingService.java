package com.example.platformtool;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BatteryLoggingService extends Service {
    static final String ACTION_START = "com.example.platformtool.BATTERY_LOG_START";
    static final String ACTION_STOP = "com.example.platformtool.BATTERY_LOG_STOP";
    static final String ACTION_RESUME = "com.example.platformtool.BATTERY_LOG_RESUME";
    static final String EXTRA_URI = "csv_uri";
    static final String PREFS = "battery_logging_service";
    static final String KEY_ACTIVE = "active";
    static final String KEY_URI = "uri";
    static final String KEY_COUNT = "count";
    static final String KEY_STATUS = "status";
    static final String KEY_LAST_RECORD = "last_record";

    private static final String CHANNEL_ID = "battery_logging_active";
    private static final int NOTIFICATION_ID = 2101;
    private static final long REFRESH_INTERVAL_MS = 1_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private BatteryManager batteryManager;
    private NotificationManager notificationManager;
    private BufferedWriter writer;
    private boolean logging;
    private boolean receiverRegistered;
    private int levelPercent = -1;
    private int voltageMv = -1;
    private float temperatureC = Float.NaN;
    private Double currentMa;
    private boolean plugged;
    private boolean charging;
    private int pluggedType;
    private int lastLoggedLevel = -1;
    private boolean lastLoggedPlugged;
    private int recordCount;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!logging) return;
            currentMa = readCurrentMa();
            updateNotification();
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
            if (batteryIntent != null) updateSnapshot(batteryIntent, forcedPlugged, true);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        batteryManager = (BatteryManager) getSystemService(BATTERY_SERVICE);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("正在启动电池记录…"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_RESUME : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopLogging("用户停止记录", true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action)) {
            String uriText = intent.getStringExtra(EXTRA_URI);
            if (uriText == null || uriText.isEmpty()) {
                failAndStop("没有有效的 CSV 保存位置");
            } else {
                startLogging(Uri.parse(uriText), true);
            }
        } else {
            SharedPreferences prefs = preferences(this);
            String uriText = prefs.getString(KEY_URI, null);
            if (prefs.getBoolean(KEY_ACTIVE, false) && uriText != null) {
                startLogging(Uri.parse(uriText), false);
            } else {
                stopSelf();
            }
        }
        return START_STICKY;
    }

    private void startLogging(Uri uri, boolean newFile) {
        if (logging) {
            if (!newFile) return;
            stopLogging("切换记录文件", false);
        }
        try {
            OutputStream output = getContentResolver().openOutputStream(uri, newFile ? "wt" : "wa");
            if (output == null) throw new IOException("无法打开 CSV 文件");
            writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
            if (newFile) {
                writer.write('\ufeff');
                writer.write("时间戳,触发原因,电量百分比,实时电流_mA,电池电压_V,电池温度_摄氏度,是否充电,供电来源\r\n");
                recordCount = 0;
            } else {
                recordCount = preferences(this).getInt(KEY_COUNT, 0);
            }
            writer.flush();
            logging = true;
            registerBatteryReceiver();
            Intent sticky = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (sticky != null) updateSnapshot(sticky, null, false);
            lastLoggedLevel = levelPercent;
            lastLoggedPlugged = plugged;
            saveState(true, uri.toString(), newFile ? "后台记录已开始" : "后台记录已恢复", null);
            appendRecord(newFile ? "开始记录" : "服务恢复");
            handler.removeCallbacks(refreshRunnable);
            handler.post(refreshRunnable);
        } catch (IOException | RuntimeException exception) {
            closeWriter();
            failAndStop("无法写入 CSV：" + safeMessage(exception));
        }
    }

    private void registerBatteryReceiver() {
        if (receiverRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        ContextCompat.registerReceiver(this, batteryReceiver, filter,
                ContextCompat.RECEIVER_EXPORTED);
        receiverRegistered = true;
    }

    private void updateSnapshot(Intent intent, Boolean forcedPlugged, boolean allowRecord) {
        int rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (rawLevel >= 0 && scale > 0) levelPercent = Math.round(rawLevel * 100f / scale);
        voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, voltageMv);
        int rawTemperature = intent.getIntExtra(
                BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        if (rawTemperature != Integer.MIN_VALUE) temperatureC = rawTemperature / 10f;
        int reportedPluggedType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, pluggedType);
        boolean newPlugged = forcedPlugged != null ? forcedPlugged : reportedPluggedType != 0;
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN);
        boolean newCharging = newPlugged
                && (status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL);
        if (forcedPlugged != null && forcedPlugged && reportedPluggedType == 0) {
            pluggedType = -1;
        } else {
            pluggedType = reportedPluggedType;
        }
        plugged = newPlugged;
        charging = newCharging;
        currentMa = readCurrentMa();

        if (logging && allowRecord) {
            if (plugged != lastLoggedPlugged) {
                appendRecord(plugged ? "充电器插入" : "充电器拔出");
                lastLoggedPlugged = plugged;
                lastLoggedLevel = levelPercent;
            } else if (levelPercent >= 0 && levelPercent != lastLoggedLevel) {
                appendRecord("电量变化");
                lastLoggedLevel = levelPercent;
            }
        }
        updateNotification();
    }

    private void appendRecord(String reason) {
        if (!logging || writer == null) return;
        String timestamp = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        String current = currentMa == null ? "" : String.format(Locale.US, "%.3f", currentMa);
        String voltage = voltageMv < 0 ? "" : String.format(Locale.US, "%.3f", voltageMv / 1000f);
        String temperature = Float.isNaN(temperatureC)
                ? "" : String.format(Locale.US, "%.1f", temperatureC);
        String row = String.format(Locale.getDefault(), "%s,%s,%d,%s,%s,%s,%s,%s",
                timestamp, reason, levelPercent, current, voltage, temperature,
                charging ? "是" : "否", powerSourceName());
        try {
            writer.write(row);
            writer.write("\r\n");
            writer.flush();
            recordCount++;
            SharedPreferences prefs = preferences(this);
            prefs.edit().putInt(KEY_COUNT, recordCount)
                    .putString(KEY_STATUS, "后台记录中")
                    .putString(KEY_LAST_RECORD, row).apply();
            updateNotification();
        } catch (IOException exception) {
            failAndStop("CSV 写入失败：" + safeMessage(exception));
        }
    }

    private void stopLogging(String reason, boolean userRequested) {
        if (logging) appendRecord(reason);
        logging = false;
        handler.removeCallbacks(refreshRunnable);
        closeWriter();
        SharedPreferences prefs = preferences(this);
        prefs.edit().putBoolean(KEY_ACTIVE, false)
                .putString(KEY_STATUS, userRequested ? "记录已停止并保存" : "记录服务已停止")
                .apply();
        stopForeground(true);
    }

    private void failAndStop(String message) {
        logging = false;
        handler.removeCallbacks(refreshRunnable);
        closeWriter();
        preferences(this).edit().putBoolean(KEY_ACTIVE, false)
                .putString(KEY_STATUS, message).apply();
        notificationManager.notify(NOTIFICATION_ID, buildNotification(message));
        stopForeground(true);
        stopSelf();
    }

    private void saveState(boolean active, String uri, String status, String lastRecord) {
        SharedPreferences.Editor editor = preferences(this).edit()
                .putBoolean(KEY_ACTIVE, active)
                .putString(KEY_URI, uri)
                .putInt(KEY_COUNT, recordCount)
                .putString(KEY_STATUS, status);
        if (lastRecord != null) editor.putString(KEY_LAST_RECORD, lastRecord);
        editor.apply();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "电池曲线后台记录", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("后台记录电量、电流、电压、温度和充电器插拔事件");
        channel.setSound(null, null);
        channel.enableVibration(false);
        notificationManager.createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        Intent openIntent = new Intent(this, BatteryInfoActivity.class);
        PendingIntent openPendingIntent = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent = new Intent(this, BatteryLoggingService.class).setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("正在记录电池曲线")
                .setContentText(text)
                .setContentIntent(openPendingIntent)
                .setOngoing(logging)
                .setOnlyAlertOnce(true)
                .addAction(0, "停止记录", stopPendingIntent)
                .build();
    }

    private void updateNotification() {
        if (!logging) return;
        String current = currentMa == null ? "-- mA"
                : String.format(Locale.getDefault(), "%+.0f mA", currentMa);
        String text = String.format(Locale.getDefault(), "%d%% · %s · %.3f V · %.1f ℃ · %d 条",
                levelPercent, current, voltageMv / 1000f, temperatureC, recordCount);
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text));
    }

    private Double readCurrentMa() {
        if (batteryManager != null) {
            long microamps = batteryManager.getLongProperty(
                    BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            if (microamps != Integer.MIN_VALUE && microamps != Long.MIN_VALUE) {
                return microamps / 1000.0;
            }
        }
        for (String path : new String[]{
                "/sys/class/power_supply/battery/current_now",
                "/sys/class/power_supply/battery/current_avg"}) {
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

    private String powerSourceName() {
        if (!plugged) return "电池";
        if ((pluggedType & BatteryManager.BATTERY_PLUGGED_AC) != 0) return "交流充电器";
        if ((pluggedType & BatteryManager.BATTERY_PLUGGED_USB) != 0) return "USB";
        if ((pluggedType & BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0) return "无线充电";
        return "已连接（类型未知）";
    }

    private void closeWriter() {
        if (writer == null) return;
        try {
            writer.close();
        } catch (IOException ignored) {
        }
        writer = null;
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    static boolean isRecording(Context context) {
        return preferences(context).getBoolean(KEY_ACTIVE, false);
    }

    private static String safeMessage(Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(refreshRunnable);
        if (logging) stopLogging("服务结束", false);
        if (receiverRegistered) unregisterReceiver(batteryReceiver);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
