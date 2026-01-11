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

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private Button startButton, stopButton;
    private static final int REQ_PERMS = 100;
    // If user triggers start but permissions are missing, remember to start after grant
    private boolean pendingStartRequest = false;

    // Local broadcast receiver to listen for recording state changes from the service
    private final BroadcastReceiver recordingStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            String action = intent.getAction();
            Log.i(TAG, "Received broadcast: " + action);
            if (RecordingService.ACTION_RECORDING_STARTED.equals(action)) {
                updateButtons(true);
            } else if (RecordingService.ACTION_RECORDING_STOPPED.equals(action)) {
                updateButtons(false);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkPermissions();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            startButton.setOnClickListener(v -> startRecording());
        }
        stopButton.setOnClickListener(v -> stopRecording());

        // Handle the case where the Activity was launched from the notification Stop action.
        Intent starting = getIntent();
        if (starting != null && RecordingService.ACTION_NOTIFICATION_STOP.equals(starting.getAction())) {
            Log.i(TAG, "Launched with ACTION_NOTIFICATION_STOP - stopping recording");
            stopRecording();
        }


        updateButtons(false);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Register local broadcast receiver to get immediate updates from service
        IntentFilter filter = new IntentFilter();
        filter.addAction(RecordingService.ACTION_RECORDING_STARTED);
        filter.addAction(RecordingService.ACTION_RECORDING_STOPPED);
        LocalBroadcastManager.getInstance(this).registerReceiver(recordingStateReceiver, filter);

        // If user was sent to settings to enable notifications or permissions, resume pending start
        if (pendingStartRequest) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                startRecording();
            }
            return;
        }

        // Restore UI state from persisted preferences (in case service is already running)
        boolean prefRecording = false;
        try {
            prefRecording = getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getBoolean("is_recording", false);
        } catch (Exception ignored) {}

        updateButtons(prefRecording);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister local broadcast receiver
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(recordingStateReceiver);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        if (RecordingService.ACTION_NOTIFICATION_STOP.equals(intent.getAction())) {
            Log.i(TAG, "onNewIntent: ACTION_NOTIFICATION_STOP received - stopping recording");
            stopRecording();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
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

        boolean needNotifications;
        // POST_NOTIFICATIONS required on Android 13+
        needNotifications = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;

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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        startRecording();
                    }
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void startRecording() {
        // Ensure we have at least one foreground location permission before starting FG service
        boolean hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasNotifications;
        hasNotifications = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;

        // Additionally check whether system notifications are enabled for this app (user can block them)
        boolean areNotificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled();
        if (!areNotificationsEnabled) {
            // Open app notification settings so the user can enable them
            pendingStartRequest = true;
            Toast.makeText(this, getString(R.string.notification_permission_required), Toast.LENGTH_LONG).show();
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            // open channel settings directly if supported
            intent.putExtra("android.provider.extra.CHANNEL_ID", "recording_channel");
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
        // Persist desired state immediately so lifecycle methods (onResume) reflect user's intent
        try {
            getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
                    .putBoolean("is_recording", true)
                    .putString("last_recording_action", "start")
                    .putLong("last_recording_action_ts", System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {}
        // Update in-memory flag so other parts of the app see the change right away
        try { RecordingService.sIsRunning = true; } catch (Exception ignored) {}
        updateButtons(true);
    }

    private void stopRecording() {
        // Persist stopped state immediately to avoid a race when onResume() reads the pref
        try {
            getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
                    .putBoolean("is_recording", false)
                    .putString("last_recording_action", "stop")
                    .putLong("last_recording_action_ts", System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {}
        try { RecordingService.sIsRunning = false; } catch (Exception ignored) {}

        Intent i = new Intent(this, RecordingService.class);
        i.setAction(RecordingService.ACTION_STOP);
        startService(i);
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
