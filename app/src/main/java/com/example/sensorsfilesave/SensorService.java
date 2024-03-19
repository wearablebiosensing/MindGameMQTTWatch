package com.example.sensorsfilesave;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

//import org.eclipse.paho.android.service.MqttAndroidClient;
import info.mqtt.android.service.Ack;
import info.mqtt.android.service.MqttAndroidClient;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class SensorService extends Service implements SensorEventListener {
    private static final String TAG = "SensorService";
    private SensorManager sensorManager;
    private Sensor gyroscope;
    private Sensor heartRateSensor;
    private Sensor orientation;
    private Sensor acceleration;

    private String watchID;

    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "sensor_notification_channel";

    private MqttAndroidClient mqttAndroidClient;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getStringExtra("action");
            if ("start".equals(action)) {
                initMqttClient(); // Initialize MQTT client
            } else if ("stop".equals(action)) {
                disconnectMqttClient(); // Disconnect MQTT client
            }
        }
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initSensors();
        watchID = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID).substring(0, 6);
        startForeground(NOTIFICATION_ID, createNotification());
    }

    private void initSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
            //orientation = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
            acceleration = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);


            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, acceleration, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void initMqttClient() {
        String clientId = MqttClient.generateClientId();
        mqttAndroidClient = new MqttAndroidClient(this, "tcp://broker.hivemq.com:1883", clientId, Ack.AUTO_ACK);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);

        mqttAndroidClient.connect(options, null, new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                Log.d(TAG, "Connected to MQTT broker.");
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                Log.e(TAG, "Failed to connect to MQTT broker.", exception);
            }
        });
    }

    private void disconnectMqttClient() {
        if (mqttAndroidClient != null && mqttAndroidClient.isConnected()) {
            mqttAndroidClient.disconnect();
            mqttAndroidClient = null;
            Log.d(TAG, "Disconnected from MQTT broker.");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
        String formattedDate = sdf.format(new Date());
        String topicBase = watchID + "/";
        String type = "";
        String data = "";

        switch (event.sensor.getType()) {

            case Sensor.TYPE_GYROSCOPE:
                type = "gyroscope";
                data = String.format("%f,%f,%f,%d,%s",
                        event.values[0], event.values[1], event.values[2],
                        event.timestamp, formattedDate);
                break;
            case Sensor.TYPE_ACCELEROMETER:
                type = "accelerometer";
                data = String.format("%f,%f,%f,%d,%s",
                        event.values[0], event.values[1], event.values[2],
                        event.timestamp, formattedDate);
                break;
            case Sensor.TYPE_HEART_RATE:
                type = "heartrate";
                data = String.format("%f,%d,%s",
                        event.values[0],
                        event.timestamp, formattedDate);
                break;
            default:
                // Fallback for unknown sensors.
                data = formattedDate; // Simple example, adjust as necessary.
                break;
        }

        String topic = topicBase + type;
        publishSensorData(topic, data);
    }


    private void publishSensorData(String topic, String payload) {
        if (mqttAndroidClient != null && mqttAndroidClient.isConnected()) {
            mqttAndroidClient.publish(topic, new MqttMessage(payload.getBytes()));
            //Log.d(TAG, "Data published to topic: " + topic);
        } else {
            //Log.e(TAG, "MQTT client is not connected. Cannot publish to topic: " + topic);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Sensor Service")
                .setContentText("Publishing sensor data to MQTT topics")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Sensor Service Channel", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
        return builder.build();
    }
}
