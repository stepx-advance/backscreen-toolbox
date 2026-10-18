package com.stepxadvance.backscreentoolbox;

public final class SensorHealthTest {
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        SensorHealth h=new SensorHealth();h.start(1000);
        check(!h.stale(2499),"initial registration has a grace period");
        check(h.stale(2500)&&h.retry(2500),"continuous stream loss triggers safe layout and reconnect");
        check(!h.retry(7499)&&h.retry(7500),"reconnects are bounded, never a tight loop");
        check(h.received(7600)&&!h.stale(7601),"a fresh sample recovers after interruption");
        for(int i=0;i<300;i++){h.received(7700+i*60);check(!h.stale(7700+i*60),"stationary sensor samples remain fresh");}
        h.start(30000);check(!h.stale(30001),"screen-on registration resets stale age");
        ChargeState s=new ChargeState(100,100,5,true);
        int[] safe=ChargeFrames.render(s,5000,true);
        for(int y=0;y<23;y++)for(int x=0;x<17;x++)if(y<3||y>=20)check(safe[y*17+x]==0,"fallback remains in compositor square for either rotation");
        System.out.println("PASS: continuous sensor freshness, bounded reconnect, recovery, stationary samples, screen resume, safe fallback crop");
    }
}
