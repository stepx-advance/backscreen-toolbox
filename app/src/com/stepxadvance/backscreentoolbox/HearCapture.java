package com.stepxadvance.backscreentoolbox;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.os.*;
import android.util.Pair;
import java.util.*;

/** User-started PCM capture. No files, audio logging, speech recognition or automatic restart. */
final class HearCapture {
    private final Context context;
    private final Handler main;
    private final Runnable changed,failed;
    private final SoundSpectrum wave=new SoundSpectrum();
    private volatile boolean requested;
    private volatile AudioRecord recorder;
    private Thread worker;
    private volatile boolean muted=true;
    private HandlerThread statusWorker;
    private volatile PowerManager.WakeLock audioWake;
    private long startedAt,stoppedAt;
    volatile String status="未开始",sixResult="尚未测试",mapping="未采集",sixDiagnostics="not_tested";
    volatile int clientChannels,deviceChannels,activeMicrophones,reportedMicrophones,directChannels;
    volatile int sampleRate;
    volatile long blocks,lastReadMs,lastProcessMs;
    volatile int sourceUsed,bufferFrames;
    volatile boolean signalSeen;
    private volatile int sensitivity;
    HearCapture(Context c,Handler main,Runnable changed,Runnable failed){context=c;this.main=main;this.changed=changed;this.failed=failed;}
    boolean active(){return requested;}
    void sensitivity(int value){sensitivity=Math.max(0,Math.min(2,value));wave.sensitivity(sensitivity);}
    int[] frame(){return wave.frame();}
    void start(){
        if(requested)return;
        requested=true;startedAt=SystemClock.elapsedRealtime();stoppedAt=0;status="正在连接麦克风";wave.clear();
        worker=new Thread(this::capture,"HearCapture");worker.start();
    }
    private AudioDeviceInfo builtIn(){
        AudioDeviceInfo back=null;
        for(AudioDeviceInfo d:context.getSystemService(AudioManager.class).getDevices(AudioManager.GET_DEVICES_INPUTS))if(d.getType()==AudioDeviceInfo.TYPE_BUILTIN_MIC){
            if(!"back".equals(d.getAddress()))return d;back=d;
        }
        return back;
    }
    private AudioRecord open(int channels,int source,AudioDeviceInfo device){return open(channels,source,device,false);}
    private AudioRecord open(int channels,int source,AudioDeviceInfo device,boolean indexed){
        AudioFormat.Builder format=new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(48000);
        if(channels==6&&indexed)format.setChannelIndexMask(0x3f);
        else if(channels==6)format.setChannelMask(0x17000c); // Android input 5.1 position mask, advertised by this device's fast profile.
        else format.setChannelMask(channels==2?AudioFormat.CHANNEL_IN_STEREO:AudioFormat.CHANNEL_IN_MONO);
        AudioRecord next=null;
        try{
            int bytes=channels==6?48000*channels*2/5:Math.max(48000*channels*2/25,AudioRecord.getMinBufferSize(48000,channels==2?AudioFormat.CHANNEL_IN_STEREO:AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)*2);
            next=new AudioRecord.Builder().setAudioSource(source).setAudioFormat(format.build()).setBufferSizeInBytes(bytes).build();
            if(next.getState()!=AudioRecord.STATE_INITIALIZED||!next.setPreferredDevice(device))throw new IllegalStateException("input unavailable");
            recorder=next;
            if(!requested)throw new IllegalStateException("cancelled");
            next.startRecording();
            if(next.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IllegalStateException("capture not started");
            return next;
        }catch(RuntimeException e){if(next!=null)next.release();recorder=null;return null;}
    }
    private boolean phoneInput(AudioRecord r){AudioDeviceInfo device=r.getRoutedDevice();return device!=null&&device.getType()==AudioDeviceInfo.TYPE_BUILTIN_MIC;}
    private void inspect(AudioRecord r){
        if(!requested)return;
        int client=r.getChannelCount(),rate=r.getSampleRate();
        AudioRecordingConfiguration config=r.getActiveRecordingConfiguration();int device=config==null?0:config.getFormat().getChannelCount();
        Set<String> microphones=new HashSet<>();Map<Integer,Set<String>> direct=new HashMap<>();StringBuilder details=new StringBuilder();
        try{
            for(MicrophoneInfo microphone:r.getActiveMicrophones()){
                microphones.add(microphone.getDescription());
                if(details.length()>0)details.append("; ");details.append(microphone.getDescription()).append(':');
                for(Pair<Integer,Integer> pair:microphone.getChannelMapping()){
                    if(pair.second==MicrophoneInfo.CHANNEL_MAPPING_DIRECT&&pair.first>=0&&pair.first<client)direct.computeIfAbsent(pair.first,k->new HashSet<>()).add(microphone.getDescription());
                    details.append(pair.first).append(pair.second==MicrophoneInfo.CHANNEL_MAPPING_DIRECT?"=direct,":"=processed,");
                }
            }
        }catch(java.io.IOException|RuntimeException ignored){}
        if(!requested)return;
        clientChannels=client;sampleRate=rate;deviceChannels=device;
        activeMicrophones=microphones.size();Set<String> owners=new HashSet<>();int independent=0;for(Set<String> names:direct.values())if(names.size()==1&&owners.add(names.iterator().next()))independent++;directChannels=independent;mapping=details.length()==0?"系统未提供映射":details.toString();
    }
    private boolean independentSix(){return clientChannels==6&&deviceChannels>=6&&activeMicrophones>=6&&directChannels>=6;}
    private void release(AudioRecord r){
        if(r==null)return;
        try{r.stop();}catch(RuntimeException ignored){}
        try{r.release();}catch(RuntimeException ignored){}
        if(recorder==r)recorder=null;
    }
    private void watchInput(AudioRecord active,AudioManager manager){
        statusWorker=new HandlerThread("HearInputStatus",android.os.Process.THREAD_PRIORITY_BACKGROUND);statusWorker.start();
        Handler checks=new Handler(statusWorker.getLooper());
        checks.post(new Runnable(){public void run(){
            if(!requested)return;
            try{
                if(!phoneInput(active))throw new IllegalStateException("built-in route lost");
                AudioRecordingConfiguration config=active.getActiveRecordingConfiguration();
                muted=manager.isMicrophoneMute()||config!=null&&config.isClientSilenced();
                inspect(active);if(requested)main.post(changed);
            }catch(RuntimeException e){if(requested){status="麦克风已中断，请重新开始";requested=false;try{active.stop();}catch(RuntimeException ignored){}main.post(failed);}}
            if(requested)checks.postDelayed(this,1000);
        }});
    }
    private void capture(){
        AudioRecord r=null;short[] samples=null;
        try{
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
            if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException();
            audioWake=context.getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"BackscreenToolbox:Hear");audioWake.setReferenceCounted(false);audioWake.acquire(60000);
            long wakeRenewed=SystemClock.elapsedRealtime();
            AudioManager manager=context.getSystemService(AudioManager.class);
            reportedMicrophones=countMicrophones(manager);
            AudioDeviceInfo device=builtIn();if(device==null)throw new IllegalStateException("no built-in microphone");
            int source="true".equals(manager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED))?MediaRecorder.AudioSource.UNPROCESSED:MediaRecorder.AudioSource.MIC;
            // An accepted six-channel client format alone is not proof of six independent microphones.
            sixDiagnostics="";sixResult="系统未开放六声道采集";
            for(boolean indexed:new boolean[]{false,true}){
                if(!requested)break;r=open(6,source,device,indexed);
                if(r!=null&&requested){
                    short[] probe=new short[r.getChannelCount()*1200];int n=0;
                    try{long deadline=SystemClock.elapsedRealtime()+250;while(requested&&SystemClock.elapsedRealtime()<deadline){n=r.read(probe,0,probe.length,AudioRecord.READ_NON_BLOCKING);if(n!=0)break;try{Thread.sleep(8);}catch(InterruptedException e){break;}}}finally{Arrays.fill(probe,(short)0);}
                    inspect(r);sixDiagnostics+=(indexed?"index":"position")+":read="+(n>0)+",client="+clientChannels+",device="+deviceChannels+",microphones="+activeMicrophones+",direct="+directChannels+",mapping="+mapping+"; ";
                    if(n>0&&phoneInput(r)&&independentSix()){sixResult="已核实六路独立输入";break;}
                    sixResult="未提供可核实的六路独立输入";release(r);r=null;
                }else sixDiagnostics+=(indexed?"index":"position")+":unavailable; ";
            }
            if(r==null&&requested)r=open(2,MediaRecorder.AudioSource.MIC,device);
            if(r==null&&requested)r=open(1,MediaRecorder.AudioSource.MIC,device);
            if(r==null||!requested)throw new IllegalStateException("capture unavailable");
            int channels=r.getChannelCount();sourceUsed=r.getAudioSource();bufferFrames=r.getBufferSizeInFrames();samples=new short[1200*channels];
            int rate=r.getSampleRate();
            while(requested){
                long readAt=SystemClock.elapsedRealtime();int n=r.read(samples,0,samples.length,AudioRecord.READ_BLOCKING);long processAt=SystemClock.elapsedRealtime();lastReadMs=processAt-readAt;
                if(!requested)break;
                if(n<0)throw new IllegalStateException("capture interrupted");
                if(n==0)continue;
                if(muted){wave.clear();status="麦克风暂不可用";}else{if(wave.push(samples,n,channels,rate,sensitivity))signalSeen=true;status="正在聆听";}
                Arrays.fill(samples,(short)0);blocks++;
                if(blocks==1)watchInput(r,manager);
                long now=SystemClock.elapsedRealtime();if(requested&&now-wakeRenewed>=30000){audioWake.acquire(60000);wakeRenewed=now;}lastProcessMs=now-processAt;
            }
        }catch(RuntimeException e){if(requested){status=context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED?"麦克风已中断，请重新开始":"需要麦克风权限";requested=false;main.post(failed);}}
        finally{if(statusWorker!=null)statusWorker.quitSafely();if(samples!=null)Arrays.fill(samples,(short)0);release(r);releaseWake();wave.clear();main.post(changed);}
    }
    void stop(){
        requested=false;stoppedAt=SystemClock.elapsedRealtime();AudioRecord r=recorder;if(r!=null)try{r.stop();}catch(RuntimeException ignored){}
        if(worker!=null)worker.interrupt();releaseWake();wave.clear();if(!status.contains("中断")&&!status.contains("权限"))status="已停止";
    }
    private void releaseWake(){PowerManager.WakeLock wake=audioWake;if(wake!=null&&wake.isHeld())try{wake.release();}catch(RuntimeException ignored){}}
    static int countMicrophones(AudioManager manager){
        try{Set<String> ids=new HashSet<>();for(MicrophoneInfo m:manager.getMicrophones())if(m.getType()==AudioDeviceInfo.TYPE_BUILTIN_MIC)ids.add(m.getDescription());return ids.size();}catch(java.io.IOException|RuntimeException e){return -1;}
    }
    static String inventory(Context c){
        AudioManager manager=c.getSystemService(AudioManager.class);StringBuilder out=new StringBuilder();
        out.append("reportedBuiltInMicrophones=").append(countMicrophones(manager));
        try{for(MicrophoneInfo m:manager.getMicrophones())if(m.getType()==AudioDeviceInfo.TYPE_BUILTIN_MIC){MicrophoneInfo.Coordinate3F p=m.getPosition();out.append("\nmic=").append(m.getDescription()).append(" group=").append(m.getGroup()).append(" index=").append(m.getIndexInTheGroup()).append(" position=").append(p.x).append(',').append(p.y).append(',').append(p.z);}}catch(java.io.IOException|RuntimeException ignored){}
        for(AudioDeviceInfo d:manager.getDevices(AudioManager.GET_DEVICES_INPUTS))if(d.getType()==AudioDeviceInfo.TYPE_BUILTIN_MIC)out.append("\ninput=").append(d.getAddress()).append(" channelCounts=").append(Arrays.toString(d.getChannelCounts()));
        return out.toString();
    }
    String diagnostics(){return "active="+requested+" status="+status+" clientChannels="+clientChannels+" deviceChannels="+deviceChannels+" reportedMics="+reportedMicrophones+" activeMics="+activeMicrophones+" directChannels="+directChannels+" sampleRate="+sampleRate+" blocks="+blocks+" readMs="+lastReadMs+" processMs="+lastProcessMs+" source="+sourceUsed+" bufferFrames="+bufferFrames+" signalSeen="+signalSeen+" sessionMs="+((stoppedAt>0?stoppedAt:SystemClock.elapsedRealtime())-startedAt)+" cpuHeld="+(audioWake!=null&&audioWake.isHeld())+" six="+sixResult+" sixProbe=["+sixDiagnostics+"] mapping="+mapping;}
}
