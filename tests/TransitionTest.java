package com.stepxadvance.backscreentoolbox;

import java.util.Arrays;

public final class TransitionTest {
    static void check(boolean condition,String why){if(!condition)throw new AssertionError(why);}
    public static void main(String[] args){
        if(args.length>0&&args[0].equals("export")){export();return;}
        int[] marker=new int[391];marker[8*17+8]=255;
        RotationTransition intoRight=new RotationTransition(marker,0,1,1000);
        int[] first=intoRight.frame(new int[391],1000);
        check(first[11*17+5]==255,"counter-rotation preserves old physical icon pose at right-turn start");
        RotationTransition back=new RotationTransition(marker,1,0,1000);
        check(back.frame(new int[391],1000)[11*17+11]==255,"leftward return counter-rotation");
        check(!intoRight.finished(1279)&&intoRight.finished(1280),"effect completes after 100ms compositor blank + 180ms motion");
        check(intoRight.nextDelay(1000)==100&&intoRight.nextDelay(1100)==20,"one hold, then 20ms frames");
        check(marker[8*17+8]==255,"source snapshot is not modified");
        int[] digitsOnly=new int[391];digitsOnly[18*17+8]=255;
        RotationTransition digitsTurn=new RotationTransition(digitsOnly,0,1,1000);
        int[] digitsFirst=digitsTurn.frame(digitsOnly,1000);
        check(digitsFirst[11*17+15]==255,"percentage dots join the same quarter-turn as the icon");
        check(digitsFirst[18*17+8]==0,"percentage is no longer pinned to the destination baseline");

        int cases=0;
        for(int oldRotation:new int[]{0,1,3})for(int newRotation:new int[]{0,1,3}){
            if(oldRotation==newRotation)continue;
            boolean oldLand=oldRotation!=0,newLand=newRotation!=0;
            for(ChargeState s:new ChargeState[]{new ChargeState(73,100,2,true),new ChargeState(100,100,5,true),new ChargeState(80,100,4,true,1),new ChargeState(80,100,4,true)}){
                int[] from=ChargeFrames.render(s,2000,oldLand);
                RotationTransition effect=new RotationTransition(from,oldRotation,newRotation,1000);
                int[] target=ChargeFrames.render(s,2000,newLand);
                for(int t=0;t<=280;t+=20){
                    int[] f=effect.frame(target,1000+t);cases++;
                    check(f.length==391,"exact hardware cell count");
                    for(int i=0;i<391;i++){
                        check(f[i]>=0&&f[i]<=255,"interpolated luminance is valid");
                        if(newLand&&(i<51||i>=340))check(f[i]==0,"landscape transition respects compositor crop");
                    }
                }
                check(Arrays.equals(effect.frame(target,1280),target),"final frame equals ordinary rendering exactly");
                int[] mid=effect.frame(target,1180);
                RotationTransition interrupted=new RotationTransition(mid,newRotation,oldRotation,1180);
                check(Arrays.equals(interrupted.frame(from,1460),from),"rapid retarget returns to newest orientation without queuing old effect");
            }
        }
        // The target is read again while animating; the final value is never a frozen snapshot.
        ChargeState a=new ChargeState(73,100,2,true),b=new ChargeState(74,100,2,true);
        RotationTransition effect=new RotationTransition(ChargeFrames.render(a,2000),0,1,0);
        int[] target=ChargeFrames.render(b,2000,true);
        check(Arrays.equals(effect.frame(target,280),target),"new target percentage is retained at completion");
        System.out.println("PASS: "+cases+" whole-scene transition frames, digits rotate with icon, both quarter-turns/half-turns, adaptive fit, crop bounds, exact endpoints, rapid retarget, 280ms total including compositor blank");
    }
    static void export(){
        ChargeState s=new ChargeState(73,100,2,true);
        System.out.print("{");
        for(int mode=0;mode<2;mode++){
            if(mode>0)System.out.print(",");
            boolean toLand=mode==0;
            System.out.print("\""+(toLand?"portrait_to_landscape":"landscape_to_portrait")+"\":[");
            RotationTransition effect=new RotationTransition(ChargeFrames.render(s,2000,!toLand),toLand?0:1,toLand?1:0,0);
            for(int t=0;t<1000;t+=20){
                if(t>0)System.out.print(",");
                int[] frame=t<100?new int[391]:effect.frame(ChargeFrames.render(s,2000+t,toLand),t);
                System.out.print(Arrays.toString(frame));
            }
            System.out.print("]");
        }
        System.out.println("}");
    }
}
