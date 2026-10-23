package io.github.foundationgames.automobility.automobile.light;

import java.util.Objects;

/** One immutable per-render sample shared by body, lenses, beams, small lights and culling. */
public record Mx5VisualPose(float hood, float leftPopup, float rightPopup,
                            VehicleLightMode leftLight, VehicleLightMode rightLight) {
    public static final Mx5VisualPose CLOSED = new Mx5VisualPose(0, 0, 0, VehicleLightMode.OFF, VehicleLightMode.OFF);

    public Mx5VisualPose {
        hood = finiteProgress(hood);
        leftPopup = finiteProgress(leftPopup);
        rightPopup = finiteProgress(rightPopup);
        Objects.requireNonNull(leftLight);
        Objects.requireNonNull(rightLight);
    }

    public static Mx5VisualPose sample(Mx5HoodAnimation hood, Mx5PopupAnimation left,
            Mx5PopupAnimation right, float partial, boolean hoodOpen, int danceFlags,
            VehicleLightMode effectiveNormal) {
        return new Mx5VisualPose(hood.sample(partial, hoodOpen),
                left.sample(partial, Mx5HeadlightDance.requestedOpen(danceFlags, true, effectiveNormal)),
                right.sample(partial, Mx5HeadlightDance.requestedOpen(danceFlags, false, effectiveNormal)),
                Mx5HeadlightDance.lightMode(danceFlags, true, effectiveNormal),
                Mx5HeadlightDance.lightMode(danceFlags, false, effectiveNormal));
    }

    public boolean ready(boolean left) { return (left ? leftPopup : rightPopup) >= 1 - .000001f; }
    public float progress(boolean left) { return left ? leftPopup : rightPopup; }
    public VehicleLightMode mode(boolean left) { return left ? leftLight : rightLight; }
    public VehicleLightMode lensMode(boolean left) { return ready(left) ? mode(left) : VehicleLightMode.OFF; }
    public VehicleLightMode beamMode(boolean left) {
        var mode = lensMode(left);
        return mode.emitsHeadlightBeam() ? mode : VehicleLightMode.OFF;
    }
    public boolean emitsBeam() { return beamMode(true).emitsHeadlightBeam() || beamMode(false).emitsHeadlightBeam(); }

    private static float finiteProgress(float value) { return Float.isFinite(value) ? Math.clamp(value, 0, 1) : 0; }
}
