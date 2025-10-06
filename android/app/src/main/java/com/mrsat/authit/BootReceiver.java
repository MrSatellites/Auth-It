package com.mrsat.authit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    
    private static final String PREFS_NAME = "AuthItPrefs";
    private static final String AUTO_START_ENABLED = "auto_start_enabled";
    private static final String SAVED_PASSWORD_HASH = "saved_password_hash";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
            Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
            
            SharedPreferences sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            
            // Vérifier si le démarrage automatique est activé et qu'un mot de passe est configuré
            boolean autoStartEnabled = sharedPrefs.getBoolean(AUTO_START_ENABLED, false);
            String savedPassword = sharedPrefs.getString(SAVED_PASSWORD_HASH, null);
            
            if (autoStartEnabled && savedPassword != null && !savedPassword.isEmpty()) {
                // Démarrer le service de premier plan
                Intent serviceIntent = new Intent(context, AuthItForegroundService.class);
                serviceIntent.setAction(AuthItForegroundService.ACTION_START_SERVICE);
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }
        }
    }
}