package com.stepxadvance.backscreentoolbox;

import android.content.*;
import android.database.ContentObserver;
import android.hardware.*;
import android.os.*;
import android.provider.Settings;

/** Keeps non-wakeup direction sensors and animation responsive only during visible plugged use. */
final class LockedDisplaySession {
    private final Context context;
    private final Handler handler;
    private final PowerManager power;
    private final PowerManager.WakeLock wake;
    private final SensorManager sensors;
    private final Sensor face;
    private long renewAt;
    private boolean active,animated;
    private int postureValue=-1;
    private int thermal;
    private final SensorEventListener posture=new SensorEventListener(){
        public void onAccuracyChanged(Sensor sensor,int accuracy){}
        public void onSensorChanged(SensorEvent event){postureValue=Math.round(event.values[0]);sync();}
    };
    private final BroadcastReceiver screen=new BroadcastReceiver(){public void onReceive(Context c,Intent i){sync();}};
    private final ContentObserver settings;
    private final PowerManager.OnThermalStatusChangedListener heat=status->{thermal=status;sync();};
    private final Runnable renew=()->sync();
    LockedDisplaySession(Context c,Handler h) {
        context=c;handler=h;power=c.getSystemService(PowerManager.class);sensors=c.getSystemService(SensorManager.class);
        wake=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"BackscreenToolbox:locked-display");wake.setReferenceCounted(false);
        thermal=power.getCurrentThermalStatus();power.addThermalStatusListener(c.getMainExecutor(),heat);
        // This vendor on-change sensor is wake-up only; the one-argument lookup
        // searches the non-wakeup variant for unknown vendor sensor types.
        face=sensors.getDefaultSensor(33172019,true);
        IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        c.registerReceiver(screen,filter,Context.RECEIVER_NOT_EXPORTED);
        settings=new ContentObserver(h){public void onChange(boolean self){sync();}};
        c.getContentResolver().registerContentObserver(Settings.System.getUriFor("stepos_back_screen_display"),false,settings);
        c.getContentResolver().registerContentObserver(Settings.System.getUriFor("stepos_back_screen_auto_rotate"),false,settings);
    }
    void setActive(boolean enabled,boolean moving){
        if(active==enabled&&animated==moving)return;
        if(active!=enabled&&face!=null){if(enabled)sensors.registerListener(posture,face,SensorManager.SENSOR_DELAY_NORMAL,handler);else{sensors.unregisterListener(posture);postureValue=-1;}}
        active=enabled;animated=moving;sync();
    }
    private void sync() {
        boolean feature=Settings.System.getInt(context.getContentResolver(),"stepos_back_screen_display",1)!=0;
        boolean rotate=Settings.System.getInt(context.getContentResolver(),"stepos_back_screen_auto_rotate",1)!=0;
        boolean hold=LockedDisplayPolicy.keepCpu(active,power.isInteractive(),LockedDisplayPolicy.rearVisible(postureValue),feature,animated||rotate,power.isPowerSaveMode(),thermal);
        handler.removeCallbacks(renew);
        if(hold){
            long now=SystemClock.elapsedRealtime();
            if(!wake.isHeld()||now>=renewAt){wake.acquire(60000);renewAt=now+30000;}
            handler.postDelayed(renew,Math.max(1,renewAt-now));
        } else if(wake.isHeld()){wake.release();renewAt=0;}
    }
    String diagnostics(){return "cpuHeld="+wake.isHeld()+" posture="+postureValue+" postureSensor="+(face!=null)+" active="+active+" thermal="+thermal;}
    void close(){
        active=false;handler.removeCallbacks(renew);if(wake.isHeld())wake.release();
        sensors.unregisterListener(posture);context.unregisterReceiver(screen);context.getContentResolver().unregisterContentObserver(settings);power.removeThermalStatusListener(heat);
    }
}
