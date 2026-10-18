package com.stepxadvance.backscreentoolbox;
import android.content.*;
final class ToolSettings {
    static boolean value(Context c,String key,boolean fallback){return c.getSharedPreferences(ChargeService.PREFS,0).getBoolean(key,fallback);}
    static void set(Context c,String key,boolean value){c.getSharedPreferences(ChargeService.PREFS,0).edit().putBoolean(key,value).apply();ContentMonitor.settingsChanged();ChargeService.toolsChanged();}
    static boolean access(Context c){return c.getSystemService(android.app.NotificationManager.class).isNotificationListenerAccessGranted(new ComponentName(c,ContentMonitor.class));}
    static Intent accessSettings(Context c){return new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(android.provider.Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,new ComponentName(c,ContentMonitor.class).flattenToString());}
}
