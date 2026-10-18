package com.stepxadvance.backscreentoolbox;
import android.content.*;
public final class TimerReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){if(ChargeService.enabled(c))TimerStore.finish(c);else TimerStore.cancel(c);}
}
