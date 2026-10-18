package com.stepxadvance.backscreentoolbox;
import java.util.*;

/** Known synthetic sounds, shared by all candidates. */
public final class HearMotionPreview {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static short[] signal(int block){
        short[] pcm=new short[1200];Random random=new Random(7221+block);
        for(int i=0;i<pcm.length;i++){
            double t=(block*1200.0+i)/48000,value=0;
            if(t>=.4&&t<1.3){double amp=.42*Math.pow(Math.sin(Math.PI*(t-.4)/.9),2);value=amp*(.65*Math.sin(2*Math.PI*141*t)+.25*Math.sin(2*Math.PI*282*t)+.1*Math.sin(2*Math.PI*564*t));}
            else if(t>=1.55&&t<2.5){double amp=.36*Math.pow(Math.sin(Math.PI*(t-1.55)/.95),2);value=amp*(.6*Math.sin(2*Math.PI*1500*t)+.25*Math.sin(2*Math.PI*3000*t)+.15*Math.sin(2*Math.PI*5200*t));}
            else if(t>=2.8&&t<2.95)value=.95*Math.exp(-(t-2.8)/.035)*(random.nextDouble()*2-1);
            pcm[i]=(short)(32767*Math.max(-1,Math.min(1,value)));
        }
        return pcm;
    }
    private static short[] tone(double hz,double amplitude){short[] pcm=new short[1200];for(int i=0;i<pcm.length;i++)pcm[i]=(short)(32767*amplitude*Math.sin(2*Math.PI*hz*i/48000));return pcm;}
    private static int peak(float[] levels){int result=0;for(int i=1;i<levels.length;i++)if(levels[i]>levels[result])result=i;return result;}
    private static double energy(float[] levels){double sum=0;for(float level:levels)sum+=level*level;return sum;}
    private static void bounds(int[] frame){for(int i=0;i<391;i++){check(frame[i]>=0&&frame[i]<=255,"brightness");if(i%17==0||i%17==16||i/17==0||i/17==22)check(frame[i]==0,"quiet margin");}}
    public static void main(String[] args){
        HearMotion art=new HearMotion();
        if(args.length>0){System.out.print("[");for(int n=0;n<160;n++){short[] pcm=signal(n);art.push(pcm,pcm.length,1);Arrays.fill(pcm,(short)0);if(n>0)System.out.print(",");System.out.print("[");for(int style=0;style<3;style++){if(style>0)System.out.print(",");System.out.print(Arrays.toString(art.frame(style)));}System.out.print("]");}System.out.print("]");return;}
        float[] low=HearMotion.spectrum(tone(141,.4),1200,1),high=HearMotion.spectrum(tone(4000,.4),1200,1);
        check(peak(low)<3&&peak(high)>10,"known low/high frequencies land in different bands");
        double power=energy(HearMotion.spectrum(tone(1000,.4),1200,1));check(Math.abs(power-.08)<.002,"FFT band power agrees with known sine RMS squared");
        check(energy(HearMotion.spectrum(tone(1000,.8),1200,1))>power*3.9,"amplitude scaling is preserved");
        short[] dc=new short[1200];Arrays.fill(dc,(short)18000);check(energy(HearMotion.spectrum(dc,dc.length,1))<1e-10,"DC removed");
        short[] mono=tone(431,.4),stereo=new short[2400];for(int i=0;i<1200;i++){stereo[2*i]=mono[i];stereo[2*i+1]=(short)-mono[i];}check(Arrays.equals(HearMotion.spectrum(mono,1200,1),HearMotion.spectrum(stereo,2400,2)),"opposite channels do not cancel");
        for(int n=0;n<160;n++){short[] pcm=signal(n);art.push(pcm,pcm.length,1);int[][] snapshot={art.frame(0),art.frame(1),art.frame(2)};Arrays.fill(pcm,(short)0);for(int style=0;style<3;style++){bounds(snapshot[style]);check(Arrays.equals(snapshot[style],art.frame(style)),"no retained PCM");}}
        for(int n=0;n<80;n++)art.push(new short[1200],1200,1);HearMotion quiet=new HearMotion();for(int style=0;style<3;style++)check(Arrays.equals(quiet.frame(style),art.frame(style)),"silence settles without free-running animations");
        art.clear();for(int style=0;style<3;style++)check(Arrays.equals(quiet.frame(style),art.frame(style)),"clear resets history and decorative rings");
        System.out.println("PASS: known-frequency bands, normalized FFT power, fixed strength scaling, phase/DC, all 3 candidates' margins, quiet, reset and no retained PCM");
    }
}
