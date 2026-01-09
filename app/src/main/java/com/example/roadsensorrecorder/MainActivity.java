package com.example.roadsensorrecorder;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private boolean isRecording = false;
    private Button startButton, stopButton;
    private static final int REQ_PERMS = 100;

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

    private void checkPermissions() {
        String[] permissions = new String[] {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
        };

        boolean hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasBg = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!hasFine || !hasCoarse || !hasBg) {
            ActivityCompat.requestPermissions(this, permissions, REQ_PERMS);
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
            }
        }
    }

    private void startRecording() {
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