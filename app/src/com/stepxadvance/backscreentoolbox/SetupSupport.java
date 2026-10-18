package com.stepxadvance.backscreentoolbox;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;

final class SetupSupport {
    static final String LEGACY_PACKAGE = "local.stepx.backcharge";
    static boolean legacyInstalled(Context c) {
        try {c.getPackageManager().getPackageInfo(LEGACY_PACKAGE,0);return true;}
        catch(PackageManager.NameNotFoundException e){return false;}
    }
    static boolean firstRun(Context c) {
        android.content.SharedPreferences p=c.getSharedPreferences(ChargeService.PREFS,0);
        return SetupPolicy.firstRun(p.getBoolean("opened",false),p.getBoolean("setup_finished",false));
    }
    static boolean confirmed(Context c) { return c.getSharedPreferences(ChargeService.PREFS,0).getBoolean("setup_oem_confirmed",false); }
    static boolean exempt(Context c) { return c.getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(c.getPackageName()); }
    static boolean restricted(Context c) { return c.getSystemService(ActivityManager.class).isBackgroundRestricted(); }
    static boolean notifications(Context c) {
        if(c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return false;
        NotificationManager n=c.getSystemService(NotificationManager.class);
        android.app.NotificationChannel channel=n.getNotificationChannel("monitor");
        return n.areNotificationsEnabled()&&(channel==null||channel.getImportance()!=NotificationManager.IMPORTANCE_NONE);
    }
    static boolean listener(Context c) {
        return c.getSystemService(NotificationManager.class).isNotificationListenerAccessGranted(new ComponentName(c,BypassMonitor.class));
    }
    static int screen(Context c) {
        try {return Settings.System.getInt(c.getContentResolver(),"stepos_back_screen_display",1);}
        catch(RuntimeException e){return -1;}
    }
    static Intent appSettings(Context c) { return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+c.getPackageName())); }
    static Intent notificationSettings(Context c) {
        return new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,c.getPackageName());
    }
    static String summary(Context c) {
        int done=SetupPolicy.backgroundDone(exempt(c),confirmed(c),restricted(c));
        return "后台设置 "+done+"/2 · "+(notifications(c)?"通知栏显示已开启":"通知栏不显示")
            +(confirmed(c)?"\n自启动和后台运行按你的确认记录":"\n首次使用建议完成设置引导");
    }
}
