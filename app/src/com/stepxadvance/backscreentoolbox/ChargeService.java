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
    static final String HEAR_START="hear_start",HEAR_STOP="hear_stop";
    static volatile ChargeState visible;
    static volatile String detail = "尚未启动";
    static volatile boolean running, demoActive;
    static volatile boolean landscape;
    static volatile int[] previewFrame;
    static volatile boolean rotating;
    static volatile long sceneStarted;
    static volatile int currentScene;
    static volatile String sceneTitle="待机";
    private final PanelText panelText=new PanelText();
    private HearCapture hear;
    static volatile String hearStatus="未开始";
    static HearCapture hearing(){ChargeService s=instance;return s==null?null:s.hear;}
    static boolean hearingActive(){HearCapture c=hearing();return c!=null&&c.active();}
    static void hearSensitivity(int value){HearCapture c=hearing();if(c!=null)c.sensitivity(value);}
    private String sceneKey="";
    private long toolStarted;
    private static ChargeService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private RearDisplay display;
    private OrientationTracker orientation;
    private LockedDisplaySession lockedSession;
    private Intent latestBattery;
    private ChargeState actual;
    private ChargeState demo;
    private int toolDemo;
    private boolean demoCode;
    private long demoUntil, demoStarted, demoTimerMillis, nextBusyCheck;
    private int demoTimerPreset;
    private boolean owns, busy;
    private int[] lastFrame;
    private int[] lastSubmittedFrame;
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
    static void toolsChanged(){ChargeService s=instance;if(s!=null)s.handler.post(s::updateState);}
    static void bypassChanged(){ChargeService s=instance;if(s!=null)s.handler.post(() -> {s.actual=fromBattery(s.latestBattery);s.updateState();});}
    static boolean enabled(Context c) { return c.getSharedPreferences(PREFS, 0).getBoolean("enabled", false); }
    static void setEnabled(Context c, boolean value) {
        c.getSharedPreferences(PREFS, 0).edit().putBoolean("enabled", value).apply();
        RecoveryJob.sync(c);
        if(!value){TimerStore.cancel(c);c.getSharedPreferences(PREFS,0).edit().putBoolean("frog",false).apply();}
        ContentMonitor.settingsChanged();
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
        channel.setDescription("显示当前背屏任务，并保持工具箱运行");
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        startForeground(1, notification("正在读取电量"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        try { display = new RearDisplay(this); }
        catch (Exception e) { fail(e); }
        running = true;
        instance = this;
        lockedSession=new LockedDisplaySession(this,handler);
        orientation = new OrientationTracker(this,handler,() -> {
            int nextRotation=orientation.rotation();
            if(nextRotation!=frameRotation) {
                transition=currentScene!=ToolPolicy.HEAR&&smoothRotationEnabled(this)&&owns&&lastFrame!=null&&!lastCompact&&!orientation.compactFallback()
                    ? new RotationTransition(lastFrame,frameRotation,nextRotation,SystemClock.uptimeMillis(),orientation.interactive(),orientation.compactPortrait()) : null;
                transitionCommittedAt=SystemClock.uptimeMillis();
                transitionFrameCount=0;
                frameRotation=nextRotation;
            }
            if(orientation.compactFallback()){transition=null;rotating=false;}
            lastCompact=orientation.compactFallback();
            landscape=orientation.landscape();
            lastFrame=null;lastSubmittedFrame=null;
            handler.removeCallbacks(tick);
            tick.run();
        });
        landscape=orientation.landscape();
        frameRotation=orientation.rotation();
        Intent sticky = registerReceiver(battery, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (actual == null) actual = fromBattery(sticky);
        TimerStore.recover(this);
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
        if(intent!=null&&HEAR_STOP.equals(intent.getAction())){stopHearing();updateState();return START_STICKY;}
        if(intent!=null&&HEAR_START.equals(intent.getAction())){
            if(!hearingActive()){
                try{
                    if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new SecurityException();
                    startForeground(1,notification("听见 · 正在连接麦克风"),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE|ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                    hear=new HearCapture(this,handler,()->{if(instance==this){if(hear.active())hearStatus=hear.status;updateState();}},()->{if(instance==this&&hear!=null&&!hear.active()){stopHearing(hear.status);updateState();}});
                    hear.sensitivity(getSharedPreferences(PREFS,0).getInt("hear_sensitivity",1));hearStatus="正在连接麦克风";hear.start();
                    getSystemService(NotificationManager.class).notify(1,notification("听见 · 麦克风使用中"));
                    HearCapture started=hear;handler.postDelayed(()->{if(instance==this&&hear==started&&started.active()&&started.blocks==0){stopHearing("未能连接麦克风，请重新开始");updateState();}},5000);
                    demo=null;toolDemo=0;demoUntil=0;sceneKey="";
                }catch(RuntimeException e){stopHearing("请回到主屏开启麦克风");}
            }
            updateState();return START_STICKY;
        }
        if (intent != null && DEMO.equals(intent.getAction())) {
            String mode = intent.getStringExtra("mode");
            if("live".equals(mode)){demo=null;toolDemo=0;demoUntil=0;sceneKey="";updateState();return START_STICKY;}
            demoCode="code".equals(mode);
            toolDemo="message".equals(mode)||"code".equals(mode)?ToolPolicy.MESSAGE:"music".equals(mode)?ToolPolicy.MUSIC:"frog".equals(mode)?ToolPolicy.FROG:"timer".equals(mode)?ToolPolicy.TIMER:"hear".equals(mode)?ToolPolicy.HEAR:ToolPolicy.CHARGE;
            demoTimerPreset=Math.max(0,Math.min(4,intent.getIntExtra("timer_preset",0)));
            demoTimerMillis=Math.max(1000,Math.min(5999000,intent.getLongExtra("timer_millis",1500000)));
            demo = toolDemo!=ToolPolicy.CHARGE?null:"full".equals(mode) ? new ChargeState(100, 100, 5, true)
                : "bypass".equals(mode) ? new ChargeState(80,100,4,true,1)
                : "waiting".equals(mode) ? new ChargeState(60,100,2,true,2)
                : "paused".equals(mode) ? new ChargeState(80, 100, 4, true)
                : "unplugged".equals(mode) ? new ChargeState(73, 100, 3, false)
                : new ChargeState(73, 100, 2, true);
            demoStarted=SystemClock.elapsedRealtime();
            demoUntil = demoStarted + 12000;
            sceneStarted = demoStarted;sceneKey="";
            lastFrame = null;
        }
        updateState();
        return START_STICKY;
    }
    private void updateState() {
        long now = SystemClock.elapsedRealtime();
        if ((demo != null||toolDemo!=0) && now >= demoUntil) { demo = null;toolDemo=0; lastFrame = null; }
        demoActive = demo != null||toolDemo!=0;
        ChargeState next = demo != null ? demo : actual;
        if (next == null) return;
        if(visible!=null&&(visible.mode!=next.mode||visible.percent!=next.percent||visible.charging!=next.charging)) {
            transition=null;rotating=false;lastFrame=null;
        }
        if (visible == null || visible.mode != next.mode) { sceneStarted = now; lastFrame = null; }
        visible = next;
        // Follow native turns while awake even between charges, so a later locked
        // plug-in starts with the compositor's latest anchor. This never wakes the CPU.
        orientation.setActive(enabled(this));
        handler.removeCallbacks(tick);
        handler.post(tick);
    }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long now = SystemClock.elapsedRealtime();
            if ((demo != null||toolDemo!=0) && now >= demoUntil) { updateState(); return; }
            ChargeState s = visible;
            if (s == null) return;
            try {
                TimerModel timer=TimerStore.read(ChargeService.this);
                if(timer.state==TimerModel.RUNNING&&timer.remaining==0){TimerStore.finish(ChargeService.this);timer=TimerStore.read(ChargeService.this);}
                MessageCard message=ContentMonitor.latest;
                if(message!=null&&(!message.alive(now)||!ToolSettings.value(ChargeService.this,"messages",false))){ContentMonitor.latest=null;message=null;}
                boolean frog=ToolSettings.value(ChargeService.this,"frog",false);
                long frogEnd=getSharedPreferences(PREFS,0).getLong("frog_until",0);
                if(frog&&frogEnd>0&&System.currentTimeMillis()>=frogEnd){getSharedPreferences(PREFS,0).edit().putBoolean("frog",false).apply();frog=false;}
                int selected=ToolPolicy.choose(enabled(ChargeService.this),timer.state==TimerModel.FINISHED,message!=null,
                    timer.state==TimerModel.RUNNING||timer.state==TimerModel.PAUSED,hearingActive(),frog,
                    ContentMonitor.playing&&ToolSettings.value(ChargeService.this,"music",false),
                    (demoActive||ToolSettings.value(ChargeService.this,"charge_display",true))&&s.mode!=ChargeState.Mode.UNPLUGGED);
                if(toolDemo!=0){
                    selected=toolDemo;
                    if(selected==ToolPolicy.MESSAGE)message=new MessageCard("工具箱演示",demoCode?"Ab12C3":"","demo",demoUntil,demoUntil);
                    if(selected==ToolPolicy.TIMER){
                        // A display-only countdown: never save an alarm or replace the real timer.
                        long remaining=Math.max(0,demoTimerMillis-(now-demoStarted));
                        timer=new TimerModel(remaining==0?TimerModel.FINISHED:TimerModel.RUNNING,demoTimerPreset,demoTimerMillis,remaining);
                        if(remaining==0)selected=ToolPolicy.DONE;
                    }
                }
                String key=(demoActive?"demo:":"")+selected+":"+(selected==ToolPolicy.MESSAGE?message.key+":"+message.expires:selected==ToolPolicy.MUSIC?(toolDemo!=0?"星河漫步":ContentMonitor.song):selected==ToolPolicy.TIMER||selected==ToolPolicy.DONE?timer.preset+":"+timer.state:"");
                if(!key.equals(sceneKey)){sceneKey=key;toolStarted=now;panelText.clear();transition=null;rotating=false;lastFrame=null;lastSubmittedFrame=null;}
                currentScene=selected;
                sceneTitle=selected==ToolPolicy.TIMER?timer.label()+(timer.state==TimerModel.PAUSED?" · 已暂停":" · "+TimerModel.clock(timer.remaining)):
                    selected==ToolPolicy.DONE?timer.label()+"结束了":selected==ToolPolicy.MESSAGE?(message.code.isEmpty()?"有新消息":"验证码 · "+message.source):
                    selected==ToolPolicy.MUSIC?(toolDemo!=0?"星河漫步":ContentMonitor.song):selected==ToolPolicy.FROG?"奶蛙大笑":selected==ToolPolicy.HEAR?"听见":selected==ToolPolicy.CHARGE?s.label():"待机";
                if (selected==ToolPolicy.NONE) {
                    lockedSession.setActive(false,false);
                    clear();
                    detail = "已让出背屏 · 等待任务";
                    if(!"工具箱待机".equals(lastNotification)){lastNotification="工具箱待机";getSystemService(NotificationManager.class).notify(1,notification(lastNotification));}
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
                    lockedSession.setActive(false,false);
                    transition=null;rotating=false;
                    if (owns) { display.show(null); owns = false; lastFrame = null;lastSubmittedFrame=null; }
                    detail = "Amoo 正在使用背屏 · 完成后自动恢复";
                    handler.postDelayed(this, 1500);
                    return;
                }
                boolean moving=s.mode==ChargeState.Mode.CHARGING||s.mode==ChargeState.Mode.BYPASS||s.mode==ChargeState.Mode.BYPASS_WAITING;
                lockedSession.setActive(enabled(ChargeService.this),selected==ToolPolicy.TIMER?timer.state==TimerModel.RUNNING:selected==ToolPolicy.CHARGE?moving:true);
                long toolAge=now-toolStarted;
                boolean compactScene=landscape||orientation.compactPortrait()||orientation.compactFallback();
                int[] pixels;
                if(selected==ToolPolicy.TIMER||selected==ToolPolicy.DONE)pixels=ToolFrames.timer(timer,toolAge,compactScene);
                else if(selected==ToolPolicy.MESSAGE){pixels=message.code.isEmpty()?ToolFrames.message(toolAge,compactScene):ToolFrames.otp(message.code,message.source,toolAge,compactScene);if(!message.code.isEmpty()){boolean sourcePage=ToolFrames.otpSourcePage(message.code,message.source,toolAge);if(sourcePage)panelText.draw(pixels,message.source,compactScene?9:10,11,(toolAge%(ToolFrames.sourceDuration(message.source)+8800))*3,220);}}
                else if(selected==ToolPolicy.MUSIC){pixels=ToolFrames.music(toolAge,compactScene);panelText.draw(pixels,toolDemo!=0?"星河漫步":ContentMonitor.song,compactScene?9:12,11,toolDemo!=0?toolAge:ContentMonitor.mediaElapsed(),235);}
                else if(selected==ToolPolicy.FROG)pixels=ToolFrames.frog(toolAge,compactScene);
                else if(selected==ToolPolicy.HEAR)pixels=toolDemo==ToolPolicy.HEAR?SoundSpectrum.demo(toolAge):hear==null?SoundSpectrum.quiet():hear.frame();
                else pixels=!landscape&&orientation.compactPortrait()?ChargeFrames.compactPortrait(s,now-sceneStarted):ChargeFrames.render(s,now-sceneStarted,landscape);
                long frameTime=SystemClock.uptimeMillis();
                boolean transitionFrame=transition!=null;
                boolean transitionEnding=transitionFrame&&transition.finished(frameTime);
                if(transition!=null) {
                    pixels=transition.frame(pixels,frameTime);
                    if(transition.finished(frameTime))transition=null;
                }
                // Hear uses the full portrait grid; it never reflows or rotates its artwork.
                int[] submitted=selected==ToolPolicy.HEAR?pixels:FrameOrientation.project(pixels,orientation.rotation(),orientation.compositorRotation());
                if (!Arrays.equals(submitted, lastSubmittedFrame)) {
                    display.show(submitted);
                    lastSubmittedFrame = submitted;
                    owns = true;
                } else if (s.mode != ChargeState.Mode.CHARGING) {
                    // Reassert a latched still frame at a low rate after system presentations end.
                    display.show(submitted);
                    owns = true;
                }
                lastFrame=pixels;
                if(transitionFrame) {
                    transitionFrameCount++;
                    if(transitionEnding)lastTransition="completedMs="+(frameTime-transitionCommittedAt)+" renderedFrames="+transitionFrameCount;
                }
                if(errorCount>0)Recovery.healthy(ChargeService.this);
                errorCount = 0;
                rotating=transition!=null;
                previewFrame=pixels;
                detail = selected==ToolPolicy.HEAR?(demoActive?"频谱山形预览 · 12 秒后恢复":"频谱山形 · 实时声音"):orientation.compactFallback()?(orientation.sensorStale()?"方向数据暂不可用 · 正在恢复，使用紧凑显示":"正在确认方向 · 使用紧凑显示"):demoActive ? "背屏预览中 · 12 秒后恢复真实状态" : "画面已提交 · 点亮由系统设置与姿态控制";
                long age = now - sceneStarted;
                long delay = selected==ToolPolicy.HEAR?25:selected==ToolPolicy.TIMER?200:selected==ToolPolicy.DONE?180:selected==ToolPolicy.MESSAGE?120:selected==ToolPolicy.MUSIC?170:selected==ToolPolicy.FROG?100:
                    s.mode == ChargeState.Mode.CHARGING || s.mode == ChargeState.Mode.BYPASS_WAITING ? 180
                    : s.mode == ChargeState.Mode.BYPASS ? 250 : s.mode == ChargeState.Mode.FULL && age < 1600 ? 100 : 5000;
                if(selected==ToolPolicy.FROG&&frogEnd>0)delay=Math.min(delay,Math.max(1,frogEnd-System.currentTimeMillis()));
                String status=(hearingActive()?"听见 · 麦克风使用中 · ":"")+sceneTitle+(s.percent<0?"":" · 电量 "+s.percent+"%");
                if(!status.equals(lastNotification)){lastNotification=status;getSystemService(NotificationManager.class).notify(1,notification(status));}
                if(transition!=null)delay=transition.nextDelay(frameTime);
                if (demo != null||toolDemo!=0) delay = Math.min(delay, Math.max(1, demoUntil - now));
                handler.postDelayed(this, delay);
            } catch (Exception e) {
                fail(e);
                display=null;lastFrame=null;lastSubmittedFrame=null;nextBusyCheck=0;transition=null;rotating=false;
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
        owns = false; lastFrame = null;lastSubmittedFrame=null;
        previewFrame=null; transition=null;rotating=false;
    }
    private void fail(Exception e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        detail = "背屏连接暂时中断 · 正在自动重试";
        Recovery.failure(this,detail);
        Log.e(TAG, detail, e);
    }
    private void stopHearing(){stopHearing("已停止");}
    private void stopHearing(String status){
        if(hear!=null)hear.stop();hearStatus=status;
        try{startForeground(1,notification(sceneTitle),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);}catch(RuntimeException ignored){}
        lastNotification="";
    }
    private Notification notification(String message) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, ChargeService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(this, "monitor").setSmallIcon(com.stepxadvance.backscreentoolbox.R.drawable.icon)
            .setContentTitle("STEPX 背屏工具箱").setContentText(message).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .addAction(new Notification.Action.Builder(null, "停止显示", stop).build());
        if(hearingActive()){PendingIntent silence=PendingIntent.getService(this,18,new Intent(this,ChargeService.class).setAction(HEAR_STOP),PendingIntent.FLAG_IMMUTABLE);builder.addAction(new Notification.Action.Builder(null,"停止聆听",silence).build());}
        return builder.build();
    }
    @Override public void onDestroy() {
        if(hear!=null)hear.stop();hearStatus="已停止";
        handler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(battery); } catch (IllegalArgumentException ignored) { }
        try{clear();}catch(Exception e){Log.w(TAG,"Unable to release rear frame",e);}
        if(orientation!=null)orientation.close();
        if(lockedSession!=null)lockedSession.close();
        instance=null;
        panelText.clear();
        demo = null;toolDemo=0; demoActive = false; running = false;
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
        if(lockedSession!=null)w.println("lockedDisplay="+lockedSession.diagnostics()+" submittedHash="+Arrays.hashCode(lastSubmittedFrame));
        TimerModel timer=TimerStore.read(this);
        w.println("scene="+currentScene+" timerState="+timer.state+" timerRemainingMs="+timer.remaining+" messageActive="+(ContentMonitor.latest!=null)+" musicPlaying="+ContentMonitor.playing);
        w.println("hear="+(hear==null?"not_started":hear.diagnostics()));
        if(args!=null&&Arrays.asList(args).contains("microphones"))w.println(HearCapture.inventory(this));
        // Only built-in, synthetic preview frames may be exported for artwork review.
        // Live messages, verification codes and music metadata are never included.
        if(args!=null&&Arrays.asList(args).contains("demo-frame")&&demoActive&&toolDemo!=0&&SystemClock.elapsedRealtime()<demoUntil&&sceneKey.startsWith("demo:")&&lastFrame!=null&&lastFrame==previewFrame)
            w.println("demoFrame="+Arrays.toString(previewFrame));
        w.println("smoothRotation="+smoothRotationEnabled(this)+" transitionActive="+(transition!=null)+" lastTransition="+lastTransition);
        w.println("detail=" + detail);
        w.println("recoverySource="+getSharedPreferences(PREFS,0).getString("recovery_source","none")+" errors="+errorCount);
        w.println("backgroundRestricted="+getSystemService(ActivityManager.class).isBackgroundRestricted()
            +" batteryOptimizationExempt="+getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName())
            +" recoveryJob="+(getSystemService(android.app.job.JobScheduler.class).getPendingJob(17023)!=null));
    }
}
