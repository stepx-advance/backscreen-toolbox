package com.stepxadvance.backscreentoolbox;

/** Native-grid art: 17x23 portrait, with separately composed 17x17 locked/landscape scenes. */
public final class ToolFrames {
    private ToolFrames(){}
    private static final String ALPHA="ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String[] LETTERS={"010101111101101","110101110101110","011100100100011","110101101101110","111100110100111","111100110100100","011100101101011","101101111101101","111010010010111","001001001101010","101101110101101","100100100100111","101111111101101","101111111111101","010101101101010","110101110100100","010101101111011","110101110101101","011100010001110","111010010010010","101101101101111","101101101101010","101101111111101","101101010101101","101101010010010","111001010100111"};
    private static final String[] LOWER={"000011101101011","100110101101110","000011100100011","001011101101011","000010111100011","011010111010010","000011101011110","100110101101101","010000010010010","001000001101010","100101110110101","110010010010111","000110111101101","000110101101101","000010101101010","000110101110100","000011101011001","000011100100100","000011110001110","010111010010011","000101101101011","000101101101010","000101111111010","000101010010101","000101101011110","000111001010111"};
    private static final String[] NUM={"111101101101111","010110010010111","111001111100111","111001111001111","101101111001001","111100111001111","111100111101111","111001010010010","111101111101111","111101111001111"};
    static void dot(int[] f,int x,int y,int v){if(x>=0&&x<17&&y>=0&&y<23)f[y*17+x]=Math.max(0,Math.min(255,v));}
    static void line(int[] f,int x,int y,int xx,int yy,int v){int dx=Math.abs(xx-x),sx=x<xx?1:-1,dy=-Math.abs(yy-y),sy=y<yy?1:-1,e=dx+dy;while(true){dot(f,x,y,v);if(x==xx&&y==yy)break;int e2=e*2;if(e2>=dy){e+=dy;x+=sx;}if(e2<=dx){e+=dx;y+=sy;}}}
    static void text(int[] f,String value,int x,int y,int v){for(int i=0;i<value.length();i++)glyph(f,value.charAt(i),x+i*4,y,v,5);}
    static void glyph(int[] f,char ch,int x,int y,int v,int height){String g=ch>='0'&&ch<='9'?NUM[ch-'0']:ch>='A'&&ch<='Z'?LETTERS[ch-'A']:ch>='a'&&ch<='z'?LOWER[ch-'a']:"000000000000000";for(int row=0;row<height;row++)for(int col=0;col<3;col++)if(g.charAt((row*5/height)*3+col)=='1')dot(f,x+col,y+row,v);}
    public static int[] message(long t){return message(t,true);}
    public static int[] message(long t,boolean compact){
        int[] f=new int[391];int top=7;
        // One solid rounded conversation bubble, cut-out dots and a small unread badge.
        int rise=t<120?2:t<240?1:0;
        int glow=210+(int)(20*Math.sin(Math.max(0,t-450)*Math.PI/2200));
        for(int y=0;y<8;y++)for(int x=0;x<13;x++){
            boolean corner=(y==0||y==7)&&(x<2||x>10)||(y==1||y==6)&&(x==0||x==12);
            if(!corner)dot(f,x+2,top+y+rise,glow);
        }
        dot(f,4,top+8+rise,glow);dot(f,5,top+8+rise,glow);dot(f,4,top+9+rise,glow);
        for(int i=0;i<3;i++){int v=t>=250+i*130?0:glow;dot(f,5+i*3,top+3+rise,v);dot(f,5+i*3,top+4+rise,v);}
        dot(f,13,top-2,255);dot(f,14,top-2,255);dot(f,13,top-1,255);dot(f,14,top-1,255);
        // A dark moat keeps the badge distinct from the filled bubble.
        dot(f,12,top,0);dot(f,13,top,0);dot(f,14,top,0);
        return f;
    }
    public static long sourceDuration(String source){return Math.min(6000,900+Math.max(0,(source==null?0:source.codePointCount(0,source.length()))*11-17)*55L);}
    public static boolean otpSourcePage(String code,long t,boolean compact){return otpSourcePage(code,"来源",t);}
    public static boolean otpSourcePage(String code,String source,long t){return code!=null&&!code.isEmpty()&&Math.max(0,t)%(sourceDuration(source)+8800)<sourceDuration(source);}
    public static int[] otp(String code,long t){return otp(code,t,true);}
    public static int[] otp(String code,long t,boolean compact){return otp(code,"来源",t,compact);}
    public static int[] otp(String code,String source,long t,boolean compact){
        int[] f=new int[391];if(code==null)code="";code=code.substring(0,Math.min(8,code.length()));
        if(otpSourcePage(code,source,t)){
            // A small key identifies the source page; actual source text is drawn separately.
            for(int y=0;y<3;y++)for(int x=0;x<3;x++)if(x==0||x==2||y==0||y==2)dot(f,4+x,4+y,160);
            line(f,7,5,11,5,160);dot(f,10,6,160);return f;
        }
        if(code.length()<=3)MatrixFont.draw(f,code,(17-MatrixFont.width(code,false))/2,compact?10:11,255,false);
        else{
            boolean narrow=code.length()>6;int split=(code.length()+1)/2;
            String first=code.substring(0,split),second=code.substring(split);
            MatrixFont.draw(f,first,(17-MatrixFont.width(first,narrow))/2,compact?4:8,255,narrow);
            MatrixFont.draw(f,second,(17-MatrixFont.width(second,narrow))/2,compact?12:16,255,narrow);
        }
        return f;
    }
    private static final String[] PRESET={"00100/11111/11111/11111/01110","00100/01110/11111/10001/11111","11110/10011/10011/11110/01100","01110/11100/11000/11100/01110","00100/01010/00100/01100/10010"};
    public static int[] timer(TimerModel model,long t){return timer(model,t,true);}
    public static int[] timer(TimerModel model,long t,boolean compact){
        int[] f=new int[391];
        if(model.state==TimerModel.FINISHED){
            int phase=(int)(Math.max(0,t)%2600);int brightness=phase<500?180+(int)(60*Math.sin(phase*Math.PI/500)):200;
            // Large drawn check, then a long still hold rather than blinking.
            int points=Math.min(9,1+(int)(Math.max(0,t)/45));for(int i=0;i<points;i++){if(i<4)dot(f,3+i,10+i,brightness);else dot(f,6+i-3,13-(i-3),brightness);}
            dot(f,4,11,brightness);dot(f,5,12,brightness);dot(f,7,13,brightness);return f;
        }
        long seconds=(model.remaining+999)/1000;boolean lastMinute=seconds<60;
        String big=String.format(java.util.Locale.ROOT,"%02d",lastMinute?seconds:seconds/60);
        MatrixFont.draw(f,big,3,compact?4:8,255,false);
        String small=lastMinute?"SEC":String.format(java.util.Locale.ROOT,"%02d",seconds%60);
        text(f,small,(17-(small.length()*4-1))/2,compact?13:17,model.state==TimerModel.PAUSED?75:145);
        if(!compact){String[] icon=PRESET[model.preset].split("/");for(int y=0;y<5;y++)for(int x=0;x<5;x++)if(icon[y].charAt(x)=='1')dot(f,6+x,1+y,115);}
        int progress=(int)Math.min(13,Math.max(0,13*(model.total-model.remaining)/model.total));
        for(int x=0;x<13;x++)dot(f,2+x,compact?19:22,x<progress?155:28);
        if(model.state==TimerModel.PAUSED){line(f,0,compact?5:9,0,compact?8:12,115);line(f,16,compact?5:9,16,compact?8:12,115);}
        return f;
    }
    public static int[] music(long t){return music(t,true);}
    public static int[] music(long t,boolean compact){
        int[] f=new int[391];int cy=5,r=compact?2:4;
        for(int y=-r;y<=r;y++)for(int x=-r;x<=r;x++){
            int d=x*x+y*y;if(d<=r*r+1){int v=d>(r-1)*(r-1)?130:d>3?45:110;dot(f,8+x,cy+y,v);}
        }
        dot(f,8,cy,255);
        int step=(int)(Math.max(0,t)%2400/200);double a=step*Math.PI/6;
        int x=(int)Math.round(Math.cos(a)*(r-1)),y=(int)Math.round(Math.sin(a)*(r-1));
        dot(f,8+x,cy+y,225);dot(f,8-x,cy-y,150);
        // The turntable arm is fixed; only two highlights move across the record.
        line(f,compact?12:14,compact?3:1,compact?12:14,compact?6:7,165);dot(f,compact?11:13,compact?7:8,220);
        return f;
    }
    // Three-quarter body for portrait; a separate close-up preserves the expression in a square.
    // 0 is a deliberate dark cut-out, not a transparent shading pixel.
    private static final String[] FROG={
        ".....22222.......","...222222222.....","..233322333222...","..2303223032222..",
        "..2333223332222..","...222222222222..","...220000002222..","....20000022222..",
        "....220330222222.",".....22222222222.","....222233332222.","...22233333332222",
        "..222333333333222","..222333333333222","..222003333002222","...2200030002222.",
        "...2220000022222.","....22233332222..",".....222222222...","....2222.22222...",
        "...2222...2222...","..22222...22222..","..1111.....1111.."};
    private static final String[] FROG_CLOSE={
        "...222.....222...","..23332...23332..","..23032...23032..","..2333222233322..",
        "...222222222222..","..22200000022222.","..22200000022222.","...222033022222..",
        "....2223322222...","...222222222222..","..22233333332222.",".222333333333222.",
        ".222003333300222.","..22000333000222.","...220000000222..","....2233333222...",
        ".....22222222...."};
    public static int[] frog(long t){return frog(t,true);}
    public static int[] frog(long t,boolean compact){
        int[] f=new int[391];long time=Math.max(0,t)%2400;
        // Anticipation, two chuckles, then a long rest. Feet and lower outline stay anchored.
        int pose=time<280?0:time<460?1:time<640?2:time<800?0:time<980?1:time<1160?2:0;
        String[] art=compact?FROG_CLOSE:FROG;int head=compact?9:10,anchor=compact?16:19,top=compact?3:0;
        int[] light={0,105,190,255};
        // Body first, head second: translation never leaves a torn seam through the face.
        for(int pass=0;pass<2;pass++)for(int row=pass==0?head:0;row<(pass==0?art.length:head);row++){
            for(int x=0;x<17;x++){
                char c=art[row].charAt(x);if(c=='.')continue;
                int xx=x+(row<head&&pose==1?-1:0);
                int yy=top+row+(pose==2&&row<anchor?1:0);
                int v=light[c-'0'];
                // The mouth opens on the lean, with one bright tongue pixel on the exhale.
                if(pose==1&&row==(compact?7:8)&&x==(compact?8:7))v=0;
                if(pose==2&&row==(compact?7:8)&&x==(compact?8:7))v=255;
                dot(f,xx,yy,v);
            }
        }
        return f;
    }
}
