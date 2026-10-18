package com.stepxadvance.backscreentoolbox;

/** Tracks continuous sensor delivery, independently of whether the angle changes. */
public final class SensorHealth {
    public static final long STALE_MS=1500, RETRY_MS=5000;
    private long started,last=-1,lastRetry=-1;
    public void start(long now){started=now;last=-1;lastRetry=-1;}
    public boolean received(long now){boolean gap=stale(now);last=now;return gap;}
    public long age(long now){return Math.max(0,now-(last<0?started:last));}
    public boolean stale(long now){return age(now)>=STALE_MS;}
    public boolean retry(long now){if(!stale(now)||lastRetry>=0&&now-lastRetry<RETRY_MS)return false;lastRetry=now;return true;}
}
