package io.github.foundationgames.automobility.entity;

/** Client pose interpolation only; mechanical velocity, gears and energy stay with their owners. */
public final class VehicleBodyInterpolation {
    public static final int TICKS = 2;
    private static final double MAX_SOFT_DISTANCE_SQUARED = 64;
    private VehicleBodyInterpolation() {}
    public record Pose(double x, double y, double z, float yaw) {
        public Pose {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw))
                throw new IllegalArgumentException("nonfinite vehicle pose");
        }
    }
    public static boolean mayInterpolate(Pose current, Pose target) {
        double x=target.x-current.x,y=target.y-current.y,z=target.z-current.z;
        return x*x+y*y+z*z <= MAX_SOFT_DISTANCE_SQUARED;
    }
    public static Pose advance(Pose current, Pose target, int remainingTicks) {
        if (remainingTicks<1 || remainingTicks>60) throw new IllegalArgumentException("interpolation ticks");
        double amount=1.0/remainingTicks,yaw=((double)target.yaw-current.yaw)%360;
        if (yaw>=180) yaw-=360;
        if (yaw< -180) yaw+=360;
        return new Pose(current.x+(target.x-current.x)*amount,current.y+(target.y-current.y)*amount,
                current.z+(target.z-current.z)*amount,(float)(current.yaw+yaw*amount));
    }
}
