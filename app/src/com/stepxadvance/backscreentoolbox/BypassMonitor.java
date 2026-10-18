package com.stepxadvance.backscreentoolbox;

import android.app.Notification;
import android.content.res.Resources;
import android.service.notification.*;
import java.io.*;

/** Discards all notifications except com.stepos.battery/2001/ChargeBypass before reading text.
 * No message storage, notification forwarding, or network permission. */
public final class BypassMonitor extends NotificationListenerService {
    static volatile int signal;
    static volatile boolean connected;
    private static BypassMonitor instance;
    private String activeTitle,waitingTemplate;
    @Override public void onListenerConnected(){
        instance=this;connected=true;
        try{
            Resources r=getPackageManager().getResourcesForApplication("com.stepos.battery");
            int a=r.getIdentifier("notification_bypass_charge_title","string","com.stepos.battery");
            int w=r.getIdentifier("notification_bypass_charge_content_suspend","string","com.stepos.battery");
            if(a!=0)activeTitle=r.getString(a);
            if(w!=0)waitingTemplate=r.getString(w);
        }catch(Exception ignored){}
        refresh();
        if(ChargeService.enabled(this)&&!ChargeService.running)Recovery.start(this,"bypass_listener_reconnected");
    }
    private static boolean target(StatusBarNotification s){return s!=null&&"com.stepos.battery".equals(s.getPackageName())&&s.getId()==2001&&"ChargeBypass".equals(s.getNotification().getChannelId());}
    private void accept(StatusBarNotification s){
        if(!target(s)||!ChargeService.enabled(this))return;
        Notification n=s.getNotification();
        CharSequence t=n.extras.getCharSequence(Notification.EXTRA_TITLE),body=n.extras.getCharSequence(Notification.EXTRA_TEXT);
        setSignal(BypassSignal.classify(s.getPackageName(),s.getId(),n.getChannelId(),t==null?"":t.toString(),body==null?"":body.toString(),activeTitle,waitingTemplate));
    }
    @Override public void onNotificationPosted(StatusBarNotification s){if(target(s))accept(s);}
    @Override public void onNotificationRemoved(StatusBarNotification s){if(target(s))setSignal(0);}
    @Override public void onListenerDisconnected(){
        connected=false;instance=null;setSignal(0);
        android.content.ComponentName component=new android.content.ComponentName(this,BypassMonitor.class);
        if(ChargeService.enabled(this)&&getSystemService(android.app.NotificationManager.class).isNotificationListenerAccessGranted(component)) {
            try{requestRebind(component);}catch(RuntimeException ignored){}
        }
    }
    static void refresh(){
        BypassMonitor i=instance;
        if(i==null||!connected)return;
        setSignal(0);
        try{for(StatusBarNotification s:i.getActiveNotifications())if(target(s))i.accept(s);}catch(RuntimeException ignored){}
    }
    private static void setSignal(int value){if(signal!=value){signal=value;ChargeService.bypassChanged();}}
    @Override protected void dump(FileDescriptor fd,PrintWriter w,String[] args){w.println("connected="+connected+" signal="+signal+" localizedResources="+(activeTitle!=null&&waitingTemplate!=null));}
}
