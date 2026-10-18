package com.stepxadvance.backscreentoolbox;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;

public final class MainActivity extends Activity {
    private static final int BG = 0xff101310, CARD = 0xff1a201b, GREEN = 0xffb5f47c, WHITE = 0xfff0f3ed, MUTED = 0xffa0aa9d;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView state, detail, percent, resolution, bypassStatus, setupSummary;
    private boolean redirectingSetup;
    private Switch toggle;
    private MatrixView matrix;
    private int uiMode = 0;
    private long uiStarted;
    private ChargeState current;
    private boolean horizontalPreview;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if(SetupSupport.firstRun(this)) {
            redirectingSetup=true;startActivity(new Intent(this,SetupActivity.class));finish();return;
        }
        getWindow().getDecorView().setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        LinearLayout root = new LinearLayout(this); root.setOrientation(1); root.setPadding(dp(24),dp(12),dp(24),dp(24)); root.setBackgroundColor(BG);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(root); setContentView(scroll);
        TextView eyebrow = text("STEPX ADVANCE  /  BACKSCREEN", 11, GREEN); eyebrow.setLetterSpacing(.18f); root.addView(eyebrow);
        TextView title = text("STEPX 背屏工具箱", 27, WHITE); title.setTypeface(null, Typeface.BOLD); add(root,title,0,8,0,2);
        add(root,text("让电量，在背面流动。",14,MUTED),0,0,0,20);

        LinearLayout card = new LinearLayout(this); card.setOrientation(1); card.setGravity(Gravity.CENTER); card.setPadding(dp(16),dp(16),dp(16),dp(18)); card.setBackground(round(CARD,24));
        percent = text("100%",25,WHITE); percent.setTypeface(null,Typeface.BOLD); card.addView(percent);
        state = text("正在读取电量",13,GREEN); add(card,state,0,2,0,10);
        matrix = new MatrixView(); card.addView(matrix,new LinearLayout.LayoutParams(dp(252),dp(235)));
        resolution=text("17 × 23  ·  原生点阵预览",10,MUTED);add(card,resolution,0,10,0,0);
        root.addView(card,new LinearLayout.LayoutParams(-1,-2));

