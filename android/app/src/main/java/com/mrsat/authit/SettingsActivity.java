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
    
    private SharedPreferences shared_prefs;
    private Switch screen_lock_switch;
    
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
        View github_link = findViewById(R.id.github_link);
        MaterialButton clear_password_btn = findViewById(R.id.clear_password_settings_btn);
        
        boolean run_with_lock = shared_prefs.getBoolean(RUN_WITH_SCREEN_LOCKED, false);
        screen_lock_switch.setChecked(run_with_lock);
        
        screen_lock_switch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            shared_prefs.edit().putBoolean(RUN_WITH_SCREEN_LOCKED, isChecked).apply();
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