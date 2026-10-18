package com.stepxadvance.backscreentoolbox;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.text.*;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.*;

/** A fixed live preview, a horizontal feature rail, and one bounded settings pane. */
public final class MainActivity extends Activity {
    private static final int CHARGE=0,TIMER=1,MESSAGE=2,MUSIC=3,FROG=4,HEAR=5;
    private static final String[] TABS={"充电","计时","消息","音乐","奶蛙","听见"};
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean redirectingSetup,binding;
    private long controlsUpdated;
    private FeaturePager pager;
    private HorizontalScrollView tabScroll;
    private final Button[] tabs=new Button[TABS.length];
    private ToolUi.Panel panel;
    private TextView current,previewState,liveNote,messageAccess,musicAccess,appsLabel,chargeAccess;
    private Switch master,chargeSwitch,messageSwitch,otpSwitch,musicSwitch;
    private Button restore,frogStart,hearStart;
    private TextView hearState,hearInput;
    private final Button[] hearSensitivity=new Button[3];
    private boolean pendingHearPermission;
    private final Button[] frogModes=new Button[2];
    private int frogMode,timerPreset;
    private long timerMillis;
    private TextView customDuration;
    private LinearLayout timerGrid,timerControls;
    private Button timerStart,timerPause;
    private final LinearLayout[] presets=new LinearLayout[6];

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        if(SetupSupport.firstRun(this)){redirectingSetup=true;startActivity(new Intent(this,SetupActivity.class));finish();return;}
        android.content.SharedPreferences prefs=getSharedPreferences(ChargeService.PREFS,0);
        timerPreset=Math.max(0,Math.min(4,prefs.getInt("ui_timer_preset",0)));timerMillis=Math.max(1000,Math.min(5999000,prefs.getLong("ui_timer_millis",1500000)));
        frogMode=ToolSettings.value(this,"frog",false)?(prefs.getLong("frog_until",0)==0?1:0):(prefs.getInt("ui_frog_mode",0)==1?1:0);
        LinearLayout root=ToolUi.screen(this);
        buildHeader(root);buildPreview(root);buildTabs(root);
        pager=new FeaturePager(this);root.addView(pager,new LinearLayout.LayoutParams(-1,0,1));
        buildCharge(pane(CHARGE));
        buildTimer(pane(TIMER));
        buildMessages(pane(MESSAGE));
        buildMusic(pane(MUSIC));
        buildFrog(pane(FROG));
        buildHear(pane(HEAR));
        pager.listener=page->{updateTabs(page);prefs.edit().putInt("ui_feature",page).apply();refreshControls();};
        int selected=saved==null?prefs.getInt("ui_feature",CHARGE):saved.getInt("feature",CHARGE);pager.select(selected,false);updateTabs(pager.page());
        pager.prepare=()->refreshControls(true);handleIntent(getIntent());refreshControls(true);
    }
    private void buildHeader(LinearLayout root){
        LinearLayout header=ToolUi.row(this);
        header.addView(ToolUi.label(this,"STEPX 背屏工具箱",19,ToolUi.WHITE),new LinearLayout.LayoutParams(0,-2,1));
        Button settings=ToolUi.action(this,"设置",false);settings.setTextSize(12);
        settings.setBackground(ToolUi.round(this,ToolUi.BG,12));
        header.addView(settings,new LinearLayout.LayoutParams(ToolUi.dp(this,52),ToolUi.dp(this,44)));
        settings.setOnClickListener(v->generalSettings());
        root.addView(header,new LinearLayout.LayoutParams(-1,ToolUi.dp(this,52)));ToolUi.space(root,10);
    }
    private void buildPreview(LinearLayout root){
        boolean compact=getResources().getConfiguration().screenHeightDp<500;
        LinearLayout live=ToolUi.row(this);
        live.setPadding(ToolUi.dp(this,14),ToolUi.dp(this,compact?10:18),ToolUi.dp(this,18),ToolUi.dp(this,compact?10:18));
        ToolUi.surface(live,ToolUi.CARD,22);
        panel=new ToolUi.Panel(this);ToolUi.surface(panel,0xff101610,14);
        panel.setPadding(ToolUi.dp(this,8),ToolUi.dp(this,8),ToolUi.dp(this,8),ToolUi.dp(this,8));
        live.addView(panel,new LinearLayout.LayoutParams(ToolUi.dp(this,compact?84:124),ToolUi.dp(this,compact?64:176)));
        LinearLayout words=ToolUi.column(this);words.setPadding(ToolUi.dp(this,18),0,0,0);
        previewState=ToolUi.text(this,"当前背屏",11,ToolUi.GREEN);words.addView(previewState);
        current=ToolUi.label(this,"正在连接",compact?16:20,ToolUi.WHITE);
        current.setMaxLines(compact?1:3);current.setEllipsize(TextUtils.TruncateAt.END);ToolUi.add(words,current,7,0);
        liveNote=ToolUi.text(this,"",11,ToolUi.AMBER);liveNote.setMaxLines(2);liveNote.setVisibility(View.GONE);ToolUi.add(words,liveNote,5,0);
        restore=ToolUi.action(this,"预览",false);restore.setTextSize(12);restore.setOnClickListener(v->{
            if(!ChargeService.enabled(this))setMaster(true);
            else if(ChargeService.demoActive)preview("live");
            else previewFeature();
        });
        ToolUi.add(words,restore,compact?6:18,0);restore.getLayoutParams().height=ToolUi.dp(this,40);
        live.addView(words,new LinearLayout.LayoutParams(0,-2,1));ToolUi.add(root,live,0,8);
    }
    private void buildTabs(LinearLayout root){
        tabScroll=new HorizontalScrollView(this);tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setHorizontalFadingEdgeEnabled(true);tabScroll.setFadingEdgeLength(ToolUi.dp(this,10));
        LinearLayout rail=ToolUi.row(this);
        for(int i=0;i<TABS.length;i++){
            final int page=i;Button tab=new ToolUi.FeatureTab(this,TABS[i]);tab.setContentDescription(TABS[i]+"功能设置");
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ToolUi.dp(this,66),ToolUi.dp(this,50));lp.rightMargin=i==TABS.length-1?0:ToolUi.dp(this,4);
            rail.addView(tab,lp);tab.setOnClickListener(v->pager.select(page,true));tabs[i]=tab;
        }
        tabScroll.addView(rail);root.addView(tabScroll,new LinearLayout.LayoutParams(-1,ToolUi.dp(this,56)));
    }
    private LinearLayout pane(int index){
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);scroll.setAccessibilityPaneTitle(TABS[index]+"设置");
        LinearLayout content=ToolUi.column(this);content.setPadding(0,ToolUi.dp(this,8),0,ToolUi.dp(this,20));
        scroll.addView(content);pager.addView(scroll);return content;
    }
    private void buildCharge(LinearLayout p){
        LinearLayout group=ToolUi.group(p);
        chargeSwitch=ToolUi.option(group,"充电时显示","",ToolSettings.value(this,"charge_display",true),(v,on)->{if(binding)return;ToolSettings.set(this,"charge_display",on);if(on)ensureService();});
        ToolUi.divider(group);
        chargeAccess=ToolUi.setting(group,"旁路识别","",()->startActivity(new Intent(this,SetupDetailActivity.class).putExtra("kind",3)));
    }
    private void buildTimer(LinearLayout p){
        timerGrid=presetGrid(index->{if(index==5)customTimer(false);else chooseTimer(index,TimerModel.MINUTES[index]*60000L);},true);p.addView(timerGrid);
        timerStart=ToolUi.action(this,"开始计时",true);ToolUi.add(p,timerStart,16,0);timerStart.getLayoutParams().height=ToolUi.dp(this,50);timerStart.setOnClickListener(v->startTimer());
        timerControls=ToolUi.row(this);timerPause=ToolUi.action(this,"暂停",true);Button end=ToolUi.action(this,"结束",false),change=ToolUi.action(this,"改时长",false);
        ToolUi.equal(timerControls,timerPause,false,50);ToolUi.equal(timerControls,end,false,50);ToolUi.equal(timerControls,change,true,50);p.addView(timerControls);
        timerPause.setOnClickListener(v->{try{if(TimerStore.read(this).state==TimerModel.PAUSED){if(!timerPermission())return;TimerStore.resume(this);}else TimerStore.pause(this);}catch(RuntimeException e){Toast.makeText(this,"请检查闹钟和提醒权限",Toast.LENGTH_LONG).show();}refreshControls();});
        end.setOnClickListener(v->{TimerStore.cancel(this);refreshControls();});change.setOnClickListener(v->adjustTimer());
    }
    private void buildMessages(LinearLayout p){
        LinearLayout group=ToolUi.group(p);
        messageSwitch=ToolUi.option(group,"新消息提示","",ToolSettings.value(this,"messages",false),(v,on)->{
            if(binding)return;
            if(on){syncSwitch(messageSwitch,false);new AlertDialog.Builder(this,R.style.ToolDialog).setTitle("启用消息提示").setMessage("在本机读取新消息通知。普通消息不显示正文；开启验证码后，锁屏背屏也能看到代码。内容不保存、不上传。系统遮住验证码时，仅提示新消息。").setNegativeButton("取消",null).setPositiveButton("启用",(d,w)->{ToolSettings.set(this,"messages",true);ensureService();refreshControls();accessIfNeeded();}).show();}
            else{ToolSettings.set(this,"messages",false);refreshControls();}
        });
        ToolUi.divider(group);
        otpSwitch=ToolUi.option(group,"显示验证码","锁屏可见 · 25 秒后消失",ToolSettings.value(this,"otp",true),(v,on)->{if(binding)return;ToolSettings.set(this,"otp",on);ContentMonitor.latest=null;ChargeService.toolsChanged();});
        ToolUi.divider(group);appsLabel=ToolUi.setting(group,"接收应用","",this::chooseApps);
        messageAccess=permissionLink(p);
    }
    private void buildMusic(LinearLayout p){
        LinearLayout group=ToolUi.group(p);
        musicSwitch=ToolUi.option(group,"音乐伴随","",ToolSettings.value(this,"music",false),(v,on)->{if(binding)return;ToolSettings.set(this,"music",on);if(on){ensureService();accessIfNeeded();}refreshControls();});
        musicAccess=permissionLink(p);
    }
    private TextView permissionLink(LinearLayout p){
        TextView link=ToolUi.link(p,"开启通知使用权","",this::openAccess);link.setTextColor(ToolUi.AMBER);link.setTextSize(13);link.setVisibility(View.GONE);return link;
    }
    private void buildFrog(LinearLayout p){
        LinearLayout group=ToolUi.group(p),modes=ToolUi.row(this);
        ToolUi.add(group,ToolUi.text(this,"播放时长",12,ToolUi.MUTED),12,12);
        for(int i=0;i<2;i++){
            final int mode=i;Button button=ToolUi.action(this,i==0?"1 分钟":"持续播放",false);frogModes[i]=button;ToolUi.equal(modes,button,i==1,48);
            button.setOnClickListener(v->{frogMode=mode;getSharedPreferences(ChargeService.PREFS,0).edit().putInt("ui_frog_mode",mode).apply();if(ToolSettings.value(this,"frog",false)){getSharedPreferences(ChargeService.PREFS,0).edit().putLong("frog_until",mode==0?System.currentTimeMillis()+60000:0).apply();ChargeService.toolsChanged();}refreshControls();});
        }
        ToolUi.add(group,modes,0,14);
        frogStart=ToolUi.action(this,"开始大笑",true);ToolUi.add(p,frogStart,16,0);frogStart.getLayoutParams().height=ToolUi.dp(this,50);
        frogStart.setOnClickListener(v->{boolean on=ToolSettings.value(this,"frog",false);if(!on)getSharedPreferences(ChargeService.PREFS,0).edit().putLong("frog_until",frogMode==0?System.currentTimeMillis()+60000:0).apply();ToolSettings.set(this,"frog",!on);if(!on)ensureService();if(ChargeService.demoActive)preview("live");refreshControls();});
    }
    private void buildHear(LinearLayout p){
        LinearLayout group=ToolUi.group(p);ToolUi.add(group,ToolUi.text(this,"灵敏度",12,ToolUi.MUTED),12,12);
        LinearLayout levels=ToolUi.row(this);
        for(int i=0;i<3;i++){
            final int sensitivity=i;Button button=ToolUi.action(this,new String[]{"低","标准","高"}[i],false);hearSensitivity[i]=button;ToolUi.equal(levels,button,i==2,46);
            button.setOnClickListener(v->{getSharedPreferences(ChargeService.PREFS,0).edit().putInt("hear_sensitivity",sensitivity).apply();ChargeService.hearSensitivity(sensitivity);refreshControls();});
        }
        ToolUi.add(group,levels,0,12);ToolUi.divider(group);hearInput=ToolUi.setting(group,"声音输入","手机麦克风",this::hearInputDetails);
        hearState=ToolUi.text(this,"",12,ToolUi.MUTED);ToolUi.add(p,hearState,12,0);
        hearStart=ToolUi.action(this,"开始聆听",true);ToolUi.add(p,hearStart,12,0);hearStart.getLayoutParams().height=ToolUi.dp(this,50);
        hearStart.setOnClickListener(v->{if(ChargeService.hearingActive()){startForegroundService(new Intent(this,ChargeService.class).setAction(ChargeService.HEAR_STOP));if(ChargeService.demoActive)preview("live");}else startHearing();});
        ToolUi.add(p,ToolUi.text(this,"仅实时显示 · 不保存声音",11,ToolUi.MUTED),12,0);
    }
    private void startHearing(){
        if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            if(shouldShowRequestPermissionRationale(android.Manifest.permission.RECORD_AUDIO)){
                new AlertDialog.Builder(this,R.style.ToolDialog).setTitle("允许使用麦克风").setMessage("“听见”需要麦克风把周围声音变成实时频谱，不保存或上传声音。").setNegativeButton("取消",null).setPositiveButton("继续",(d,w)->requestHearPermission()).show();
            }else requestHearPermission();
            return;
        }
        ensureService();try{startForegroundService(new Intent(this,ChargeService.class).setAction(ChargeService.HEAR_START));}catch(RuntimeException e){Toast.makeText(this,"请保持主屏亮着，再次开始聆听",Toast.LENGTH_LONG).show();}refreshControls();
    }
    private void requestHearPermission(){
        pendingHearPermission=true;requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},218);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==218&&pendingHearPermission){pendingHearPermission=false;if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)startHearing();else{
            new AlertDialog.Builder(this,R.style.ToolDialog).setTitle("尚未允许麦克风").setMessage("允许后可以开始聆听。你也可以先用顶部预览查看效果。").setNegativeButton("稍后",null).setPositiveButton("应用设置",(d,w)->startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())))).show();
        }}
    }
    private void hearInputDetails(){
        HearCapture capture=ChargeService.hearing();int reported=HearCapture.countMicrophones(getSystemService(android.media.AudioManager.class));
        String text=reported<0?"系统未提供麦克风清单。":"系统上报 "+reported+" 个手机麦克风。";
        if(capture!=null&&capture.clientChannels>0){text+="\n当前采集 "+capture.clientChannels+" 声道"+(capture.deviceChannels>0?"，设备输入 "+capture.deviceChannels+" 声道":"")+"。\n"+capture.sixResult+"。";}
        else text+="\n开始聆听时检测可用输入。";
        text+="\n显示实时频谱，不标注未经核实的声源方位。";
        new AlertDialog.Builder(this,R.style.ToolDialog).setTitle("声音输入").setMessage(text).setPositiveButton("知道了",null).show();
    }
    private void requestMaster(boolean on){
        if(!on){TimerModel timer=TimerStore.read(this);if(timer.state==TimerModel.RUNNING||timer.state==TimerModel.PAUSED){
            syncSwitch(master,true);new AlertDialog.Builder(this,R.style.ToolDialog).setTitle("关闭背屏工具箱？").setMessage("当前计时也会结束。").setNegativeButton("保留计时",null).setPositiveButton("关闭并结束计时",(d,w)->setMaster(false)).show();return;
        }}setMaster(on);
    }
    private void setMaster(boolean on){ChargeService.setEnabled(this,on);if(on)Recovery.start(this,"app_switch");else stopService(new Intent(this,ChargeService.class));syncSwitch(master,on);refreshControls();}
    private void syncSwitch(Switch view,boolean checked){if(view!=null&&view.isChecked()!=checked){binding=true;view.setChecked(checked);binding=false;}}
    private void updateTabs(int page){for(int i=0;i<tabs.length;i++){tabs[i].setSelected(i==page);tabs[i].setTextColor(i==page?ToolUi.GREEN:ToolUi.MUTED);tabs[i].invalidate();}tabScroll.post(()->{View chip=tabs[page];tabScroll.smoothScrollTo(chip.getLeft()-(tabScroll.getWidth()-chip.getWidth())/2,0);});}
    private void chooseTimer(int preset,long millis){timerPreset=preset;timerMillis=millis;getSharedPreferences(ChargeService.PREFS,0).edit().putInt("ui_timer_preset",preset).putLong("ui_timer_millis",millis).apply();refreshControls();}
    private boolean timerPermission(){if(TimerStore.exact(this))return true;new AlertDialog.Builder(this,R.style.ToolDialog).setTitle("允许准时提醒").setMessage("请允许“闹钟和提醒”，再返回开始计时，保证熄屏后准时提醒。").setNegativeButton("稍后",null).setPositiveButton("前往设置",(d,w)->{try{startActivity(TimerStore.permission(this));}catch(RuntimeException e){Toast.makeText(this,"请从应用信息打开闹钟和提醒",Toast.LENGTH_LONG).show();}}).show();return false;}
    private void startTimer(){
        if(!timerPermission())return;TimerModel old=TimerStore.read(this);
        if(old.state==TimerModel.RUNNING||old.state==TimerModel.PAUSED){new AlertDialog.Builder(this,R.style.ToolDialog).setTitle("重新开始计时？").setMessage("当前计时将替换为 "+TimerModel.clock(timerMillis)+"。").setNegativeButton("保留当前",null).setPositiveButton("重新开始",(d,w)->beginTimer()).show();}else beginTimer();
    }
    private void beginTimer(){try{TimerStore.start(this,timerPreset,timerMillis);syncSwitch(master,true);if(ChargeService.demoActive)preview("live");}catch(RuntimeException e){Toast.makeText(this,"计时未开始，请检查闹钟和提醒权限",Toast.LENGTH_LONG).show();}refreshControls();}
    private LinearLayout presetGrid(java.util.function.IntConsumer choose,boolean main){
        LinearLayout grid=ToolUi.column(this);
        for(int row=0;row<2;row++){
            LinearLayout line=ToolUi.row(this);
            for(int col=0;col<3;col++){
                int index=row*3+col;LinearLayout cell=ToolUi.column(this);cell.setGravity(Gravity.CENTER);cell.setPadding(ToolUi.dp(this,4),0,ToolUi.dp(this,4),0);cell.setFocusable(true);cell.setClickable(true);
                String label=index<5?TimerModel.NAMES[index]:"自定义";
                TextView name=ToolUi.label(this,label,12,ToolUi.WHITE),duration=ToolUi.text(this,index<5?TimerModel.MINUTES[index]+" 分钟":"分钟 / 秒",11,ToolUi.MUTED);name.setGravity(Gravity.CENTER);duration.setGravity(Gravity.CENTER);cell.addView(name);cell.addView(duration);
                boolean selected=index<5?timerPreset==index&&timerMillis==TimerModel.MINUTES[index]*60000L:timerMillis!=TimerModel.MINUTES[timerPreset]*60000L;
                ToolUi.selected(cell,selected);cell.setContentDescription(label+(selected?"，已选择":""));if(main)presets[index]=cell;
                if(main&&index==5)customDuration=duration;ToolUi.equal(line,cell,col==2,68);cell.setOnClickListener(v->choose.accept(index));
            }
            ToolUi.add(grid,line,0,row==0?7:0);
        }
        return grid;
    }
    private void adjustTimer(){
        TimerModel current=TimerStore.read(this);chooseTimer(current.preset,current.total);
        LinearLayout content=ToolUi.column(this);content.setPadding(ToolUi.dp(this,22),ToolUi.dp(this,4),ToolUi.dp(this,22),ToolUi.dp(this,8));
        AlertDialog[] dialog=new AlertDialog[1];content.addView(presetGrid(which->{dialog[0].dismiss();if(which==5)customTimer(true);else{chooseTimer(which,TimerModel.MINUTES[which]*60000L);startTimer();}},false));
        dialog[0]=new AlertDialog.Builder(this,R.style.ToolDialog).setTitle("更改计时时长").setView(content).setNegativeButton("取消",null).create();dialog[0].show();
    }
    private void customTimer(boolean replace){
        LinearLayout fields=ToolUi.row(this);fields.setPadding(ToolUi.dp(this,20),ToolUi.dp(this,4),ToolUi.dp(this,20),0);EditText minutes=new EditText(this),seconds=new EditText(this);minutes.setInputType(InputType.TYPE_CLASS_NUMBER);seconds.setInputType(InputType.TYPE_CLASS_NUMBER);minutes.setHint("0–99");seconds.setHint("0–59");minutes.setContentDescription("计时分钟，0 到 99");seconds.setContentDescription("计时秒数，0 到 59");minutes.setText(Long.toString(timerMillis/60000));seconds.setText(Long.toString(timerMillis/1000%60));minutes.setSelectAllOnFocus(true);seconds.setSelectAllOnFocus(true);seconds.setImeOptions(EditorInfo.IME_ACTION_DONE);LinearLayout minuteField=ToolUi.column(this),secondField=ToolUi.column(this);minuteField.setPadding(0,0,ToolUi.dp(this,8),0);minuteField.addView(ToolUi.text(this,"分钟",12,ToolUi.MUTED));minuteField.addView(minutes);secondField.addView(ToolUi.text(this,"秒",12,ToolUi.MUTED));secondField.addView(seconds);fields.addView(minuteField,new LinearLayout.LayoutParams(0,-2,1));fields.addView(secondField,new LinearLayout.LayoutParams(0,-2,1));
        AlertDialog dialog=new AlertDialog.Builder(this,R.style.ToolDialog).setTitle("自定义时长").setView(fields).setNegativeButton("取消",null).setPositiveButton("使用",null).create();dialog.setOnShowListener(d->{View.OnClickListener apply=v->{try{int m=minutes.getText().length()==0?0:Integer.parseInt(minutes.getText().toString()),s=seconds.getText().length()==0?0:Integer.parseInt(seconds.getText().toString());if(m<0||m>99){minutes.setError("0–99 分钟");return;}if(s<0||s>59){seconds.setError("0–59 秒");return;}if(m*60+s<=0){seconds.setError("时长至少 1 秒");return;}chooseTimer(timerPreset,(m*60+s)*1000L);dialog.dismiss();if(replace)startTimer();}catch(NumberFormatException e){minutes.setError("请输入有效的分钟和秒数");}};dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(apply);seconds.setOnEditorActionListener((v,action,e)->{if(action==EditorInfo.IME_ACTION_DONE){apply.onClick(v);return true;}return false;});});dialog.show();
    }
    private void generalSettings(){
        LinearLayout p=ToolUi.column(this);p.setPadding(ToolUi.dp(this,22),0,ToolUi.dp(this,22),ToolUi.dp(this,8));
        master=ToolUi.option(p,"启用工具箱","",ChargeService.enabled(this),(v,on)->{if(!binding)requestMaster(on);});
        ToolUi.divider(p);ToolUi.option(p,"旋转过渡","",ChargeService.smoothRotationEnabled(this),(v,on)->ChargeService.setSmoothRotation(this,on));
        ToolUi.divider(p);ToolUi.setting(p,"通知使用权",ToolSettings.access(this)?"已授权":"未授权",this::openAccess);
        AlertDialog dialog=new AlertDialog.Builder(this,R.style.ToolDialog).setTitle("设置").setView(p).setNegativeButton("完成",null).create();
        ToolUi.divider(p);ToolUi.setting(p,"后台与自动启动","",()->{dialog.dismiss();startActivity(new Intent(this,SetupActivity.class));});
        ToolUi.add(p,ToolUi.text(this,"版本 1.1.0",11,ToolUi.MUTED),16,4);dialog.show();
    }
    private void ensureService(){if(!ChargeService.enabled(this)){ChargeService.setEnabled(this,true);syncSwitch(master,true);}Recovery.start(this,"tool_enabled");}
    private void accessIfNeeded(){if(!ToolSettings.access(this))openAccess();else if(!ContentMonitor.connected)try{android.service.notification.NotificationListenerService.requestRebind(new ComponentName(this,ContentMonitor.class));}catch(RuntimeException ignored){}}
    private void openAccess(){new AlertDialog.Builder(this,R.style.ToolDialog).setTitle("消息与音乐的通知使用权").setMessage("请允许“消息与音乐背屏（可选）”。原来的旁路识别只处理系统电池通知，不能代替此项。你可以随时撤销授权。").setNegativeButton("稍后",null).setPositiveButton("前往设置",(d,w)->{try{startActivity(ToolSettings.accessSettings(this));}catch(RuntimeException e){startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}}).show();}
    private void chooseApps(){List<ResolveInfo> all=getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);TreeMap<String,String> names=new TreeMap<>();for(ResolveInfo r:all){String pkg=r.activityInfo.packageName;if(!pkg.equals(getPackageName()))names.put(r.loadLabel(getPackageManager()).toString()+"\n"+pkg,pkg);}String[] labels=names.keySet().toArray(new String[0]),packages=names.values().toArray(new String[0]);Set<String> selected=new HashSet<>(getSharedPreferences(ChargeService.PREFS,0).getStringSet("message_apps",Collections.emptySet()));boolean[] checks=new boolean[labels.length];for(int i=0;i<checks.length;i++)checks[i]=selected.contains(packages[i]);new AlertDialog.Builder(this,R.style.ToolDialog).setTitle("未勾选时自动识别消息类通知").setMultiChoiceItems(labels,checks,(d,which,on)->{if(on)selected.add(packages[which]);else selected.remove(packages[which]);}).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{getSharedPreferences(ChargeService.PREFS,0).edit().putStringSet("message_apps",selected).apply();ContentMonitor.latest=null;ChargeService.toolsChanged();refreshControls();}).show();}
    private void previewFeature(){
        switch(pager.page()){
            case CHARGE:new AlertDialog.Builder(this,R.style.ToolDialog).setTitle("预览充电效果").setItems(new String[]{"充电","满电","旁路"},(d,w)->preview(new String[]{"charging","full","bypass"}[w])).show();break;
            case TIMER:preview("timer");break;
            case MESSAGE:new AlertDialog.Builder(this,R.style.ToolDialog).setTitle("预览消息效果").setItems(new String[]{"新消息","验证码"},(d,w)->preview(w==0?"message":"code")).show();break;
            case MUSIC:preview("music");break;
            case FROG:preview("frog");break;
            case HEAR:preview("hear");break;
        }
    }
    private void preview(String mode){ensureService();startForegroundService(new Intent(this,ChargeService.class).setAction(ChargeService.DEMO).putExtra("mode",mode).putExtra("timer_preset",timerPreset).putExtra("timer_millis",timerMillis));}
    private void handleIntent(Intent i){if(ChargeService.HEAR_STOP.equals(i.getAction())){if(ChargeService.hearingActive())startService(new Intent(this,ChargeService.class).setAction(ChargeService.HEAR_STOP));i.setAction(Intent.ACTION_MAIN);}if(i.hasExtra("feature")){pager.select(i.getIntExtra("feature",CHARGE),false);updateTabs(pager.page());i.removeExtra("feature");}String mode=i.getStringExtra("preview");if(mode!=null){preview(mode);i.removeExtra("preview");}}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleIntent(i);}
    @Override protected void onSaveInstanceState(Bundle out){out.putInt("feature",pager==null?CHARGE:pager.page());super.onSaveInstanceState(out);}
    private static void value(TextView view,String text){if(view!=null&&!text.contentEquals(view.getText()))view.setText(text);}
    private void refreshControls(){refreshControls(false);}
    private void refreshControls(boolean all){
        if(pager==null||chargeSwitch==null||musicSwitch==null||frogStart==null)return;
        controlsUpdated=SystemClock.elapsedRealtime();
        syncSwitch(master,ChargeService.enabled(this));syncSwitch(chargeSwitch,ToolSettings.value(this,"charge_display",true));
        syncSwitch(messageSwitch,ToolSettings.value(this,"messages",false));syncSwitch(otpSwitch,ToolSettings.value(this,"otp",true));syncSwitch(musicSwitch,ToolSettings.value(this,"music",false));
        value(chargeAccess,SetupSupport.listener(this)?"已授权":"可选");
        if(all||pager.page()==MESSAGE||pager.page()==MUSIC){
            boolean allowed=ToolSettings.access(this);
            messageAccess.setVisibility(ToolSettings.value(this,"messages",false)&&!allowed?View.VISIBLE:View.GONE);
            musicAccess.setVisibility(ToolSettings.value(this,"music",false)&&!allowed?View.VISIBLE:View.GONE);
            Set<String> selected=getSharedPreferences(ChargeService.PREFS,0).getStringSet("message_apps",Collections.emptySet());
            value(appsLabel,selected.isEmpty()?"自动识别":selected.size()+" 个应用");
        }
        if(all||pager.page()==TIMER){
            TimerModel m=TimerStore.read(this);boolean active=m.state==TimerModel.RUNNING||m.state==TimerModel.PAUSED;
            timerGrid.setVisibility(active?View.GONE:View.VISIBLE);timerStart.setVisibility(active?View.GONE:View.VISIBLE);timerControls.setVisibility(active?View.VISIBLE:View.GONE);
            value(timerPause,m.state==TimerModel.PAUSED?"继续":"暂停");
            boolean custom=timerMillis!=TimerModel.MINUTES[timerPreset]*60000L;
            value(customDuration,custom?TimerModel.clock(timerMillis):"分钟 / 秒");
            for(int i=0;i<presets.length;i++){
                boolean chosen=i<5?timerPreset==i&&timerMillis==TimerModel.MINUTES[i]*60000L:custom;
                if(presets[i].isSelected()!=chosen||presets[i].getBackground()==null)ToolUi.selected(presets[i],chosen);
                presets[i].setContentDescription((i<5?TimerModel.NAMES[i]+"，"+TimerModel.MINUTES[i]+"分钟":"自定义时长，"+TimerModel.clock(timerMillis))+(chosen?"，已选择":""));
            }
        }
        if(all||pager.page()==HEAR){
            HearCapture capture=ChargeService.hearing();boolean active=ChargeService.hearingActive();value(hearStart,active?"停止聆听":"开始聆听");
            value(hearInput,capture==null||capture.clientChannels==0?"手机麦克风":capture.clientChannels+" 声道");
            String status=active?(ChargeService.currentScene==ToolPolicy.TIMER||ChargeService.currentScene==ToolPolicy.DONE?"计时显示优先":capture.status):ChargeService.hearStatus;
            value(hearState,status);hearState.setTextColor(active?ToolUi.GREEN:ToolUi.MUTED);
            int level=Math.max(0,Math.min(2,getSharedPreferences(ChargeService.PREFS,0).getInt("hear_sensitivity",1)));
            for(int i=0;i<3;i++)if(hearSensitivity[i].getTag()==null||hearSensitivity[i].isSelected()!=(i==level)){ToolUi.selected(hearSensitivity[i],i==level);hearSensitivity[i].setTag(Boolean.TRUE);}
        }
        if(all||pager.page()==FROG){
            boolean on=ToolSettings.value(this,"frog",false);value(frogStart,on?"停止大笑":"开始大笑");
            for(int i=0;i<2;i++)if(frogModes[i].isSelected()!=(i==frogMode)||frogModes[i].getTag()==null){ToolUi.selected(frogModes[i],i==frogMode);frogModes[i].setTag(Boolean.TRUE);}
        }
    }
    private final Runnable redraw=new Runnable(){public void run(){
        if(panel==null)return;boolean enabled=ChargeService.enabled(MainActivity.this);
        value(previewState,ChargeService.demoActive?"预览 · 12 秒":"当前背屏");
        value(current,!enabled?"工具箱已关闭":ChargeService.running?ChargeService.sceneTitle:"正在连接");
        panel.pixels=!enabled||ChargeService.previewFrame==null?new int[391]:ChargeService.previewFrame;panel.horizontal=ChargeService.currentScene!=ToolPolicy.HEAR&&ChargeService.landscape;panel.invalidate();
        value(restore,!enabled?"启用":ChargeService.demoActive?"结束预览":"预览");restore.setContentDescription(!enabled?"启用工具箱":ChargeService.demoActive?"结束预览": "预览"+TABS[pager.page()]+"效果");
        String note=enabled&&ChargeService.detail.contains("Amoo")?"系统正在使用背屏":"";value(liveNote,note);liveNote.setVisibility(note.isEmpty()?View.GONE:View.VISIBLE);
        if(SystemClock.elapsedRealtime()-controlsUpdated>=250)refreshControls();handler.postDelayed(this,ChargeService.rotating?20:ChargeService.currentScene==ToolPolicy.HEAR?25:100);
    }};
    @Override protected void onResume(){super.onResume();if(redirectingSetup)return;if(ChargeService.enabled(this)&&!ChargeService.running)Recovery.start(this,"app_resumed");handler.removeCallbacks(redraw);handler.post(redraw);}
    @Override protected void onPause(){handler.removeCallbacks(redraw);super.onPause();}
}
