package com.stepxadvance.backscreentoolbox;

/** Only the original battery app's identified status is authoritative. */
public final class BypassSignal {
    public static int classify(String pkg,int id,String channel,String title,String text,String activeTitle,String waitingTemplate){
        if(!"com.stepos.battery".equals(pkg)||id!=2001||!"ChargeBypass".equals(channel))return 0;
        if(activeTitle!=null&&!activeTitle.isEmpty()&&activeTitle.equals(title))return 1;
        if(waitingTemplate!=null && text!=null){
            waitingTemplate=waitingTemplate.replace("%%","%");
            int start=waitingTemplate.indexOf("%1$s");
            if(start>=0){
                String a=waitingTemplate.substring(0,start),b=waitingTemplate.substring(start+4);
                if(text.startsWith(a)&&text.endsWith(b)&&text.length()>a.length()+b.length())return 2;
            }
        }
        return 0;
    }
}
