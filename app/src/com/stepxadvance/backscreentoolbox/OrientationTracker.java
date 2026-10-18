package com.stepxadvance.backscreentoolbox;

import android.content.*;
import android.database.ContentObserver;
import android.os.*;
import android.provider.Settings;
import android.hardware.*;
import java.util.ArrayDeque;

/** Relayouts inside the system compositor's 100 ms rotation blank, not after it. */
final class OrientationTracker {
    private final Context context;
    private final Handler handler;
    private final Runnable changed;
    private final SensorManager sensors;
    private final Sensor accelerometer;
    private final Sensor discreteSensor;
    private long discreteSamples,discreteTime;
    private int discreteRotation=-1;
    private final SensorEventListener sensor;
    private final SensorHealth health=new SensorHealth();
    private boolean compact=true;
    private long samples;
    private int reconnects;
    private final RotationPolicy policy;
    private final ArrayDeque<String> recent = new ArrayDeque<>();
    private boolean interactive, autoEnabled, listening;
    private final Runnable commit = new Runnable() { public void run() {
        if (!interactive || !autoEnabled) return;
        long now = SystemClock.uptimeMillis();
        int from = policy.rotation();
        long started = policy.candidateStarted();
        if (!policy.commit(now)) return;
        long beforePaint = SystemClock.uptimeMillis();
        compact=false;
        changed.run();
        long paintMs = SystemClock.uptimeMillis() - beforePaint;
        String event = "uptime=" + now + " " + from + "->" + policy.rotation()
                + " degrees=" + policy.lastDegrees() + " stableMs=" + (now-started) + " redrawMs=" + paintMs;
        if (recent.size() == 12) recent.removeFirst();
        recent.addLast(event);
        android.util.Log.i(ChargeService.TAG,"rotation "+event);
        context.getSharedPreferences(ChargeService.PREFS, 0).edit().putInt("lastRotation", policy.rotation()).apply();
    }};
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) { sync(); }
    };
    private final ContentObserver settings;

    OrientationTracker(Context c, Handler h, Runnable callback) {
        context=c; handler=h; changed=callback;
        policy=new RotationPolicy(c.getSharedPreferences(ChargeService.PREFS,0).getInt("lastRotation",0));
        sensors=c.getSystemService(SensorManager.class);
        accelerometer=sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        // This firmware keeps TYPE_DEVICE_ORIENTATION alive and uses its events to
        // resume sensor clients. Keep this registration even though layout uses the
        // same continuous gravity calculation as the rear-screen compositor.
        discreteSensor=sensors.getDefaultSensor(27);
        sensor=new SensorEventListener() {
            public void onAccuracyChanged(Sensor s,int accuracy){}
            public void onSensorChanged(SensorEvent event) {
                if(!interactive||!autoEnabled)return;
                long now=SystemClock.uptimeMillis();
                if(event.sensor.getType()==27){discreteSamples++;discreteTime=now;discreteRotation=Math.round(event.values[0]);android.util.Log.i(ChargeService.TAG,"deviceOrientation="+discreteRotation+" rawFresh="+!health.stale(now));return;}
                samples++;
                if(health.received(now))policy.resetSamples();
                int degrees=RotationPolicy.degrees(event.values[0],event.values[1],event.values[2]);
                long previousDeadline=policy.deadline();
                policy.sample(degrees,now);
                long deadline=policy.deadline();
                if(deadline!=previousDeadline) {
                    handler.removeCallbacks(commit);
                    if(deadline>=0)handler.postAtTime(commit,deadline);
                }
                // Leave the common safe layout only after the fresh angle has settled.
                if(compact&&degrees>=0&&policy.pending()<0){compact=false;changed.run();}
            }
        };
        settings=new ContentObserver(h) { public void onChange(boolean self) { sync(); changed.run(); } };
        IntentFilter f=new IntentFilter(Intent.ACTION_SCREEN_ON); f.addAction(Intent.ACTION_SCREEN_OFF);
        c.registerReceiver(screen,f,Context.RECEIVER_NOT_EXPORTED);
        c.getContentResolver().registerContentObserver(Settings.System.getUriFor("stepos_back_screen_auto_rotate"),false,settings);
        sync();
    }
    private void sync() {
        interactive=context.getSystemService(PowerManager.class).isInteractive();
        autoEnabled=Settings.System.getInt(context.getContentResolver(),"stepos_back_screen_auto_rotate",1)!=0;
        if (!autoEnabled) {policy.forcePortrait();compact=false;}
        boolean enable=interactive && autoEnabled && accelerometer!=null;
        if (enable==listening) return;
        handler.removeCallbacks(commit);
        handler.removeCallbacks(watch);
        if (enable) {
            health.start(SystemClock.uptimeMillis());
            listening=sensors.registerListener(sensor,accelerometer,SensorManager.SENSOR_DELAY_UI,handler);
            if(discreteSensor!=null)sensors.registerListener(sensor,discreteSensor,SensorManager.SENSOR_DELAY_UI,handler);
            handler.postDelayed(watch,500);
        } else {
            sensors.unregisterListener(sensor);policy.resetSamples();listening=false;
        }
    }
    private final Runnable watch=new Runnable(){public void run(){
        if(!interactive||!autoEnabled)return;
        long now=SystemClock.uptimeMillis();
        if(health.stale(now)) {
            if(!compact){compact=true;handler.removeCallbacks(commit);policy.resetSamples();changed.run();android.util.Log.w(ChargeService.TAG,"Direction sensor paused; using compact safe layout");}
            if(health.retry(now)){
                sensors.unregisterListener(sensor);
                listening=sensors.registerListener(sensor,accelerometer,SensorManager.SENSOR_DELAY_UI,handler);
                if(discreteSensor!=null)sensors.registerListener(sensor,discreteSensor,SensorManager.SENSOR_DELAY_UI,handler);reconnects++;
            }
        }
        handler.postDelayed(this,500);
    }};
    boolean compactFallback(){return compact;}
    boolean sensorStale(){return health.stale(SystemClock.uptimeMillis());}
    boolean landscape() { return compact || autoEnabled && policy.landscape(); }
    int rotation() { return autoEnabled ? policy.rotation() : 0; }
    String diagnostics() { return "stableMs="+RotationPolicy.STABLE_MS+" listening="+listening+" pending="+policy.pending()+" sampleAgeMs="+health.age(SystemClock.uptimeMillis())+" samples="+samples+" compactFallback="+compact+" reconnects="+reconnects+" discrete="+discreteRotation+" discreteSamples="+discreteSamples+" discreteAge="+(SystemClock.uptimeMillis()-discreteTime)+" recent="+recent; }
    void close() {
        handler.removeCallbacks(commit);handler.removeCallbacks(watch);sensors.unregisterListener(sensor);context.unregisterReceiver(screen);
        context.getContentResolver().unregisterContentObserver(settings);
        context.getSharedPreferences(ChargeService.PREFS,0).edit().putInt("lastRotation",policy.rotation()).apply();
    }
}
