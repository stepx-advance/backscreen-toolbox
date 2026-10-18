package com.stepxadvance.backscreentoolbox;
import java.util.*;
public final class SoundWaveTest {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static long spread(int[] frame){long sum=0;for(int i=0;i<frame.length;i++)sum+=(long)frame[i]*Math.abs(i/17-11);return sum;}
    private static short[] sine(double amplitude){short[] pcm=new short[1200];for(int i=0;i<pcm.length;i++)pcm[i]=(short)(Math.sin(i*Math.PI/24)*32767*amplitude);return pcm;}
    private static int[] steady(short[] pcm,int sensitivity){SoundWave wave=new SoundWave();for(int i=0;i<15;i++)wave.push(pcm,pcm.length,1,sensitivity);return wave.frame();}
    private static void bounds(int[] frame){
        check(frame.length==391,"frame cells");
        for(int i=0;i<frame.length;i++){int x=i%17,y=i/17;check(frame[i]>=0&&frame[i]<=255,"valid luminance");if(x==0||x==16||y==0||y==22)check(frame[i]==0,"one-point quiet margin on full portrait grid");}
    }
    private static int[][] scenarios(){
        int[][] frames=new int[5][];frames[0]=SoundWave.quiet();frames[1]=steady(sine(.025),1);
        SoundWave speech=new SoundWave();
        for(int block=0;block<15;block++){
            short[] pcm=new short[1200];double envelope=.035+.2*Math.pow(Math.sin(block*.38),2);
            for(int i=0;i<pcm.length;i++){double t=(block*1200+i)/48000.0;pcm[i]=(short)(32767*envelope*(.62*Math.sin(2*Math.PI*173*t)+.24*Math.sin(2*Math.PI*389*t)+.14*Math.sin(2*Math.PI*811*t)));}
            speech.push(pcm,pcm.length,1,1);
        }
        frames[2]=speech.frame();
        SoundWave clap=new SoundWave();Random random=new Random(27);
        for(int block=0;block<15;block++){
            short[] pcm=new short[1200];double envelope=block<6?0:.9*Math.exp(-(block-6)*.8);
            for(int i=0;i<pcm.length;i++)pcm[i]=(short)(32767*envelope*(random.nextDouble()*2-1)*Math.exp(-i/1800.0));
            clap.push(pcm,pcm.length,1,1);
        }
        frames[3]=clap.frame();frames[4]=steady(sine(.95),1);return frames;
    }
    public static void main(String[] args){
        if(args.length>0){
            int[][] frames;
            if("scenarios".equals(args[0]))frames=scenarios();
            else{frames=new int[160][];for(int n=0;n<frames.length;n++)frames[n]=SoundWave.demo(n*25);}
            System.out.print("[");for(int i=0;i<frames.length;i++){if(i>0)System.out.print(",");System.out.print(Arrays.toString(frames[i]));}System.out.print("]");return;
        }
        short[] mono=sine(.5),stereo=new short[2400],six=new short[7200],dc=new short[1200];Arrays.fill(dc,(short)12000);
        for(int i=0;i<1200;i++){stereo[i*2]=mono[i];stereo[i*2+1]=(short)-mono[i];six[i*6+5]=mono[i];}
        check(SoundWave.rms(new short[1200],1200,1)==0,"silence");check(SoundWave.rms(dc,dc.length,1)<1e-7,"remove DC offset");
        check(SoundWave.rms(stereo,stereo.length,2)>.3,"opposite-phase microphones do not cancel");check(SoundWave.rms(six,six.length,6)>.3,"any of six channels can supply measured detail");check(SoundWave.rms(null,0,0)==0,"empty input");
        SoundWave.Block measured=SoundWave.measure(mono,mono.length,1);
        check(Math.abs(measured.high-.5)<.001&&Math.abs(measured.low+.5)<.001,"actual signed peaks");check(Math.abs(measured.rms-.5/Math.sqrt(2))<.001,"actual RMS");
        short[] transientPcm=new short[1200];transientPcm[16]=30000;
        SoundWave.Block transientBlock=SoundWave.measure(transientPcm,transientPcm.length,1);
        check(transientBlock.high>.9&&transientBlock.low>-.001,"asymmetric impulse stays asymmetric instead of mirroring RMS");
        short[] pulses=new short[1200];Arrays.fill(pulses,0,6,(short)32760);
        SoundWave.Block pulseBlock=SoundWave.measure(pulses,pulses.length,1),toneBlock=SoundWave.measure(sine(.1),1200,1);
        check(Math.abs(pulseBlock.rms-toneBlock.rms)<.001&&pulseBlock.high>toneBlock.high*9,"same average energy retains very different short peaks");
        check(!Arrays.equals(steady(pulses,1),steady(sine(.1),1)),"crest details survive drawing");
        for(int sensitivity=0;sensitivity<3;sensitivity++){
            long last=0;for(double amplitude:new double[]{.004,.02,.1,.8}){long next=spread(steady(sine(amplitude),sensitivity));check(next>last,"fixed gain preserves increasing amplitudes at every sensitivity");last=next;}
            bounds(steady(sine(.999),sensitivity));
        }
        check(spread(steady(sine(.01),2))>spread(steady(sine(.01),0)),"sensitivity changes fixed gain");
        SoundWave wave=new SoundWave();int[] quiet=wave.frame();
        wave.push(transientPcm,transientPcm.length,1,1);int[] inputFrame=wave.frame();Arrays.fill(transientPcm,(short)0);check(Arrays.equals(inputFrame,wave.frame()),"only summaries retained after PCM reuse");
        for(int i=0;i<14;i++)wave.push(new short[1200],1200,1,1);check(!Arrays.equals(quiet,wave.frame()),"25 ms transient remains visible through history");
        wave.push(new short[1200],1200,1,1);check(Arrays.equals(quiet,wave.frame()),"no invented slow release or peak hold");
        wave.push(mono,mono.length,1,1);wave.clear();check(Arrays.equals(quiet,wave.frame()),"stop clears all summaries");
        check(Arrays.stream(quiet).filter(v->v>0).count()==15,"quiet is a fine baseline");
        check(Arrays.equals(SoundWave.demo(0),SoundWave.demo(4000)),"matching synthetic loop endpoints");
        for(int t=0;t<10000;t+=25)bounds(SoundWave.demo(t));
        for(int[] frame:scenarios())bounds(frame);
        float[] invalid={Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.MAX_VALUE};bounds(SoundWave.render(invalid,invalid,invalid,2));
        check(ToolPolicy.choose(true,false,false,false,true,true,true,true)==ToolPolicy.HEAR,"hear precedes passive scenes");check(ToolPolicy.choose(true,false,true,false,true,true,true,true)==ToolPolicy.MESSAGE,"message interrupts hear");check(ToolPolicy.choose(false,true,true,true,true,true,true,true)==ToolPolicy.NONE,"master off stops all scenes");
        System.out.println("PASS: 15-bin portrait peak/RMS waveform, signed and transient detail, same-RMS crest distinction, phase/DC handling, fixed-gain range, PCM reuse, exact history expiry, quiet margins and scene priority");
    }
}
