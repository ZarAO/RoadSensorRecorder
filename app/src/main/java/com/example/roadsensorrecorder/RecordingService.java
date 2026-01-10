package com.example.roadsensorrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleService;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecordingService extends LifecycleService implements SensorEventListener {

    public static final String ACTION_START = "com.example.roadsensorrecorder.action.START";
    public static final String ACTION_STOP = "com.example.roadsensorrecorder.action.STOP";

    private static final String CHANNEL_ID = "recording_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final String TAG = "RecordingService";

    private SensorManager sensorManager;
    private Sensor accelerometer, gyroscope;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private BufferedWriter bufferedWriter;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                for (Location location : locationResult.getLocations()) {
                    writeLocation(location);
                }
            }
        };

        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Recording sensor and location data");
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, RecordingService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent activityIntent = new Intent(this, MainActivity.class);
        PendingIntent activityPending = PendingIntent.getActivity(this, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.recording_started))
                .setContentText(getString(R.string.recording_in_progress))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(activityPending)
                .addAction(new NotificationCompat.Action(0, getString(R.string.stop), stopPending))
                .setOngoing(true)
                .build();
    }

    private void startRecordingInternal() {
        ioExecutor.execute(() -> {
            try {
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                File file = new File(getExternalFilesDir(null), "sensor_data_" + timestamp + ".csv");
                bufferedWriter = new BufferedWriter(new FileWriter(file));
                bufferedWriter.write("Time,Type,X,Y,Z,Latitude,Longitude\n");
                bufferedWriter.flush();
            } catch (IOException e) {
                Log.e(TAG, "Failed to create file", e);
            }
        });

        // Register sensors
        if (accelerometer != null) sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        if (gyroscope != null) sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);

        // Request location updates - only if we have location permission
        try {
            boolean hasFine = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
            boolean hasCoarse = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (hasFine || hasCoarse) {
                LocationRequest req = LocationRequest.create();
                req.setInterval(1000);
                req.setFastestInterval(500);
                req.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                fusedLocationClient.requestLocationUpdates(req, locationCallback, getMainLooper());
            } else {
                Log.w(TAG, "Skipping location updates because no location permission granted");
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Location permission missing (caught)", e);
        }
    }

    private void stopRecordingInternal() {
        if (sensorManager != null) sensorManager.unregisterListener(this);
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        } catch (Exception e) {
            Log.w(TAG, "Error removing location updates", e);
        }

        ioExecutor.execute(() -> {
            try {
                if (bufferedWriter != null) {
                    bufferedWriter.flush();
                    bufferedWriter.close();
                    bufferedWriter = null;
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing file", e);
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopRecordingInternal();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        // Start foreground
        startRecordingInternal();
        Notification n = buildNotification();
        try {
            startForeground(NOTIFICATION_ID, n);
        } catch (SecurityException se) {
            // This can happen if the service declares location foreground type in manifest but the app
            // doesn't have the required runtime permission. Handle gracefully: log and continue without
            // throwing so the app doesn't crash.
            Log.e(TAG, "Failed to start foreground due to missing permission", se);
            // Do not rethrow; sensors continue to run but location updates were skipped earlier.
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRecordingInternal();
        ioExecutor.shutdownNow();
    }

    // SensorEventListener
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (bufferedWriter == null) return;
        if (event == null || event.sensor == null || event.values == null) return;

        String type = event.sensor.getType() == Sensor.TYPE_ACCELEROMETER ? "Accelerometer" : "Gyroscope";
        float x = event.values.length > 0 ? event.values[0] : 0f;
        float y = event.values.length > 1 ? event.values[1] : 0f;
        float z = event.values.length > 2 ? event.values[2] : 0f;

        String line = System.currentTimeMillis() + "," + type + "," + x + "," + y + "," + z + ",,\n";
        writeToFile(line);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void writeLocation(Location location) {
        if (location == null) return;
        String line = System.currentTimeMillis() + ",Location,,," + "," + location.getLatitude() + "," + location.getLongitude() + "\n";
        writeToFile(line);
    }

    private void writeToFile(String line) {
        ioExecutor.execute(() -> {
            try {
                if (bufferedWriter != null) {
                    bufferedWriter.write(line);
                }
            } catch (IOException e) {
                Log.e(TAG, "IO error", e);
            }
        });
    }
}
