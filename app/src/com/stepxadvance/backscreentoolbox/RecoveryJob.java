package com.stepxadvance.backscreentoolbox;

import android.app.job.*;
import android.content.*;
import android.util.Log;

/** Charging-only, inexact 15-minute fallback; normal recovery is START_STICKY. */
public final class RecoveryJob extends JobService {
    private static final int ID=17023;
    static void sync(Context c) {
        JobScheduler scheduler=c.getSystemService(JobScheduler.class);
        if(!ChargeService.enabled(c)){scheduler.cancel(ID);return;}
        if(scheduler.getPendingJob(ID)!=null)return;
        int result=scheduler.schedule(new JobInfo.Builder(ID,new ComponentName(c,RecoveryJob.class))
            .setRequiresCharging(true).setPersisted(true).setPeriodic(15*60*1000L,5*60*1000L).build());
        if(result!=JobScheduler.RESULT_SUCCESS)Log.w(ChargeService.TAG,"Unable to schedule charging recovery check");
    }
    @Override public boolean onStartJob(JobParameters p) {
        if(!ChargeService.enabled(this)){sync(this);return false;}
        if(!ChargeService.running&&!Recovery.start(this,"charging_check"))Recovery.notifyResumeNeeded(this);
        return false;
    }
    @Override public boolean onStopJob(JobParameters p){return true;}
}
