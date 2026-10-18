package com.stepxadvance.backscreentoolbox;

import java.util.Arrays;

public final class LockedRotationTest {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    // Independent physical-panel oracle: map each lit point around the panel centre.
    private static int[] physical(int[] input,int rotation) {
        if(rotation==0)return input.clone();
        int[] out=new int[391];
        for(int i=0;i<input.length;i++)if(input[i]!=0){
            int x=i%17-8,y=i/17-11;
            double angle=rotation*Math.PI/2;
            int xx=(int)Math.round(x*Math.cos(angle)-y*Math.sin(angle))+8;
            int yy=(int)Math.round(x*Math.sin(angle)+y*Math.cos(angle))+11;
            if(xx>=0&&xx<17&&yy>=3&&yy<20)out[yy*17+xx]=input[i];
        }
        return out;
    }
    public static void main(String[] args) {
        RotationCoordinator c=new RotationCoordinator(0,0,true);
        c.sample(90,0);c.setInteractive(false);
        check(c.commit(40)&&c.rotation()==1&&c.compositorRotation()==0,"screen off cancels native pending turn but keeps scene tracking");
        c.sample(270,100);check(c.commit(140)&&c.rotation()==3&&c.compositorRotation()==0,"locked second turn preserves frozen anchor");
        c.setInteractive(true);c.sample(270,200);
        check(!c.commit(239)&&c.commit(240)&&c.compositorRotation()==3,"wake handover follows a fresh settled native sample");
        c.setInteractive(false);c.sample(0,300);c.commit(340);
        check(c.rotation()==0&&c.compositorRotation()==3&&FrameOrientation.compactPortrait(c.rotation(),c.compositorRotation()),"portrait after landscape lock needs crop-safe layout");
        RotationCoordinator restored=new RotationCoordinator(0,3,false);
        check(restored.compositorRotation()==3,"restart retains separate saved compositor anchor");
        restored.forcePortrait();check(restored.compositorRotation()==0&&restored.rotation()==0,"auto-rotate off clears both transforms");
        int cases=0;
        for(int nativeRotation:new int[]{0,1,3})for(int sceneRotation:new int[]{0,1,3})for(int percent=0;percent<=100;percent++) {
            for(ChargeState state:new ChargeState[]{new ChargeState(percent,100,2,true),new ChargeState(percent,100,5,true),new ChargeState(percent,100,4,true,1),new ChargeState(percent,100,4,true)}) {
                int[] scene=sceneRotation==0&&nativeRotation!=0?ChargeFrames.compactPortrait(state,2000):ChargeFrames.render(state,2000,sceneRotation!=0);
                if(nativeRotation!=0||sceneRotation!=0)for(int i=0;i<391;i++)if(i<51||i>=340)check(scene[i]==0,"all transformed source pixels fit crop");
                int[] sent=FrameOrientation.project(scene,sceneRotation,nativeRotation);
                check(Arrays.equals(physical(sent,nativeRotation),physical(scene,sceneRotation)),"physical picture matches requested reading direction for every native anchor");
                if(sceneRotation==nativeRotation)check(Arrays.equals(sent,scene),"awake identity retains existing frames exactly");
                if(sceneRotation==0&&nativeRotation!=0){int[] digits=ChargeFrames.render(state,2000,true);for(int y=14;y<=18;y++)for(int x=0;x<17;x++)check(scene[y*17+x]==digits[y*17+x],"compact portrait retains exact crisp percentage glyphs");}
                cases++;
            }
        }
        RotationTransition locked=new RotationTransition(ChargeFrames.render(new ChargeState(73,100,2,true),2000),0,1,1000,false);
        check(locked.nextDelay(1000)==20&&!locked.finished(1179)&&locked.finished(1180),"locked transition has no compositor blank to wait for");
        ChargeState full=new ChargeState(100,100,5,true);
        int[] compact=ChargeFrames.compactPortrait(full,2000);
        RotationTransition portrait=new RotationTransition(ChargeFrames.render(full,2000,true),3,0,1000,false,true);
        for(long now=1000;now<1180;now+=20){int[] frame=portrait.frame(compact,now);for(int i=0;i<391;i++)if(i<51||i>=340)check(frame[i]==0,"locked return-to-portrait transition stays inside native crop");}
        check(Arrays.equals(compact,portrait.frame(compact,1180)),"locked compact transition settles on crisp complete percentage");
        check(LockedDisplayPolicy.keepCpu(true,false,true,true,true,false,0),"eligible plugged locked display can update");
        check(LockedDisplayPolicy.rearVisible(1)&&LockedDisplayPolicy.rearVisible(2),"rear up and upright viewing both need updates");
        check(!LockedDisplayPolicy.rearVisible(0)&&!LockedDisplayPolicy.rearVisible(-1),"rear down and unknown posture do not hold CPU");
        check(!LockedDisplayPolicy.keepCpu(false,false,true,true,true,false,0),"unplug or disable releases CPU");
        check(!LockedDisplayPolicy.keepCpu(true,true,true,true,true,false,0),"awake main screen needs no CPU hold");
        check(!LockedDisplayPolicy.keepCpu(true,false,false,true,true,false,0),"rear down releases CPU");
        check(!LockedDisplayPolicy.keepCpu(true,false,true,false,true,false,0),"disabled system rear screen releases CPU");
        check(!LockedDisplayPolicy.keepCpu(true,false,true,true,false,false,0),"static display with rotation off needs no CPU hold");
        check(!LockedDisplayPolicy.keepCpu(true,false,true,true,true,true,0),"power saving releases CPU");
        check(!LockedDisplayPolicy.keepCpu(true,false,true,true,true,false,3),"severe heat releases CPU");
        System.out.println("PASS: "+cases+" locked scene/native anchor projections, full percentage range, crop-safe portrait, screen handover, no false blank, bounded CPU eligibility");
    }
}
