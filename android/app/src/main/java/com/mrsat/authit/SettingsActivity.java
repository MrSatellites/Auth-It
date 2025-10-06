package com.mrsat.authit;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class SettingsActivity extends AppCompatActivity {
    
    private static final String PREFS_NAME = "AuthItPrefs";
    private static final String RUN_WITH_SCREEN_LOCKED = "run_with_screen_locked";
    private static final String AUTO_START_ENABLED = "auto_start_enabled";
    private static final String USE_FOREGROUND_SERVICE = "use_foreground_service";
    
    private SharedPreferences shared_prefs;
    private Switch screen_lock_switch;
    private Switch auto_start_switch;
    private Switch foreground_service_switch;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }
        
        shared_prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        screen_lock_switch = findViewById(R.id.screen_lock_switch);
        auto_start_switch = findViewById(R.id.auto_start_switch);
        foreground_service_switch = findViewById(R.id.foreground_service_switch);
        View github_link = findViewById(R.id.github_link);
        MaterialButton clear_password_btn = findViewById(R.id.clear_password_settings_btn);
        MaterialButton battery_optimization_btn = findViewById(R.id.battery_optimization_btn);
        MaterialButton auto_start_settings_btn = findViewById(R.id.auto_start_settings_btn);
        
        boolean run_with_lock = shared_prefs.getBoolean(RUN_WITH_SCREEN_LOCKED, true);
        boolean auto_start = shared_prefs.getBoolean(AUTO_START_ENABLED, false);
        boolean use_foreground_service = shared_prefs.getBoolean(USE_FOREGROUND_SERVICE, true);
        
        screen_lock_switch.setChecked(run_with_lock);
        auto_start_switch.setChecked(auto_start);
        foreground_service_switch.setChecked(use_foreground_service);
        
        screen_lock_switch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            shared_prefs.edit().putBoolean(RUN_WITH_SCREEN_LOCKED, isChecked).apply();
        });
        
        auto_start_switch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            shared_prefs.edit().putBoolean(AUTO_START_ENABLED, isChecked).apply();
            if (isChecked) {
                BatteryOptimizationHelper.requestAutoStartPermission(this);
            }
        });
        
        foreground_service_switch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            shared_prefs.edit().putBoolean(USE_FOREGROUND_SERVICE, isChecked).apply();
            Toast.makeText(this, "Redémarrez l'application pour que le changement prenne effet", Toast.LENGTH_LONG).show();
        });
        
        github_link.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://github.com/MrSatellites/Auth-It/releases/tag/v1.2.1"));
            startActivity(intent);
        });
        
        clear_password_btn.setOnClickListener(v -> {
            Intent stopBroadcastIntent = new Intent("com.mrsat.authit.STOP_BROADCAST");
            sendBroadcast(stopBroadcastIntent);
            
            shared_prefs.edit().remove("saved_password_hash").apply();
            
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.cancelAll();
            }
            Toast.makeText(this, "Password cleared - Restarting app...", Toast.LENGTH_SHORT).show();
            v.postDelayed(() -> {
                Intent restartIntent = new Intent(this, MainActivity.class);
                restartIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(restartIntent);
                finish();
            }, 500);
        });
        
        battery_optimization_btn.setOnClickListener(v -> {
            BatteryOptimizationHelper.requestIgnoreBatteryOptimization(this);
        });
        
        auto_start_settings_btn.setOnClickListener(v -> {
            BatteryOptimizationHelper.requestAutoStartPermission(this);
        });
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}