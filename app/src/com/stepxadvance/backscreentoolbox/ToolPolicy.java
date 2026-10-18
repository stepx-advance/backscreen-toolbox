package com.stepxadvance.backscreentoolbox;

/** User scenes share one owner; short notices return to the previous live scene. */
public final class ToolPolicy {
    public static final int NONE=0,CHARGE=1,TIMER=2,MESSAGE=3,MUSIC=4,FROG=5,DONE=6,HEAR=7;
    private ToolPolicy(){}
    public static int choose(boolean enabled,boolean timerDone,boolean message,boolean timer,boolean frog,boolean music,boolean charge){
        return choose(enabled,timerDone,message,timer,false,frog,music,charge);
    }
    public static int choose(boolean enabled,boolean timerDone,boolean message,boolean timer,boolean hear,boolean frog,boolean music,boolean charge){
        if(!enabled)return NONE;
        if(message)return MESSAGE;
        if(timerDone)return DONE;
        if(timer)return TIMER;
        if(hear)return HEAR;
        if(frog)return FROG;
        if(music)return MUSIC;
        return charge?CHARGE:NONE;
    }
}
