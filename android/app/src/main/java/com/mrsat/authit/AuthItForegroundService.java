package com.mrsat.authit;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
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
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.PowerManager;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class AuthItForegroundService extends Service {
    
    private static final String TAG = "AuthItForegroundService";
    private static final String NOTIFICATION_CHANNEL_ID = "authit_foreground_service";
    private static final int FOREGROUND_NOTIFICATION_ID = 2;
    private static final String PREFS_NAME = "AuthItPrefs";
    private static final String SAVED_PASSWORD_HASH = "saved_password_hash";
    private static final String DEBUG_NOTIFICATIONS_ENABLED = "debug_notifications_enabled";
    
    public static final String ACTION_START_SERVICE = "com.mrsat.authit.START_SERVICE";
    public static final String ACTION_STOP_SERVICE = "com.mrsat.authit.STOP_SERVICE";
    public static final String ACTION_UPDATE_STATUS = "com.mrsat.authit.UPDATE_STATUS";
    
    private BluetoothLeAdvertiser advertiser;
    private BluetoothAdapter bluetoothAdapter;
    private NotificationManager notificationManager;
    private SharedPreferences sharedPrefs;
    private PowerManager.WakeLock wakeLock;
    
    private Handler handler = new Handler();
    private String currentHash;
    private String password;
    private boolean isRunning = false;
    private String lastBroadcastHash = "";
    private boolean serviceStartedMessageSent = false;
    
    private AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            // N'envoyer le message "Service started successfully" qu'une seule fois au démarrage initial
            if (!serviceStartedMessageSent) {
                sendStatusBroadcast("Service started successfully", true);
                serviceStartedMessageSent = true;
            }
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            sendStatusBroadcast("Advertising failed, error code: " + errorCode, false);
            stopSelf();
        }
    };
    
    private final BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                // Service continue de fonctionner même écran éteint
                updateNotificationIfDebugEnabled("Running in background", "Auth-It service is running");
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                // Écran déverrouillé
                if (isRunning && currentHash != null) {
                    String hashPrefix = currentHash.substring(0, Math.min(20, currentHash.length()));
                    updateNotificationIfDebugEnabled("Broadcasting hash: " + hashPrefix, "Service active");
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        
        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        // Créer le canal de notification
        createNotificationChannel();
        
        // Obtenir l'adaptateur Bluetooth
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }
        
        // Créer un WakeLock pour empêcher l'appareil de s'endormir
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AuthIt::ForegroundService");
        
        // Enregistrer le récepteur d'état de l'écran
        registerScreenStateReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            
            if (ACTION_START_SERVICE.equals(action)) {
                startForegroundService();
            } else if (ACTION_STOP_SERVICE.equals(action)) {
                stopForegroundService();
                stopSelf();
            }
        }
        
        // START_STICKY fait que le service redémarre automatiquement s'il est tué
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Service non lié
    }

    private void startForegroundService() {
        // Créer la notification de premier plan
        Notification notification = createForegroundNotification("Starting Auth-It service...", "Initializing");
        startForeground(FOREGROUND_NOTIFICATION_ID, notification);
        
        // Acquérir le WakeLock
        if (!wakeLock.isHeld()) {
            wakeLock.acquire(60*60*1000L /*60 minutes*/);
        }
        
        // Démarrer le processus d'authentification
        startAuthentication();
    }

    private void stopForegroundService() {
        isRunning = false;
        serviceStartedMessageSent = false; // Réinitialiser le flag pour le prochain démarrage
        
        // Arrêter les tâches d'advertising
        stopAdvertisingTasks();
        
        // Libérer le WakeLock
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        
        // Arrêter le service de premier plan
        stopForeground(true);
        
        sendStatusBroadcast("Service stopped", false);
    }

    private void startAuthentication() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            sendStatusBroadcast("Bluetooth not available or not enabled", false);
            stopSelf();
            return;
        }
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            sendStatusBroadcast("Bluetooth Advertise permission not granted", false);
            stopSelf();
            return;
        }
        
        advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            sendStatusBroadcast("BLE Advertising not supported", false);
            stopSelf();
            return;
        }
        
        // Récupérer le mot de passe sauvegardé
        String savedHash = sharedPrefs.getString(SAVED_PASSWORD_HASH, null);
        if (savedHash == null || savedHash.isEmpty()) {
            sendStatusBroadcast("No password configured", false);
            stopSelf();
            return;
        }
        
        password = savedHash;
        currentHash = savedHash;
        isRunning = true;
        
        // Initialiser le hash avec la date actuelle
        String hashPrefix = currentHash.substring(0, Math.min(20, currentHash.length()));
        String padding = "0".repeat(108);
        String currentDate = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String inputNext = hashPrefix + padding + password + currentDate;
        currentHash = sha512(inputNext);
        
        // Démarrer les tâches d'advertising
        startAdvertisingTasks();
        
        sendStatusBroadcast("Service started successfully", true);
    }

    private void startAdvertisingTasks() {
        if (!isRunning) return;
        
        broadcastHash();
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::rollHash, 200);
    }

    private void stopAdvertisingTasks() {
        handler.removeCallbacksAndMessages(null);
        if (advertiser != null && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED) {
            advertiser.stopAdvertising(advertiseCallback);
        }
    }

    private void rollHash() {
        if (!isRunning) return;

        String hashPrefixOld = currentHash.substring(0, Math.min(20, currentHash.length()));
        String padding = "0".repeat(108);
        String currentDate = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String inputNext = hashPrefixOld + padding + password + currentDate;
        currentHash = sha512(inputNext);

        if (currentHash.isEmpty()) {
            sendStatusBroadcast("Hash generation failed", false);
            stopSelf();
            return;
        }

        broadcastHash();
        
        // Mettre à jour la notification seulement si les notifications de debug sont activées
        String currentHashPrefix = currentHash.substring(0, Math.min(20, currentHash.length()));
        updateNotificationIfDebugEnabled("Broadcasting hash: " + currentHashPrefix, currentHashPrefix);
        
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::rollHash, 200);
    }

    private void broadcastHash() {
        if (!isRunning || advertiser == null || currentHash == null || currentHash.isEmpty()) {
            return;
        }
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            String hashPrefix = currentHash.substring(0, Math.min(20, currentHash.length()));
            
            // Redémarrer l'advertising seulement si le hash a changé
            if (hashPrefix.equals(lastBroadcastHash)) {
                return;
            }
            
            lastBroadcastHash = hashPrefix;
            byte[] hashBytes = hashPrefix.getBytes(StandardCharsets.UTF_8);

            ParcelUuid uuid = ParcelUuid.fromString("0000FFF0-0000-1000-8000-00805F9B34FB");
            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW)
                    .setConnectable(false)
                    .setTimeout(0)
                    .build();
                    
            AdvertiseData data = new AdvertiseData.Builder()
                    .addServiceData(uuid, hashBytes)
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(false)
                    .build();
            
            AdvertiseData scanResponse = new AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(false)
                    .build();

            advertiser.stopAdvertising(advertiseCallback);
            advertiser.startAdvertising(settings, data, scanResponse, advertiseCallback);

        } catch (Exception e) {
            sendStatusBroadcast("Broadcast error: " + e.getMessage(), false);
        }
    }

    private String sha512(String input) {
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

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "AuthIt Foreground Service";
            String description = "Persistent AuthIt service running in background";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createForegroundNotification(String title, String content) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stopIntent = new Intent(this, AuthItForegroundService.class);
        stopIntent.setAction(ACTION_STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, 
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
                .build();
    }

    private void updateNotification(String title, String content) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Notification notification = createForegroundNotification(title, content);
        if (notificationManager != null) {
            notificationManager.notify(FOREGROUND_NOTIFICATION_ID, notification);
        }
    }
    
    private void updateNotificationIfDebugEnabled(String debugTitle, String debugContent) {
        boolean debugNotifications = sharedPrefs.getBoolean(DEBUG_NOTIFICATIONS_ENABLED, false);
        if (debugNotifications) {
            updateNotification(debugTitle, debugContent);
        } else {
            // Notification basique pour le service de premier plan (requis par Android)
            updateNotification("Auth-It Service", "Running in background");
        }
    }

    private void sendStatusBroadcast(String message, boolean isRunning) {
        Intent intent = new Intent(ACTION_UPDATE_STATUS);
        intent.putExtra("message", message);
        intent.putExtra("isRunning", isRunning);
        sendBroadcast(intent);
    }

    private void registerScreenStateReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenStateReceiver, filter);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // Réinitialiser les flags
        isRunning = false;
        serviceStartedMessageSent = false;
        
        // Libérer le WakeLock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        
        // Arrêter les tâches d'advertising
        stopAdvertisingTasks();
        
        // Désinscrire le récepteur
        try {
            unregisterReceiver(screenStateReceiver);
        } catch (IllegalArgumentException e) {
            // Récepteur pas enregistré
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Service continue même si l'application est supprimée de la liste des tâches récentes
        super.onTaskRemoved(rootIntent);
    }
}