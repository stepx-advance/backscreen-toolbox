package com.stepxadvance.backscreentoolbox;

/** Second-round, offline-only native-grid candidates: spectrum, radial spectrum, ripples. */
final class HearMotion {
    private static final int FFT=2048,BANDS=15,RATE=48000;
    private final float[] levels=new float[BANDS];
    private final int[] ages={-1,-1,-1};
    private final float[] strengths=new float[3];
    private float envelope;
    private int sinceEvent=100;
    void clear(){java.util.Arrays.fill(levels,0);java.util.Arrays.fill(ages,-1);java.util.Arrays.fill(strengths,0);envelope=0;sinceEvent=100;}
    static float[] spectrum(short[] pcm,int count,int channels){return SoundSpectrum.analyze(pcm,count,channels,48000);}
    void push(short[] pcm,int count,int channels){
        float[] bands=spectrum(pcm,count,channels);
        for(int i=0;i<BANDS;i++){
            float target=18*bands[i]/(1+18*bands[i]);
            levels[i]+=(target-levels[i])*(target>levels[i]?.7f:.45f);
            if(levels[i]<.001f)levels[i]=0;
        }
        float rms=(float)SoundWave.rms(pcm,count,channels),target=12*rms/(1+12*rms),previous=envelope;
        envelope+=(target-envelope)*(target>envelope?.7f:.45f);if(envelope<.001f)envelope=0;
        sinceEvent++;
        for(int i=0;i<ages.length;i++)if(ages[i]>=0){ages[i]++;if(1+ages[i]*.35>Math.min(7,3.5+5*strengths[i]))ages[i]=-1;}
        // Decorative, input-driven response: amplitude affects the rings; the ring
        // travel itself does not claim to measure physical sound propagation.
        if(envelope>.08f&&sinceEvent>=8&&(envelope-previous>.015f||sinceEvent>=10)){
            for(int i=ages.length-1;i>0;i--){ages[i]=ages[i-1];strengths[i]=strengths[i-1];}
            ages[0]=0;strengths[0]=envelope;sinceEvent=0;
        }
    }
    private static void dot(int[] frame,int x,int y,int brightness){if(x>0&&x<16&&y>0&&y<22)frame[y*17+x]=Math.max(frame[y*17+x],Math.max(0,Math.min(255,brightness)));}
    private static void line(int[] frame,int x0,int y0,int x1,int y1,int brightness){
        int dx=Math.abs(x1-x0),sx=x0<x1?1:-1,dy=-Math.abs(y1-y0),sy=y0<y1?1:-1,error=dx+dy;
        while(true){dot(frame,x0,y0,brightness);if(x0==x1&&y0==y1)break;int twice=error*2;if(twice>=dy){error+=dy;x0+=sx;}if(twice<=dx){error+=dx;y0+=sy;}}
    }
    private static void circle(int[] frame,float radius,int brightness){
        int lastX=8+Math.round(radius),lastY=11;
        for(int i=1;i<=48;i++){double angle=i*2*Math.PI/48;int x=8+(int)Math.round(radius*Math.cos(angle)),y=11+(int)Math.round(radius*Math.sin(angle));line(frame,lastX,lastY,x,y,brightness);lastX=x;lastY=y;}
    }
    int[] frame(int style){
        int[] frame=new int[391];
        if(style==0){
            return SoundSpectrum.render(levels);
        }else if(style==1){
            int lastX=0,lastY=0;
            for(int i=0;i<=48;i++){
                double position=(i%48)*BANDS/48.0,angle=i*2*Math.PI/48-Math.PI/2;
                int band=(int)position;float level=levels[band]*(float)(1-position+band)+levels[(band+1)%BANDS]*(float)(position-band);
                double radius=3.6+level*3.6;int x=8+(int)Math.round(radius*Math.cos(angle)),y=11+(int)Math.round(radius*Math.sin(angle));
                int brightness=32+Math.round(190*Math.max(level,envelope*.65f));
                if(i==0)dot(frame,x,y,brightness);else line(frame,lastX,lastY,x,y,brightness);
                lastX=x;lastY=y;
            }
            dot(frame,8,11,32+Math.round(envelope*152));
        }else{
            for(int i=ages.length-1;i>=0;i--)if(ages[i]>=0){float radius=1+ages[i]*.35f,max=Math.min(7,3.5f+5*strengths[i]),fade=(float)Math.pow(Math.max(0,1-radius/max),.55);circle(frame,radius,Math.round((80+strengths[i]*190)*fade));}
            dot(frame,8,11,32+Math.round(envelope*152));
        }
        return frame;
    }
}
