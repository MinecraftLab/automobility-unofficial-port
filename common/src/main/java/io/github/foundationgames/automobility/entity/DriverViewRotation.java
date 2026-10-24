package io.github.foundationgames.automobility.entity;

/** Continuous passenger view angles; wrapping a camera at +/-180 also jerks vanilla hand bobbing. */
public final class DriverViewRotation {
    private DriverViewRotation() { }
    public static float turn(float current,float delta) { return current+delta; }
    public static float nearest(float current,float target) {
        float delta=(target-current)%360;
        if(delta>=180)delta-=360;if(delta< -180)delta+=360;
        return current+delta;
    }
}
