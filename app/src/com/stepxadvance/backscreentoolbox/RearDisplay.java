package com.stepxadvance.backscreentoolbox;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import java.lang.reflect.Method;

/** Uses the device's own vendor manager, without copying or packaging vendor code. */
final class RearDisplay {
    private final Object manager;
    private final Method frame, info;
    RearDisplay(Context context) throws Exception {
        if (!"PZ261A".equals(Build.MODEL)) throw new IllegalStateException("当前机型尚未适配");
        manager = context.getSystemService("display_ext");
        if (manager == null) throw new IllegalStateException("找不到 STEPX 背屏接口");
        frame = manager.getClass().getMethod("setBackScreenAgentFrame", int[].class, Bundle.class);
        info = manager.getClass().getMethod("getBackScreenAnimationInfo");
    }
    void show(int[] pixels) throws Exception {
        if (pixels != null && pixels.length != 391) throw new IllegalArgumentException("17×23 required");
        frame.invoke(manager, pixels, null);
    }
    boolean agentBusy() throws Exception {
        Object current = info.invoke(manager);
        if (current == null) return false;
        String state = (String)current.getClass().getMethod("getState").invoke(current);
        String type = (String)current.getClass().getMethod("getType").invoke(current);
        // System notifications and camera take priority inside the vendor compositor.
        // Also voluntarily yield to active Amoo tasks, beyond the system's own priorities.
        return ("agent".equals(type) || "input".equals(type))
                && !state.startsWith("idle") && !state.startsWith("basic_");
    }
}
