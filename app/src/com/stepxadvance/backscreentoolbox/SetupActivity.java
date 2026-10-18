package com.stepxadvance.backscreentoolbox;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;

/** A compact checklist; optional choices never block charging display. */
public final class SetupActivity extends Activity {
    static final int BG=0xff101310,CARD=0xff1a201b,GREEN=0xffb5f47c,WHITE=0xfff0f3ed,MUTED=0xffa0aa9d,AMBER=0xffe4bd80;
    private final TextView[] statuses=new TextView[5],marks=new TextView[5];
    private TextView progress;
    private boolean first;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);first=SetupSupport.firstRun(this);
        getWindow().getDecorView().setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);return insets;
        });
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(22),dp(14),dp(22),dp(16));root.setBackgroundColor(BG);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);
        TextView title=text("准备好背屏工具箱",26,WHITE);title.setTypeface(null,Typeface.BOLD);add(root,title,0,5);
        add(root,text("点开条目，按图中红框设置",13,MUTED),0,10);
        progress=text("",12,GREEN);add(root,progress,0,12);
        String[] labels={"系统背屏","后台运行","开机与后台恢复","旁路识别","通知栏显示"};
        for(int i=0;i<labels.length;i++) {
            final int kind=i;
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(14),dp(10),dp(14),dp(10));row.setBackground(round(CARD,17));
            marks[i]=text("○",21,GREEN);row.addView(marks[i],new LinearLayout.LayoutParams(dp(34),-2));
            LinearLayout words=new LinearLayout(this);words.setOrientation(1);
            TextView label=text(labels[i],16,WHITE);label.setTypeface(null,Typeface.BOLD);words.addView(label);
            statuses[i]=text("",11,MUTED);add(words,statuses[i],3,0);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
            row.addView(text("›",25,MUTED));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(70));lp.bottomMargin=dp(7);root.addView(row,lp);
            row.setContentDescription(labels[i]+"，查看设置图解");row.setFocusable(true);row.setOnClickListener(v->startActivity(new Intent(this,SetupDetailActivity.class).putExtra("kind",kind)));
        }
        add(root,text("消息、音乐与计时权限在使用时按需开启",11,MUTED),4,12);
        Button finish=new Button(this);finish.setText(first?"开启背屏工具箱":"完成并返回");finish.setTextSize(14);finish.setAllCaps(false);finish.setTextColor(BG);finish.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN));add(root,finish,0,6);
        finish.setOnClickListener(v->{
            if(first&&SetupSupport.legacyInstalled(this)) {
                new AlertDialog.Builder(this).setTitle("先关闭旧版背屏充电")
                    .setMessage("检测到旧版。请先关闭旧版的自动显示，再开启工具箱，避免两个应用争用背屏。新包名的后台设置和可选授权需要重新完成。")
                    .setNegativeButton("稍后",null)
                    .setNeutralButton("查看旧版",(d,w)->{try{startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+SetupSupport.LEGACY_PACKAGE)));}catch(RuntimeException ignored){}})
                    .setPositiveButton("已关闭旧版，继续",(d,w)->complete()).show();
            } else complete();
        });
        add(root,text("可稍后设置；重启后首次解锁才恢复",11,MUTED),0,0);
    }
    private void complete() {
        getSharedPreferences(ChargeService.PREFS,0).edit().putBoolean("setup_finished",true).putBoolean("opened",true).apply();
        if(first)ChargeService.setEnabled(this,true);
        startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();
    }
    private void refresh() {
        boolean power=SetupSupport.exempt(this),oem=SetupSupport.confirmed(this)&&!SetupSupport.restricted(this);
        progress.setText("后台准备 "+SetupPolicy.backgroundDone(power,SetupSupport.confirmed(this),SetupSupport.restricted(this))+"/2 · 选做项可跳过");
        set(0,SetupSupport.screen(this)==1,"已开启","请核对系统开关");
        set(1,power,"已允许","待设置 · 建议完成");
        set(2,oem,"你已确认","待核对 · 建议完成");
        set(3,SetupSupport.listener(this),"已允许 · 可选","未开启 · 可选");
        set(4,SetupSupport.notifications(this),"已开启 · 可选","不显示 · 可选");
    }
    private void set(int i,boolean done,String yes,String no){statuses[i].setText(done?yes:no);statuses[i].setTextColor(done?GREEN:i<3?AMBER:MUTED);marks[i].setText(done?"✓":"○");marks[i].setTextColor(done?GREEN:MUTED);}
    @Override protected void onResume(){super.onResume();refresh();}
    private TextView text(String value,float size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    private void add(LinearLayout parent,View child,int top,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(top),0,dp(bottom));parent.addView(child,p);}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
