package com.stepxadvance.backscreentoolbox;

/** Timing and hysteresis for this firmware's rear display, isolated for trace tests. */
public final class RotationPolicy {
    public static final long STABLE_MS = 40;
    public static final long FLAT_HOLD_MS = 500;
    private int rotation, pending = -1, lastDegrees = -1;
    private boolean flat, hasCommittedSample;
    private long flatEnded = -1, deadline = -1, candidateStarted = -1;

    public RotationPolicy(int initial) { rotation = initial == 1 || initial == 3 ? initial : 0; }
    public static int degrees(float rawX,float rawY,float rawZ) {
        float x=-rawX,y=-rawY,z=-rawZ;
        if((x*x+y*y)*4<z*z)return -1;
        int angle=90-Math.round((float)Math.atan2(-y,x)*57.29577957855f);
        return (angle%360+360)%360;
    }
    public int rotation() { return rotation; }
    public int pending() { return pending; }
    public int lastDegrees() { return lastDegrees; }
    public long deadline() { return deadline; }
    public long candidateStarted() { return candidateStarted; }
    public boolean landscape() { return rotation == 1 || rotation == 3; }

    public void sample(int degrees, long now) {
        lastDegrees = degrees;
        if (degrees < 0) { flat = true; cancel(); return; }
        if (flat) { flat = false; flatEnded = now; }
        if (flatEnded >= 0 && now - flatEnded < FLAT_HOLD_MS) { cancel(); return; }
        int candidate = ((degrees + 45) / 90) % 4;
        if (candidate == 2) candidate = rotation;
        if (candidate == rotation) { cancel(); return; }
        int distance = Math.abs(degrees - candidate * 90);
        distance = Math.min(distance, 360 - distance);
        if (hasCommittedSample && distance > 23) {
            // Keep an armed candidate in the same quadrant, as the compositor does.
            // Cancelling here caused the old return-to-portrait lag.
            if (candidate != pending) cancel();
            return;
        }
        if (candidate != pending) {
            pending = candidate;
            candidateStarted = now;
            deadline = now + STABLE_MS;
        }
    }
    public boolean commit(long now) {
        if (pending < 0 || now < deadline || flat || flatEnded >= 0 && now - flatEnded < FLAT_HOLD_MS) return false;
        rotation = pending;
        hasCommittedSample = true;
        cancel();
        return true;
    }
    public void resetSamples() { cancel(); flat = false; flatEnded = -1; hasCommittedSample = false; }
    public void forcePortrait() { rotation = 0; resetSamples(); }
    private void cancel() { pending = -1; deadline = -1; candidateStarted = -1; }
}
