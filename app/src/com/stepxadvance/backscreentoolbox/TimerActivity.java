package com.stepxadvance.backscreentoolbox;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Retain the old explicit notification target while keeping timer controls on the dashboard. */
public final class TimerActivity extends Activity {
    @Override public void onCreate(Bundle saved){super.onCreate(saved);startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("feature",1));finish();}
}
