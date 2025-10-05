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
import android.content.SharedPreferences;
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
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.widget.Toolbar;

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
    private BluetoothAdapter bluetooth_adapter;
    private NotificationManager notification_manager;

    private EditText password_input;
    private MaterialButton start_btn;
    private MaterialCardView status_container;
    private com.google.android.material.textfield.TextInputLayout password_input_layout;
    
    private static final String PREFS_NAME = "AuthItPrefs";
    private static final String SAVED_PASSWORD_HASH = "saved_password_hash";
    private static final String RUN_WITH_SCREEN_LOCKED = "run_with_screen_locked";
    private SharedPreferences shared_prefs;
    private TextView status_text;
    private View status_indicator;
    private boolean is_running = false;
    private Handler handler = new Handler();
    private String current_hash;
    private String password;
    private boolean was_running_before_lock = false;

    private long last_offscreen = 0;
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

    private final BroadcastReceiver stop_broadcast_receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.mrsat.authit.STOP_BROADCAST".equals(intent.getAction())) {
                if (is_running) {
                    stop();
                }
            }
        }
    };

    private final BroadcastReceiver screen_state_receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - last_offscreen < 1000) { 
                    return;
                }
                last_offscreen = currentTime;

                if (is_running) {
                    boolean run_with_lock = shared_prefs.getBoolean(RUN_WITH_SCREEN_LOCKED, false);
                    
                    if (run_with_lock) {
                        runOnUiThread(() -> {
                            status_text.setText("Running in Background");
                            status_indicator.setBackgroundTintList(ContextCompat.getColorStateList(MainActivity.this, R.color.accent_green));
                        });
                        update_notif("Running in background", "Auth-It is running while screen is locked");
                    } else {
                        was_running_before_lock = true;
                        stop_adv_tasks();
                        runOnUiThread(() -> {
                            status_text.setText("Phone Locked");
                            status_indicator.setBackgroundTintList(ContextCompat.getColorStateList(MainActivity.this, R.color.warning));
                        });
                        update_notif("Phone locked", "unlock the phone to unlock your computer :)");
                    }
                }
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                handle_screen_unlock(context);
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
            }
        }
        
        private void handle_screen_unlock(Context context) {
            boolean run_with_lock = shared_prefs.getBoolean(RUN_WITH_SCREEN_LOCKED, false);
            
            if (run_with_lock && is_running) {
                runOnUiThread(() -> {
                    status_text.setText("Broadcasting Active");
                    status_indicator.setBackgroundTintList(ContextCompat.getColorStateList(MainActivity.this, R.color.success));
                });
                if (current_hash != null && !current_hash.isEmpty()) {
                    String pref_hash = current_hash.substring(0, Math.min(20, current_hash.length()));
                    update_notif("Broadcasting hash: " + pref_hash, pref_hash);
                }
            } else if (was_running_before_lock) {
                was_running_before_lock = false;
                
                handler.postDelayed(() -> {
                    if (is_running && bluetooth_adapter != null && bluetooth_adapter.isEnabled()) {
                        if (advertiser == null) {
                            advertiser = bluetooth_adapter.getBluetoothLeAdvertiser();
                        }
                        
                        if (advertiser != null) {
                            start_adv_tasks();
                            runOnUiThread(() -> {
                                status_text.setText("Broadcasting Active");
                                status_indicator.setBackgroundTintList(ContextCompat.getColorStateList(MainActivity.this, R.color.success));
                            });
                            if (current_hash != null && !current_hash.isEmpty()) {
                                String pref_hash = current_hash.substring(0, Math.min(20, current_hash.length()));
                                update_notif("Broadcasting hash: " + pref_hash, pref_hash);
                            }
                        } else {
                            stop(); 
                            notify_user("Could not resume broadcast. BLE Advertiser not available.");
                        }
                    } else if (is_running) {
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

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        password_input = findViewById(R.id.password_edit_text);
        password_input_layout = findViewById(R.id.password_input_layout);
        start_btn = findViewById(R.id.start_button);
        status_container = findViewById(R.id.status_container);
        status_text = findViewById(R.id.status_text);
        status_indicator = findViewById(R.id.status_indicator);
        
        shared_prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        setup_password_ui();

        BluetoothManager mgr = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (mgr != null) {
            bluetooth_adapter = mgr.getAdapter();
        } else {
            notify_user("Bluetooth Manager not available.");
            finish();
            return;
        }
        
        notification_manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        create_notif_channel();

        check_req_perms();

        start_btn.setOnClickListener(v -> {
            if (is_running) {
                stop();
            } else {
                start();
            }
        });

        register_screen_state_receiver();
        register_stop_broadcast_receiver();
        
        update_ui();
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
                if (bluetooth_adapter != null && bluetooth_adapter.isEnabled()) {
                     advertiser = bluetooth_adapter.getBluetoothLeAdvertiser();
                     if (advertiser == null) {
                        notify_user("BLE Advertising not supported on this device after permission grant.");
                     }
                }
            } else {
                notify_user("Some permissions were denied. The app may not function correctly.");
            }
        }
    }

    private void update_ui() {
        runOnUiThread(() -> {
            if (is_running) {
                start_btn.setText("Stop Broadcasting");
                start_btn.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_stop_simple));
                start_btn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.error));
                status_container.setVisibility(View.VISIBLE);
                status_text.setText("Broadcasting Active");
                status_indicator.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.success));
            } else {
                start_btn.setText("Start Broadcasting");
                start_btn.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_arrow_right));
               
                start_btn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.custom_purple));
                status_container.setVisibility(View.GONE);
            }
            start_btn.clearFocus();
            start_btn.setPressed(false);
        });
    }

    void notify_user(String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    void start() {
        if (bluetooth_adapter == null) {
            notify_user("Bluetooth adapter not available.");
            return;
        }
        if (!bluetooth_adapter.isEnabled()) {
            notify_user("Bluetooth is not enabled. Requesting to enable...");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return; 
        }
        
        if (advertiser != null) {
            stop_adv_tasks();
            advertiser = null;
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
            if (bluetooth_adapter != null && bluetooth_adapter.isEnabled()) {
                advertiser = bluetooth_adapter.getBluetoothLeAdvertiser();
            }
            if (advertiser == null) {
                notify_user("Bluetooth LE Advertiser not available. Device may not support BLE advertising.");
                return;
            }
        }

        String saved_hash = shared_prefs.getString(SAVED_PASSWORD_HASH, null);
        
        if (saved_hash != null && !saved_hash.isEmpty()) {
            password = saved_hash;
            current_hash = saved_hash;
        } else {
            String inputPassword = password_input.getText().toString();
            if (inputPassword.isEmpty()) {
                notify_user("Please enter a password.");
                return;
            }
            String passwordHash = sha512(inputPassword);
            shared_prefs.edit().putString(SAVED_PASSWORD_HASH, passwordHash).apply();
            setup_password_ui();
            password = passwordHash;
            current_hash = passwordHash;
        }

        is_running = true;
        was_running_before_lock = false;
        update_ui();

        String hashPrefix = current_hash.substring(0, Math.min(20, current_hash.length()));
        String padding = "0".repeat(108);
        String currentDate = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String input_next = hashPrefix + padding + password + currentDate;
        current_hash = sha512(input_next);

        start_adv_tasks();

        String updatedHashPrefix = current_hash.substring(0, Math.min(20, current_hash.length()));
        notify_user("AuthIt Started.");
        update_notif("Broadcasting : " + updatedHashPrefix, updatedHashPrefix);
    }

    void stop() {
        is_running = false;
        was_running_before_lock = false;
        update_ui();
        stop_adv_tasks();
    
        advertiser = null;

        if (notification_manager != null) {
            notification_manager.cancel(NOTIFICATION_ID);
        }
        notify_user("AuthIt stopped.");
    }
    
    private void start_adv_tasks() {
        if (!is_running) return; 
        if (advertiser == null) {
            notify_user("Cannot start advertising tasks: Advertiser not initialized.");
            return;
        }
        advertiser.stopAdvertising(callback); 
        
        broadcast_hash(); 
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::roll_hash, 200);
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
        if (!is_running) return;

        String hashPrefixOld = current_hash.substring(0, Math.min(20, current_hash.length()));
        // Use same padding length as Linux client (128 - 20 = 108)
        String padding = "0".repeat(108);

        String currentDate = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String input_next = hashPrefixOld + padding + password + currentDate;
        current_hash = sha512(input_next);

        if (current_hash.isEmpty()) {
            stop();
            notify_user("Error: Hash generation failed.");
            return;
        }

        start_adv_tasks();
        String current_hashPrefix = current_hash.substring(0, Math.min(20, current_hash.length()));
        update_notif("Broadcasting hash: " + current_hashPrefix, current_hashPrefix);
    }

    void broadcast_hash() {
        if (!is_running || advertiser == null || current_hash == null || current_hash.isEmpty()) {
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            notify_user("Cannot broadcast: BLUETOOTH_ADVERTISE permission missing.");
            return;
        }

        try {
            String hashPrefix = current_hash.substring(0, Math.min(20, current_hash.length()));
            byte[] hashBytes = hashPrefix.getBytes(StandardCharsets.UTF_8);

            ParcelUuid uuid = ParcelUuid.fromString("0000FFF0-0000-1000-8000-00805F9B34FB");
            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) 
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW) // ultra low to save space and battery
                    .setConnectable(false)
                    .setTimeout(0)
                    .build();
            AdvertiseData data = new AdvertiseData.Builder()
                    .addServiceData(uuid, hashBytes)
                    .setIncludeDeviceName(false) // exclude device name
                    .setIncludeTxPowerLevel(false) // exclude TX power level
                    .build();
            
            AdvertiseData scanResponse = new AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(false)
                    .build();

            advertiser.stopAdvertising(callback); 
            advertiser.startAdvertising(settings, data, scanResponse, callback);

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
            notification_manager.createNotificationChannel(channel);
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
                .setOngoing(is_running || was_running_before_lock) 
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (bigTextContent != null && !bigTextContent.isEmpty()) {
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText("Current hash prefix: " + bigTextContent));
        }
        
        if (notification_manager != null) {
            notification_manager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                notify_user("Bluetooth enabled.");
                if (bluetooth_adapter != null) { 
                    advertiser = bluetooth_adapter.getBluetoothLeAdvertiser();
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

    private boolean is_receiver_registered = false;

    private void register_screen_state_receiver() {
        if (!is_receiver_registered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screen_state_receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(screen_state_receiver, filter);
            }
            is_receiver_registered = true;
        }
    }

    private void unregister_screen_state_receiver() {
        if (is_receiver_registered) {
            try {
                unregisterReceiver(screen_state_receiver);
                is_receiver_registered = false;
            } catch (IllegalArgumentException e) {
                // Receiver was not registered
            }
        }
    }

    private void register_stop_broadcast_receiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.mrsat.authit.STOP_BROADCAST");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stop_broadcast_receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stop_broadcast_receiver, filter);
        }
    }

    private void setup_password_ui() {
        String savedpasshash = shared_prefs.getString(SAVED_PASSWORD_HASH, null);
        
        if (savedpasshash != null && !savedpasshash.isEmpty()) {
            password_input.setEnabled(false);
            password_input.setText("Password saved - go to settings to change");
            password_input_layout.setHint("Saved Password");
        } else {
            password_input.setEnabled(true);
            password_input.setText("");
            password_input_layout.setHint("Enter your password");
        }
    }
    
    

    @Override
    protected void onResume() {
        super.onResume();
        register_screen_state_receiver();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Don't unregister here - we want to keep listening for screen events
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(screen_state_receiver);
        } catch (IllegalArgumentException e) {
        }
        try {
            unregisterReceiver(stop_broadcast_receiver);
        } catch (IllegalArgumentException e) {
        }
        stop_adv_tasks();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
}
