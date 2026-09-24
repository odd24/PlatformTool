package com.example.platformtool;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.SwitchCompat;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConsoleActivity extends PlatformActivity {
    private static final String PREFS = "console";
    private static final String KEY_HISTORY = "history";
    private static final int MAX_HISTORY = 100;
    private static final int MAX_OUTPUT_CHARS = 500_000;
    private static final int KEEP_OUTPUT_CHARS = 400_000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<String> history = new ArrayList<>();
    private final Object processLock = new Object();

    private TextView outputView;
    private TextView statusView;
    private ScrollView outputScroll;
    private EditText commandInput;
    private Button runButton;
    private Button stopButton;
    private SwitchCompat rootSwitch;
    private volatile Process runningProcess;
    private volatile boolean stopRequested;
    private volatile SuMode suMode = SuMode.NONE;
    private boolean commandRunning;
    private boolean changingRootSwitch;
    private int historyPosition;

    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/plain"), this::exportOutput);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_console);
        setTitle(R.string.tool_console_title);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        outputView = findViewById(R.id.consoleOutput);
        statusView = findViewById(R.id.consoleStatus);
        outputScroll = findViewById(R.id.consoleOutputScroll);
        commandInput = findViewById(R.id.consoleCommandInput);
        runButton = findViewById(R.id.consoleRunButton);
        stopButton = findViewById(R.id.consoleStopButton);
        rootSwitch = findViewById(R.id.consoleRootSwitch);
        outputView.setTypeface(Typeface.MONOSPACE);

        loadHistory();
        historyPosition = history.size();
        runButton.setOnClickListener(v -> runInputCommand());
        stopButton.setOnClickListener(v -> stopCommand());
        findViewById(R.id.consoleHistoryPrevious).setOnClickListener(v -> navigateHistory(-1));
        findViewById(R.id.consoleHistoryNext).setOnClickListener(v -> navigateHistory(1));
        findViewById(R.id.consoleClearButton).setOnClickListener(v -> outputView.setText(""));
        findViewById(R.id.consoleCopyButton).setOnClickListener(v -> copyOutput());
        findViewById(R.id.consoleExportButton).setOnClickListener(v ->
                exportLauncher.launch("console_" + fileTimestamp() + ".txt"));
        commandInput.setOnEditorActionListener((view, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEND || enter) {
                runInputCommand();
                return true;
            }
            return false;
        });
        rootSwitch.setEnabled(false);
        rootSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (changingRootSwitch) return;
            if (checked && suMode == SuMode.NONE) {
                setRootChecked(false);
                Toast.makeText(this, R.string.console_root_unavailable, Toast.LENGTH_LONG).show();
            } else {
                updateStatus();
            }
        });

        appendOutput(getString(R.string.console_intro));
        setCommandRunning(false);
        detectRoot();
    }

    private void detectRoot() {
        statusView.setText(R.string.console_status_detecting);
        executor.execute(() -> {
            SuMode detected = probeRoot(SuMode.DASH_C) ? SuMode.DASH_C
                    : (probeRoot(SuMode.UID_ZERO) ? SuMode.UID_ZERO : SuMode.NONE);
            suMode = detected;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                rootSwitch.setEnabled(detected != SuMode.NONE);
                updateStatus();
            });
        });
    }

    private boolean probeRoot(SuMode mode) {
        Process process = null;
        try {
            process = buildProcess(mode, "id").start();
            long deadline = SystemClock.elapsedRealtime() + 3_000L;
            while (SystemClock.elapsedRealtime() < deadline) {
                try {
                    if (process.exitValue() != 0) return false;
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                            process.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.contains("uid=0")) return true;
                        }
                    }
                    return false;
                } catch (IllegalThreadStateException running) {
                    Thread.sleep(50L);
                }
            }
            return false;
        } catch (Exception ignored) {
            if (ignored instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

    private void runInputCommand() {
        if (commandRunning) return;
        String raw = commandInput.getText().toString().trim();
        if (raw.isEmpty()) return;
        boolean stripped = raw.matches("(?is)^adb\\s+shell(?:\\s+.*)?$");
        String command = raw.replaceFirst("(?is)^adb\\s+shell(?:\\s+)?", "").trim();
        if (command.isEmpty()) {
            Toast.makeText(this, R.string.console_missing_adb_command, Toast.LENGTH_SHORT).show();
            return;
        }
        addHistory(command);
        commandInput.setText("");
        historyPosition = history.size();
        SuMode mode = rootSwitch.isChecked() ? suMode : SuMode.NONE;
        appendOutput(getString(
                R.string.console_prompt_format,
                displayTimestamp(),
                mode == SuMode.NONE ? "$" : "#",
                command));
        if (stripped) appendOutput(getString(R.string.console_prefix_removed));
        setCommandRunning(true);
        executor.execute(() -> executeCommand(mode, command));
    }

    private void executeCommand(SuMode mode, String command) {
        stopRequested = false;
        long started = SystemClock.elapsedRealtime();
        int exitCode = -1;
        String failure = null;
        Process process = null;
        try {
            process = buildProcess(mode, command).redirectErrorStream(true).start();
            synchronized (processLock) {
                runningProcess = process;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buffer = new char[2048];
                int count;
                while ((count = reader.read(buffer)) >= 0) {
                    if (count > 0) appendOutput(new String(buffer, 0, count));
                }
            }
            exitCode = process.waitFor();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failure = getString(R.string.console_interrupted);
        } catch (IOException | RuntimeException exception) {
            failure = exception.getMessage() == null ? exception.toString() : exception.getMessage();
        } finally {
            synchronized (processLock) {
                if (runningProcess == process) runningProcess = null;
            }
            if (process != null) process.destroy();
        }
        long elapsed = SystemClock.elapsedRealtime() - started;
        String footer = stopRequested
                ? getString(R.string.console_stopped_format, elapsed / 1000d)
                : failure == null
                ? getString(R.string.console_finished_format, exitCode, elapsed / 1000d)
                : getString(R.string.console_failed_format, failure);
        appendOutput(footer);
        runOnUiThread(() -> setCommandRunning(false));
    }

    private ProcessBuilder buildProcess(SuMode mode, String command) {
        ProcessBuilder builder;
        if (mode == SuMode.DASH_C) {
            builder = new ProcessBuilder("su", "-c", command);
        } else if (mode == SuMode.UID_ZERO) {
            builder = new ProcessBuilder("su", "0", "sh", "-c", command);
        } else {
            builder = new ProcessBuilder("sh", "-c", command);
        }
        // Some vendor ROMs give an app process an inaccessible default cwd (often "/").
        // A known readable cwd makes basic commands such as pwd/ls useful in normal mode.
        builder.directory(getFilesDir());
        return builder;
    }

    private void stopCommand() {
        Process process;
        synchronized (processLock) {
            process = runningProcess;
        }
        if (process == null) return;
        stopRequested = true;
        appendOutput(getString(R.string.console_stopping));
        process.destroy();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && process.isAlive()) {
            process.destroyForcibly();
        }
    }

    private void setCommandRunning(boolean running) {
        commandRunning = running;
        runButton.setEnabled(!running);
        stopButton.setEnabled(running);
        rootSwitch.setEnabled(!running && suMode != SuMode.NONE);
        commandInput.setEnabled(!running);
        updateStatus();
    }

    private void updateStatus() {
        if (commandRunning) {
            statusView.setText(rootSwitch.isChecked()
                    ? R.string.console_status_running_root
                    : R.string.console_status_running_app);
        } else if (rootSwitch.isChecked()) {
            statusView.setText(getString(
                    R.string.console_status_root_format,
                    getString(suMode.labelRes)));
        } else if (suMode == SuMode.NONE) {
            statusView.setText(R.string.console_status_no_root);
        } else {
            statusView.setText(R.string.console_status_root_available);
        }
    }

    private void appendOutput(String text) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            outputView.append(text);
            int length = outputView.length();
            if (length > MAX_OUTPUT_CHARS) {
                CharSequence kept = outputView.getText().subSequence(
                        length - KEEP_OUTPUT_CHARS, length);
                outputView.setText(getString(R.string.console_output_truncated_format, kept));
            }
            outputScroll.post(() -> outputScroll.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    private void navigateHistory(int direction) {
        if (history.isEmpty()) return;
        historyPosition = Math.max(0, Math.min(history.size(), historyPosition + direction));
        commandInput.setText(historyPosition == history.size() ? "" : history.get(historyPosition));
        commandInput.setSelection(commandInput.length());
    }

    private void addHistory(String command) {
        history.remove(command);
        history.add(command);
        while (history.size() > MAX_HISTORY) history.remove(0);
        saveHistory();
    }

    private void loadHistory() {
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_HISTORY, "[]");
        try {
            JSONArray array = new JSONArray(saved);
            for (int index = 0; index < array.length(); index++) {
                String command = array.optString(index, "");
                if (!command.isEmpty()) history.add(command);
            }
        } catch (JSONException ignored) { }
    }

    private void saveHistory() {
        JSONArray array = new JSONArray();
        for (String command : history) array.put(command);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_HISTORY, array.toString()).apply();
    }

    private void copyOutput() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(
                getString(R.string.console_clipboard_label), outputView.getText()));
        Toast.makeText(this, R.string.console_copied, Toast.LENGTH_SHORT).show();
    }

    private void exportOutput(Uri uri) {
        if (uri == null) return;
        String snapshot = outputView.getText().toString();
        executor.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                if (output == null) throw new IOException(
                        getString(R.string.console_open_target_failed));
                output.write(snapshot.getBytes(StandardCharsets.UTF_8));
                runOnUiThread(() -> Toast.makeText(this,
                        R.string.console_exported, Toast.LENGTH_LONG).show());
            } catch (Exception exception) {
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.console_export_failed_format, exception.getMessage()),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void setRootChecked(boolean checked) {
        changingRootSwitch = true;
        rootSwitch.setChecked(checked);
        changingRootSwitch = false;
    }

    private static String displayTimestamp() {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
    }

    private static String fileTimestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
    }

    @Override
    protected void onDestroy() {
        Process process;
        synchronized (processLock) {
            process = runningProcess;
            runningProcess = null;
        }
        if (process != null) process.destroy();
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }

    private enum SuMode {
        NONE(R.string.console_shell_label),
        DASH_C(R.string.console_root_dash_label),
        UID_ZERO(R.string.console_root_uid_label);
        final int labelRes;
        SuMode(int labelRes) { this.labelRes = labelRes; }
    }
}
