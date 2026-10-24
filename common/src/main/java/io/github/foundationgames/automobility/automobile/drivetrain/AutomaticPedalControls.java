package io.github.foundationgames.automobility.automobile.drivetrain;

import java.util.Objects;

/** Decode the legacy W/S pedals once, before either combustion or electric propulsion. */
public final class AutomaticPedalControls {
    public record Commands(boolean reversePropulsion, double throttle, double footBrake,
                           double serviceBrake, PowertrainSimulation.RangeRequest range) { }
    private AutomaticPedalControls() { }

    // Keyboard reverse keeps full launch torque, then eases toward an 18 km/h propulsion ceiling.
    // This is a throttle governor: downhill gravity and existing momentum are never clamped or braked.
    private static double reverseDriveFraction(double signedSpeed) {
        double reserve = Math.clamp((5.0 - Math.max(0.0, -signedSpeed)) / 2.0, 0.0, 1.0);
        return reserve * reserve * (3.0 - 2.0 * reserve);
    }

    public static Commands resolve(boolean controls, boolean blocked, boolean automaticRange,
                                   double physicalPedal, double accelerator, boolean reverseReleaseRequired,
                                   int gear, double speed, double wheelOmega,
                                   PowertrainSimulation.RangeRequest requestedRange, boolean handbrake) {
        Units.between(physicalPedal, 0, 1, "physical pedal");
        Units.between(accelerator, 0, 1, "accelerator");
        Units.finite(speed, "vehicle speed"); Units.finite(wheelOmega, "wheel omega");
        Objects.requireNonNull(requestedRange);
        double physical = controls ? physicalPedal : 0;
        var range = controls && !blocked ? requestedRange : PowertrainSimulation.RangeRequest.KEEP;
        boolean reverse = controls && !blocked && automaticRange && physical > 0 && accelerator <= .01
                && !reverseReleaseRequired && speed <= .15 && range == PowertrainSimulation.RangeRequest.KEEP;
        double throttle = controls && !blocked ? accelerator : 0;
        double footBrake = physical > 0 && !reverse ? 1 : 0;
        double service = Math.max(footBrake, handbrake ? 1 : 0);
        if (reverse) {
            // Once R has engaged, keep it without restarting its range-shift controller every tick.
            if (gear >= 0) range = PowertrainSimulation.RangeRequest.REVERSE;
            throttle = physical * reverseDriveFraction(speed);
        } else if (throttle > 0 && gear <= 0 && range == PowertrainSimulation.RangeRequest.KEEP && automaticRange) {
            range = PowertrainSimulation.RangeRequest.DRIVE;
        }
        // Test the actual accelerator, not the reverse throttle just decoded from S.
        if (automaticRange && throttle > 0 && !reverse && gear < 0 && (speed < -.15 || wheelOmega < -.75)) {
            throttle = 0; service = 1;
            range = PowertrainSimulation.RangeRequest.KEEP;
        }
        return new Commands(reverse, throttle, footBrake, service, range);
    }
}
