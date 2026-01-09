package com.example.roadsensorrecorder;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.*;
import android.location.*;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {

    private SensorManager sensorManager;
    private Sensor accelerometer, gyroscope;
    private LocationManager locationManager;
    private boolean isRecording = false;

    private FileWriter fileWriter;

    private Button startButton, stopButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        checkPermissions();

        startButton.setOnClickListener(v -> startRecording());
        stopButton.setOnClickListener(v -> stopRecording());
    }

    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        };
        ActivityCompat.requestPermissions(this, permissions, 100);
    }

    private void startRecording() {
        isRecording = true;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);

        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File file = new File(getExternalFilesDir(null), "sensor_data_" + timestamp + ".csv");
            fileWriter = new FileWriter(file);
            fileWriter.write("Time,Type,X,Y,Z,Latitude,Longitude\n");
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to create file", Toast.LENGTH_SHORT).show();
        }

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, this);
        }
    }

    private void stopRecording() {
        isRecording = false;
        startButton.setEnabled(true);
        stopButton.setEnabled(false);

        sensorManager.unregisterListener(this);
        locationManager.removeUpdates(this);

        try {
            if (fileWriter != null) {
                fileWriter.flush();
                fileWriter.close();
                Toast.makeText(this, "Recording saved", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isRecording || fileWriter == null) return;

        String type = event.sensor.getType() == Sensor.TYPE_ACCELEROMETER ? "Accelerometer" : "Gyroscope";
        String line = System.currentTimeMillis() + "," + type + "," +
                event.values[0] + "," +
                event.values[1] + "," +
                event.values[2] + ",,\n";
        writeToFile(line);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (!isRecording || fileWriter == null) return;

        String line = System.currentTimeMillis() + ",Location,,," +
                "," + location.getLatitude() + "," + location.getLongitude() + "\n";
        writeToFile(line);
    }

    private void writeToFile(String line) {
        try {
            fileWriter.write(line);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {}
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
}