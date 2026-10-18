package com.stepxadvance.backscreentoolbox;
import java.util.*;

public final class SoundSpectrumTest {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static short[] tone(double hz,double amplitude,int rate){short[] pcm=new short[rate/40];for(int i=0;i<pcm.length;i++)pcm[i]=(short)(32767*amplitude*Math.sin(2*Math.PI*hz*i/rate));return pcm;}
    private static int peak(float[] bands){int result=0;for(int i=1;i<bands.length;i++)if(bands[i]>bands[result])result=i;return result;}
    private static double power(float[] bands){double sum=0;for(float value:bands){check(Float.isFinite(value)&&value>=0,"finite nonnegative power");sum+=value*value;}return sum;}
    private static long height(int[] frame){long sum=0;for(int i=0;i<391;i++)sum+=(long)frame[i]*(21-i/17);return sum;}
    private static int[] steady(short[] pcm,int sensitivity){SoundSpectrum spectrum=new SoundSpectrum();for(int i=0;i<25;i++)spectrum.push(pcm,pcm.length,1,sensitivity);return spectrum.frame();}
    private static void bounds(int[] frame){check(frame.length==391,"391 cells");for(int i=0;i<391;i++){check(frame[i]>=0&&frame[i]<=255,"brightness");if(i%17==0||i%17==16||i/17<4||i/17==22)check(frame[i]==0,"headroom and quiet border");}}
    private static short[] noise(){short[] pcm=new short[1200];Random random=new Random(71);for(int i=0;i<pcm.length;i++)pcm[i]=(short)(18000*(random.nextDouble()*2-1));return pcm;}
    public static void main(String[] args){
        if(args.length>0){
            int[][] frames;
            if("scenarios".equals(args[0]))frames=new int[][]{SoundSpectrum.quiet(),steady(tone(141,.25,48000),1),steady(tone(4000,.25,48000),1),steady(noise(),1),steady(tone(1000,.99,48000),1)};
            else{frames=new int[160][];for(int n=0;n<frames.length;n++)frames[n]=SoundSpectrum.demo(n*25);}
            System.out.print("[");for(int i=0;i<frames.length;i++){if(i>0)System.out.print(",");System.out.print(Arrays.toString(frames[i]));}System.out.print("]");return;
        }
        short[] low=tone(141,.4,48000),high=tone(4000,.4,48000),mono=tone(1000,.4,48000);
        check(peak(SoundSpectrum.analyze(low,low.length,1,48000))<3,"low frequencies at the left");check(peak(SoundSpectrum.analyze(high,high.length,1,48000))>10,"high frequencies at the right");
        double measured=power(SoundSpectrum.analyze(mono,mono.length,1,48000));check(Math.abs(measured-.08)<.002,"Hann-normalized spectrum agrees with known sine RMS squared");
        short[] louder=tone(1000,.8,48000);check(power(SoundSpectrum.analyze(louder,louder.length,1,48000))>measured*3.9,"doubling input preserves fourfold power");
        short[] dc=new short[1200];Arrays.fill(dc,(short)12000);check(power(SoundSpectrum.analyze(dc,dc.length,1,48000))<1e-10,"DC removed");
        short[] stereo=new short[2400],six=new short[7200];for(int i=0;i<1200;i++){stereo[i*2]=mono[i];stereo[i*2+1]=(short)-mono[i];six[i*6+5]=mono[i];}
        check(Arrays.equals(SoundSpectrum.analyze(mono,1200,1,48000),SoundSpectrum.analyze(stereo,2400,2,48000)),"opposite phase channels never cancel");check(Arrays.equals(SoundSpectrum.analyze(mono,1200,1,48000),SoundSpectrum.analyze(six,7200,6,48000)),"strongest of six delivered channels");
        SoundSpectrum reusable=new SoundSpectrum();short[] halfRate=tone(1000,.4,24000);for(int i=0;i<25;i++)reusable.push(halfRate,halfRate.length,1,24000,1);bounds(reusable.frame());check(Math.abs(power(SoundSpectrum.analyze(halfRate,halfRate.length,1,24000))-.08)<.002,"actual sample rate and partial window supported");
        for(short[] empty:new short[][]{null,new short[0],new short[1],new short[2],new short[3]})check(power(SoundSpectrum.analyze(empty,empty==null?0:empty.length,1,48000))==0,"invalid/short input settles safely");check(power(SoundSpectrum.analyze(mono,-1,1,48000))==0,"negative count");check(power(SoundSpectrum.analyze(mono,1200,0,48000))==0,"invalid channels");check(power(SoundSpectrum.analyze(mono,1200,1,0))==0,"invalid rate");
        for(int sensitivity=0;sensitivity<3;sensitivity++){
            long previous=0;for(double amplitude:new double[]{.02,.08,.3,.95}){int[] frame=steady(tone(1000,amplitude,48000),sensitivity);bounds(frame);check(height(frame)>previous,"fixed gain preserves input-strength ordering at every sensitivity");previous=height(frame);}
        }
        check(height(steady(tone(1000,.02,48000),2))>height(steady(tone(1000,.02,48000),0)),"three fixed gains differ");
        SoundSpectrum spectrum=new SoundSpectrum();int[] quiet=spectrum.frame();check(Arrays.stream(quiet).filter(v->v>0).count()==15,"quiet is the dim baseline");spectrum.push(mono,1200,1,1);int[] snapshot=spectrum.frame();Arrays.fill(mono,(short)0);check(Arrays.equals(snapshot,spectrum.frame()),"input PCM reuse cannot alter rendered bands");
        for(int i=0;i<25;i++)spectrum.push(new short[1200],1200,1,1);check(Arrays.equals(quiet,spectrum.frame()),"quiet settles without fabricated animation");spectrum.push(low,1200,1,1);spectrum.clear();check(Arrays.equals(quiet,spectrum.frame()),"stop clears bands");
        check(Arrays.equals(SoundSpectrum.demo(0),SoundSpectrum.demo(4000)),"synthetic demo seam");check(Arrays.equals(quiet,SoundSpectrum.demo(3975)),"demo ends at quiet");for(int t=0;t<4000;t+=25)bounds(SoundSpectrum.demo(t));bounds(SoundSpectrum.render(new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1,Float.MAX_VALUE}));
        System.out.println("PASS: selected 15-band mountain spectrum, known frequency/RMS power, amplitude and sensitivity range, phase/DC, actual rate/short input, scratch reuse, quiet/stop, demo seam and headroom");
    }
}
