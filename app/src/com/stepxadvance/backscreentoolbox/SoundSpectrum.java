package com.stepxadvance.backscreentoolbox;

import java.util.Arrays;

/** Fifteen real frequency bands, rendered as the selected portrait mountain artwork. */
public final class SoundSpectrum {
    static final int BANDS=15,FFT=2048,MAX_FRAMES=1200;
    private final Analyzer analyzer=new Analyzer();
    private final float[] levels=new float[BANDS];
    private int sensitivity=1;

    /** One audio-thread owner. Scratch arrays never contain a previous block after return. */
    private static final class Analyzer {
        private final double[] real=new double[FFT],imag=new double[FFT],window=new double[MAX_FRAMES];
        private final float[] bands=new float[BANDS];
        private final int[] first=new int[BANDS],last=new int[BANDS];
        private int windowFrames,rate;
        private double windowEnergy;
        private boolean signal;
        float[] analyze(short[] pcm,int count,int channels,int sampleRate){
            Arrays.fill(bands,0);signal=false;
            int frames=pcm==null||channels<=0?0:Math.max(0,Math.min(count,pcm.length))/channels;
            frames=Math.min(frames,MAX_FRAMES);
            if(frames<4||sampleRate<=0)return bands;
            if(windowFrames!=frames){
                windowFrames=frames;windowEnergy=0;
                for(int i=0;i<frames;i++){window[i]=.5-.5*Math.cos(2*Math.PI*i/(frames-1));windowEnergy+=window[i]*window[i];}
            }
            if(rate!=sampleRate){
                rate=sampleRate;
                for(int band=0;band<BANDS;band++){
                    first[band]=Math.max(1,(int)Math.ceil(80*Math.pow(150,band/(double)BANDS)*FFT/rate));
                    last[band]=Math.min(FFT/2,(int)Math.ceil(80*Math.pow(150,(band+1)/(double)BANDS)*FFT/rate));
                }
            }
            double strongest=-1,mean=0;int selected=0;
            for(int channel=0;channel<channels;channel++){
                double sum=0,squares=0;
                for(int i=0;i<frames;i++){double value=pcm[i*channels+channel]/32768.0;sum+=value;squares+=value*value;}
                double average=sum/frames,energy=Math.max(0,squares/frames-average*average);
                if(energy>strongest){strongest=energy;mean=average;selected=channel;}
            }
            if(strongest<1e-14)return bands;
            signal=true;
            try{
                for(int i=0;i<frames;i++)real[i]=(pcm[i*channels+selected]/32768.0-mean)*window[i];
                for(int i=1,j=0;i<FFT;i++){
                    int bit=FFT>>1;for(;(j&bit)!=0;bit>>=1)j^=bit;j^=bit;
                    if(i<j){double value=real[i];real[i]=real[j];real[j]=value;}
                }
                for(int length=2;length<=FFT;length<<=1){
                    double angle=-2*Math.PI/length,stepR=Math.cos(angle),stepI=Math.sin(angle);
                    for(int start=0;start<FFT;start+=length){double wr=1,wi=0;
                        for(int j=0;j<length/2;j++){
                            int a=start+j,b=a+length/2;
                            double tr=real[b]*wr-imag[b]*wi,ti=real[b]*wi+imag[b]*wr;
                            real[b]=real[a]-tr;imag[b]=imag[a]-ti;real[a]+=tr;imag[a]+=ti;
                            double nextR=wr*stepR-wi*stepI;wi=wr*stepI+wi*stepR;wr=nextR;
                        }
                    }
                }
                for(int band=0;band<BANDS;band++){
                    double power=0;
                    for(int bin=first[band];bin<last[band];bin++)power+=real[bin]*real[bin]+imag[bin]*imag[bin];
                    bands[band]=(float)Math.sqrt(2*power/(FFT*windowEnergy));
                }
                return bands;
            }finally{Arrays.fill(real,0);Arrays.fill(imag,0);}
        }
    }
    static float[] analyze(short[] pcm,int count,int channels,int sampleRate){return new Analyzer().analyze(pcm,count,channels,sampleRate).clone();}
    public boolean push(short[] pcm,int count,int channels,int sampleRate,int sensitivity){
        float[] bands=analyzer.analyze(pcm,count,channels,sampleRate);
        synchronized(this){this.sensitivity=clampSensitivity(sensitivity);advance(levels,bands,this.sensitivity);}
        Arrays.fill(bands,0);
        return analyzer.signal;
    }
    public boolean push(short[] pcm,int count,int channels,int sensitivity){return push(pcm,count,channels,48000,sensitivity);}
    synchronized void sensitivity(int value){sensitivity=clampSensitivity(value);}
    private static int clampSensitivity(int value){return Math.max(0,Math.min(2,value));}
    private static void advance(float[] levels,float[] bands,int sensitivity){
        float gain=sensitivity==0?9:sensitivity==1?18:36;
        for(int i=0;i<BANDS;i++){
            float value=bands!=null&&i<bands.length&&Float.isFinite(bands[i])?Math.max(0,bands[i]):0;
            float scaled=value*gain,target=scaled/(1+scaled);
            levels[i]+=(target-levels[i])*(target>levels[i]?.7f:.45f);
            if(levels[i]<.001f)levels[i]=0;
        }
    }
    public synchronized int[] frame(){return render(levels);}
    public synchronized void clear(){Arrays.fill(levels,0);}
    public static int[] quiet(){return render(null);}
    static int[] render(float[] levels){
        int[] frame=new int[391];
        for(int band=0;band<BANDS;band++){
            float level=levels!=null&&band<levels.length&&Float.isFinite(levels[band])?Math.max(0,Math.min(1,levels[band])):0;
            int height=Math.round(level*18),x=band+1;
            if(height==0){frame[21*17+x]=32;continue;}
            int top=22-height;
            for(int y=top;y<=21;y++)frame[y*17+x]=80+Math.round(64*(21-y)/(float)Math.max(1,height-1));
            frame[top*17+x]=232;
        }
        return frame;
    }
    /** Only known synthetic profiles; no microphone or live spectrum enters this path. */
    private static final class Demo {
        static final float[][] PROFILES=profiles();
        private static float[][] profiles(){
            float[][] profiles=new float[3][];java.util.Random random=new java.util.Random(7221);
            for(int mode=0;mode<3;mode++){
                short[] pcm=new short[MAX_FRAMES];
                for(int i=0;i<pcm.length;i++){
                    double time=i/48000.0;
                    double value=mode==0?.65*Math.sin(2*Math.PI*141*time)+.25*Math.sin(2*Math.PI*282*time)+.1*Math.sin(2*Math.PI*564*time):mode==1?.6*Math.sin(2*Math.PI*1500*time)+.25*Math.sin(2*Math.PI*3000*time)+.15*Math.sin(2*Math.PI*5200*time):random.nextDouble()*2-1;
                    pcm[i]=(short)(value*32767);
                }
                profiles[mode]=analyze(pcm,pcm.length,1,48000);Arrays.fill(pcm,(short)0);
            }
            return profiles;
        }
    }
    public static int[] demo(long millis){
        float[] levels=new float[BANDS],bands=new float[BANDS];
        long current=Math.floorMod(millis,4000)/25;
        // Analytic synthetic amplitudes use the same gain, decay and renderer as live data.
        // Sixteen preceding updates bound filter error without per-frame FFT work.
        for(int step=-16;step<=0;step++){
            double time=Math.floorMod(current+step,160)*.025,amplitude=0;int profile=0;
            if(time>=.4&&time<1.3)amplitude=.42*Math.pow(Math.sin(Math.PI*(time-.4)/.9),2);
            else if(time>=1.55&&time<2.5){amplitude=.36*Math.pow(Math.sin(Math.PI*(time-1.55)/.95),2);profile=1;}
            else if(time>=2.8&&time<2.95){amplitude=.95*Math.exp(-(time-2.8)/.035);profile=2;}
            for(int i=0;i<BANDS;i++)bands[i]=(float)(Demo.PROFILES[profile][i]*amplitude);
            advance(levels,bands,1);
        }
        return render(levels);
    }
}
