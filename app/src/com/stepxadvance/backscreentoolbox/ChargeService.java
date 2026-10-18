package com.stepxadvance.backscreentoolbox;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.util.Log;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;

public final class ChargeService extends Service {
    static final String PREFS = "charge", TAG = "BackCharge", STOP = "stop", DEMO = "demo";
    static volatile ChargeState visible;
    static volatile String detail = "尚未启动";
    static volatile boolean running, demoActive;
    static volatile boolean landscape;
    static volatile int[] previewFrame;
    static volatile boolean rotating;
    static volatile long sceneStarted;
    private static ChargeService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private RearDisplay display;
    private OrientationTracker orientation;
    private Intent latestBattery;
    private ChargeState actual;
    private ChargeState demo;
    private long demoUntil, nextBusyCheck;
    private boolean owns, busy;
    private int[] lastFrame;
    private int frameRotation;
    private boolean lastCompact=true;
    private RotationTransition transition;
    private long transitionCommittedAt;
    private int transitionFrameCount;
    private String lastTransition="none";
    private String lastNotification = "";
    private int errorCount;
    private final BroadcastReceiver battery = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(i.getAction())) {
                latestBattery = new Intent(i);
                actual = fromBattery(i, BypassMonitor.signal);
                updateState();
            }
        }
    };
    static ChargeState fromBattery(Intent i) {
        return fromBattery(i,BypassMonitor.signal);
    }
    static ChargeState fromBattery(Intent i,int bypass) {
        return i == null ? new ChargeState(-1, 0, 1, false) : new ChargeState(
            i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), i.getIntExtra(BatteryManager.EXTRA_SCALE, 100),
            i.getIntExtra(BatteryManager.EXTRA_STATUS, 1), i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0,bypass);
    }
    static void bypassChanged(){ChargeService s=instance;if(s!=null)s.handler.post(() -> {s.actual=fromBattery(s.latestBattery);s.updateState();});}
    static boolean enabled(Context c) { return c.getSharedPreferences(PREFS, 0).getBoolean("enabled", false); }
    static void setEnabled(Context c, boolean value) {
        c.getSharedPreferences(PREFS, 0).edit().putBoolean("enabled", value).apply();
        RecoveryJob.sync(c);
    }
    static void start(Context c) { Recovery.start(c,"app"); }
    static boolean smoothRotationEnabled(Context c) { return c.getSharedPreferences(PREFS,0).getBoolean("smooth_rotation",true); }
    static void setSmoothRotation(Context c,boolean value) {
        c.getSharedPreferences(PREFS,0).edit().putBoolean("smooth_rotation",value).apply();
        ChargeService s=instance;
        if(s!=null)s.handler.post(() -> {s.transition=null;s.lastFrame=null;s.handler.removeCallbacks(s.tick);s.tick.run();});
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel("monitor", "背屏工具箱运行状态", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("显示当前电量，并保持充电背屏服务运行");
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        startForeground(1, notification("正在读取电量"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        try { display = new RearDisplay(this); }
        catch (Exception e) { fail(e); }
        running = true;
        instance = this;
        orientation = new OrientationTracker(this,handler,() -> {
            int nextRotation=orientation.rotation();
            if(nextRotation!=frameRotation) {
                transition=smoothRotationEnabled(this)&&owns&&lastFrame!=null&&!lastCompact&&!orientation.compactFallback()
                    ? new RotationTransition(lastFrame,frameRotation,nextRotation,SystemClock.uptimeMillis()) : null;
                transitionCommittedAt=SystemClock.uptimeMillis();
                transitionFrameCount=0;
                frameRotation=nextRotation;
            }
            if(orientation.compactFallback()){transition=null;rotating=false;}
            lastCompact=orientation.compactFallback();
            landscape=orientation.landscape();
            lastFrame=null;
            handler.removeCallbacks(tick);
            tick.run();
        });
        landscape=orientation.landscape();
        frameRotation=orientation.rotation();
        Intent sticky = registerReceiver(battery, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (actual == null) actual = fromBattery(sticky);
        BypassMonitor.refresh();
        updateState();
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && STOP.equals(intent.getAction())) {
            setEnabled(this, false); stopSelf(); return START_NOT_STICKY;
        }
        if (!enabled(this)) { stopSelf(); return START_NOT_STICKY; }
        RecoveryJob.sync(this);
        Recovery.started(this,intent==null?"system_sticky_restart":intent.getStringExtra("source")==null?"app":intent.getStringExtra("source"));
        getSystemService(NotificationManager.class).cancel(2);
        if (intent != null && DEMO.equals(intent.getAction())) {
            String mode = intent.getStringExtra("mode");
            demo = "full".equals(mode) ? new ChargeState(100, 100, 5, true)
                : "bypass".equals(mode) ? new ChargeState(80,100,4,true,1)
                : "waiting".equals(mode) ? new ChargeState(60,100,2,true,2)
                : "paused".equals(mode) ? new ChargeState(80, 100, 4, true)
                : "unplugged".equals(mode) ? new ChargeState(73, 100, 3, false)
                : new ChargeState(73, 100, 2, true);
            demoUntil = SystemClock.elapsedRealtime() + 12000;
            sceneStarted = SystemClock.elapsedRealtime();
            lastFrame = null;
        }
        updateState();
        return START_STICKY;
    }
    private void updateState() {
        long now = SystemClock.elapsedRealtime();
        if (demo != null && now >= demoUntil) { demo = null; lastFrame = null; }
        demoActive = demo != null;
        ChargeState next = demo != null ? demo : actual;
        if (next == null) return;
        if(visible!=null&&(visible.mode!=next.mode||visible.percent!=next.percent||visible.charging!=next.charging)) {
            transition=null;rotating=false;lastFrame=null;
        }
        if (visible == null || visible.mode != next.mode) { sceneStarted = now; lastFrame = null; }
        visible = next;
        String message = (demoActive ? "预览 · " : "") + next.label() + (next.percent < 0 ? "" : " · " + next.percent + "%");
        if (!message.equals(lastNotification)) {
            lastNotification = message;
            getSystemService(NotificationManager.class).notify(1, notification(message));
            Log.i(TAG, "state=" + next.mode + " percent=" + next.percent + " demo=" + demoActive);
        }
        handler.removeCallbacks(tick);
        handler.post(tick);
    }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long now = SystemClock.elapsedRealtime();
            if (demo != null && now >= demoUntil) { updateState(); return; }
            ChargeState s = visible;
            if (s == null) return;
            try {
                if (s.mode == ChargeState.Mode.UNPLUGGED) {
                    clear();
                    detail = "已让出背屏 · 等待充电";
                    // Battery events restart the tick; idle has no recurring timer.
                    if (demo != null) handler.postDelayed(this, Math.max(1, demoUntil - now));
                    return;
                }
                if(display==null)display=new RearDisplay(ChargeService.this);
                if (now >= nextBusyCheck) {
                    busy = display.agentBusy();
                    nextBusyCheck = now + 1500;
                }
                if (busy) {
                    transition=null;rotating=false;
                    if (owns) { display.show(null); owns = false; lastFrame = null; }
                    detail = "Amoo 正在使用背屏 · 完成后自动恢复";
                    handler.postDelayed(this, 1500);
                    return;
                }
                int[] pixels = ChargeFrames.render(s, now - sceneStarted,landscape);
                long frameTime=SystemClock.uptimeMillis();
                boolean transitionFrame=transition!=null;
                boolean transitionEnding=transitionFrame&&transition.finished(frameTime);
                if(transition!=null) {
                    pixels=transition.frame(pixels,frameTime);
                    if(transition.finished(frameTime))transition=null;
                }
                if (!Arrays.equals(pixels, lastFrame)) {
                    display.show(pixels);
                    lastFrame = pixels;
                    owns = true;
                } else if (s.mode != ChargeState.Mode.CHARGING) {
                    // Reassert a latched still frame at a low rate after system presentations end.
                    display.show(pixels);
                    owns = true;
                }
                if(transitionFrame) {
                    transitionFrameCount++;
                    if(transitionEnding)lastTransition="completedMs="+(frameTime-transitionCommittedAt)+" renderedFrames="+transitionFrameCount;
                }
                if(errorCount>0)Recovery.healthy(ChargeService.this);
                errorCount = 0;
                rotating=transition!=null;
                previewFrame=pixels;
                detail = orientation.compactFallback()?(orientation.sensorStale()?"方向数据暂不可用 · 正在恢复，使用紧凑显示":"正在确认方向 · 使用紧凑显示"):demoActive ? "背屏预览中 · 12 秒后恢复真实状态" : "背屏已接入 · 跟随系统亮屏与亮度设置";
                long age = now - sceneStarted;
                long delay = s.mode == ChargeState.Mode.CHARGING || s.mode == ChargeState.Mode.BYPASS_WAITING ? 180
                    : s.mode == ChargeState.Mode.BYPASS ? 250 : s.mode == ChargeState.Mode.FULL && age < 1600 ? 100 : 5000;
                if(transition!=null)delay=transition.nextDelay(frameTime);
                if (demo != null) delay = Math.min(delay, Math.max(1, demoUntil - now));
                handler.postDelayed(this, delay);
            } catch (Exception e) {
                fail(e);
                display=null;lastFrame=null;nextBusyCheck=0;transition=null;rotating=false;
                // A transient vendor-service outage must never turn the user's switch off.
                if(errorCount<4)errorCount++;
                handler.postDelayed(this,RetryPolicy.delay(errorCount));
            }
        }
    };
    private void clear() throws Exception {
        if (owns) {
            if(display==null)display=new RearDisplay(this);
            display.show(null);
        }
        owns = false; lastFrame = null;
        previewFrame=null; transition=null;rotating=false;
    }
    private void fail(Exception e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        detail = "背屏连接暂时中断 · 正在自动重试";
        Recovery.failure(this,detail);
        Log.e(TAG, detail, e);
    }
    private Notification notification(String message) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, ChargeService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "monitor").setSmallIcon(com.stepxadvance.backscreentoolbox.R.drawable.icon)
            .setContentTitle("STEPX 背屏工具箱").setContentText(message).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .addAction(new Notification.Action.Builder(null, "停止显示", stop).build()).build();
    }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(battery); } catch (IllegalArgumentException ignored) { }
        try{clear();}catch(Exception e){Log.w(TAG,"Unable to release rear frame",e);}
        if(orientation!=null)orientation.close();
        instance=null;
        demo = null; demoActive = false; running = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onTaskRemoved(Intent rootIntent) {
        // Removing the settings task leaves the explicitly enabled foreground service running.
        Log.i(TAG,"Settings task removed; charging display remains enabled="+enabled(this));
    }
    @Override protected void dump(FileDescriptor fd, PrintWriter w, String[] args) {
        ChargeState s = visible;
        w.println("enabled=" + enabled(this) + " running=" + running + " owns=" + owns + " demo=" + demoActive);
        w.println("mode=" + (s == null ? "none" : s.mode) + " percent=" + (s == null ? -1 : s.percent));
        w.println("frameCells=" + (lastFrame == null ? 0 : lastFrame.length) + " frameHash=" + Arrays.hashCode(lastFrame));
        w.println("landscape="+landscape+" rotation="+(orientation==null?-1:orientation.rotation())+" bypassSignal="+BypassMonitor.signal+" bypassConnected="+BypassMonitor.connected);
        if(orientation!=null)w.println("orientation="+orientation.diagnostics());
        w.println("smoothRotation="+smoothRotationEnabled(this)+" transitionActive="+(transition!=null)+" lastTransition="+lastTransition);
        w.println("detail=" + detail);
        w.println("recoverySource="+getSharedPreferences(PREFS,0).getString("recovery_source","none")+" errors="+errorCount);
        w.println("backgroundRestricted="+getSystemService(ActivityManager.class).isBackgroundRestricted()
            +" batteryOptimizationExempt="+getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName())
            +" recoveryJob="+(getSystemService(android.app.job.JobScheduler.class).getPendingJob(17023)!=null));
    }
}
