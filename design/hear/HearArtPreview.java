package com.stepxadvance.backscreentoolbox;
import java.util.*;

/** Same deterministic PCM drives all three candidates. No device or microphone access. */
public final class HearArtPreview {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static short[] signal(int block){
        short[] pcm=new short[1200];Random random=new Random(block+2467);
        for(int i=0;i<pcm.length;i++){
            double time=(block*1200.0+i)/48000,amplitude=0,value=0;
            if(time>=.4&&time<1.3){amplitude=.24*Math.pow(Math.sin(Math.PI*(time-.4)/.9),2);value=amplitude*(.72*Math.sin(2*Math.PI*137*time)+.28*Math.sin(2*Math.PI*274*time));}
            else if(time>=1.55&&time<2.5){amplitude=.38*Math.pow(Math.sin(Math.PI*(time-1.55)/.95),2);value=amplitude*(.65*Math.sin(2*Math.PI*253*time)+.23*Math.sin(2*Math.PI*506*time)+.12*Math.sin(2*Math.PI*759*time));}
            else if(time>=2.8&&time<2.95)value=.95*Math.exp(-(time-2.8)/.035)*(random.nextDouble()*2-1);
            pcm[i]=(short)(Math.max(-1,Math.min(1,value))*32767);
        }
        return pcm;
    }
    private static short[] tone(double hz,double amplitude,int phase){short[] pcm=new short[1200];for(int i=0;i<pcm.length;i++)pcm[i]=(short)(32767*amplitude*Math.sin(2*Math.PI*hz*(i+phase)/48000));return pcm;}
    private static long spread(int[] frame,int style){long result=0;for(int i=0;i<391;i++)result+=(long)frame[i]*Math.abs(style==2?i%17-8:i/17-11);return result;}
    private static void bounds(int[] frame){check(frame.length==391,"cells");for(int i=0;i<391;i++){check(frame[i]>=0&&frame[i]<=255,"brightness");if(i%17==0||i%17==16||i/17==0||i/17==22)check(frame[i]==0,"margin");}}
    public static void main(String[] args){
        HearArt art=new HearArt();
        if(args.length>0){System.out.print("[");for(int n=0;n<160;n++){short[] pcm=signal(n);art.push(pcm,pcm.length,1,1);Arrays.fill(pcm,(short)0);if(n>0)System.out.print(",");System.out.print("[");for(int style=0;style<3;style++){if(style>0)System.out.print(",");System.out.print(Arrays.toString(art.frame(style)));}System.out.print("]");}System.out.print("]");return;}
        for(int n=0;n<160;n++){short[] pcm=signal(n);art.push(pcm,pcm.length,1,1);for(int style=0;style<3;style++)bounds(art.frame(style));}
        for(int style=0;style<3;style++){
            art.clear();int[] quiet=art.frame(style);short[] mono=tone(137,.24,0);art.push(mono,mono.length,1,1);int[] measured=art.frame(style);Arrays.fill(mono,(short)0);check(Arrays.equals(measured,art.frame(style)),"no retained PCM dependency");check(spread(measured,style)>spread(quiet,style),"signal changes the drawing");
            for(int n=0;n<21;n++)art.push(new short[1200],1200,1,1);check(Arrays.equals(quiet,art.frame(style)),"silence settles");
            art.push(tone(137,.04,0),1200,1,1);long weak=spread(art.frame(style),style);art.clear();art.push(tone(137,.7,0),1200,1,1);check(spread(art.frame(style),style)>weak,"fixed gain preserves input strength");
        }
        for(int style=0;style<2;style++){art.clear();art.push(tone(137,.4,0),1200,1,1);int[] low=art.frame(style);art.push(tone(397,.4,0),1200,1,1);check(!Arrays.equals(low,art.frame(style)),"signed line retains changes in time-domain shape");}
        for(int sign:new int[]{-1,1})for(int position:new int[]{50,400,1198}){short[] pulse=new short[1200];pulse[position]=(short)(sign*30000);art.clear();art.push(pulse,pulse.length,1,1);for(int style=0;style<3;style++)check(spread(art.frame(style),style)>0,"positive and negative narrow impulses stay visible across the input block");}
        art.clear();short[] dc=new short[1200];Arrays.fill(dc,(short)10000);art.push(dc,dc.length,1,1);check(spread(art.frame(0),0)==0,"DC not mistaken for sound");
        short[] mono=tone(173,.3,0),stereo=new short[2400];for(int i=0;i<1200;i++){stereo[2*i]=mono[i];stereo[2*i+1]=(short)-mono[i];}art.clear();art.push(mono,mono.length,1,1);int[] reference=art.frame(0);art.push(stereo,stereo.length,2,1);check(Arrays.equals(reference,art.frame(0)),"opposite channels do not cancel");
        System.out.println("PASS: three native-grid candidates, same PCM, margins, strength, waveform-shape detail, phase/DC, clearing and no PCM retention dependency");
    }
}
