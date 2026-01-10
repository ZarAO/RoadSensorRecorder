package com.example.roadsensorrecorder;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private boolean isRecording = false;
    private Button startButton, stopButton;
    private static final int REQ_PERMS = 100;
    // If user triggers start but permissions are missing, remember to start after grant
    private boolean pendingStartRequest = false;

    private final BroadcastReceiver recordingStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            String action = intent.getAction();
            Log.i(TAG, "Broadcast received: " + action);
            switch (action) {
                case RecordingService.ACTION_RECORDING_STARTED:
                    Log.i(TAG, "Received ACTION_RECORDING_STARTED");
                    isRecording = true;
                    updateButtons(true);
                    break;
                case RecordingService.ACTION_RECORDING_STOPPED:
                    Log.i(TAG, "Received ACTION_RECORDING_STOPPED");
                    isRecording = false;
                    updateButtons(false);
                    break;
                case RecordingService.ACTION_NOTIFICATION_STOP:
                    Log.i(TAG, "Received ACTION_NOTIFICATION_STOP (from notification)");
                    // User tapped Stop in notification; trigger stop flow to update UI immediately
                    stopRecording();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);

        checkPermissions();

        startButton.setOnClickListener(v -> startRecording());
        stopButton.setOnClickListener(v -> stopRecording());

        updateButtons(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If user was sent to settings to enable notifications or permissions, resume pending start
        if (pendingStartRequest) {
            // small delay not necessary; just attempt to start again which will re-check permissions
            startRecording();
        }
        // Register receiver to update UI when service broadcasts start/stop
        IntentFilter f = new IntentFilter();
        f.addAction(RecordingService.ACTION_RECORDING_STARTED);
        f.addAction(RecordingService.ACTION_RECORDING_STOPPED);
        // On Android 14+ the registerReceiver API requires an explicit exported flag when
        // registering for non-system broadcasts. Use RECEIVER_NOT_EXPORTED so only our app
        // can receive these broadcasts.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerReceiver(recordingStateReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(recordingStateReceiver, f);
        }

        // If the service is already running, ensure UI reflects that (in case broadcast was missed)
        // Prefer the persistent state saved by the service as the source of truth in case the
        // activity missed the runtime broadcast (e.g., activity was paused). If it's not present
        // fall back to the in-memory flag on the service.
        boolean prefRecording = false;
        try {
            prefRecording = getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getBoolean("is_recording", false);
        } catch (Exception ignored) {}

        if (prefRecording || RecordingService.sIsRunning) {
            isRecording = true;
            updateButtons(true);
        } else {
            isRecording = false;
            updateButtons(false);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(recordingStateReceiver); } catch (Exception ignored) {}
    }

    private void checkPermissions() {
        // Base permissions
        String[] permissions = new String[] {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
        };

        boolean hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasBg = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;

        boolean needNotifications = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // POST_NOTIFICATIONS required on Android 13+
            needNotifications = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
        }

        if (!hasFine || !hasCoarse || !hasBg || needNotifications) {
            if (needNotifications) {
                // Request location + notifications together
                String[] all = new String[permissions.length + 1];
                System.arraycopy(permissions, 0, all, 0, permissions.length);
                all[permissions.length] = Manifest.permission.POST_NOTIFICATIONS;
                ActivityCompat.requestPermissions(this, all, REQ_PERMS);
            } else {
                ActivityCompat.requestPermissions(this, permissions, REQ_PERMS);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMS) {
            boolean granted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (!granted) {
                Toast.makeText(this, getString(R.string.location_permission_required), Toast.LENGTH_SHORT).show();
            } else {
                // If user granted permissions and previously attempted to start recording, start now
                if (pendingStartRequest) {
                    pendingStartRequest = false;
                    startRecording();
                }
            }
        }
    }

    private void startRecording() {
        // Ensure we have at least one foreground location permission before starting FG service
        boolean hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasNotifications = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotifications = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }

        // Additionally check whether system notifications are enabled for this app (user can block them)
        boolean areNotificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled();
        if (!areNotificationsEnabled) {
            // Open app notification settings so the user can enable them
            pendingStartRequest = true;
            Toast.makeText(this, getString(R.string.notification_permission_required), Toast.LENGTH_LONG).show();
            Intent intent = new Intent();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                // open channel settings directly if supported
                intent.putExtra("android.provider.extra.CHANNEL_ID", "recording_channel");
            } else {
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            }
            startActivity(intent);
            return;
        }

        if (!hasFine && !hasCoarse) {
            // Request permissions and remember to start after grant
            pendingStartRequest = true;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_PERMS);
            Toast.makeText(this, getString(R.string.location_permission_required), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasNotifications) {
            pendingStartRequest = true;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_PERMS);
            Toast.makeText(this, getString(R.string.notification_permission_required), Toast.LENGTH_SHORT).show();
            return;
        }

        Intent i = new Intent(this, RecordingService.class);
        i.setAction(RecordingService.ACTION_START);
        ContextCompat.startForegroundService(this, i);
        isRecording = true;
        updateButtons(true);
    }

    private void stopRecording() {
        Intent i = new Intent(this, RecordingService.class);
        i.setAction(RecordingService.ACTION_STOP);
        startService(i);
        isRecording = false;
        updateButtons(false);
    }

    private void updateButtons(boolean recording) {
        runOnUiThread(() -> {
            startButton.setEnabled(!recording);
            stopButton.setEnabled(recording);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
