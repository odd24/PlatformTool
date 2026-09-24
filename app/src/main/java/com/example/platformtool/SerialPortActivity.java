package com.example.platformtool;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SerialPortActivity extends PlatformActivity {
    private static final int MAX_LOG_CHARS = 100_000;
    private static final String[] BAUD_RATES = {
            "50", "75", "110", "134", "150", "200", "300", "600", "1200", "1800",
            "2400", "4800", "9600", "14400", "19200", "28800", "38400", "56000",
            "57600", "76800", "115200", "128000", "153600", "230400", "256000",
            "460800", "500000", "576000", "921600", "1000000", "1152000", "1500000",
            "2000000", "2500000", "3000000", "3500000", "4000000", "自定义…"
    };
    private static final int MAX_BAUD_RATE = 10_000_000;

    private final ExecutorService ioExecutor = Executors.newCachedThreadPool();
    private final AtomicInteger connectionGeneration = new AtomicInteger();
    private final Object writeLock = new Object();
    private Spinner deviceSpinner;
    private Spinner baudSpinner;
    private Spinner receiveModeSpinner;
    private Spinner sendModeSpinner;
    private EditText pathInput;
    private EditText sendInput;
    private EditText customBaudInput;
    private Switch portSwitch;
    private TextView statusText;
    private TextView receiveText;
    private ScrollView receiveScroll;
    private Button sendButton;
    private Button refreshButton;
    private ArrayAdapter<String> deviceAdapter;
    private final List<String> devicePaths = new ArrayList<>();
    private volatile FileInputStream serialInput;
    private volatile FileOutputStream serialOutput;
    private volatile boolean portOpen;
    private boolean changingSwitchProgrammatically;

    private final ActivityResultLauncher<String> exportDocument = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/plain"), uri -> {
                if (uri != null) exportLog(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_serial_port);
        setTitle("串口收发工具");
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        bindViews();
        setupControls();
        refreshDeviceList();
    }

    private void bindViews() {
        deviceSpinner = findViewById(R.id.serialDeviceSpinner);
        baudSpinner = findViewById(R.id.baudRateSpinner);
        receiveModeSpinner = findViewById(R.id.receiveModeSpinner);
        sendModeSpinner = findViewById(R.id.sendModeSpinner);
        pathInput = findViewById(R.id.serialPathInput);
        sendInput = findViewById(R.id.serialSendInput);
        customBaudInput = findViewById(R.id.customBaudRateInput);
        portSwitch = findViewById(R.id.serialPortSwitch);
        statusText = findViewById(R.id.serialStatusText);
        receiveText = findViewById(R.id.serialReceiveText);
        receiveScroll = findViewById(R.id.serialReceiveScroll);
        sendButton = findViewById(R.id.sendSerialButton);
        refreshButton = findViewById(R.id.refreshSerialDevicesButton);
    }

    private void setupControls() {
        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, devicePaths);
        deviceSpinner.setAdapter(deviceAdapter);
        deviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < devicePaths.size()) {
                    String selected = devicePaths.get(position);
                    if (selected.startsWith("/dev/")) pathInput.setText(selected);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        baudSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, BAUD_RATES));
        baudSpinner.setSelection(Arrays.asList(BAUD_RATES).indexOf("115200"));
        baudSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean custom = position == BAUD_RATES.length - 1;
                customBaudInput.setVisibility(custom ? View.VISIBLE : View.GONE);
                customBaudInput.setEnabled(custom && !portOpen);
                if (custom) customBaudInput.requestFocus();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        String[] dataModes = {"普通文本", "HEX"};
        receiveModeSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, dataModes));
        sendModeSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, dataModes));

        refreshButton.setOnClickListener(v -> refreshDeviceList());
        findViewById(R.id.clearSerialLogButton).setOnClickListener(v -> receiveText.setText(""));
        findViewById(R.id.exportSerialLogButton).setOnClickListener(v -> {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            exportDocument.launch("serial_log_" + timestamp + ".txt");
        });
        sendButton.setOnClickListener(v -> sendCurrentInput());
        portSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (changingSwitchProgrammatically) return;
            if (isChecked) openSelectedPort(); else closePort("串口已关闭");
        });
    }

    private void refreshDeviceList() {
        String previous = pathInput.getText().toString().trim();
        List<String> found = scanSerialDevices();
        devicePaths.clear();
        if (found.isEmpty()) devicePaths.add("未自动检测到设备，请手动输入路径");
        else devicePaths.addAll(found);
        deviceAdapter.notifyDataSetChanged();
        if (!previous.isEmpty()) {
            pathInput.setText(previous);
            int index = devicePaths.indexOf(previous);
            if (index >= 0) deviceSpinner.setSelection(index);
        }
        statusText.setText(String.format(Locale.getDefault(),
                "串口已关闭；自动检测到 %d 个候选设备", found.size()));
    }

    private static List<String> scanSerialDevices() {
        File directory = new File("/dev");
        File[] files = directory.listFiles((dir, name) -> isSerialDeviceName(name));
        Set<String> candidates = new LinkedHashSet<>();
        if (files != null) {
            for (File file : files) candidates.add(file.getAbsolutePath());
        }
        // Many Android SELinux policies allow stat/open on an assigned UART but deny listing /dev.
        String[] commonPrefixes = {
                "ttyS", "ttyUSB", "ttyACM", "ttyHS", "ttyHSL", "ttyMT",
                "ttyAMA", "ttyFIQ", "ttyXRUSB", "ttyGS", "ttyLP", "ttyMSM"
        };
        for (String prefix : commonPrefixes) {
            for (int index = 0; index < 32; index++) {
                File candidate = new File("/dev/" + prefix + index);
                if (candidate.exists() && !candidate.isDirectory()) {
                    candidates.add(candidate.getAbsolutePath());
                }
            }
        }
        List<String> result = new ArrayList<>(candidates);
        Collections.sort(result);
        return result;
    }

    private static boolean isSerialDeviceName(String name) {
        return name.matches("tty(S|USB|ACM|HS|HSL|MT|AMA|FIQ|XRUSB|GS|LP|MSM)[0-9]+")
                || name.matches("tty[0-9]+");
    }

    private void openSelectedPort() {
        String path = pathInput.getText().toString().trim();
        if (!path.startsWith("/dev/") || path.contains("\n") || path.contains("\r")) {
            showOpenFailure("请输入有效的 /dev/ 串口设备路径");
            return;
        }
        File device = new File(path);
        if (!device.exists() || device.isDirectory()) {
            showOpenFailure("串口设备不存在：" + path);
            return;
        }
        int baudRate;
        try {
            baudRate = selectedBaudRate();
        } catch (IllegalArgumentException exception) {
            showOpenFailure(exception.getMessage());
            return;
        }
        int generation = connectionGeneration.incrementAndGet();
        setConfigurationEnabled(false);
        sendButton.setEnabled(false);
        statusText.setText("正在配置并打开 " + path + " …");
        ioExecutor.execute(() -> openPortInBackground(device, baudRate, generation));
    }

    private int selectedBaudRate() {
        boolean custom = baudSpinner.getSelectedItemPosition() == BAUD_RATES.length - 1;
        String value = custom
                ? customBaudInput.getText().toString().trim()
                : (String) baudSpinner.getSelectedItem();
        if (value.isEmpty()) throw new IllegalArgumentException("请输入自定义波特率");
        final int baudRate;
        try {
            baudRate = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("波特率必须是有效的整数");
        }
        if (baudRate <= 0 || baudRate > MAX_BAUD_RATE) {
            throw new IllegalArgumentException("波特率范围必须在 1 到 " + MAX_BAUD_RATE + " 之间");
        }
        return baudRate;
    }

    private void openPortInBackground(File device, int baudRate, int generation) {
        FileInputStream input = null;
        FileOutputStream output = null;
        try {
            String configurationError = configureWithStty(device.getAbsolutePath(), baudRate);
            if (configurationError != null) throw new IOException(configurationError);
            input = new FileInputStream(device);
            output = new FileOutputStream(device);
            if (generation != connectionGeneration.get()) {
                closeQuietly(input);
                closeQuietly(output);
                return;
            }
            serialInput = input;
            serialOutput = output;
            portOpen = true;
            runOnUiThread(() -> {
                statusText.setText(String.format(Locale.getDefault(),
                        "已打开 %s · %d baud · 8N1", device.getAbsolutePath(), baudRate));
                sendButton.setEnabled(true);
                appendRecord("SYS", "串口已打开");
            });
            readLoop(input, generation);
        } catch (Exception exception) {
            closeQuietly(input);
            closeQuietly(output);
            if (generation == connectionGeneration.get()) {
                runOnUiThread(() -> showOpenFailure(openErrorMessage(exception)));
            }
        }
    }

    private String configureWithStty(String path, int baudRate) {
        String executable = new File("/system/bin/stty").exists()
                ? "/system/bin/stty" : "/system/bin/toybox";
        List<String> command = new ArrayList<>();
        command.add(executable);
        if (executable.endsWith("toybox")) command.add("stty");
        Collections.addAll(command, "-F", path, String.valueOf(baudRate),
                "raw", "-echo", "cs8", "-cstopb", "-parenb", "-ixon", "-ixoff");
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = readProcessOutput(process.getInputStream());
            int result = process.waitFor();
            return result == 0 ? null : "stty 配置失败" + (output.isEmpty() ? "" : "：" + output.trim());
        } catch (Exception exception) {
            return "系统缺少可用的 stty，无法设置波特率：" + exception.getMessage();
        }
    }

    private void readLoop(FileInputStream input, int generation) throws IOException {
        byte[] buffer = new byte[1024];
        while (portOpen && generation == connectionGeneration.get()) {
            int count = input.read(buffer);
            if (count < 0) throw new IOException("串口已断开");
            if (count == 0) continue;
            byte[] received = Arrays.copyOf(buffer, count);
            runOnUiThread(() -> appendReceived(received));
        }
    }

    private void appendReceived(byte[] data) {
        if (!portOpen) return;
        if (receiveModeSpinner.getSelectedItemPosition() == 1) {
            appendRecord("RX HEX <", bytesToHex(data));
        } else {
            appendRecord("RX <", new String(data, StandardCharsets.UTF_8));
        }
    }

    private void sendCurrentInput() {
        if (!portOpen || serialOutput == null) {
            Toast.makeText(this, "请先打开串口", Toast.LENGTH_SHORT).show();
            return;
        }
        String content = sendInput.getText().toString();
        if (content.isEmpty()) return;
        final byte[] data;
        final boolean hexMode = sendModeSpinner.getSelectedItemPosition() == 1;
        try {
            data = hexMode ? parseHex(content) : content.getBytes(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        ioExecutor.execute(() -> {
            try {
                synchronized (writeLock) {
                    FileOutputStream output = serialOutput;
                    if (!portOpen || output == null) throw new IOException("串口未打开");
                    output.write(data);
                    output.flush();
                }
                runOnUiThread(() -> {
                    if (hexMode) appendRecord("TX HEX >", bytesToHex(data));
                    else appendRecord("TX >", content);
                });
            } catch (IOException exception) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "发送失败：" + exception.getMessage(), Toast.LENGTH_LONG).show();
                    setPortSwitchChecked(false);
                    closePort("串口发送失败，连接已关闭");
                });
            }
        });
    }

    private static byte[] parseHex(String input) {
        String normalized = input.replace("0x", "").replace("0X", "")
                .replaceAll("[\\s,:-]", "");
        if (normalized.isEmpty()) return new byte[0];
        if (!normalized.matches("[0-9A-Fa-f]+")) {
            throw new IllegalArgumentException("HEX 内容只能包含 0-9、A-F 和分隔符");
        }
        if ((normalized.length() & 1) != 0) {
            throw new IllegalArgumentException("HEX 内容长度必须为偶数，每个字节需要两位");
        }
        byte[] result = new byte[normalized.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(normalized.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    private void exportLog(Uri destination) {
        String path = pathInput.getText().toString().trim();
        String baud = displayedBaudRate();
        String exportedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String content = "串口收发记录\n"
                + "设备：" + (path.isEmpty() ? "未选择" : path) + "\n"
                + "波特率：" + baud + "\n"
                + "导出时间：" + exportedAt + "\n"
                + "接收显示：" + receiveModeSpinner.getSelectedItem() + "\n"
                + "发送格式：" + sendModeSpinner.getSelectedItem() + "\n"
                + "----------------------------------------\n"
                + receiveText.getText().toString();
        ioExecutor.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(destination, "wt")) {
                if (output == null) throw new IOException("无法创建目标文件");
                output.write(content.getBytes(StandardCharsets.UTF_8));
                output.flush();
                runOnUiThread(() -> Toast.makeText(this, "串口记录已导出", Toast.LENGTH_LONG).show());
            } catch (IOException | RuntimeException exception) {
                runOnUiThread(() -> Toast.makeText(this,
                        "导出失败：" + exception.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private String displayedBaudRate() {
        if (baudSpinner.getSelectedItemPosition() == BAUD_RATES.length - 1) {
            String custom = customBaudInput.getText().toString().trim();
            return custom.isEmpty() ? "自定义（未填写）" : custom;
        }
        Object selected = baudSpinner.getSelectedItem();
        return selected == null ? "未选择" : selected.toString();
    }

    private static String bytesToHex(byte[] data) {
        StringBuilder builder = new StringBuilder(data.length * 3);
        for (byte value : data) builder.append(String.format(Locale.US, "%02X ", value & 0xFF));
        return builder.toString().trim();
    }

    private void appendLog(String content) {
        if (receiveText.length() + content.length() > MAX_LOG_CHARS) {
            receiveText.setText(receiveText.getText().subSequence(
                    Math.min(20_000, receiveText.length()), receiveText.length()));
        }
        receiveText.append(content);
        receiveScroll.post(() -> receiveScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void appendRecord(String type, String content) {
        String timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        String record = "[" + timestamp + "] " + type + " " + content;
        if (!record.endsWith("\n")) record += "\n";
        appendLog(record);
    }

    private void showOpenFailure(String message) {
        portOpen = false;
        serialInput = null;
        serialOutput = null;
        setPortSwitchChecked(false);
        setConfigurationEnabled(true);
        sendButton.setEnabled(false);
        statusText.setText(message);
        appendRecord("ERROR", message);
    }

    private static String openErrorMessage(Exception exception) {
        String detail = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        if (detail.toLowerCase(Locale.ROOT).contains("permission denied")) {
            return "打开失败：设备节点无读写权限。请由系统固件为应用配置 UART 权限/SELinux 策略。";
        }
        return "打开失败：" + detail;
    }

    private void closePort(String status) {
        connectionGeneration.incrementAndGet();
        portOpen = false;
        FileInputStream input = serialInput;
        FileOutputStream output = serialOutput;
        serialInput = null;
        serialOutput = null;
        closeQuietly(input);
        closeQuietly(output);
        sendButton.setEnabled(false);
        setConfigurationEnabled(true);
        statusText.setText(status);
    }

    private void setConfigurationEnabled(boolean enabled) {
        pathInput.setEnabled(enabled);
        deviceSpinner.setEnabled(enabled);
        baudSpinner.setEnabled(enabled);
        customBaudInput.setEnabled(enabled
                && baudSpinner.getSelectedItemPosition() == BAUD_RATES.length - 1);
        refreshButton.setEnabled(enabled);
    }

    private void setPortSwitchChecked(boolean checked) {
        changingSwitchProgrammatically = true;
        portSwitch.setChecked(checked);
        changingSwitchProgrammatically = false;
    }

    private static String readProcessOutput(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) return;
        try { closeable.close(); } catch (IOException ignored) { }
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }

    @Override
    protected void onStop() {
        if (portSwitch.isChecked()) setPortSwitchChecked(false);
        closePort("串口已关闭");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        closePort("串口已关闭");
        ioExecutor.shutdownNow();
        super.onDestroy();
    }
}
