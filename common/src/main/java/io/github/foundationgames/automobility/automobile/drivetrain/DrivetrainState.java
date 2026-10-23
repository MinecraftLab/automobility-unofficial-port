package io.github.foundationgames.automobility.automobile.drivetrain;

/** Mechanical state only. Positive wheel/speed means forward; ignition is not inferred from RPM. */
public record DrivetrainState(double engineRadiansPerSecond, double wheelRadiansPerSecond,
                              double speedMetresPerSecond, int gear) {
    public DrivetrainState {
        Units.finite(engineRadiansPerSecond, "engine omega");
        Units.finite(wheelRadiansPerSecond, "wheel omega");
        Units.finite(speedMetresPerSecond, "speed");
    }

    /** A ratio selection alone changes no velocity; controller safety checks precede this operation. */
    public DrivetrainState withGear(int selectedGear) {
        return new DrivetrainState(engineRadiansPerSecond, wheelRadiansPerSecond, speedMetresPerSecond, selectedGear);
    }
}
