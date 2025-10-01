package com.mrsat.authit;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.time.ZoneOffset;
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "AuthItMainActivity";
    private static final int REQUEST_ENABLE_BT = 1001;
    private static final int PERMISSIONS_REQUEST_CODE = 1002;
    private static final String NOTIFICATION_CHANNEL_ID = "authit_ble_channel";
    private static final int NOTIFICATION_ID = 1;

    private BluetoothLeAdvertiser advertiser;
    private BluetoothAdapter bluetoothAdapter;
    private NotificationManager notificationManager;

    private EditText passwordInput;
    private MaterialButton startBtn;
    private MaterialCardView statusContainer;
    private TextView statusText;
    private View statusIndicator;
    private boolean isRunning = false;
    private Handler handler = new Handler();
    private String currentHash;
    private String password;
    private boolean wasRunningBeforeLock = false;

    private long lastoffscreen = 0;
    private AdvertiseCallback callback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            notify_user("Advertising failed, error code: " + errorCode);
        }
    };

    private final BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastoffscreen < 1000) { 
                    return;
                }
                lastoffscreen = currentTime;

                if (isRunning) {
                    wasRunningBeforeLock = true;
                    stop_adv_tasks();
                    runOnUiThread(() -> {
                        statusText.setText("Phone Locked");
                        statusIndicator.setBackgroundTintList(ContextCompat.getColorStateList(MainActivity.this, R.color.warning));
                    });
                    update_notif("Phone locked", "unlock the phone to unlock your computer :)");
                }
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                handleScreenUnlock(context);
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
            }
        }
        
        private void handleScreenUnlock(Context context) {
            if (wasRunningBeforeLock) {
                wasRunningBeforeLock = false;
                
                handler.postDelayed(() -> {
                    if (isRunning && bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                        if (advertiser == null) {
                            advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
                        }
                        
                        if (advertiser != null) {
                            start_adv_tasks();
                            runOnUiThread(() -> {
                                statusText.setText("Broadcasting Active");
                                statusIndicator.setBackgroundTintList(ContextCompat.getColorStateList(MainActivity.this, R.color.success));
                            });
                            if (currentHash != null && !currentHash.isEmpty()) {
                                String pref_hash = currentHash.substring(0, Math.min(20, currentHash.length()));
                                update_notif("Broadcasting hash: " + pref_hash, pref_hash);
                            }
                        } else {
                            stop(); 
                            notify_user("Could not resume broadcast. BLE Advertiser not available.");
                        }
                    } else if (isRunning) {
                        stop(); 
                        notify_user("Could not resume broadcast. Please check Bluetooth and press Start.");
                    }
                }, 500);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        passwordInput = findViewById(R.id.passwordEditText);
        startBtn = findViewById(R.id.startButton);
        statusContainer = findViewById(R.id.statusContainer);
        statusText = findViewById(R.id.statusText);
        statusIndicator = findViewById(R.id.statusIndicator);

        BluetoothManager mgr = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (mgr != null) {
            bluetoothAdapter = mgr.getAdapter();
        } else {
            notify_user("Bluetooth Manager not available.");
            finish();
            return;
        }
        
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        create_notif_channel();

        check_req_perms();

        startBtn.setOnClickListener(v -> {
            if (isRunning) {
                stop();
            } else {
                start();
            }
        });

        registerScreenStateReceiver();
    }

    private void check_req_perms() {
        List<String> permissionsToRequest = new ArrayList<>();
        permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
        permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        List<String> permissionsNeeded = new ArrayList<>();
        for (String permission : permissionsToRequest) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSIONS_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                notify_user("Permissions granted.");
                if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                     advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
                     if (advertiser == null) {
                        notify_user("BLE Advertising not supported on this device after permission grant.");
                     }
                }
            } else {
                notify_user("Some permissions were denied. The app may not function correctly.");
            }
        }
    }

    private void updateUI() {
        runOnUiThread(() -> {
            if (isRunning) {
                startBtn.setText("Stop Broadcasting");
                startBtn.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_stop_simple));
                startBtn.setBackground(ContextCompat.getDrawable(this, R.drawable.button_gradient_stop));
                statusContainer.setVisibility(View.VISIBLE);
                statusText.setText("Broadcasting Active");
                statusIndicator.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.success));
            } else {
                startBtn.setText("Start Broadcasting");
                startBtn.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_arrow_right));
                startBtn.setBackground(ContextCompat.getDrawable(this, R.drawable.button_gradient));
                statusContainer.setVisibility(View.GONE);
            }
        });
    }

    void notify_user(String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    void start() {
        if (bluetoothAdapter == null) {
            notify_user("Bluetooth adapter not available.");
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            notify_user("Bluetooth is not enabled. Requesting to enable...");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return; 
        }
        proceed_start();
    }

    void proceed_start() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            notify_user("Bluetooth Advertise permission not granted.");
            check_req_perms();
            return;
        }

        if (advertiser == null) {
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
            }
            if (advertiser == null) {
                notify_user("Bluetooth LE Advertiser not available. Device may not support BLE advertising.");
                return;
            }
        }

        password = passwordInput.getText().toString();
        if (password.isEmpty()) {
            notify_user("Please enter a password.");
            return;
        }

        isRunning = true;
        wasRunningBeforeLock = false;
        updateUI();
        int randomIterations = new Random().nextInt(10000) + 10;
        currentHash = password;
        for (int i = 0; i < randomIterations; i++) {
            currentHash = sha512(currentHash);
        }

        start_adv_tasks();

        String hashPrefix = currentHash.substring(0, Math.min(20, currentHash.length()));
        notify_user("AuthIt Started.");
        update_notif("Broadcasting : " + hashPrefix, hashPrefix);
    }

    void stop() {
        isRunning = false;
        wasRunningBeforeLock = false;
        updateUI();
        stop_adv_tasks();

        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
        notify_user("AuthIt stopped.");
    }
    
    private void start_adv_tasks() {
        if (!isRunning) return; 
        if (advertiser == null) {
            notify_user("Cannot start advertising tasks: Advertiser not initialized.");
            return;
        }
        advertiser.stopAdvertising(callback); 
        
        broadcast_hash(); 
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::roll_hash, 600);
    }

    private void stop_adv_tasks() {
        handler.removeCallbacksAndMessages(null);
        if (advertiser != null && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED) {
            advertiser.stopAdvertising(callback);
        }
    }


    String sha512(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }


    void roll_hash() {
        if (!isRunning) return;

        String hashPrefixOld = currentHash.substring(0, 20);
        String padding = "";
        for (int i = 0; i < 108; i++)
            padding += "0";

        String currentDate = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String hashedPassword = sha512(password);
        String input_next = hashPrefixOld + padding + hashedPassword + currentDate;
        currentHash = sha512(input_next);

        if (currentHash.isEmpty()) {
            stop();
            notify_user("Error: Hash generation failed.");
            return;
        }

        start_adv_tasks();
        String currentHashPrefix = currentHash.substring(0, Math.min(20, currentHash.length()));
        update_notif("Broadcasting hash: " + currentHashPrefix, currentHashPrefix);
    }

    void broadcast_hash() {
        if (!isRunning || advertiser == null || currentHash == null || currentHash.isEmpty()) {
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            notify_user("Cannot broadcast: BLUETOOTH_ADVERTISE permission missing.");
            return;
        }

        try {
            String hashPrefix = currentHash.substring(0, Math.min(20, currentHash.length()));
            byte[] hashBytes = hashPrefix.getBytes(StandardCharsets.UTF_8);

            ParcelUuid uuid = ParcelUuid.fromString("0000FFF0-0000-1000-8000-00805F9B34FB");
            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) 
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW) // i reduced power for better battery life and proximity detection
                    .setConnectable(false)
                    .build();
            AdvertiseData data = new AdvertiseData.Builder()
                    .addServiceData(uuid, hashBytes)
                    .setIncludeDeviceName(false)
                    .build();

            advertiser.stopAdvertising(callback); 
            advertiser.startAdvertising(settings, data, callback);

        } catch (Exception e) {
            notify_user("Broadcast error: " + e.getMessage());
        }
    }
    
    private void create_notif_channel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "AuthIt Broadcasting Service";
            String description = "Notifications for AuthIt BLE broadcasting status";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void update_notif(String text, String bigTextContent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return; 
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("AuthIt Service")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(isRunning || wasRunningBeforeLock) 
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (bigTextContent != null && !bigTextContent.isEmpty()) {
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText("Current hash prefix: " + bigTextContent));
        }
        
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                notify_user("Bluetooth enabled.");
                if (bluetoothAdapter != null) { 
                    advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
                     if (advertiser == null) {
                        notify_user("BLE Advertising not supported on this device after enabling Bluetooth.");
                        return;
                     }
                }
            } else {
                notify_user("Bluetooth not enabled. Cannot start broadcasting.");
            }
        }
    }

    private boolean isReceiverRegistered = false;

    private void registerScreenStateReceiver() {
        if (!isReceiverRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
            registerReceiver(screenStateReceiver, filter);
            isReceiverRegistered = true;
        }
    }

    private void unregisterScreenStateReceiver() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(screenStateReceiver);
                isReceiverRegistered = false;
            } catch (IllegalArgumentException e) {
                // Receiver was not registered
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerScreenStateReceiver();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Don't unregister here - we want to keep listening for screen events
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterScreenStateReceiver();
        if (isRunning) { 
            stop();
        } else if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
        if (advertiser != null && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED) {
            advertiser.stopAdvertising(callback);
        }
    }
}
