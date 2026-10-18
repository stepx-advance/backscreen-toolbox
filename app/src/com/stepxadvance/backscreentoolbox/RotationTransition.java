package com.stepxadvance.backscreentoolbox;

import java.util.Arrays;

/** Whole-scene quarter-turn: icon and digits contract, turn, then settle together. */
public final class RotationTransition {
    public static final long COMPOSITOR_BLANK_MS=100, DURATION_MS=180, FRAME_MS=20;
    private final int[] from;
    private final boolean toLandscape;
    private final double turn;
    private final long start;

    public RotationTransition(int[] fromFrame,int fromRotation,int toRotation,long committedAt) {
        if(fromFrame==null||fromFrame.length!=391)throw new IllegalArgumentException("17×23 required");
        from=Arrays.copyOf(fromFrame,fromFrame.length);
        toLandscape=toRotation==1||toRotation==3;
        int quarter=(fromRotation-toRotation)%4;
        if(quarter>2)quarter-=4;else if(quarter< -2)quarter+=4;
        turn=quarter*Math.PI/2;
        start=committedAt+COMPOSITOR_BLANK_MS;
    }
    public boolean finished(long now) { return now>=start+DURATION_MS; }
    public long nextDelay(long now) { return now<start?start-now:Math.min(FRAME_MS,Math.max(1,start+DURATION_MS-now)); }
    public int[] frame(int[] target,long now) {
        if(target==null||target.length!=391)throw new IllegalArgumentException("17×23 required");
        if(finished(now))return target;
        double p=Math.max(0,Math.min(1,(now-start)/(double)DURATION_MS));
        double eased=p*p*(3-2*p),angle=turn*(1-eased),cos=Math.cos(angle),sin=Math.sin(angle);
        double shrink=1-.15*Math.sin(Math.PI*p);
        double oldScale=fit(from,cos,sin,(1-eased)*.6)*shrink;
        double newScale=fit(target,cos,sin,(1-eased)*.6)*shrink;
        // Exchange layouts while the entire scene is dimmer, avoiding two bright numbers.
        double mix=Math.max(0,Math.min(1,(p-.32)/.36));
        mix=mix*mix*(3-2*mix);
        double brightness=.35+.65*Math.abs(2*p-1);
        int[] out=new int[391];
        int top=toLandscape?3:0,bottom=toLandscape?20:23;
        for(int y=top;y<bottom;y++)for(int x=0;x<17;x++) {
            double dx=x-8,dy=y-11;
            double sx=cos*dx+sin*dy,sy=-sin*dx+cos*dy;
            double a=sample(from,sx/oldScale+8,sy/oldScale+11);
            double b=sample(target,sx/newScale+8,sy/newScale+11);
            out[y*17+x]=(int)Math.round((a*(1-mix)+b*mix)*brightness);
        }
        return out;
    }
    private double fit(int[] source,double cos,double sin,double padding) {
        double maxX=0,maxY=0;
        for(int i=0;i<source.length;i++)if(source[i]>0) {
            double x=i%17-8,y=i/17-11;
            maxX=Math.max(maxX,Math.abs(cos*x-sin*y));
            maxY=Math.max(maxY,Math.abs(sin*x+cos*y));
        }
        double scale=1;
        if(maxX>0)scale=Math.min(scale,(8-padding)/maxX);
        if(maxY>0)scale=Math.min(scale,((toLandscape?8:11)-padding)/maxY);
        return scale;
    }
    private static double sample(int[] source,double x,double y) {
        int ix=(int)Math.floor(x),iy=(int)Math.floor(y);
        double fx=x-ix,fy=y-iy;
        return value(source,ix,iy)*(1-fx)*(1-fy)
            +value(source,ix+1,iy)*fx*(1-fy)
            +value(source,ix,iy+1)*(1-fx)*fy
            +value(source,ix+1,iy+1)*fx*fy;
    }
    private static int value(int[] source,int x,int y) {
        if(x<0||x>=17||y<0||y>=23)return 0;
        return source[y*17+x];
    }
}
