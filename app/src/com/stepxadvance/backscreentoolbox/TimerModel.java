package com.stepxadvance.backscreentoolbox;

/** Monotonic countdown within a boot; wall deadline is used only after reboot. */
public final class TimerModel {
    public static final String[] NAMES={"番茄钟","做饭","泡茶 / 倒茶","小憩","运动"};
    public static final int[] MINUTES={25,10,3,20,15};
    public static final int IDLE=0,RUNNING=1,PAUSED=2,FINISHED=3;
    public final int state,preset;public final long total,remaining;
    public TimerModel(int state,int preset,long total,long remaining){this.state=state;this.preset=Math.max(0,Math.min(4,preset));this.total=Math.max(1000,total);this.remaining=Math.max(0,remaining);}
    public static long remaining(long endElapsed,long endWall,int savedBoot,int boot,long elapsed,long wall){return Math.max(0,savedBoot==boot?endElapsed-elapsed:endWall-wall);}
    public static String clock(long millis){long seconds=(Math.max(0,millis)+999)/1000;return String.format(java.util.Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);}
    public String label(){return total==MINUTES[preset]*60000L?NAMES[preset]:"自定义计时";}
}
