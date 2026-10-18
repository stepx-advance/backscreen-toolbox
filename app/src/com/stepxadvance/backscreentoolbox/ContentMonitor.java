package com.stepxadvance.backscreentoolbox;

import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.*;
import android.os.*;
import android.service.notification.*;
import java.util.*;

/** Opt-in message and media access, independent of the existing battery-only listener. */
public final class ContentMonitor extends NotificationListenerService {
    static volatile boolean connected,playing;
    static volatile String song="",artist="";
    static volatile MessageCard latest;
    private static ContentMonitor instance;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Map<String,String> fingerprints=new LinkedHashMap<>();
    private final Map<String,MediaController> controllers=new LinkedHashMap<>();
    private final Map<String,MediaController.Callback> callbacks=new HashMap<>();
    private MediaSessionManager sessions;
    private long mediaStarted;
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionChange=list->bindMedia(list);
    @Override public void onListenerConnected(){instance=this;connected=true;sessions=getSystemService(MediaSessionManager.class);try{sessions.addOnActiveSessionsChangedListener(sessionChange,new ComponentName(this,ContentMonitor.class),handler);}catch(SecurityException ignored){}sync();}
    static void settingsChanged(){ContentMonitor s=instance;if(s!=null)s.handler.post(s::sync);else if(latest!=null){latest=null;ChargeService.toolsChanged();}}
    private void sync(){
        if(!ChargeService.enabled(this)||!ToolSettings.value(this,"messages",false))latest=null;
        if(!ChargeService.enabled(this)||!ToolSettings.value(this,"music",false)){unbind();playing=false;song="";artist="";}
        else try{bindMedia(sessions.getActiveSessions(new ComponentName(this,ContentMonitor.class)));}catch(SecurityException e){unbind();playing=false;song="";artist="";}
        ChargeService.toolsChanged();
    }
    private void bindMedia(List<MediaController> list){
        unbind();if(!ChargeService.enabled(this)||!ToolSettings.value(this,"music",false)){playing=false;song="";artist="";ChargeService.toolsChanged();return;}
        if(list!=null)for(MediaController c:list){String key=c.getSessionToken().toString();MediaController.Callback cb=new MediaController.Callback(){public void onPlaybackStateChanged(PlaybackState s){refreshMedia();}public void onMetadataChanged(MediaMetadata m){refreshMedia();}public void onSessionDestroyed(){handler.post(()->sync());}};controllers.put(key,c);callbacks.put(key,cb);c.registerCallback(cb,handler);}
        refreshMedia();
    }
    private void refreshMedia(){
        boolean next=false;String title="",who="";
        for(MediaController c:controllers.values())try{PlaybackState s=c.getPlaybackState();if(s!=null&&s.getState()==PlaybackState.STATE_PLAYING){MediaMetadata m=c.getMetadata();next=true;title=m==null?"":MessageCard.clean(m.getString(MediaMetadata.METADATA_KEY_TITLE),100);who=m==null?"":MessageCard.clean(m.getString(MediaMetadata.METADATA_KEY_ARTIST),60);if(title.isEmpty()&&m!=null)title=MessageCard.clean(m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),100);if(title.isEmpty())title="正在播放";break;}}catch(RuntimeException ignored){}
        if(next!=playing||!title.equals(song)||!who.equals(artist)){if(next&&(!playing||!title.equals(song)))mediaStarted=SystemClock.elapsedRealtime();playing=next;song=title;artist=who;ChargeService.toolsChanged();if(next&&!ChargeService.running)Recovery.start(this,"music_playing");}
    }
    static long mediaElapsed(){ContentMonitor s=instance;return s==null?0:SystemClock.elapsedRealtime()-s.mediaStarted;}
    private void unbind(){for(String key:controllers.keySet())try{controllers.get(key).unregisterCallback(callbacks.get(key));}catch(RuntimeException ignored){}controllers.clear();callbacks.clear();}
    private String appName(String pkg){try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception e){return pkg;}}
    @Override public void onNotificationPosted(StatusBarNotification s){
        if(s==null||!ChargeService.enabled(this)||!ToolSettings.value(this,"messages",false)||s.getPackageName().equals(getPackageName()))return;
        Notification n=s.getNotification();if(n==null||n.extras==null)return;if((n.flags&(Notification.FLAG_GROUP_SUMMARY|Notification.FLAG_FOREGROUND_SERVICE))!=0||s.isOngoing()||Notification.CATEGORY_TRANSPORT.equals(n.category))return;
        Set<String> selected=getSharedPreferences(ChargeService.PREFS,0).getStringSet("message_apps",Collections.emptySet());
        if(!selected.isEmpty()){if(!selected.contains(s.getPackageName()))return;}
        else if(!Notification.CATEGORY_MESSAGE.equals(n.category)&&!Notification.CATEGORY_EMAIL.equals(n.category)&&!n.extras.containsKey(Notification.EXTRA_MESSAGES))return;
        CharSequence text=n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);if(text==null)text=n.extras.getCharSequence(Notification.EXTRA_TEXT);
        long posted=s.getPostTime();
        Parcelable[] bundled=n.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if(bundled!=null){List<Notification.MessagingStyle.Message> messages=Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundled);if(!messages.isEmpty()){Notification.MessagingStyle.Message last=messages.get(messages.size()-1);text=last.getText();if(last.getTimestamp()>0)posted=last.getTimestamp();}}
        String body=text==null?"":text.toString();if(body.length()>4096)body=body.substring(0,4096);
        String fingerprint=Integer.toHexString(Objects.hash(body,String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TITLE))));
        if(fingerprint.equals(fingerprints.get(s.getKey())))return;
        if(System.currentTimeMillis()-posted>120000||posted>System.currentTimeMillis()+60000)return;
        if(latest!=null&&posted<latest.posted)return;
        fingerprints.put(s.getKey(),fingerprint);if(fingerprints.size()>80)fingerprints.remove(fingerprints.keySet().iterator().next());
        String code=ToolSettings.value(this,"otp",true)?MessageCard.code(body):"";
        String source=code.isEmpty()?"":MessageCard.source(body,appName(s.getPackageName()));
        long now=SystemClock.elapsedRealtime();latest=new MessageCard(source,code,s.getKey(),posted,now+(code.isEmpty()?10000:25000));
        ChargeService.toolsChanged();if(!ChargeService.running)Recovery.start(this,"new_message");
        handler.removeCallbacks(expire);handler.postAtTime(expire,SystemClock.uptimeMillis()+(code.isEmpty()?10000:25000));
    }
    private final Runnable expire=()->{latest=null;ChargeService.toolsChanged();};
    @Override public void onNotificationRemoved(StatusBarNotification s){if(s!=null){fingerprints.remove(s.getKey());MessageCard card=latest;if(card!=null&&card.key.equals(s.getKey())){latest=null;handler.removeCallbacks(expire);ChargeService.toolsChanged();}}}
    @Override public void onListenerDisconnected(){connected=false;latest=null;playing=false;song="";artist="";unbind();if(sessions!=null)try{sessions.removeOnActiveSessionsChangedListener(sessionChange);}catch(RuntimeException ignored){}handler.removeCallbacksAndMessages(null);instance=null;ChargeService.toolsChanged();if(ChargeService.enabled(this)&&ToolSettings.access(this))try{requestRebind(new ComponentName(this,ContentMonitor.class));}catch(RuntimeException ignored){}}
    @Override public void onDestroy(){onListenerDisconnected();super.onDestroy();}
    @Override protected void dump(java.io.FileDescriptor fd,java.io.PrintWriter w,String[] args){w.println("connected="+connected+" musicPlaying="+playing+" activeMessage="+(latest!=null)+" sessions="+controllers.size());}
}
