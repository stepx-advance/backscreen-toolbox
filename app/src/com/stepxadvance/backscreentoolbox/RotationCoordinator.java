package com.stepxadvance.backscreentoolbox;

/** Tracks the requested scene separately from the compositor that freezes while asleep. */
public final class RotationCoordinator {
    private final RotationPolicy scene, compositor;
    private boolean interactive;
    public RotationCoordinator(int sceneRotation,int compositorRotation,boolean awake) {
        scene=new RotationPolicy(sceneRotation);compositor=new RotationPolicy(compositorRotation);interactive=awake;
    }
    public void setInteractive(boolean awake) {
        if(interactive==awake)return;
        interactive=awake;compositor.resetSamples();
    }
    public void sample(int degrees,long now) {scene.sample(degrees,now);if(interactive)compositor.sample(degrees,now);}
    public boolean commit(long now) {boolean changed=scene.commit(now);if(interactive)changed|=compositor.commit(now);return changed;}
    public long deadline() {
        long a=scene.deadline(),b=interactive?compositor.deadline():-1;
        return a<0?b:b<0?a:Math.min(a,b);
    }
    public int rotation(){return scene.rotation();}
    public int compositorRotation(){return compositor.rotation();}
    public int pending(){return scene.pending();}
    public int lastDegrees(){return scene.lastDegrees();}
    public long candidateStarted(){return scene.candidateStarted();}
    public boolean landscape(){return scene.landscape();}
    public void resetSamples(){scene.resetSamples();if(interactive)compositor.resetSamples();}
    public void forcePortrait(){scene.forcePortrait();compositor.forcePortrait();}
}
