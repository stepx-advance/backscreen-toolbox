package com.stepxadvance.backscreentoolbox;

/** Native-grid design candidates; not connected to live capture or packaged in the app. */
final class HearArt {
    private final float[] trace=new float[15],lows=new float[21],highs=new float[21],rms=new float[21];
    private int sensitivity=1;
    void clear(){java.util.Arrays.fill(trace,0);java.util.Arrays.fill(lows,0);java.util.Arrays.fill(highs,0);java.util.Arrays.fill(rms,0);}
    void push(short[] pcm,int count,int channels,int sensitivity){
        this.sensitivity=Math.max(0,Math.min(2,sensitivity));
        int frames=pcm==null||channels<=0?0:Math.max(0,Math.min(count,pcm.length))/channels;
        SoundWave.Block block=SoundWave.measure(pcm,count,channels);
        shift(lows,block.low);shift(highs,block.high);shift(rms,block.rms);
        java.util.Arrays.fill(trace,0);
        if(frames<30||block.rms<1e-7)return;
        double best=-1,mean=0;int selected=0;
        for(int channel=0;channel<channels;channel++){
            double sum=0,squares=0;
            for(int i=0;i<frames;i++){double value=pcm[i*channels+channel]/32768.0;sum+=value;squares+=value*value;}
            double average=sum/frames,energy=squares/frames-average*average;
            if(energy>best){best=energy;mean=average;selected=channel;}
        }
        int peak=0;double peakAmplitude=0;
        for(int i=0;i<frames;i++){double value=Math.abs(pcm[i*channels+selected]/32768.0-mean);if(value>peakAmplitude){peakAmplitude=value;peak=i;}}
        // 6 ms at the established 48 kHz input rate. Stable rising crossings reduce
        // arbitrary phase jumps; choose one with high local energy so transients stay visible.
        int window=Math.min(288,frames),start=0;double score=-1;
        for(int i=1;i<=frames-window;i++){
            double before=pcm[(i-1)*channels+selected]/32768.0-mean;
            double after=pcm[i*channels+selected]/32768.0-mean;
            if(before>0||after<0)continue;
            double energy=0;for(int n=0;n<window;n+=4){double value=pcm[(i+n)*channels+selected]/32768.0-mean;energy+=value*value;}
            if(energy>score){score=energy;start=i;}
        }
        int peakStart=Math.max(0,Math.min(frames-window,peak-window/3));double peakEnergy=0;
        for(int i=peakStart;i<peakStart+window;i++){double value=pcm[i*channels+selected]/32768.0-mean;peakEnergy+=value*value;}
        // A zero crossing after a negative impulse must not discard the impulse itself.
        if(score<0||score*4<peakEnergy*.75)start=peakStart;
        // Area averages, not skipped samples: a true signed, time-domain reduction.
        for(int column=0;column<15;column++){
            int from=start+column*window/15,to=start+(column+1)*window/15;double sum=0;
            for(int i=from;i<to;i++)sum+=pcm[i*channels+selected]/32768.0-mean;
            trace[column]=(float)(sum/Math.max(1,to-from));
        }
    }
    private static void shift(float[] data,float value){System.arraycopy(data,1,data,0,data.length-1);data[data.length-1]=value;}
    private float scaled(float value,float extent){double gain=sensitivity==0?2.5:sensitivity==1?5:10;double v=Math.abs(value)*gain;return (float)(extent*v/(1+v));}
    private static void dot(int[] frame,int x,int y,int value){if(x>0&&x<16&&y>0&&y<22)frame[y*17+x]=Math.max(frame[y*17+x],value);}
    private static void line(int[] frame,int x0,int y0,int x1,int y1,int value){
        int dx=Math.abs(x1-x0),sx=x0<x1?1:-1,dy=-Math.abs(y1-y0),sy=y0<y1?1:-1,error=dx+dy;
        while(true){dot(frame,x0,y0,value);if(x0==x1&&y0==y1)break;int twice=2*error;if(twice>=dy){error+=dy;x0+=sx;}if(twice<=dx){error+=dx;y0+=sy;}}
    }
    int[] frame(int style){
        int[] frame=new int[391];
        if(style==2){
            // Time runs down the longer axis: a coherent ribbon with 525 ms of history.
            for(int row=0;row<21;row++){
                int left=8-Math.round(scaled(lows[row],7.5f)),right=8+Math.round(scaled(highs[row],7.5f));
                int core=Math.round(scaled(rms[row],7.5f));
                if(left==right){dot(frame,8,row+1,40);continue;}
                for(int x=left;x<=right;x++)dot(frame,x,row+1,Math.abs(x-8)<=core?160:88);
                dot(frame,left,row+1,232);dot(frame,right,row+1,232);
            }
            return frame;
        }
        int previous=11;
        float strongest=0;for(float value:trace)strongest=Math.max(strongest,Math.abs(value));
        double gain=sensitivity==0?2.5:sensitivity==1?5:10;
        int brightness=40+(int)Math.round(192*Math.min(1,strongest*gain/.12));
        for(int column=0;column<15;column++){
            int y=11-Math.round(Math.copySign(scaled(trace[column],10),trace[column]));
            if(style==1){for(int fill=Math.min(11,y);fill<=Math.max(11,y);fill++)dot(frame,column+1,fill,40+Math.round((brightness-40)*.58f));}
            if(column==0)dot(frame,1,y,brightness);
            else line(frame,column,previous,column+1,y,brightness);
            previous=y;
        }
        return frame;
    }
}
