package com.stepxadvance.backscreentoolbox;

public final class RotationTest {
    static void check(boolean condition,String why){if(!condition)throw new AssertionError(why);}
    public static void main(String[] args){
        check(RotationPolicy.degrees(0,9.81f,0)==0,"gravity portrait matches platform convention");
        check(RotationPolicy.degrees(-9.81f,0,0)==90,"gravity right matches platform convention");
        check(RotationPolicy.degrees(9.81f,0,0)==270,"gravity left matches platform convention");
        check(RotationPolicy.degrees(0,-9.81f,0)==180,"upside down is retained by policy");
        check(RotationPolicy.degrees(0,0,9.81f)==-1,"flat sample has no valid angle");
        RotationPolicy p=new RotationPolicy(0);
        p.sample(90,1000);
        check(p.deadline()==1040,"switch is due in 40ms, inside compositor blank window");
        for(int t=1005;t<1040;t+=5)p.sample(89,t);
        check(p.deadline()==1040,"repeated samples do not postpone deadline");
        check(!p.commit(1039),"wait for stable window");
        check(p.commit(1040)&&p.rotation()==1,"enter landscape at the system deadline");

        p.sample(20,1100);
        check(p.deadline()==1140,"return to portrait has same 40ms deadline");
        p.sample(40,1110);
        check(p.deadline()==1140,"same-quadrant edge jitter retains already armed portrait");
        check(p.commit(1140)&&p.rotation()==0,"portrait return does not stall at angle boundary");

        p.sample(70,1200);p.sample(40,1220);
        check(p.pending()==-1&&!p.commit(1240),"crossing back into current quadrant cancels safely");
        p.sample(270,1300);
        check(p.commit(1340)&&p.rotation()==3,"other landscape direction");
        p.sample(180,1400);
        check(p.pending()==-1&&p.rotation()==3,"upside-down retains system direction");

        p.sample(0,1500);p.sample(-1,1510);
        check(!p.commit(1540),"flat sample cancels pending change");
        p.sample(0,1600);
        check(p.pending()==-1,"retain mandatory system flat-exit hold");
        p.sample(0,2099);
        check(p.pending()==-1,"flat-exit hold lasts 500ms");
        p.sample(0,2100);
        check(p.deadline()==2140&&p.commit(2140)&&p.rotation()==0,"one hold then a single 40ms change");

        p.resetSamples();p.sample(54,2200);
        check(p.deadline()==2240&&p.commit(2240)&&p.rotation()==1,"first sample after screen-on follows system without extra angle wait");
        p.forcePortrait();
        check(p.rotation()==0&&p.pending()==-1,"disabling auto rotation resets orientation");

        p=new RotationPolicy(0);
        for(int i=0;i<200;i++){
            int target=i%3==0?1:i%3==1?0:3;
            int deg=target*90;
            long t=3000+i*100;
            p.sample(deg,t);p.sample(deg,t+20);
            check(p.commit(t+40)&&p.rotation()==target,"rapid alternating traces each settle once within 40ms");
            check(!p.commit(t+41),"no duplicate orientation commit");
        }
        System.out.println("PASS: rotation deadlines 40ms, same-quadrant jitter, flat exit, both directions, resume/reset, 200 alternating changes, no duplicate commits");
    }
}
