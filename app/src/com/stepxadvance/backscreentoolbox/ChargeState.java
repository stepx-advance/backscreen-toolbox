package com.stepxadvance.backscreentoolbox;

/** Pure state rules, independent of Android, shared by display and preview. */
public final class ChargeState {
    public enum Mode { UNPLUGGED, CHARGING, FULL, BYPASS, BYPASS_WAITING, PAUSED, UNKNOWN }
    public final int percent;
    public final Mode mode;
    public final boolean charging;
    public ChargeState(int level, int scale, int status, boolean plugged) {
        this(level, scale, status, plugged, 0);
    }
    /** bypass: 0 unavailable/off, 1 confirmed active, 2 explicitly waiting. */
    public ChargeState(int level, int scale, int status, boolean plugged, int bypass) {
        percent = level < 0 || scale <= 0 ? -1 : (int)Math.min(100L, Math.max(0L, level * 100L / scale));
        charging=status==2;
        // Status FULL is authoritative; a rounded 100% while still charging is not completion.
        mode = !plugged ? Mode.UNPLUGGED : bypass == 1 ? Mode.BYPASS : percent < 0 ? Mode.UNKNOWN
                : bypass == 2 ? Mode.BYPASS_WAITING : status == 5 ? Mode.FULL
                : status == 2 ? Mode.CHARGING : Mode.PAUSED;
    }
    public String label() {
        switch (mode) {
            case CHARGING: return "正在充电";
            case FULL: return "充电完成";
            case BYPASS: return "旁路供电中";
            case BYPASS_WAITING: return charging ? "正在充电 · 等待旁路生效" : "旁路已开启 · 等待生效";
            case PAUSED: return "已接电 · 暂未充电";
            case UNKNOWN: return "等待电量信息";
            default: return "等待连接充电器";
        }
    }
}
