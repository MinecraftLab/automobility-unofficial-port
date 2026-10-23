package io.github.foundationgames.automobility.automobile.drivetrain;

/** SI conversion and validation shared by the dependency-free drivetrain model. */
public final class Units {
    private Units() { }

    public static double rpmToRadiansPerSecond(double rpm) {
        return finite(finite(rpm, "rpm") * (Math.PI / 30.0), "converted omega");
    }

    public static double radiansPerSecondToRpm(double omega) {
        return finite(finite(omega, "omega") * (30.0 / Math.PI), "converted RPM");
    }

    public static double powerKilowatts(double torqueNewtonMetres, double rpm) {
        return finite(finite(torqueNewtonMetres, "torque") * rpmToRadiansPerSecond(rpm) / 1000.0, "power kW");
    }

    static double finite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
        return value;
    }

    static double positive(double value, String name) {
        if (finite(value, name) <= 0.0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    static double nonnegative(double value, String name) {
        if (finite(value, name) < 0.0) throw new IllegalArgumentException(name + " must be nonnegative");
        return value;
    }

    static double between(double value, double min, double max, String name) {
        if (finite(value, name) < min || value > max) throw new IllegalArgumentException(name + " out of range");
        return value;
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
