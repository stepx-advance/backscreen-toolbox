package com.stepxadvance.backscreentoolbox;

import android.content.Context;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.*;
import android.widget.Scroller;

/** A small native pager. Vertical scrolling remains inside the selected settings pane. */
final class FeaturePager extends ViewGroup {
    interface Listener {void selected(int page);}
    private final Scroller scroller;
    private final int slop,minVelocity;
    private int page;
    private float downX,downY,lastX;
    private boolean dragging;
    private VelocityTracker velocity;
    Listener listener;
    Runnable prepare;
    FeaturePager(Context c){super(c);scroller=new Scroller(c);ViewConfiguration config=ViewConfiguration.get(c);slop=config.getScaledTouchSlop();minVelocity=config.getScaledMinimumFlingVelocity()*3;setClipChildren(true);setFocusable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
    int page(){return page;}
    void select(int target,boolean animate){
        target=Math.max(0,Math.min(getChildCount()-1,target));boolean changed=target!=page;page=target;
        scroller.abortAnimation();int x=page*getWidth();if(animate&&getWidth()>0){scroller.startScroll(getScrollX(),0,x-getScrollX(),0,200);postInvalidateOnAnimation();}else scrollTo(x,0);
        for(int i=0;i<getChildCount();i++){View child=getChildAt(i);child.setImportantForAccessibility(i==page?IMPORTANT_FOR_ACCESSIBILITY_AUTO:IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);if(child instanceof ViewGroup)((ViewGroup)child).setDescendantFocusability(i==page?FOCUS_AFTER_DESCENDANTS:FOCUS_BLOCK_DESCENDANTS);}
        if(changed&&listener!=null)listener.selected(page);
        if(changed)sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SCROLLED);
    }
    @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs);setMeasuredDimension(w,h);for(int i=0;i<getChildCount();i++)getChildAt(i).measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){int w=r-l;for(int i=0;i<getChildCount();i++)getChildAt(i).layout(i*w,0,(i+1)*w,b-t);if(changed){scroller.abortAnimation();scrollTo(page*w,0);}}
    @Override public void computeScroll(){if(scroller.computeScrollOffset()){scrollTo(scroller.getCurrX(),0);postInvalidateOnAnimation();}}
    private void release(){if(velocity!=null){velocity.recycle();velocity=null;}dragging=false;}
    @Override public boolean onInterceptTouchEvent(MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:if(prepare!=null)prepare.run();release();downX=lastX=e.getX();downY=e.getY();velocity=VelocityTracker.obtain();velocity.addMovement(e);scroller.abortAnimation();break;
            case MotionEvent.ACTION_MOVE:
                if(Math.abs(e.getX()-downX)>slop&&Math.abs(e.getX()-downX)>Math.abs(e.getY()-downY)*1.25f){dragging=true;lastX=e.getX();getParent().requestDisallowInterceptTouchEvent(true);return true;}break;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:release();break;
        }
        return dragging;
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(velocity==null)velocity=VelocityTracker.obtain();velocity.addMovement(e);
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:downX=lastX=e.getX();downY=e.getY();scroller.abortAnimation();return true;
            case MotionEvent.ACTION_MOVE:
                float delta=lastX-e.getX();lastX=e.getX();scrollTo(Math.max(0,Math.min((getChildCount()-1)*getWidth(),getScrollX()+Math.round(delta))),0);break;
            case MotionEvent.ACTION_UP:
                velocity.computeCurrentVelocity(1000);float vx=velocity.getXVelocity(),distance=e.getX()-downX;
                int target=page;if(Math.abs(distance)>getWidth()*.22f||Math.abs(vx)>minVelocity&&Math.abs(distance)>slop*2)target+=distance<0?1:-1;
                select(target,true);release();getParent().requestDisallowInterceptTouchEvent(false);break;
            case MotionEvent.ACTION_CANCEL:select(page,true);release();getParent().requestDisallowInterceptTouchEvent(false);break;
        }
        return true;
    }
    @Override protected void onDetachedFromWindow(){release();scroller.abortAnimation();super.onDetachedFromWindow();}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.HorizontalScrollView");info.setScrollable(true);if(page>0)info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);if(page<getChildCount()-1)info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);}
    @Override public void onInitializeAccessibilityEvent(AccessibilityEvent e){super.onInitializeAccessibilityEvent(e);e.setClassName("android.widget.HorizontalScrollView");e.setScrollable(true);e.setItemCount(getChildCount());e.setFromIndex(page);e.setToIndex(page);}
    @Override public boolean performAccessibilityAction(int action,Bundle args){if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD&&page<getChildCount()-1){select(page+1,true);return true;}if(action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD&&page>0){select(page-1,true);return true;}return super.performAccessibilityAction(action,args);}
}
