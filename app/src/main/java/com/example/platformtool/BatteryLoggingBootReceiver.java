package com.example.platformtool;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

public class BatteryLoggingBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || !BatteryLoggingService.isRecording(context)) return;
        Intent serviceIntent = new Intent(context, BatteryLoggingService.class)
                .setAction(BatteryLoggingService.ACTION_RESUME);
        ContextCompat.startForegroundService(context, serviceIntent);
    }
}
