package com.stepxadvance.backscreentoolbox;
import java.util.*;
public final class ToolboxTest {
    private static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
    private static void safe(int[] f){check(f.length==391,"391 cells");int lit=0;for(int i=0;i<f.length;i++){check(f[i]>=0&&f[i]<=255,"luminance");if(i<51||i>=340)check(f[i]==0,"central crop safety");if(f[i]>0)lit++;}check(lit>0,"visible content");}
    public static void main(String[] args){
        if(args.length>0){System.out.print("[");boolean compact=args[0].equals("export-compact");for(int n=0;n<60;n++){if(n>0)System.out.print(",");int[][] frames={ToolFrames.timer(new TimerModel(1,0,1500000,1234000-n*100),n*100,compact),ToolFrames.message(n*100,compact),ToolFrames.otp("Ab12C3","工具箱演示",n*100,compact),ToolFrames.music(n*100,compact),ToolFrames.frog(n*100,compact)};System.out.print("[");for(int j=0;j<frames.length;j++){if(j>0)System.out.print(",");System.out.print(Arrays.toString(frames[j]));}System.out.print("]");}System.out.print("]");return;}
        check(MessageCard.code("【银行】验证码：123456，5分钟内有效").equals("123456"),"Chinese numeric OTP");
        check(MessageCard.code("Your verification code is Ab12Cd").equals("Ab12Cd"),"mixed-case OTP remains exact");
        check(MessageCard.code("Your security code: ABCDEF").equals("ABCDEF"),"letters-only code");
        check(MessageCard.code("123456 是你的验证码").equals("123456"),"code before marker");
        check(MessageCard.code("订单123456已发货").isEmpty(),"ordinary numbers are not OTP");
        check(MessageCard.code("验证码已发送到手机尾号1234").isEmpty(),"phone suffix is not an actual code");
        check(MessageCard.code("本次消费1000元，验证码已发送").isEmpty(),"transaction amount is not a code");
        check(MessageCard.code("Your verification code is hidden").isEmpty(),"redacted content must not become fake code");
        check(MessageCard.source("【示例银行】验证码123456","短信").equals("示例银行"),"issuer source");
        check(MessageCard.source("Your verification code is [Ab12C3]","邮件").equals("邮件"),"code brackets cannot impersonate issuer");
        check(MessageCard.code("Your code is 123456").equals("123456"),"common English OTP marker");
        check(MessageCard.source("验证码123456","短信").equals("短信"),"application fallback");
        check(TimerModel.remaining(10000,50000,1,1,2000,90000)==8000,"wall changes do not change running timer");
        check(TimerModel.remaining(10000,50000,1,2,2000,40000)==10000,"reboot uses saved wall deadline");
        check(TimerModel.clock(1).equals("00:01")&&TimerModel.clock(60000).equals("01:00")&&TimerModel.clock(0).equals("00:00"),"clock endpoints");
        check(ToolPolicy.choose(true,false,true,true,true,true,true)==ToolPolicy.MESSAGE,"latest message temporarily wins");
        check(ToolPolicy.choose(true,false,false,true,true,true,true)==ToolPolicy.TIMER,"expiry restores timer");
        check(ToolPolicy.choose(true,false,false,false,false,true,true)==ToolPolicy.MUSIC,"playing media overrides charging");
        check(ToolPolicy.choose(false,true,true,true,true,true,true)==0,"master off releases every scene");
        check(ToolPolicy.choose(true,true,true,true,true,true,true)==ToolPolicy.MESSAGE,"short message is not hidden behind a completion hold");
        check(ToolPolicy.choose(true,true,false,true,true,true,true)==ToolPolicy.DONE,"completion wins when no fresh message exists");
        for(boolean narrow:new boolean[]{false,true}){
            int[] zero=new int[391],letterO=new int[391],one=new int[391],letterI=new int[391],letterL=new int[391];
            MatrixFont.draw(zero,"0",0,3,255,narrow);MatrixFont.draw(letterO,"O",0,3,255,narrow);MatrixFont.draw(one,"1",0,3,255,narrow);MatrixFont.draw(letterI,"I",0,3,255,narrow);MatrixFont.draw(letterL,"l",0,3,255,narrow);
            check(!Arrays.equals(zero,letterO),"zero differs from O");check(!Arrays.equals(one,letterI)&&!Arrays.equals(one,letterL)&&!Arrays.equals(letterI,letterL),"1, I and lowercase l remain distinct");
            for(char upper='A';upper<='Z';upper++){int[] a=new int[391],b=new int[391];MatrixFont.draw(a,""+upper,0,3,255,narrow);MatrixFont.draw(b,""+Character.toLowerCase(upper),0,3,255,narrow);check(!Arrays.equals(a,b),"code letter case: "+upper);}
        }
        int frames=0;for(int t=0;t<10000;t+=50){safe(ToolFrames.message(t));safe(ToolFrames.music(t));safe(ToolFrames.frog(t));safe(ToolFrames.otp("Ab12C3d4",t));for(int preset=0;preset<5;preset++)for(int state=1;state<=3;state++)safe(ToolFrames.timer(new TimerModel(state,preset,1500000,Math.max(0,1500000-t)),t));frames+=19;}
        for(int t=0;t<3800;t+=100)for(int preset=0;preset<5;preset++){
            int[][] full={ToolFrames.timer(new TimerModel(1,preset,1500000,1234000),t,false),ToolFrames.message(t,false),ToolFrames.otp("Ab12C3","来源",t,false),ToolFrames.music(t,false),ToolFrames.frog(t,false)};
            for(int[] f:full){check(f.length==391,"full portrait cells");for(int v:f)check(v>=0&&v<=255,"full portrait brightness");}
        }
        check(ToolFrames.sourceDuration("中国工商银行")>ToolFrames.sourceDuration("短信"),"source gets enough reading time");
        check(!ToolFrames.otpSourcePage("Ab12C3","短信",5000),"code has a long still reading hold");
        check(!Arrays.equals(ToolFrames.message(0),ToolFrames.message(500)),"restrained message animation moves");check(!Arrays.equals(ToolFrames.frog(0),ToolFrames.frog(400)),"laugh has distinct poses");
        TimerModel sample=new TimerModel(1,0,1500000,1234000);
        int[][] portrait={ToolFrames.timer(sample,3800,false),ToolFrames.message(3800,false),ToolFrames.otp("Ab12C3",3800,false),ToolFrames.music(3800,false),ToolFrames.frog(3800,false)};
        int[][] square={ToolFrames.timer(sample,3800,true),ToolFrames.message(3800,true),ToolFrames.otp("Ab12C3",3800,true),ToolFrames.music(3800,true),ToolFrames.frog(3800,true)};
        for(int scene=0;scene<portrait.length;scene++)for(int direction:new int[]{1,3}){
            RotationTransition transition=new RotationTransition(portrait[scene],0,direction,100,false);
            for(int elapsed=0;elapsed<=180;elapsed+=20){
                int[] f=transition.frame(square[scene],100+elapsed);safe(f);
                int sum=Arrays.stream(f).sum();for(int anchor:new int[]{0,1,3})check(Arrays.stream(FrameOrientation.project(f,direction,anchor)).sum()==sum,"new scene survives native-anchor compensation");
            }
            check(Arrays.equals(transition.frame(square[scene],280),square[scene]),"new scene settles on exact target layout");
        }
        System.out.println("PASS: "+frames+" new scene frames, 100 new scene transition frames, source and exact OTP parsing, timer reboot/clock behavior, transient priority and disabled master");
    }
}
