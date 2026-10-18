package com.stepxadvance.backscreentoolbox;

/** Portrait peak/RMS waveform: fifteen 25 ms summaries, never retained PCM. */
public final class SoundWave {
    static final int COLUMNS=15;
    private final float[] lows=new float[COLUMNS],highs=new float[COLUMNS],powers=new float[COLUMNS];
    private int sensitivity=1;
    static final class Block {
        final float low,high,rms;
        Block(double low,double high,double rms){this.low=(float)low;this.high=(float)high;this.rms=(float)rms;}
    }
    static Block measure(short[] pcm,int count,int channels){
        if(channels<=0||pcm==null)return new Block(0,0,0);
        int frames=Math.max(0,Math.min(count,pcm.length))/channels;if(frames<2)return new Block(0,0,0);
        double strongest=-1,selectedMean=0;int selected=0;
        // Select by AC energy instead of mixing channels, which could cancel opposite phases.
        for(int channel=0;channel<channels;channel++){
            double sum=0,squares=0;
            for(int frame=0;frame<frames;frame++){double sample=pcm[frame*channels+channel]/32768.0;sum+=sample;squares+=sample*sample;}
            double mean=sum/frames,energy=Math.max(0,squares/frames-mean*mean);
            if(energy>strongest){strongest=energy;selected=channel;selectedMean=mean;}
        }
        double low=0,high=0;
        for(int frame=0;frame<frames;frame++){
            double sample=pcm[frame*channels+selected]/32768.0-selectedMean;
            low=Math.min(low,sample);high=Math.max(high,sample);
        }
        return new Block(low,high,Math.sqrt(strongest));
    }
    public static double rms(short[] pcm,int count,int channels){return measure(pcm,count,channels).rms;}
    public boolean push(short[] pcm,int count,int channels,int sensitivity){
        Block block=measure(pcm,count,channels);
        synchronized(this){
            this.sensitivity=Math.max(0,Math.min(2,sensitivity));
            shift(lows,block.low);shift(highs,block.high);shift(powers,block.rms);
        }
        return block.rms>1e-7;
    }
    private static void shift(float[] values,float value){System.arraycopy(values,1,values,0,values.length-1);values[values.length-1]=value;}
    synchronized void sensitivity(int value){sensitivity=Math.max(0,Math.min(2,value));}
    public synchronized int[] frame(){return render(lows,highs,powers,sensitivity);}
    public synchronized void clear(){java.util.Arrays.fill(lows,0);java.util.Arrays.fill(highs,0);java.util.Arrays.fill(powers,0);}
    public static int[] quiet(){return render(null,null,null,1);}
    private static float value(float[] values,int column){return values!=null&&column<values.length&&Float.isFinite(values[column])?values[column]:0;}
    private static float height(float amplitude,int sensitivity){
        double gain=sensitivity<=0?2.5:sensitivity==1?5:10;
        double scaled=Math.max(0,Math.abs(amplitude))*gain;
        // Fixed gain and a monotone soft knee. No per-frame normalization or peak hold.
        return (float)(10*scaled/(1+scaled));
    }
    private static void point(int[] frame,int x,float y,int brightness){
        int base=(int)Math.floor(y);float fraction=y-base;
        dot(frame,x,base,Math.round(brightness*(1-fraction)));
        dot(frame,x,base+1,Math.round(brightness*fraction));
    }
    private static void dot(int[] frame,int x,int y,int brightness){
        if(x<1||x>15||y<1||y>21)return;
        frame[y*17+x]=Math.max(frame[y*17+x],Math.max(0,Math.min(255,brightness)));
    }
    private static void bridge(int[] frame,int x,float from,float to){
        // Connect sharp changes with dim on-grid points; the measured contour stays bright.
        int start=Math.round(Math.min(from,to)),end=Math.round(Math.max(from,to));
        for(int y=start+1;y<end;y++)dot(frame,x,y,60);
    }
    static int[] render(float[] lows,float[] highs,float[] powers,int sensitivity){
        int[] frame=new int[391];float previousTop=11,previousBottom=11;
        for(int column=0;column<COLUMNS;column++){
            int x=column+1;
            float upper=height(Math.max(0,value(highs,column)),sensitivity);
            float lower=height(Math.min(0,value(lows,column)),sensitivity);
            float power=height(Math.max(0,value(powers,column)),sensitivity);
            float top=11-upper,bottom=11+lower;
            if(upper+lower<.08f){dot(frame,x,11,32);}
            else{
                if(column>0){bridge(frame,x,previousTop,top);bridge(frame,x,previousBottom,bottom);}
                point(frame,x,top,224);point(frame,x,bottom,224);
                // RMS is a quiet inner trace. It never obscures the positive/negative peaks.
                point(frame,x,11-Math.min(upper,power),76);point(frame,x,11+Math.min(lower,power),76);
            }
            previousTop=top;previousBottom=bottom;
        }
        return frame;
    }
    public static int[] demo(long millis){
        float[] lows=new float[COLUMNS],highs=new float[COLUMNS],powers=new float[COLUMNS];
        for(int column=0;column<COLUMNS;column++){
            // Synthetic voiced syllables and a short transient, repeated with a quiet seam.
            long t=Math.floorMod(millis-(COLUMNS-1-column)*25,4000);
            double pulse=Math.pow(Math.max(0,Math.sin(Math.PI*(t-250)/1250)),2);
            if(t<250||t>1500)pulse=0;
            double second=t>=1900&&t<2900?Math.pow(Math.sin(Math.PI*(t-1900)/1000),2)*.6:0;
            double transientPeak=t>=3250&&t<3275?.85:0;
            double level=(pulse+second)*(.09+.045*Math.sin(t*.043)+.02*Math.sin(t*.077));
            highs[column]=(float)(level*1.8+transientPeak);
            lows[column]=(float)(-level*(1.35+.35*Math.sin(t*.031))-transientPeak*.62);
            powers[column]=(float)(level*.68+transientPeak*.14);
        }
        return render(lows,highs,powers,1);
    }
}
