package com.stepxadvance.backscreentoolbox;

import android.graphics.*;

/** Rasterizes local system glyphs only when the label changes; no remote font or text service. */
final class PanelText {
    private String cached="";private int cachedHeight;
    private Bitmap strip;
    void clear(){cached="";cachedHeight=0;if(strip!=null){strip.recycle();strip=null;}}
    void draw(int[] frame,String value,int top,int height,long elapsed,int luminance){
        String clean=MessageCard.clean(value,100);if(clean.isEmpty())return;
        if(strip==null||!cached.equals(clean)||cachedHeight!=height){
            cached=clean;cachedHeight=height;Paint p=new Paint();p.setColor(Color.WHITE);p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));p.setTextSize(height);p.setAntiAlias(false);p.setSubpixelText(false);
            int width=Math.max(1,Math.min(1800,(int)Math.ceil(p.measureText(clean))+1));
            strip=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(strip);Paint.FontMetrics fm=p.getFontMetrics();c.drawText(clean,0,(height-fm.ascent-fm.descent)/2,p);
        }
        int width=strip.getWidth();long travel=Math.max(0,elapsed-1200)/170;int offset=width<=17?0:(int)(travel%(width+12));
        for(int y=0;y<height;y++)for(int x=0;x<17;x++){
            int sx=width<=17?x-(17-width)/2:x+offset;
            if(sx>=width&&width>17)sx-=width+12;
            if(sx>=0&&sx<width){int alpha=Color.alpha(strip.getPixel(sx,y));if(alpha>0)ToolFrames.dot(frame,x,top+y,alpha*luminance/255);}
        }
    }
}
