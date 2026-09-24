package com.example.platformtool;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QuickToolsActivity extends AppCompatActivity {
    private static final String SHOW_TOUCHES = "show_touches";
    private static final String POINTER_LOCATION = "pointer_location";

    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor();
    private SwitchCompat showTouchesSwitch;
    private SwitchCompat flashlightSwitch;
    private SwitchCompat vibrationSwitch;
    private SwitchCompat keepScreenOnSwitch;
    private TextView statusView;
    private CameraManager cameraManager;
    private String flashCameraId;
    private Vibrator vibrator;
    private boolean updatingSwitch;
    private boolean torchEnabled;
    private boolean pendingTorchEnable;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted && pendingTorchEnable) {
                    setTorch(true);
                } else if (!granted) {
                    setChecked(flashlightSwitch, false);
                    showStatus(getString(R.string.quick_tools_camera_permission_denied), true);
                }
                pendingTorchEnable = false;
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quick_tools);
        setTitle(R.string.quick_tools_title);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        statusView = findViewById(R.id.quickToolsStatus);
        showTouchesSwitch = findViewById(R.id.showTouchesSwitch);
        flashlightSwitch = findViewById(R.id.flashlightSwitch);
        vibrationSwitch = findViewById(R.id.vibrationSwitch);
        keepScreenOnSwitch = findViewById(R.id.keepScreenOnSwitch);

        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        flashCameraId = findFlashCameraId();
        if (flashCameraId == null) {
            flashlightSwitch.setEnabled(false);
            flashlightSwitch.setText(R.string.quick_tools_flashlight_unavailable);
        }

        if (BuildConfig.ENGINEERING_FEATURES) {
            showTouchesSwitch.setOnCheckedChangeListener((button, checked) -> {
                if (!updatingSwitch) changeTouchDebugSetting(checked);
            });
            refreshProtectedSwitches();
        } else {
            showTouchesSwitch.setVisibility(View.GONE);
            findViewById(R.id.quickToolsEngineeringHelp).setVisibility(View.GONE);
            statusView.setText(R.string.quick_tools_standard_status);
        }
        flashlightSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!updatingSwitch) requestTorch(checked);
        });
        vibrationSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!updatingSwitch) setVibration(checked);
        });
        keepScreenOnSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (BuildConfig.ENGINEERING_FEATURES && showTouchesSwitch != null) {
            refreshProtectedSwitches();
        }
    }

    private void refreshProtectedSwitches() {
        boolean showTouches = Settings.System.getInt(
                getContentResolver(), SHOW_TOUCHES, 0) != 0;
        boolean pointerLocation = Settings.System.getInt(
                getContentResolver(), POINTER_LOCATION, 0) != 0;
        setChecked(showTouchesSwitch, showTouches && pointerLocation);
    }

    private void changeTouchDebugSetting(boolean enabled) {
        showTouchesSwitch.setEnabled(false);
        showStatus(getString(R.string.quick_tools_touch_updating), false);
        commandExecutor.execute(() -> {
            boolean success = writeTouchDebugSettings(enabled);
            runOnUiThread(() -> {
                showTouchesSwitch.setEnabled(true);
                if (success) {
                    setChecked(showTouchesSwitch, enabled);
                    showStatus(getString(enabled
                            ? R.string.quick_tools_touch_enabled
                            : R.string.quick_tools_touch_disabled), false);
                } else {
                    refreshProtectedSwitches();
                    showStatus(getString(R.string.quick_tools_touch_failed), true);
                }
            });
        });
    }

    private boolean writeTouchDebugSettings(boolean enabled) {
        int value = enabled ? 1 : 0;
        try {
            boolean touchesWritten = Settings.System.putInt(
                    getContentResolver(), SHOW_TOUCHES, value);
            boolean pointerWritten = Settings.System.putInt(
                    getContentResolver(), POINTER_LOCATION, value);
            if (touchesWritten && pointerWritten) return true;
        } catch (SecurityException | IllegalArgumentException ignored) {
            // Continue with the adb/root bridge used on restricted vendor ROMs.
        }
        if (writeViaQuickToolsBridge(value)) return true;
        String command = "settings put system " + SHOW_TOUCHES + " " + value
                + " && settings put system " + POINTER_LOCATION + " " + value;
        return runCommand(new String[]{"su", "-c", command})
                || runCommand(new String[]{"su", "0", "sh", "-c", command});
    }

    private boolean writeViaQuickToolsBridge(int value) {
        File marker = new File(getFilesDir(), "quick-tools.bridge");
        if (!marker.isFile()) return false;
        File request = new File(getFilesDir(), "quick-tools.request");
        File response = new File(getFilesDir(), "quick-tools.response");
        if (response.exists() && !response.delete()) return false;
        try (FileOutputStream output = new FileOutputStream(request, false)) {
            output.write(Integer.toString(value).getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (IOException exception) {
            return false;
        }
        long deadline = System.currentTimeMillis() + 4_000L;
        while (System.currentTimeMillis() < deadline) {
            if (response.isFile()) {
                try (FileInputStream input = new FileInputStream(response)) {
                    byte[] buffer = new byte[32];
                    int count = input.read(buffer);
                    String result = count > 0
                            ? new String(buffer, 0, count, StandardCharsets.UTF_8).trim()
                            : "";
                    return result.equals(value + ":ok");
                } catch (IOException ignored) {
                    return false;
                }
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private boolean runCommand(String[] command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            long deadline = System.currentTimeMillis() + 4_000L;
            while (System.currentTimeMillis() < deadline) {
                try {
                    return process.exitValue() == 0;
                } catch (IllegalThreadStateException stillRunning) {
                    Thread.sleep(50L);
                }
            }
            process.destroy();
            return false;
        } catch (IOException | InterruptedException | RuntimeException ignored) {
            if (ignored instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

    private String findFlashCameraId() {
        if (cameraManager == null) return null;
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                Boolean available = cameraManager.getCameraCharacteristics(cameraId)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(available)) return cameraId;
            }
        } catch (CameraAccessException | SecurityException ignored) {
        }
        return null;
    }

    private void requestTorch(boolean enabled) {
        if (!enabled) {
            setTorch(false);
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            pendingTorchEnable = true;
            setChecked(flashlightSwitch, false);
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            return;
        }
        setTorch(true);
    }

    private void setTorch(boolean enabled) {
        if (cameraManager == null || flashCameraId == null) {
            setChecked(flashlightSwitch, false);
            return;
        }
        try {
            cameraManager.setTorchMode(flashCameraId, enabled);
            torchEnabled = enabled;
            setChecked(flashlightSwitch, enabled);
            showStatus(getString(enabled
                    ? R.string.quick_tools_flashlight_enabled
                    : R.string.quick_tools_flashlight_disabled), false);
        } catch (CameraAccessException | SecurityException | IllegalArgumentException exception) {
            torchEnabled = false;
            setChecked(flashlightSwitch, false);
            showStatus(getString(R.string.quick_tools_flashlight_failed), true);
        }
    }

    @SuppressWarnings("deprecation")
    private void setVibration(boolean enabled) {
        if (vibrator == null || !vibrator.hasVibrator()) {
            setChecked(vibrationSwitch, false);
            vibrationSwitch.setEnabled(false);
            vibrationSwitch.setText(R.string.quick_tools_vibrator_unavailable_label);
            showStatus(getString(R.string.quick_tools_vibrator_unavailable), true);
            return;
        }
        if (enabled) {
            long[] pattern = {0, 500, 300};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
            showStatus(getString(R.string.quick_tools_vibration_enabled), false);
        } else {
            vibrator.cancel();
            showStatus(getString(R.string.quick_tools_vibration_disabled), false);
        }
    }

    private void setChecked(SwitchCompat target, boolean checked) {
        if (target == null) return;
        updatingSwitch = true;
        target.setChecked(checked);
        updatingSwitch = false;
    }

    private void showStatus(String message, boolean error) {
        statusView.setText(message);
        if (error) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        if (torchEnabled) setTorch(false);
        if (vibrator != null) vibrator.cancel();
        commandExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
