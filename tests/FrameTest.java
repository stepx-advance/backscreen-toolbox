package com.stepxadvance.backscreentoolbox;

import java.util.Arrays;

public final class FrameTest {
    static void check(boolean condition, String description) { if (!condition) throw new AssertionError(description); }
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("export")) { export(); return; }
        check(new ChargeState(100,100,2,true).mode == ChargeState.Mode.CHARGING,"100% while charging must not claim FULL");
        check(new ChargeState(100,100,5,true).mode == ChargeState.Mode.FULL,"system FULL");
        check(new ChargeState(100,100,5,false).mode == ChargeState.Mode.UNPLUGGED,"unplug always yields display");
        check(new ChargeState(80,100,4,true).mode == ChargeState.Mode.PAUSED,"plugged but not charging");
        check(new ChargeState(-1,100,2,true).mode == ChargeState.Mode.UNKNOWN,"unknown battery level");
        check(new ChargeState(1,0,2,true).percent == -1,"invalid scale");
        check(new ChargeState(Integer.MAX_VALUE,1,2,true).percent == 100,"overflow-safe scaling");
        check(new ChargeState(50,200,2,true).percent == 25,"scaled battery");
        check(new ChargeState(100,100,5,true,1).mode==ChargeState.Mode.BYPASS,"confirmed bypass takes precedence over full");
        check(new ChargeState(60,100,2,true,2).mode==ChargeState.Mode.BYPASS_WAITING,"pending bypass still charging");
        check(new ChargeState(80,100,4,true,0).mode==ChargeState.Mode.PAUSED,"not charging never implies bypass");
        check(new ChargeState(80,100,4,false,1).mode==ChargeState.Mode.UNPLUGGED,"stale bypass notification cannot override unplug");
        String active="旁路充电已开启",wait="充电达到 %1$s%% 时旁路充电将生效";
        check(BypassSignal.classify("com.stepos.battery",2001,"ChargeBypass",active,"",active,wait)==1,"trusted active status");
        check(BypassSignal.classify("com.stepos.battery",2001,"ChargeBypass","","充电达到 80% 时旁路充电将生效",active,wait)==2,"trusted pending status");
        check(BypassSignal.classify("other.app",2001,"ChargeBypass",active,"",active,wait)==0,"reject other apps");
        check(BypassSignal.classify("com.stepos.battery",8,"ChargeBypass",active,"",active,wait)==0,"reject unrelated battery notification");
        check(BypassSignal.classify("com.stepos.battery",2001,"other",active,"",active,wait)==0,"reject unknown channel");
        for (int level=0;level<=100;level++) {
            ChargeState s=new ChargeState(level,100,2,true);
            int[] first=ChargeFrames.render(s,0);
            for (int t=0;t<4800;t+=100) {
                int[] frame=ChargeFrames.render(s,t);
                check(frame.length==391,"exact hardware size");
                for(int v:frame)check(v>=0 && v<=255,"valid luminance");
                for(int i=17*17;i<391;i++)check(frame[i]==first[i],"percentage stays steady");
            }
            check(Arrays.equals(first,ChargeFrames.render(s,2400)),"seamless period");
        }
        ChargeState full=new ChargeState(100,100,5,true);
        check(!Arrays.equals(ChargeFrames.render(full,0),ChargeFrames.render(full,1000)),"completion animates in");
        check(Arrays.equals(ChargeFrames.render(full,1600),ChargeFrames.render(full,90000)),"completion becomes still");
        check(!Arrays.equals(ChargeFrames.render(new ChargeState(73,100,2,true),0),ChargeFrames.render(new ChargeState(73,100,2,true),720)),"charging visibly animates");
        check(!Arrays.equals(ChargeFrames.render(new ChargeState(73,100,2,true),0,true),ChargeFrames.render(new ChargeState(73,100,2,true),720,true)),"landscape energy travels inside horizontal battery");
        check(Arrays.equals(ChargeFrames.render(full,1600,true),ChargeFrames.render(full,90000,true)),"landscape full holds still");
        for(int bypass=0;bypass<3;bypass++)for(int level=0;level<=100;level++)for(int status:new int[]{2,4,5})for(int t:new int[]{0,400,1000,1800,2500}){
            ChargeState s=new ChargeState(level,100,status,true,bypass);
            int[] f=ChargeFrames.render(s,t,true),first=ChargeFrames.render(s,0,true);
            check(f.length==391,"landscape hardware size");
            for(int x=2;x<=13;x++)check(f[5*17+x]==150&&f[11*17+x]==150,"landscape symbol knockout cannot break the battery outline");
            for(int i=0;i<f.length;i++){
                check(f[i]>=0&&f[i]<=255,"landscape luminance");
                if(i<51||i>=340)check(f[i]==0,"all landscape content survives vendor center crop");
                if(i>=14*17&&i<19*17)check(f[i]==first[i],"one-line landscape percentage stays still during all animations");
            }
        }
        System.out.println("PASS: battery and bypass states, trusted-notification filtering, 4848 portrait frames, 4545 landscape frames, crop safety, steady digits, loop seam, completion hold");
    }
    static void export() {
        System.out.print("{");
        String[] modes={"charging","full","bypass","waiting","paused","charging_land","full_land","bypass_land","waiting_land"};
        for(int m=0;m<modes.length;m++) {
            if(m>0)System.out.print(",");
            System.out.print("\""+modes[m]+"\":[");
            String mode=modes[m].replace("_land","");
            boolean full=mode.equals("full"),charging=mode.equals("charging"),waiting=mode.equals("waiting"),bypass=mode.equals("bypass");
            ChargeState s=new ChargeState(charging?73:full?100:waiting?60:80,100,full?5:charging||waiting?2:4,true,bypass?1:waiting?2:0);
            for(int k=0;k<120;k++) {
                if(k>0)System.out.print(",");
                System.out.print(Arrays.toString(ChargeFrames.render(s,k*100,modes[m].endsWith("_land"))));
            }
            System.out.print("]");
        }
        System.out.println("}");
    }
}
