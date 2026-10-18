package com.stepxadvance.backscreentoolbox;

import android.content.*;

public final class PowerReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (!ChargeService.enabled(c)) return;
        RecoveryJob.sync(c);
        Recovery.start(c,i.getAction());
    }
}
