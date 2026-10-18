package com.stepxadvance.backscreentoolbox;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Device-specific setting screenshots are illustrations, never live system overlays. */
public final class SetupDetailActivity extends Activity {
    private int kind;
    private TextView status;
    private Button action;
    private final Handler handler=new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);kind=Math.max(0,Math.min(4,getIntent().getIntExtra("kind",0)));
        getWindow().getDecorView().setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);return insets;});
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(22),dp(10),dp(22),dp(12));root.setBackgroundColor(SetupActivity.BG);setContentView(root);
        TextView back=text("‹  设置清单",14,SetupActivity.GREEN);back.setPadding(0,dp(7),0,dp(10));root.addView(back);back.setOnClickListener(v->finish());
        String[] titles={"开启系统背屏","允许后台运行","开启自动恢复","识别旁路充电","通知栏按需显示"};
        TextView title=text(titles[kind],25,SetupActivity.WHITE);title.setTypeface(null,Typeface.BOLD);add(root,title,0,6);
        status=text("",12,SetupActivity.GREEN);add(root,status,0,10);
        ScrollView scroll=new ScrollView(this);LinearLayout content=new LinearLayout(this);content.setOrientation(1);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(kind==0) {
            add(content,text("打开「背屏显示」。持续显示需选「始终显示」；背屏朝下或系统保护仍可能熄灭。",14,SetupActivity.WHITE),0,10);
            picture(content,R.drawable.setup_screen,new Rect(52,1030,1148,1710),new Rect(78,1280,1122,1515));
        } else if(kind==1) {
            add(content,text("允许系统确认；或在列表选「STEPX 背屏工具箱」，开启「允许后台使用」。",14,SetupActivity.WHITE),0,10);
            picture(content,R.drawable.setup_power,new Rect(52,1052,1148,1450),new Rect(78,1230,1122,1430));
        } else if(kind==2) {
            add(content,text("1  应用信息 → 耗电和后台",14,SetupActivity.WHITE),0,8);
            picture(content,R.drawable.setup_app_info,new Rect(52,1440,1148,1650),new Rect(78,1480,1122,1630));
            add(content,text("2  选手动管理，开启红框两项",14,SetupActivity.WHITE),16,8);
            picture(content,R.drawable.setup_oem,new Rect(52,1165,1148,2440),new Rect(78,1530,1122,1735),new Rect(78,1760,1122,1945),new Rect(78,2230,1122,2410));
            Button confirm=button("我已核对这两项");add(content,confirm,12,0);confirm.setOnClickListener(v->confirmOem());
            add(content,text("此项按你的确认记录，应用无法读取原厂开关。",11,SetupActivity.MUTED),8,0);
        } else if(kind==3) {
            add(content,text("可选：开启通知使用权，识别原厂旁路状态。只处理系统电池通知。",14,SetupActivity.WHITE),0,10);
            picture(content,R.drawable.setup_listener,new Rect(52,715,1148,1220),new Rect(78,785,1122,945));
        } else {
            add(content,text("可选：打开显示通知，关闭则不在通知栏常驻。背屏仍可运行，系统「活动应用」仍有标识。",14,SetupActivity.WHITE),0,10);
            picture(content,R.drawable.setup_notification,new Rect(52,660,1148,890),new Rect(78,710,1122,880));
            add(content,text("不显示通知时，也收不到后台恢复提醒。",12,SetupActivity.MUTED),12,0);
        }
        add(content,text("本机系统设置示意 · 以实际页面为准",10,SetupActivity.MUTED),10,12);
        action=button("前往设置");action.setTextColor(SetupActivity.BG);action.setBackgroundTintList(android.content.res.ColorStateList.valueOf(SetupActivity.GREEN));add(root,action,10,0);action.setOnClickListener(v->openSetting());
    }
    private void openSetting() {
        if(kind==0)open(new Intent().setComponent(new ComponentName("com.android.settings","com.stepos.settings.StepOSSettingsActivity$StepOSBackScreenActivity")),new Intent(Settings.ACTION_SETTINGS));
        else if(kind==1)open(SetupSupport.exempt(this)?new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS):Recovery.settings(this),SetupSupport.appSettings(this));
        else if(kind==2)open(SetupSupport.appSettings(this),new Intent(Settings.ACTION_SETTINGS));
        else if(kind==3) {
            if(SetupSupport.listener(this)&&!BypassMonitor.connected)try{android.service.notification.NotificationListenerService.requestRebind(new ComponentName(this,BypassMonitor.class));}catch(RuntimeException ignored){}
            open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,new ComponentName(this,BypassMonitor.class).flattenToString()),new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } else {
            boolean allowed=checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
            boolean requested=getSharedPreferences(ChargeService.PREFS,0).getBoolean("setup_notifications_requested",false);
            if(!allowed&&(!requested||shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
                getSharedPreferences(ChargeService.PREFS,0).edit().putBoolean("setup_notifications_requested",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},9);
            } else open(SetupSupport.notificationSettings(this),SetupSupport.appSettings(this));
        }
    }
    private void refresh(){if(status==null)return;String s;
        if(kind==0)s=SetupSupport.screen(this)==1?"已开启":"待设置";
        else if(kind==1)s=SetupSupport.exempt(this)?"已允许 · 系统已确认":"尚未允许 · 建议完成";
        else if(kind==2)s=SetupSupport.restricted(this)?"系统仍限制后台，请核对":SetupSupport.confirmed(this)?"你已确认 · 以实际开关为准":"尚未确认 · 建议完成";
        else if(kind==3)s=SetupSupport.listener(this)?(BypassMonitor.connected?"已允许，识别已连接":"已允许，等待系统连接"):"未开启 · 可跳过";
        else s=SetupSupport.notifications(this)?"通知栏显示已开启 · 可选":"通知栏不显示 · 可选";
        status.setText(s);
    }
    private void confirmOem(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(20),dp(8),dp(20),dp(8));
        CheckBox self=new CheckBox(this);self.setText("已开启「允许自启动」");box.addView(self);
        CheckBox background=new CheckBox(this);background.setText("已开启「允许后台运行」");box.addView(background);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("按系统实际状态确认").setView(box).setNegativeButton("稍后",null).setPositiveButton("保存确认",(dialog,w)->{getSharedPreferences(ChargeService.PREFS,0).edit().putBoolean("setup_oem_confirmed",true).apply();refresh();}).create();
        d.setOnShowListener(v->{Button save=d.getButton(AlertDialog.BUTTON_POSITIVE);save.setEnabled(false);android.widget.CompoundButton.OnCheckedChangeListener change=(b,c)->save.setEnabled(self.isChecked()&&background.isChecked());self.setOnCheckedChangeListener(change);background.setOnCheckedChangeListener(change);});d.show();
    }
    private void open(Intent target,Intent fallback){try{startActivity(target);}catch(ActivityNotFoundException|SecurityException e){try{startActivity(fallback);}catch(RuntimeException ignored){Toast.makeText(this,"请从系统设置打开本应用信息",Toast.LENGTH_LONG).show();}}}
    private void picture(LinearLayout parent,int resource,Rect crop,Rect... frames){add(parent,new SettingPicture(resource,crop,frames),0,0);}
    private final class SettingPicture extends View {
        private final Bitmap image;private final Rect crop;private final Rect[] frames;private final Paint paint=new Paint(3);
        SettingPicture(int resource,Rect source,Rect[] boxes){super(SetupDetailActivity.this);BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;image=BitmapFactory.decodeResource(getResources(),resource,options);crop=source;frames=boxes;setContentDescription("系统设置示意图，红框标出需要开启的位置");}
        @Override protected void onMeasure(int w,int h){int width=MeasureSpec.getSize(w);setMeasuredDimension(width,Math.round(width*crop.height()/(float)crop.width()));}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float scale=getWidth()/(float)crop.width();c.drawBitmap(image,crop,new Rect(0,0,getWidth(),getHeight()),paint);paint.setColor(0xfff03232);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));for(Rect r:frames)c.drawRoundRect(new RectF((r.left-crop.left)*scale,(r.top-crop.top)*scale,(r.right-crop.left)*scale,(r.bottom-crop.top)*scale),dp(5),dp(5),paint);paint.setStyle(Paint.Style.FILL);}
    }
    @Override protected void onResume(){super.onResume();refresh();handler.postDelayed(()->refresh(),1000);}
    @Override protected void onPause(){handler.removeCallbacksAndMessages(null);super.onPause();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);refresh();}
    private TextView text(String value,float size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);return t;}
    private Button button(String label){Button b=new Button(this);b.setText(label);b.setTextSize(14);b.setAllCaps(false);return b;}
    private void add(LinearLayout parent,View child,int top,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(top),0,dp(bottom));parent.addView(child,p);}
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
