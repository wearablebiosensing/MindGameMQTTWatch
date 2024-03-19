package com.example.sensorsfilesave;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.sensorsfilesave.databinding.ActivityMainBinding;

import java.util.ArrayList;

// Import statements for MQTT








public class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST_CODE = 123;
    private TextView tvMqttStatus;
    private TextView tvWatchID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkAndRequestPermissions();
        openAppSettings();

        //Setup TextViews
        tvMqttStatus = findViewById(R.id.tvMqttStatus);
        tvWatchID = findViewById(R.id.tvWatchID);
        updateWatchIDDisplay(); // Update watch ID display

        //Setup buttons
        Button btnStart = findViewById(R.id.btnStart);
        Button btnStop = findViewById(R.id.btnStop);

        btnStart.setOnClickListener(v -> {
            // Start the sensor data collection service and begin publishing
            Intent startIntent = new Intent(this, SensorService.class);
            startIntent.putExtra("action", "start");
            startService(startIntent);
            tvMqttStatus.setText("MQTT Status: Sending...");
            tvMqttStatus.setTextColor(Color.GREEN);
        });

        btnStop.setOnClickListener(v -> {
            // Stop publishing and the service
            Intent stopIntent = new Intent(this, SensorService.class);
            stopIntent.putExtra("action", "stop");
            startService(stopIntent);
            tvMqttStatus.setText("MQTT Status: Stopped");
            tvMqttStatus.setTextColor(Color.RED);

        });


    }

    private void updateWatchIDDisplay() {
        String watchID = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if (watchID != null && watchID.length() >= 6) {
            tvWatchID.setText("Watch ID: " + watchID.substring(0, 6));
        } else {
            tvWatchID.setText("Watch ID: Unknown");
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }
    private void checkAndRequestPermissions() {
        // List of permissions to request
        ArrayList<String> permissionsNeeded = new ArrayList<>();

        // Check each permission and add it to the list if not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WAKE_LOCK) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.WAKE_LOCK);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.FOREGROUND_SERVICE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.BODY_SENSORS);
        }

        // If the list isn't empty, request the permissions
        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }


    // ...

    // Override onRequestPermissionsResult to handle the result of the permission request
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Check if all requested permissions are granted
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                // Permissions are granted, continue with your logic
            } else {
                // Permissions are not granted, handle accordingly (e.g., show a message or exit the service)
            }
        }
    }



    // New Stuff added for mqtt


    //===================================
    // Network Conectivity Status
    //===================================
    private final BroadcastReceiver networkStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateInternetStatus();
        }
    };

    private void updateInternetStatus() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        TextView tvInternetStatus = findViewById(R.id.tvInternetStatus);
        tvInternetStatus.setText(isConnected ? "Internet Connected" : "No Internet");
        tvInternetStatus.setTextColor(isConnected ? Color.GREEN : Color.RED);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(networkStateReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    protected void onPause() {
        unregisterReceiver(networkStateReceiver);
        super.onPause();
    }



    //===================================
    // Buttons & MQTT
    //===================================






}
