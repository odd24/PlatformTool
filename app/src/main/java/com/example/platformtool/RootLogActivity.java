package com.example.platformtool;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RootLogActivity extends AppCompatActivity {
    private static final int EXPORT_REQUEST = 1001;
    private static final int MAX_STORED_LINES = 10000;
    private static final int MAX_VISIBLE_LINES = 5000;
    private static final int MAX_PENDING_UI_LINES = 2000;
    private static final int MAX_LINES_PER_FLUSH = 250;
    private static final int RECONNECT_DELAY_MS = 500;
    private static final String[] BUFFER_LABELS = {
            "Main", "System", "Radio", "Events", "Crash", "All", "Kernel (dmesg)"
    };
    private static final String[] BUFFER_VALUES = {
            "main", "system", "radio", "events", "crash", "all", "kernel"
    };
    private static final String[] LEVEL_LABELS = {
            "全部", "Verbose", "Debug", "Info", "Warn", "Error", "Fatal"
    };
    private static final int[] LEVEL_VALUES = {0, 2, 3, 4, 5, 6, 7};
    private static final Pattern PRIORITY_THREADTIME = Pattern.compile("\\s([VDIWEF])\\s+[^:]+:");
    private static final Pattern PRIORITY_BRIEF = Pattern.compile("(?:^|\\s)([VDIWEF])/[^:]+:");

    private final Object logLock = new Object();
    private final Deque<LogEntry> storedLogs = new ArrayDeque<>();
    private final ConcurrentLinkedQueue<LogEntry> pendingUiLogs = new ConcurrentLinkedQueue<>();
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean flushScheduled = new AtomicBoolean(false);
    private final AtomicInteger pendingUiCount = new AtomicInteger(0);
    private final AtomicInteger readerGeneration = new AtomicInteger(0);

    private TextView statusText;
    private Spinner bufferSpinner;
    private Spinner levelSpinner;
    private EditText keywordEdit;
    private Button pauseButton;
    private CheckBox autoScrollCheck;
    private ListView logList;
    private ArrayAdapter<String> logAdapter;
    private volatile Process readerProcess;
    private volatile boolean rootGranted;
    private volatile boolean paused;
    private volatile boolean destroyed;
    private volatile boolean kernelLogcatAvailable;
    private volatile SuMode suMode;
    private volatile int selectedMinimumLevel;
    private volatile String keywordFilter = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_root_log);
        setTitle(R.string.root_log_title);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        bindViews();
        setupControls();
        checkRootAndStart();
    }

    private void bindViews() {
        statusText = findViewById(R.id.logStatusText);
        bufferSpinner = findViewById(R.id.logBufferSpinner);
        levelSpinner = findViewById(R.id.logLevelSpinner);
        keywordEdit = findViewById(R.id.logKeywordEdit);
        pauseButton = findViewById(R.id.logPauseButton);
        autoScrollCheck = findViewById(R.id.logAutoScrollCheck);
        logList = findViewById(R.id.logList);
        logAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
                new ArrayList<>()) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(getColor(R.color.log_text));
                view.setTextSize(11f);
                view.setTypeface(android.graphics.Typeface.MONOSPACE);
                view.setPadding(6, 0, 6, 0);
                return view;
            }
        };
        logList.setAdapter(logAdapter);
        pauseButton.setEnabled(false);
        bufferSpinner.setEnabled(false);
    }

    private void setupControls() {
        ArrayAdapter<String> bufferAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, BUFFER_LABELS);
        bufferAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        bufferSpinner.setAdapter(bufferAdapter);
        ArrayAdapter<String> levelAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, LEVEL_LABELS);
        levelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        levelSpinner.setAdapter(levelAdapter);

        bufferSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (rootGranted) restartReader();
            }
        });
        levelSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedMinimumLevel = LEVEL_VALUES[position];
                rebuildVisibleLogs();
            }
        });
        keywordEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                keywordFilter = s.toString().trim().toLowerCase(Locale.ROOT);
                rebuildVisibleLogs();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        pauseButton.setOnClickListener(v -> {
            if (!rootGranted) return;
            paused = !paused;
            pauseButton.setText(paused ? R.string.log_resume : R.string.log_pause);
            setStatus(paused ? "已暂停显示；后台仍在采集日志" : "已继续实时显示", true);
            if (!paused) rebuildVisibleLogs();
        });
        findViewById(R.id.logClearButton).setOnClickListener(v -> clearViewer());
        findViewById(R.id.logExportButton).setOnClickListener(v -> requestExport());
    }

    private void checkRootAndStart() {
        setStatus("正在请求 Root 权限…", true);
        ioExecutor.execute(() -> {
            CommandResult result = runCommandWithMode(SuMode.DASH_C, "id", 10);
            if (result.success && result.output.contains("uid=0")) {
                suMode = SuMode.DASH_C;
            } else {
                CommandResult uidResult = runCommandWithMode(SuMode.UID_ZERO, "id", 10);
                if (uidResult.success && uidResult.output.contains("uid=0")) {
                    suMode = SuMode.UID_ZERO;
                    result = uidResult;
                } else {
                    result = new CommandResult(false,
                            "su -c: " + result.output + "\nsu 0: " + uidResult.output);
                }
            }
            rootGranted = suMode != null;
            if (!rootGranted) {
                suMode = SuMode.DIRECT;
                rootGranted = true;
            }
            CommandResult kernelProperty = runPlainCommand("getprop ro.logd.kernel", 3);
            kernelLogcatAvailable = kernelProperty.success
                    && "true".equalsIgnoreCase(kernelProperty.output.trim());
            final CommandResult finalResult = result;
            runOnUiThread(() -> {
                if (destroyed) return;
                pauseButton.setEnabled(true);
                bufferSpinner.setEnabled(true);
                if (suMode != SuMode.DIRECT) {
                    setStatus("Root 已授权（" + suMode.label + "），正在读取日志", true);
                } else if (checkSelfPermission("android.permission.READ_LOGS")
                        == PackageManager.PERMISSION_GRANTED) {
                    setStatus("su 仅限 adb shell；已使用 READ_LOGS 调试权限", true);
                } else {
                    setStatus("su 仅限 adb shell，系统日志的一次性授权会自动过期。请在电脑双击：\n" +
                            "grant-log-access.bat\n" + finalResult.output, false);
                }
                restartReader();
            });
        });
    }

    private void restartReader() {
        stopReader();
        clearViewer();
        final int generation = readerGeneration.incrementAndGet();
        final int selectedBuffer = bufferSpinner.getSelectedItemPosition();
        final String buffer = BUFFER_VALUES[selectedBuffer];
        if ("kernel".equals(buffer) && isKernelBridgeActive()) {
            setStatus("实时读取：Kernel Bridge (dmesg)", true);
            addLog(new LogEntry("=== PlatformTool: ADB Kernel Bridge / dmesg -w ===", 4));
            ioExecutor.execute(() -> readKernelBridge(generation));
            return;
        }
        if ("kernel".equals(buffer) && suMode == SuMode.DIRECT && !kernelLogcatAvailable) {
            setStatus("等待 ADB Kernel Bridge…", false);
            addLog(new LogEntry("请在电脑双击项目中的 start-kernel-bridge.bat，然后重新选择 Kernel。", 5));
            ioExecutor.execute(() -> readKernelBridge(generation));
            return;
        }
        final String command = "kernel".equals(buffer)
                ? (suMode == SuMode.DIRECT
                    ? "exec logcat -b kernel -v threadtime"
                    : "dmesg -w || exec logcat -b kernel -v threadtime")
                : "exec logcat -v threadtime -b " + buffer;
        setStatus("实时读取：" + BUFFER_LABELS[selectedBuffer], true);
        addLog(new LogEntry("=== PlatformTool: " + suMode.label + " / " + command + " ===", 4));
        ioExecutor.execute(() -> readLogs(command, buffer, generation));
    }

    private boolean isKernelBridgeActive() {
        File markerFile = new File(getFilesDir(), "kernel.bridge");
        File logFile = new File(getFilesDir(), "kernel.log");
        if (!markerFile.isFile() || !logFile.isFile()) return false;
        try (BufferedReader markerReader = new BufferedReader(new FileReader(markerFile));
             BufferedReader bootReader = new BufferedReader(
                     new FileReader("/proc/sys/kernel/random/boot_id"))) {
            String markerBootId = markerReader.readLine();
            String currentBootId = bootReader.readLine();
            return markerBootId != null && markerBootId.equals(currentBootId);
        } catch (IOException ignored) {
            return false;
        }
    }

    private void readKernelBridge(int generation) {
        File bridgeFile = new File(getFilesDir(), "kernel.log");
        boolean waitingMessageShown = false;
        while (!destroyed && generation == readerGeneration.get() && !bridgeFile.exists()) {
            if (!waitingMessageShown) {
                waitingMessageShown = true;
                addLog(new LogEntry("等待 Kernel Bridge。请在电脑双击项目中的 start-kernel-bridge.bat", 5));
                runOnUiThread(() -> setStatus("等待 ADB Kernel Bridge…", false));
            }
            if (!sleepSafely(300)) return;
        }
        if (destroyed || generation != readerGeneration.get()) return;
        runOnUiThread(() -> setStatus("实时读取：Kernel Bridge (dmesg)", true));
        Set<String> seenLines = new LinkedHashSet<>();
        try (RandomAccessFile input = new RandomAccessFile(bridgeFile, "r")) {
            long position = 0;
            while (!destroyed && generation == readerGeneration.get()) {
                if (input.length() < position) {
                    position = 0;
                    input.seek(0);
                }
                String rawLine = input.readLine();
                if (rawLine != null) {
                    position = input.getFilePointer();
                    String line = new String(rawLine.getBytes(StandardCharsets.ISO_8859_1),
                            StandardCharsets.UTF_8);
                    if (seenLines.add(line)) {
                        addLog(new LogEntry(line, 4));
                        if (seenLines.size() > MAX_STORED_LINES) {
                            String oldest = seenLines.iterator().next();
                            seenLines.remove(oldest);
                        }
                    }
                } else {
                    if (!sleepSafely(120)) return;
                    input.seek(position);
                }
            }
        } catch (Exception e) {
            if (!destroyed && generation == readerGeneration.get()) {
                runOnUiThread(() -> setStatus("Kernel Bridge 读取失败：" + e.getMessage(), false));
            }
        }
    }

    private void readLogs(String command, String buffer, int generation) {
        while (!destroyed && generation == readerGeneration.get()) {
            Process process = null;
            try {
                process = createRootProcess(command);
                readerProcess = process;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (!destroyed && generation == readerGeneration.get()
                            && (line = reader.readLine()) != null) {
                        addLog(new LogEntry(line, parsePriority(line, buffer)));
                    }
                }
                process.waitFor();
            } catch (Exception ignored) {
                if (destroyed || generation != readerGeneration.get()) return;
            } finally {
                if (readerProcess == process) readerProcess = null;
                if (process != null) process.destroy();
            }
            if (!destroyed && generation == readerGeneration.get()) {
                runOnUiThread(() -> setStatus("日志连接中断，正在自动重连：" +
                        BUFFER_LABELS[bufferSpinner.getSelectedItemPosition()], false));
                if (!sleepSafely(RECONNECT_DELAY_MS)) return;
                runOnUiThread(() -> setStatus("实时读取：" +
                        BUFFER_LABELS[bufferSpinner.getSelectedItemPosition()], true));
            }
        }
    }

    private void addLog(LogEntry entry) {
        synchronized (logLock) {
            storedLogs.addLast(entry);
            while (storedLogs.size() > MAX_STORED_LINES) storedLogs.removeFirst();
        }
        if (!paused && matchesCurrentFilter(entry)) {
            pendingUiLogs.offer(entry);
            int pending = pendingUiCount.incrementAndGet();
            while (pending > MAX_PENDING_UI_LINES) {
                if (pendingUiLogs.poll() == null) break;
                decrementPendingUiCount();
                pending = pendingUiCount.get();
            }
            scheduleUiFlush();
        }
    }

    private void scheduleUiFlush() {
        if (flushScheduled.compareAndSet(false, true)) {
            mainHandler.postDelayed(this::flushPendingLogs, 120);
        }
    }

    private void flushPendingLogs() {
        flushScheduled.set(false);
        if (paused || destroyed) {
            pendingUiLogs.clear();
            pendingUiCount.set(0);
            return;
        }
        logAdapter.setNotifyOnChange(false);
        LogEntry entry;
        int processed = 0;
        while (processed < MAX_LINES_PER_FLUSH && (entry = pendingUiLogs.poll()) != null) {
            decrementPendingUiCount();
            if (matchesCurrentFilter(entry)) logAdapter.add(entry.text);
            processed++;
        }
        while (logAdapter.getCount() > MAX_VISIBLE_LINES) {
            logAdapter.remove(logAdapter.getItem(0));
        }
        logAdapter.notifyDataSetChanged();
        if (autoScrollCheck.isChecked() && logAdapter.getCount() > 0) {
            logList.setSelection(logAdapter.getCount() - 1);
        }
        if (!pendingUiLogs.isEmpty()) scheduleUiFlush();
    }

    private void decrementPendingUiCount() {
        int current;
        do {
            current = pendingUiCount.get();
            if (current <= 0) return;
        } while (!pendingUiCount.compareAndSet(current, current - 1));
    }

    private void rebuildVisibleLogs() {
        if (paused || logAdapter == null) return;
        pendingUiLogs.clear();
        pendingUiCount.set(0);
        List<String> visible = new ArrayList<>();
        synchronized (logLock) {
            for (LogEntry entry : storedLogs) {
                if (matchesCurrentFilter(entry)) {
                    visible.add(entry.text);
                    if (visible.size() > MAX_VISIBLE_LINES) visible.remove(0);
                }
            }
        }
        logAdapter.clear();
        logAdapter.addAll(visible);
        logAdapter.notifyDataSetChanged();
        if (autoScrollCheck.isChecked() && !visible.isEmpty()) {
            logList.setSelection(visible.size() - 1);
        }
    }

    private boolean matchesCurrentFilter(LogEntry entry) {
        if (selectedMinimumLevel > 0 && entry.priority < selectedMinimumLevel) return false;
        String keyword = keywordFilter;
        return keyword.isEmpty() || entry.text.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private int parsePriority(String line, String buffer) {
        if ("kernel".equals(buffer)) return 4;
        Matcher matcher = PRIORITY_THREADTIME.matcher(line);
        if (!matcher.find()) matcher = PRIORITY_BRIEF.matcher(line);
        if (matcher.find(0)) return priorityValue(matcher.group(1).charAt(0));
        return 4;
    }

    private int priorityValue(char value) {
        switch (value) {
            case 'V': return 2;
            case 'D': return 3;
            case 'I': return 4;
            case 'W': return 5;
            case 'E': return 6;
            case 'F': return 7;
            default: return 4;
        }
    }

    private void clearViewer() {
        synchronized (logLock) { storedLogs.clear(); }
        pendingUiLogs.clear();
        pendingUiCount.set(0);
        if (logAdapter != null) {
            logAdapter.clear();
            logAdapter.notifyDataSetChanged();
        }
    }

    private void requestExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        intent.putExtra(Intent.EXTRA_TITLE, "platform_log_" + timestamp + ".txt");
        startActivityForResult(intent, EXPORT_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EXPORT_REQUEST && resultCode == Activity.RESULT_OK
                && data != null && data.getData() != null) {
            exportVisibleLogs(data.getData());
        }
    }

    private void exportVisibleLogs(Uri uri) {
        List<String> snapshot = new ArrayList<>();
        synchronized (logLock) {
            for (LogEntry entry : storedLogs) {
                if (matchesCurrentFilter(entry)) snapshot.add(entry.text);
            }
        }
        ioExecutor.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                if (output == null) throw new IOException("无法打开目标文件");
                for (String line : snapshot) {
                    output.write(line.getBytes(StandardCharsets.UTF_8));
                    output.write('\n');
                }
                runOnUiThread(() -> Toast.makeText(this,
                        "已导出 " + snapshot.size() + " 行", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> setStatus("导出失败：" + e.getMessage(), false));
            }
        });
    }

    private Process createRootProcess(String command) throws IOException {
        ProcessBuilder builder;
        if (suMode == SuMode.UID_ZERO) {
            builder = new ProcessBuilder("su", "0", "sh", "-c", command);
        } else if (suMode == SuMode.DIRECT) {
            builder = new ProcessBuilder("sh", "-c", command);
        } else {
            builder = new ProcessBuilder("su", "-c", command);
        }
        return builder.redirectErrorStream(true).start();
    }

    private CommandResult runCommandWithMode(SuMode mode, String command, int timeoutSeconds) {
        Process process = null;
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder builder = mode == SuMode.UID_ZERO
                    ? new ProcessBuilder("su", "0", "sh", "-c", command)
                    : new ProcessBuilder("su", "-c", command);
            process = builder.redirectErrorStream(true).start();
            if (!waitForProcess(process, timeoutSeconds)) {
                process.destroy();
                return new CommandResult(false, "Root 授权超时");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append('\n');
            }
            return new CommandResult(process.exitValue() == 0, output.toString().trim());
        } catch (Exception e) {
            return new CommandResult(false, e.getMessage() == null ? e.toString() : e.getMessage());
        } finally {
            if (process != null) process.destroy();
        }
    }

    private CommandResult runPlainCommand(String command, int timeoutSeconds) {
        Process process = null;
        StringBuilder output = new StringBuilder();
        try {
            process = new ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start();
            if (!waitForProcess(process, timeoutSeconds)) {
                process.destroy();
                return new CommandResult(false, "命令超时");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append('\n');
            }
            return new CommandResult(process.exitValue() == 0, output.toString().trim());
        } catch (Exception e) {
            return new CommandResult(false, e.getMessage() == null ? e.toString() : e.getMessage());
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static boolean waitForProcess(Process process, int timeoutSeconds) {
        long deadline = SystemClock.uptimeMillis() + timeoutSeconds * 1000L;
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                process.exitValue();
                return true;
            } catch (IllegalThreadStateException ignored) {
                if (!sleepSafely(50)) return false;
            }
        }
        return false;
    }

    private static boolean sleepSafely(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void stopReader() {
        readerGeneration.incrementAndGet();
        Process process = readerProcess;
        readerProcess = null;
        if (process != null) process.destroy();
    }

    private void setStatus(String message, boolean ok) {
        statusText.setText(message);
        statusText.setTextColor(getColor(ok ? R.color.status_ok : R.color.status_error));
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        stopReader();
        mainHandler.removeCallbacksAndMessages(null);
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }

    private abstract static class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {
        @Override public void onNothingSelected(AdapterView<?> parent) { }
    }

    private static final class CommandResult {
        final boolean success;
        final String output;
        CommandResult(boolean success, String output) {
            this.success = success;
            this.output = output;
        }
    }

    private enum SuMode {
        DASH_C("su -c"), UID_ZERO("su 0"), DIRECT("READ_LOGS/direct");
        final String label;
        SuMode(String label) { this.label = label; }
    }
}
