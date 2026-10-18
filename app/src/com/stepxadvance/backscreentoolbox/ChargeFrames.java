package com.stepxadvance.backscreentoolbox;

/** Original pixel art. Row-major 17 x 23, luminance 0..255; no bitmap resampling. */
public final class ChargeFrames {
    public static final int WIDTH = 17, HEIGHT = 23;
    private static final String[] DIGITS = {
        "111101101101111", "010110010010111", "111001111100111", "111001111001111",
        "101101111001001", "111100111001111", "111100111101111", "111001010010010",
        "111101111101111", "111101111001111"
    };
    private static final int[][] CHECK = {{5,8},{6,9},{7,10},{8,9},{9,8},{10,7},{11,6}};
    public static int[] render(ChargeState state, long elapsed, boolean landscape) {
        return landscape ? renderLandscape(state, elapsed) : render(state, elapsed);
    }
    /** Crisp upright scene for a compositor locked to its 17x17 landscape crop. */
    public static int[] compactPortrait(ChargeState s,long t) {
        int[] f=new int[391];
        line(f,7,3,9,3,150);line(f,5,5,11,5,150);line(f,5,12,11,12,150);
        line(f,4,6,4,11,150);line(f,12,6,12,11,150);
        int fill=s.percent<0?0:(s.percent*5+99)/100;
        boolean flow=s.mode==ChargeState.Mode.CHARGING||s.mode==ChargeState.Mode.BYPASS_WAITING&&s.charging;
        for(int y=6;y<=10;y++)for(int x=6;x<=10;x++) {
            int v=y>=11-fill?65:0;
            if(flow&&v>0&&y==10-(int)(Math.max(0,t)%2400/300))v=150;
            dot(f,x,y,v);
        }
        if(s.mode==ChargeState.Mode.FULL) {
            line(f,6,8,7,9,255);line(f,7,9,10,6,255);
        } else if(s.mode==ChargeState.Mode.BYPASS) {
            line(f,6,8,10,8,255);line(f,6,9,10,9,0);line(f,6,10,10,10,255);
            int y=11-(int)(Math.max(0,t)%2400/300);if(y>=5){dot(f,2,y,255);dot(f,14,y,255);}
        } else if(s.mode==ChargeState.Mode.PAUSED||s.mode==ChargeState.Mode.BYPASS_WAITING&&!s.charging) {
            line(f,6,7,6,10,255);line(f,10,7,10,10,255);
        } else if(flow) {
            glyph(f,"011010111010110",3,7,6,255,false);
            if(s.mode==ChargeState.Mode.BYPASS_WAITING){dot(f,2,6,65);dot(f,2,9,65);}
        } else glyph(f,"111001010000010",3,7,6,255,false);
        String number=s.percent<0?"--":Integer.toString(s.percent);
        int left=(WIDTH-(number.length()*4-1+4))/2;
        for(int i=0;i<number.length();i++) {
            char ch=number.charAt(i);
            if(ch=='-')line(f,left+i*4,16,left+i*4+2,16,255);
            else glyph(f,DIGITS[ch-'0'],3,left+i*4,14,255,false);
        }
        glyph(f,"101001010100101",3,left+number.length()*4,14,150,false);
        return f;
    }
    private static int[] renderLandscape(ChargeState s, long t) {
        int[] f=new int[391];
        // A wide, chamfered battery and one centred baseline, within y=3..19.
        int fill=s.percent<0?0:(s.percent*10+99)/100;
        boolean flowing=s.mode==ChargeState.Mode.CHARGING||s.mode==ChargeState.Mode.BYPASS_WAITING&&s.charging;
        for(int y=6;y<=10;y++)for(int x=3;x<=12;x++) {
            int v=x<3+fill?65:0;
            if(flowing&&v>0) {
                int head=3+(int)((Math.max(0,t)%2400)/160);
                if(x==head)v=150;else if(x==head-1)v=100;
            }
            dot(f,x,y,s.mode==ChargeState.Mode.FULL?v/2:v);
        }
        if(s.mode==ChargeState.Mode.FULL){
            int[][] check={{5,8},{6,9},{7,8},{8,7},{9,6},{10,6}};
            int count=Math.min(check.length,1+(int)Math.max(0,t)/100);
            for(int i=0;i<count;i++)dot(f,check[i][0],check[i][1],255);
            if(t>=700&&t<1500){dot(f,3,3,90);dot(f,12,3,150);}
        }else if(s.mode==ChargeState.Mode.BYPASS){
            // A quiet equal sign marks held charge; the moving energy stays outside.
            line(f,6,7,10,7,255);line(f,6,8,10,8,0);line(f,6,9,10,9,255);
            line(f,2,3,13,3,45);line(f,2,12,13,12,45);
            int head=2+(int)((Math.max(0,t)%3000)/200);
            if(head<=13){dot(f,head,3,255);dot(f,head,12,255);}
            if(head>2&&head<=14){dot(f,head-1,3,100);dot(f,head-1,12,100);}
        }else if(s.mode==ChargeState.Mode.PAUSED||s.mode==ChargeState.Mode.BYPASS_WAITING&&!s.charging){
            line(f,6,7,6,9,255);line(f,10,7,10,9,255);
        }else if(flowing){
            glyph(f,"011010111010110",3,7,6,255,true);
            if(s.mode==ChargeState.Mode.BYPASS_WAITING){dot(f,3,3,65);dot(f,7,3,65);dot(f,11,3,65);}
        }else if(s.mode==ChargeState.Mode.UNKNOWN){
            glyph(f,"111001010000010",3,7,6,255,true);
        }
        // Draw the shell after symbol knockout so a tall bolt cannot cut the outline.
        line(f,2,5,13,5,150); line(f,2,11,13,11,150);
        line(f,1,6,1,10,150); line(f,14,6,14,10,150);
        line(f,15,7,15,9,150);
        String number=s.percent<0?"--":Integer.toString(s.percent);
        int width=number.length()*4-1+4;
        int left=(WIDTH-width)/2;
        for(int i=0;i<number.length();i++){
            char ch=number.charAt(i);
            if(ch=='-')line(f,left+i*4,16,left+i*4+2,16,255);
            else glyph(f,DIGITS[ch-'0'],3,left+i*4,14,255,false);
        }
        glyph(f,"101001010100101",3,left+number.length()*4,14,150,false);
        return f;
    }
    public static int[] render(ChargeState state, long elapsed) {
        int[] f = new int[WIDTH * HEIGHT];
        // A one-dot chamfer makes a clean silhouette, with a quiet grey shell.
        line(f, 6, 1, 10, 1, 150);
        line(f, 4, 3, 12, 3, 150); line(f, 4, 14, 12, 14, 150);
        line(f, 3, 4, 3, 13, 150); line(f, 13, 4, 13, 13, 150);
        int rows = state.percent < 0 ? 0 : (state.percent * 8 + 99) / 100;
        for (int y = 5; y <= 12; y++) for (int x = 5; x <= 11; x++) {
            int v = y >= 13 - rows ? 65 : 0;
            if ((state.mode == ChargeState.Mode.CHARGING || state.mode == ChargeState.Mode.BYPASS_WAITING&&state.charging) && v > 0) {
                // An upward travelling highlight with a dim trailing row; pauses at loop end.
                int phase = (int)((Math.max(0, elapsed) % 2400) / 180);
                int head = 12 - phase;
                if (y == head) v = 150;
                else if (y == head + 1) v = 100;
            }
            dot(f, x, y, state.mode == ChargeState.Mode.FULL ? v / 2 : v);
        }
        if (state.mode == ChargeState.Mode.FULL) {
            // Brief drawn check + tiny asymmetric sparkles, then a still completion screen.
            int n = elapsed >= 1000 ? CHECK.length : Math.min(CHECK.length, 1 + (int)Math.max(0, elapsed) / 100);
            for (int i = 0; i < n; i++) dot(f, CHECK[i][0], CHECK[i][1], 255);
            if (elapsed >= 700 && elapsed < 1500) {
                int b = elapsed < 1100 ? 150 : 65;
                dot(f, 0, 4, b); dot(f, 1, 3, b); dot(f, 1, 5, b); dot(f, 2, 4, b);
                dot(f, 15, 11, b); dot(f, 16, 10, b); dot(f, 16, 12, b);
            }
        } else if (state.mode == ChargeState.Mode.BYPASS) {
            // The route visibly travels OUTSIDE the battery. A small '=' inside means held charge.
            for (int y=4;y<=13;y++) { dot(f,1,y,45); dot(f,15,y,45); }
            line(f,2,3,2,3,90); line(f,14,3,14,3,90);
            int head = 13 - (int)((Math.max(0, elapsed) % 3000) / 250);
            if (head>=4) { dot(f,1,head,255); dot(f,15,head,255); }
            if(head+1<=13 && head>=3) { dot(f,1,head+1,100); dot(f,15,head+1,100); }
            line(f,6,7,10,7,0); line(f,6,8,10,8,255);
            line(f,6,9,10,9,0); line(f,6,10,10,10,255); line(f,6,11,10,11,0);
        } else if (state.mode == ChargeState.Mode.CHARGING || state.mode == ChargeState.Mode.BYPASS_WAITING&&state.charging) {
            // Stable, high-contrast bolt. Only energy behind it moves; digits never blink.
            glyph(f, "0011001100111110011001100", 5, 6, 6, 255, true);
            if (state.mode == ChargeState.Mode.BYPASS_WAITING) {
                // Pending route markers are still, never an active bypass stream.
                dot(f,1,6,65); dot(f,1,8,65); dot(f,1,10,65);
                dot(f,15,6,65); dot(f,15,8,65); dot(f,15,10,65);
            }
        } else if (state.mode == ChargeState.Mode.PAUSED || state.mode == ChargeState.Mode.BYPASS_WAITING) {
            line(f, 6, 7, 6, 10, 255); line(f, 10, 7, 10, 10, 255);
        } else if (state.mode == ChargeState.Mode.UNPLUGGED) {
            line(f, 6, 7, 6, 8, 220); line(f, 10, 7, 10, 8, 220);
            line(f, 7, 11, 9, 11, 150);
        } else {
            glyph(f, "111001010000010", 3, 7, 6, 255, true);
        }
        String number = state.percent < 0 ? "--" : Integer.toString(state.percent);
        int width = number.length() * 4 - 1 + 4;
        int left = (WIDTH - width) / 2;
        for (int i = 0; i < number.length(); i++) {
            char c = number.charAt(i);
            if (c == '-') line(f, left + i * 4, 19, left + i * 4 + 2, 19, 255);
            else glyph(f, DIGITS[c - '0'], 3, left + i * 4, 17, 255, false);
        }
        glyph(f, "101001010100101", 3, left + number.length() * 4, 17, 150, false);
        return f;
    }
    private static void glyph(int[] f, String bits, int w, int x, int y, int value, boolean knockOut) {
        if (knockOut) {
            for (int i = 0; i < bits.length(); i++) if (bits.charAt(i) == '1') {
                int px = x + i % w, py = y + i / w;
                dot(f, px-1, py, 0); dot(f, px+1, py, 0); dot(f, px, py-1, 0); dot(f, px, py+1, 0);
            }
        }
        for (int i = 0; i < bits.length(); i++) if (bits.charAt(i) == '1') dot(f, x + i % w, y + i / w, value);
    }
    private static void dot(int[] f, int x, int y, int v) {
        if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT) f[y * WIDTH + x] = v;
    }
    private static void line(int[] f, int x0, int y0, int x1, int y1, int v) {
        int dx = Integer.signum(x1-x0), dy = Integer.signum(y1-y0);
        for (;;) { dot(f, x0, y0, v); if (x0 == x1 && y0 == y1) break; x0 += dx; y0 += dy; }
    }
}
