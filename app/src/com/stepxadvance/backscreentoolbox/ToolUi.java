package com.stepxadvance.backscreentoolbox;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;

/** Small native UI building blocks; no network fonts or UI dependencies. */
final class ToolUi {
    static final int BG=0xff101310,CARD=0xff1a201b,GREEN=0xffb5f47c,WHITE=0xfff0f3ed,MUTED=0xffa0aa9d,LINE=0xff323c32,AMBER=0xffe4bd80;
    static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static LinearLayout column(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    static LinearLayout row(Context c){LinearLayout v=new LinearLayout(c);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    static GradientDrawable round(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    static void surface(View view,int color,int radius){view.setBackground(round(view.getContext(),color,radius));}
    static void selected(View view,boolean on){GradientDrawable d=round(view.getContext(),on?0xff293c21:CARD,14);d.setStroke(dp(view.getContext(),1),on?GREEN:LINE);view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22ffffff),d,null));view.setSelected(on);}
    static LinearLayout screen(Activity a){
        LinearLayout root=column(a);root.setBackgroundColor(BG);root.setPadding(dp(a,20),dp(a,4),dp(a,20),dp(a,12));
        FrameLayout frame=new FrameLayout(a);frame.setBackgroundColor(BG);frame.addView(root);a.setContentView(frame);
        frame.setOnApplyWindowInsetsListener((v,i)->{Insets b=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(b.left,b.top,b.right,b.bottom);return i;});
        return root;
    }
    static TextView text(Context c,String value,float size,int color){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(c,2),1);return t;}
    static TextView label(Context c,String value,float size,int color){TextView t=text(c,value,size,color);t.setTypeface(null,Typeface.BOLD);return t;}
    static Button action(Context c,String value,boolean primary){
        Button b=new Button(c);b.setText(value);b.setTextSize(14);b.setTextColor(primary?BG:WHITE);b.setAllCaps(false);b.setTypeface(null,Typeface.BOLD);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(c,12),0,dp(c,12),0);b.setStateListAnimator(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(primary?0x22000000:0x22ffffff),round(c,primary?GREEN:0xff29312a,13),null));return b;
    }
    static void add(LinearLayout p,View child,int top,int bottom){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(p.getContext(),top),0,dp(p.getContext(),bottom));p.addView(child,lp);}
    static void space(LinearLayout p,int height){p.addView(new View(p.getContext()),new LinearLayout.LayoutParams(1,dp(p.getContext(),height)));}
    static void equal(LinearLayout row,View child,boolean last,int height){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(row.getContext(),height),1);p.rightMargin=last?0:dp(row.getContext(),8);row.addView(child,p);}
    static LinearLayout group(LinearLayout parent){
        LinearLayout group=column(parent.getContext());group.setPadding(dp(parent.getContext(),16),0,dp(parent.getContext(),16),0);surface(group,CARD,18);add(parent,group,0,0);return group;
    }
    static void divider(LinearLayout parent){View line=new View(parent.getContext());line.setBackgroundColor(LINE);parent.addView(line,new LinearLayout.LayoutParams(-1,dp(parent.getContext(),.5f)));}
    static TextView setting(LinearLayout parent,String title,String value,Runnable action){
        Context c=parent.getContext();LinearLayout row=row(c);row.setMinimumHeight(dp(c,60));row.setPadding(0,dp(c,8),0,dp(c,8));
        TextView name=text(c,title,15,WHITE);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView detail=text(c,value,12,MUTED);detail.setMaxLines(1);detail.setEllipsize(TextUtils.TruncateAt.END);row.addView(detail,new LinearLayout.LayoutParams(-2,-2));
        TextView arrow=text(c,"  ›",20,MUTED);row.addView(arrow);row.setFocusable(true);row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x16ffffff),null,round(c,Color.WHITE,8)));row.setOnClickListener(v->action.run());parent.addView(row);return detail;
    }
    static final class FeatureTab extends Button {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        FeatureTab(Context c,String title){super(c);setText(title);setTextSize(15);setTextColor(MUTED);setAllCaps(false);setPadding(0,0,0,dp(c,3));setMinWidth(0);setMinimumWidth(0);setMinHeight(0);setMinimumHeight(0);setStateListAnimator(null);setBackground(new RippleDrawable(ColorStateList.valueOf(0x16ffffff),null,round(c,Color.WHITE,12)));}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(isSelected()){paint.setColor(GREEN);float half=dp(getContext(),10),y=getHeight()-dp(getContext(),5);canvas.drawRoundRect(getWidth()/2f-half,y,getWidth()/2f+half,y+dp(getContext(),3),dp(getContext(),2),dp(getContext(),2),paint);}}
    }
    static Switch option(LinearLayout p,String title,String subtitle,boolean checked,CompoundButton.OnCheckedChangeListener listener){
        Context c=p.getContext();LinearLayout r=row(c);r.setPadding(0,dp(c,6),0,dp(c,6));r.setMinimumHeight(dp(c,62));LinearLayout words=column(c);TextView t=text(c,title,15,WHITE);words.addView(t);if(!subtitle.isEmpty())add(words,text(c,subtitle,11,MUTED),2,0);r.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        Switch s=new Switch(c);s.setContentDescription(title);s.setChecked(checked);s.setMinimumWidth(dp(c,54));s.setMinimumHeight(dp(c,48));r.addView(s,new LinearLayout.LayoutParams(dp(c,58),dp(c,48)));p.addView(r);s.setOnCheckedChangeListener(listener);r.setOnClickListener(v->s.toggle());
        s.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN)v.getParent().requestDisallowInterceptTouchEvent(true);if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)v.getParent().requestDisallowInterceptTouchEvent(false);return false;});return s;
    }
    static TextView link(LinearLayout p,String title,String summary,Runnable action){Context c=p.getContext();TextView t=text(c,title+(summary.isEmpty()?"":"\n"+summary)+"   ›",14,WHITE);t.setPadding(0,dp(c,10),dp(c,4),dp(c,10));t.setMinHeight(dp(c,52));t.setGravity(Gravity.CENTER_VERTICAL);t.setFocusable(true);t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x16ffffff),null,round(c,Color.WHITE,8)));t.setOnClickListener(v->action.run());p.addView(t);return t;}
    static final class Panel extends View {
        int[] pixels=new int[391];boolean horizontal;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Panel(Context c){super(c);setContentDescription("当前背屏显示效果预览");}
        @Override protected void onDraw(Canvas c){int w=horizontal?23:17,h=horizontal?17:23;float width=getWidth()-getPaddingLeft()-getPaddingRight(),height=getHeight()-getPaddingTop()-getPaddingBottom(),cell=Math.min(width/w,height/h),ox=getPaddingLeft()+(width-w*cell)/2,oy=getPaddingTop()+(height-h*cell)/2;for(int y=0;y<h;y++)for(int x=0;x<w;x++){int v=horizontal?(x>=3&&x<20?pixels[(y+3)*17+x-3]:0):pixels[y*17+x];int b=12+v*243/255;paint.setColor(Color.rgb(b,Math.min(255,b+3),Math.max(0,b-5)));c.drawCircle(ox+(x+.5f)*cell,oy+(y+.5f)*cell,cell*.32f,paint);}}
    }
}
