package io.github.foundationgames.automobility.automobile.drivetrain;

import java.util.List;

/** Gear -1 = reverse, 0 = neutral, positive = forward gear. Ratios are dimensionless. */
public record TransmissionProfile(List<Double> forwardRatios, double reverseRatio, double finalDrive) {
    public TransmissionProfile {
        forwardRatios = List.copyOf(forwardRatios);
        if (forwardRatios.isEmpty() || forwardRatios.size() > 16) {
            throw new IllegalArgumentException("transmission needs 1..16 forward ratios");
        }
        for (double ratio : forwardRatios) Units.positive(ratio, "forward ratio");
        Units.positive(reverseRatio, "reverse ratio magnitude");
        Units.positive(finalDrive, "final drive");
        for (double ratio : forwardRatios) Units.positive(ratio * finalDrive, "total forward ratio");
        Units.positive(reverseRatio * finalDrive, "total reverse ratio");
    }

    public double totalRatio(int gear) {
        if (gear == -1) return -reverseRatio * finalDrive;
        if (gear == 0) return 0.0;
        if (gear < 1 || gear > forwardRatios.size()) throw new IllegalArgumentException("unknown gear " + gear);
        return forwardRatios.get(gear - 1) * finalDrive;
    }
}
