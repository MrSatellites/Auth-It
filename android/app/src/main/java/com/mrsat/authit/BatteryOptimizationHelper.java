package com.mrsat.authit;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.appcompat.app.AlertDialog;

public class BatteryOptimizationHelper {
    
    /**
     * Vérifie si l'application est exemptée de l'optimisation de la batterie
     */
    public static boolean isIgnoringBatteryOptimizations(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                return powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
            }
        }
        return true; // Pour les versions antérieures à Android M
    }
    
    /**
     * Demande à l'utilisateur d'exempter l'application de l'optimisation de la batterie
     */
    public static void requestIgnoreBatteryOptimization(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isIgnoringBatteryOptimizations(activity)) {
                showBatteryOptimizationDialog(activity);
            }
        }
    }
    
    @TargetApi(Build.VERSION_CODES.M)
    private static void showBatteryOptimizationDialog(Activity activity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Optimisation de la batterie")
                .setMessage("Pour que Auth-It fonctionne correctement en arrière-plan, vous devez désactiver l'optimisation de la batterie pour cette application.\n\n" +
                           "Dans les paramètres qui vont s'ouvrir :\n" +
                           "1. Trouvez 'Auth-It' dans la liste\n" +
                           "2. Appuyez dessus\n" +
                           "3. Sélectionnez 'Ne pas optimiser'")
                .setPositiveButton("Ouvrir les paramètres", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + activity.getPackageName()));
                    try {
                        activity.startActivity(intent);
                    } catch (Exception e) {
                        // Si l'intent spécifique ne fonctionne pas, ouvrir les paramètres généraux
                        Intent fallbackIntent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                        activity.startActivity(fallbackIntent);
                    }
                })
                .setNegativeButton("Plus tard", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
    
    /**
     * Ouvre directement les paramètres d'optimisation de la batterie
     */
    public static void openBatteryOptimizationSettings(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            context.startActivity(intent);
        }
    }
    
    /**
     * Vérifie si l'application peut démarrer des activités en arrière-plan (Android 10+)
     */
    public static boolean canStartActivitiesFromBackground(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Pour Android 10+, vérifier les autorisations de démarrage en arrière-plan
            // Cette vérification est plus complexe et dépend du fabricant
            return true; // Simplification pour cet exemple
        }
        return true;
    }
    
    /**
     * Guide l'utilisateur vers les paramètres de démarrage automatique (spécifique aux fabricants)
     */
    public static void requestAutoStartPermission(Activity activity) {
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Démarrage automatique")
                .setMessage("Pour que Auth-It continue de fonctionner après redémarrage, vous devrez peut-être autoriser le démarrage automatique dans les paramètres de votre appareil.\n\n" +
                           "Ceci dépend de votre fabricant (" + Build.MANUFACTURER + ").")
                .setPositiveButton("Ouvrir les paramètres", (dialog, which) -> {
                    openAutoStartSettings(activity, manufacturer);
                })
                .setNegativeButton("Plus tard", null)
                .show();
    }
    
    private static void openAutoStartSettings(Activity activity, String manufacturer) {
        Intent intent = new Intent();
        
        try {
            switch (manufacturer) {
                case "xiaomi":
                    intent.setComponent(new android.content.ComponentName("com.miui.securitycenter", 
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"));
                    break;
                case "huawei":
                case "honor":
                    intent.setComponent(new android.content.ComponentName("com.huawei.systemmanager", 
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"));
                    break;
                case "oppo":
                    intent.setComponent(new android.content.ComponentName("com.coloros.safecenter", 
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
                    break;
                case "vivo":
                    intent.setComponent(new android.content.ComponentName("com.vivo.permissionmanager", 
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
                    break;
                case "oneplus":
                    intent.setComponent(new android.content.ComponentName("com.oneplus.security", 
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"));
                    break;
                default:
                    // Paramètres généraux des applications
                    intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + activity.getPackageName()));
                    break;
            }
            
            activity.startActivity(intent);
        } catch (Exception e) {
            // Si les paramètres spécifiques ne fonctionnent pas, ouvrir les paramètres de l'application
            Intent fallbackIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            fallbackIntent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(fallbackIntent);
        }
    }
}