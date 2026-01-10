package com.example.roadsensorrecorder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class NotificationActionReceiver extends BroadcastReceiver {
    private static final String TAG = "NotifActionReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (RecordingService.ACTION_NOTIFICATION_STOP.equals(action)) {
            Log.i(TAG, "Received notification STOP action");
            // Stop the RecordingService; RecordingService will broadcast RECORDING_STOPPED when done
            try {
                Intent stop = new Intent(context, RecordingService.class);
                stop.setAction(RecordingService.ACTION_STOP);
                // Use startForegroundService to ensure the service receives the stop intent even when
                // the receiver runs while the app is in background (restricted on newer Android).
                ContextCompat.startForegroundService(context, stop);
            } catch (Exception e) {
                Log.w(TAG, "Failed to stop RecordingService from notification action", e);
            }
        }
    }
}
