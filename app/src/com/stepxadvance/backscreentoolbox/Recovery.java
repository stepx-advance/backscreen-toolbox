package com.stepxadvance.backscreentoolbox;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

/** Recovery remains subordinate to the user's enabled switch and Android restrictions. */
final class Recovery {
    static boolean start(Context c,String source) {
        if(!ChargeService.enabled(c))return false;
        try {
            c.startForegroundService(new Intent(c,ChargeService.class).putExtra("source",source));
            return true;
        } catch(RuntimeException e) {
            failure(c,"系统限制了后台启动，打开应用即可恢复");
            ChargeService.detail="系统暂停了后台服务，请打开应用恢复";
            Log.w(ChargeService.TAG,"Background restart denied: "+source,e);
            return false;
        }
    }
    static void failure(Context c,String message) {
        c.getSharedPreferences(ChargeService.PREFS,0).edit()
            .putString("recovery_error",message).putLong("recovery_error_time",System.currentTimeMillis()).apply();
    }
    static void started(Context c,String source) {
        c.getSharedPreferences(ChargeService.PREFS,0).edit().putString("recovery_source",source)
            .putLong("recovery_started",System.currentTimeMillis()).remove("recovery_error").apply();
    }
    static void healthy(Context c) {
        c.getSharedPreferences(ChargeService.PREFS,0).edit().remove("recovery_error").apply();
    }
    static String status(Context c) {
        ActivityManager a=c.getSystemService(ActivityManager.class);
        PowerManager p=c.getSystemService(PowerManager.class);
        if(a.isBackgroundRestricted())return "后台运行受限，可能无法自动恢复";
        if(p.isIgnoringBatteryOptimizations(c.getPackageName()))return "电池优化已放开 · 请同时允许系统后台管理中的自启动";
        return "建议允许后台运行，减少省电策略造成的中断";
    }
    static Intent settings(Context c) {
        if(c.getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(c.getPackageName()))
            return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+c.getPackageName()));
        return new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+c.getPackageName()));
    }
    static void notifyResumeNeeded(Context c) {
        PendingIntent open=PendingIntent.getActivity(c,7,new Intent(c,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        c.getSystemService(NotificationManager.class).notify(2,new Notification.Builder(c,"monitor")
            .setSmallIcon(com.stepxadvance.backscreentoolbox.R.drawable.icon).setContentTitle("背屏工具箱需要恢复")
            .setContentText("点按打开应用，继续显示充电状态").setContentIntent(open)
            .setAutoCancel(true).setOnlyAlertOnce(true).build());
    }
}
