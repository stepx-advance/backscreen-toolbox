package com.stepxadvance.backscreentoolbox;

/** Original coordinate projection, using the panel centre and exact integer quarter turns. */
public final class FrameOrientation {
    private FrameOrientation(){}
    public static int[] project(int[] scene,int rotation,int compositor) {
        if(scene==null||scene.length!=391)throw new IllegalArgumentException("17x23 required");
        int turns=(rotation-compositor+4)%4;
        if(turns==0)return scene;
        int[] out=new int[391];
        for(int y=3;y<20;y++)for(int x=0;x<17;x++) {
            int dx=x-8,dy=y-11;
            int rx=turns==1?-dy:turns==2?-dx:dy;
            int ry=turns==1?dx:turns==2?-dy:-dx;
            out[(ry+11)*17+rx+8]=scene[y*17+x];
        }
        return out;
    }
    public static boolean compactPortrait(int rotation,int compositor) {return rotation==0&&compositor!=0;}
}
