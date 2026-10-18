package com.stepxadvance.backscreentoolbox;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;

/** One timer, saved without notification content. Alarm and live view share the same deadline. */
final class TimerStore {
    private static final String PREF="timer";
    static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences(PREF,0);}
    static int boot(Context c){return Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,0);}
    static boolean exact(Context c){return c.getSystemService(AlarmManager.class).canScheduleExactAlarms();}
    static Intent permission(Context c){return new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+c.getPackageName()));}
    static TimerModel read(Context c){
        android.content.SharedPreferences p=prefs(c);int state=p.getInt("state",0);
        long remaining=p.getLong("remaining",0);
        if(state==TimerModel.RUNNING)remaining=TimerModel.remaining(p.getLong("endElapsed",0),p.getLong("endWall",0),p.getInt("boot",-1),boot(c),SystemClock.elapsedRealtime(),System.currentTimeMillis());
        if(state==TimerModel.FINISHED&&System.currentTimeMillis()>p.getLong("doneUntil",0))state=TimerModel.IDLE;
        return new TimerModel(state,p.getInt("preset",0),p.getLong("total",60000),remaining);
    }
    private static PendingIntent alarm(Context c){return PendingIntent.getBroadcast(c,310,new Intent(c,TimerReceiver.class).setAction("timer_end"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static synchronized void start(Context c,int preset,long millis){
        if(!exact(c))throw new SecurityException("请先允许闹钟和提醒");
        millis=Math.max(1000,Math.min(99*60000L+59000,millis));
        android.content.SharedPreferences p=prefs(c);p.edit().putInt("preset",preset).putInt("state",TimerModel.RUNNING).putLong("total",millis).putLong("remaining",millis).putLong("endElapsed",SystemClock.elapsedRealtime()+millis).putLong("endWall",System.currentTimeMillis()+millis).putInt("boot",boot(c)).remove("doneUntil").commit();
        try{schedule(c);}catch(RuntimeException e){p.edit().putInt("state",TimerModel.PAUSED).commit();throw e;}
        ChargeService.setEnabled(c,true);Recovery.start(c,"timer_started");ChargeService.toolsChanged();
    }
    static synchronized void schedule(Context c){
        TimerModel model=read(c);if(model.state!=TimerModel.RUNNING)return;
        if(model.remaining<=0){finish(c);return;}
        c.getSystemService(AlarmManager.class).setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,SystemClock.elapsedRealtime()+model.remaining,alarm(c));
    }
    static synchronized void pause(Context c){TimerModel m=read(c);if(m.state!=TimerModel.RUNNING)return;c.getSystemService(AlarmManager.class).cancel(alarm(c));prefs(c).edit().putInt("state",TimerModel.PAUSED).putLong("remaining",m.remaining).commit();ChargeService.toolsChanged();}
    static synchronized void resume(Context c){TimerModel m=read(c);if(m.state!=TimerModel.PAUSED)return;long total=m.total;start(c,m.preset,m.remaining);prefs(c).edit().putLong("total",total).commit();ChargeService.toolsChanged();}
    static synchronized void cancel(Context c){c.getSystemService(AlarmManager.class).cancel(alarm(c));prefs(c).edit().clear().commit();c.getSystemService(NotificationManager.class).cancel(31);ChargeService.toolsChanged();}
    static synchronized void finish(Context c){
        TimerModel m=read(c);if(m.state!=TimerModel.RUNNING||m.remaining>250)return;
        prefs(c).edit().putInt("state",TimerModel.FINISHED).putLong("remaining",0).putLong("doneUntil",System.currentTimeMillis()+20000).commit();
        c.getSystemService(AlarmManager.class).cancel(alarm(c));
        if(ChargeService.enabled(c)){
            Recovery.start(c,"timer_finished");ChargeService.toolsChanged();
            try{c.getSystemService(VibratorManager.class).getDefaultVibrator().vibrate(VibrationEffect.createWaveform(new long[]{0,180,120,180},-1));}catch(RuntimeException ignored){}
            NotificationManager n=c.getSystemService(NotificationManager.class);n.createNotificationChannel(new NotificationChannel("timer","计时完成",NotificationManager.IMPORTANCE_HIGH));
            PendingIntent open=PendingIntent.getActivity(c,31,new Intent(c,TimerActivity.class),PendingIntent.FLAG_IMMUTABLE);
            n.notify(31,new Notification.Builder(c,"timer").setSmallIcon(R.drawable.icon).setContentTitle(m.label()+"结束了").setContentText("点按返回计时器").setContentIntent(open).setAutoCancel(true).setCategory(Notification.CATEGORY_ALARM).build());
        }
    }
    static void recover(Context c){try{schedule(c);}catch(SecurityException e){TimerModel m=read(c);if(m.state==TimerModel.RUNNING)prefs(c).edit().putInt("state",TimerModel.PAUSED).putLong("remaining",m.remaining).commit();}}
}