        toggle = new Switch(this); toggle.setText("自动显示充电状态"); toggle.setTextSize(16); toggle.setTextColor(WHITE); toggle.setPadding(0,dp(18),0,dp(14)); root.addView(toggle);
        detail = text("插电显示，拔线恢复原背屏",12,MUTED); detail.setLineSpacing(dp(3),1); add(root,detail,0,0,0,14);
        setupSummary=text("",12,MUTED);add(root,setupSummary,0,0,0,6);
        Button guide=button("设置引导与运行检查");add(root,guide,0,0,0,14);
        guide.setOnClickListener(v->startActivity(new Intent(this,SetupActivity.class)));
        LinearLayout tabs = new LinearLayout(this);
        String[] labels = {"实时", "充电", "充满", "旁路"};
        for (int i=0;i<labels.length;i++) {
            final int n=i;
            Button b=button(labels[i]); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1); lp.setMargins(i==0?0:dp(6),0,0,0); tabs.addView(b,lp);
            b.setOnClickListener(v -> { uiMode=n; uiStarted=SystemClock.elapsedRealtime(); refresh(); });
        }
        root.addView(tabs);
        Switch horizontal=new Switch(this);horizontal.setText("横屏效果预览");horizontal.setTextSize(13);horizontal.setTextColor(MUTED);horizontal.setPadding(0,dp(12),0,dp(6));
        horizontal.setOnCheckedChangeListener((v,value)->{horizontalPreview=value;refresh();});root.addView(horizontal);
        Switch smooth=new Switch(this);smooth.setText("旋转时平滑过渡");smooth.setTextSize(13);smooth.setTextColor(MUTED);smooth.setPadding(0,dp(6),0,dp(6));
        smooth.setChecked(ChargeService.smoothRotationEnabled(this));
        smooth.setOnCheckedChangeListener((v,value)->ChargeService.setSmoothRotation(this,value));root.addView(smooth);
        Button test=button("在背屏预览 12 秒"); add(root,test,0,10,0,14);
        test.setOnClickListener(v -> preview(uiMode==2?"full":uiMode==3?"bypass":"charging"));
        bypassStatus=text("",12,MUTED);add(root,bypassStatus,0,0,0,8);
        TextView note=text("竖屏：电池在上，电量在下\n横屏：横向电池与一行电量，随握持方向切换\n旁路：电池旁的能量绕行，保留真实电量\n来电、通知和相机优先；亮度跟随系统",12,MUTED); note.setLineSpacing(dp(5),1); root.addView(note);
        toggle.setChecked(ChargeService.enabled(this));
        toggle.setOnCheckedChangeListener((v,checked) -> {
            ChargeService.setEnabled(this,checked);
            if (checked) startMonitor(); else stopService(new Intent(this,ChargeService.class));
        });
        handleIntent(getIntent());
    }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); handleIntent(i); }
    private void handleIntent(Intent i) {
        // Bounded device preview, through the same path as the visible preview button.
        String mode=i.getStringExtra("preview");
        if (mode!=null) { preview(mode); i.removeExtra("preview"); }
    }
    private void startMonitor() {
        try { ChargeService.start(this); }
        catch (RuntimeException e) { ChargeService.detail="启动失败："+e.getMessage(); }
    }
    private void preview(String mode) {
        if (!ChargeService.enabled(this)) { Toast.makeText(this,"请先启用自动显示",Toast.LENGTH_SHORT).show(); return; }
        startForegroundService(new Intent(this,ChargeService.class).setAction(ChargeService.DEMO).putExtra("mode",mode));
    }
    private final Runnable redraw = new Runnable(){ @Override public void run(){ refresh(); handler.postDelayed(this,ChargeService.rotating?20:180); }};
    @Override protected void onResume(){
        super.onResume();
        if(redirectingSetup)return;
        if(ChargeService.enabled(this)&&!ChargeService.running)Recovery.start(this,"app_resumed");
        setupSummary.setText(SetupSupport.summary(this));
        handler.post(redraw);
    }
    @Override protected void onPause(){handler.removeCallbacks(redraw);super.onPause();}
    private void refresh(){
        long now=SystemClock.elapsedRealtime();
        ChargeState s=ChargeService.visible;
        if(s==null) s=ChargeService.fromBattery(registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED)));
        if(uiMode==1)s=new ChargeState(73,100,2,true);
        if(uiMode==2)s=new ChargeState(100,100,5,true);
        if(uiMode==3)s=new ChargeState(80,100,4,true,1);
        if(current==null||current.mode!=s.mode){uiStarted=now;current=s;}
        long elapsed=uiMode==0&&ChargeService.running?now-ChargeService.sceneStarted:now-uiStarted;
        boolean land=horizontalPreview||(uiMode==0&&ChargeService.landscape);
        matrix.horizontal=land;
        int[] live=ChargeService.previewFrame;
        matrix.pixels=uiMode==0&&!horizontalPreview&&ChargeService.running&&live!=null?live:ChargeFrames.render(s,elapsed,land);
        matrix.invalidate();
        resolution.setText(land?"横屏 · 宽电池与居中电量":"竖屏 · 图标与电量分层");
        percent.setText(s.percent<0?"--%":s.percent+"%");
        state.setText((uiMode==0?(ChargeService.demoActive?"背屏预览 · ":""):"效果预览 · ")+s.label());
        detail.setText(ChargeService.enabled(this)?ChargeService.detail:"已关闭 · 使用原来的背屏内容");
        bypassStatus.setText(BypassMonitor.connected?"旁路识别已连接 · 仅读取系统电池状态通知":"识别旁路需开启通知使用权；仅处理系统电池通知");
    }
    private TextView text(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setTextColor(WHITE);b.setAllCaps(false);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff29312a));return b;}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void add(LinearLayout p,View v,int l,int t,int r,int b){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(l),dp(t),dp(r),dp(b));p.addView(v,lp);}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private final class MatrixView extends View{
        int[] pixels=new int[391];boolean horizontal;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        MatrixView(){super(MainActivity.this);setContentDescription("点阵背屏效果预览");}
        @Override protected void onDraw(Canvas c){
            int w=horizontal?23:17,h=horizontal?17:23;
            float cell=Math.min(getWidth()/(float)w,getHeight()/(float)h), ox=(getWidth()-w*cell)/2,oy=(getHeight()-h*cell)/2;
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                int v=horizontal?(x>=3&&x<20?pixels[(y+3)*17+x-3]:0):pixels[y*17+x];
                int b=30+v*225/255;
                p.setColor(Color.rgb(b,Math.min(255,b+3),Math.max(0,b-5)));
                c.drawCircle(ox+(x+.5f)*cell,oy+(y+.5f)*cell,cell*.31f,p);
            }
        }
    }
}
