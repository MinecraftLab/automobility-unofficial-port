package io.github.foundationgames.automobility.automobile.drivetrain;

import java.util.List;

/** Immutable positive-RPM shaft torque curve; power is always derived from torque. */
public final class TorqueCurve {
    public record Point(double rpm, double torqueNewtonMetres) {
        public Point {
            Units.nonnegative(rpm, "rpm");
            Units.nonnegative(torqueNewtonMetres, "torque");
        }
    }

    public record Peak(double rpm, double value) {
        public Peak {
            Units.nonnegative(rpm, "peak RPM");
            Units.nonnegative(value, "peak value");
        }
    }
    public record Statistics(Peak torqueNewtonMetres, Peak powerKilowatts) { }

    private final List<Point> points;

    public TorqueCurve(List<Point> points) {
        this.points = List.copyOf(points);
        if (this.points.size() < 2 || this.points.size() > 128) {
            throw new IllegalArgumentException("curve needs 2..128 points");
        }
        for (int i = 1; i < this.points.size(); i++) {
            if (this.points.get(i).rpm() <= this.points.get(i - 1).rpm()) {
                throw new IllegalArgumentException("RPM samples must strictly increase");
            }
            Point a = this.points.get(i - 1);
            Point b = this.points.get(i);
            double slope = Units.finite((b.torqueNewtonMetres() - a.torqueNewtonMetres()) / (b.rpm() - a.rpm()), "curve slope");
            Units.finite(a.torqueNewtonMetres() - slope * a.rpm(), "curve intercept");
        }
        for (Point point : this.points) Units.powerKilowatts(point.torqueNewtonMetres(), point.rpm());
    }

    public List<Point> points() { return points; }

    /** No extrapolated propulsion outside the declared operating interval. */
    public double torqueAtRpm(double rpm) {
        Units.nonnegative(rpm, "rpm");
        if (rpm < points.getFirst().rpm() || rpm > points.getLast().rpm()) return 0.0;
        int lo = 0;
        int hi = points.size() - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (rpm < points.get(mid).rpm()) hi = mid;
            else lo = mid;
        }
        Point a = points.get(lo);
        Point b = points.get(hi);
        double fraction = (rpm - a.rpm()) / (b.rpm() - a.rpm());
        return a.torqueNewtonMetres() + fraction * (b.torqueNewtonMetres() - a.torqueNewtonMetres());
    }

    public double powerAtRpmKilowatts(double rpm) {
        return Units.powerKilowatts(torqueAtRpm(rpm), rpm);
    }

    /** Checks the quadratic power maximum inside every linear torque segment. */
    public Statistics statistics() {
        Peak torque = new Peak(points.getFirst().rpm(), points.getFirst().torqueNewtonMetres());
        Peak power = new Peak(points.getFirst().rpm(), powerAtRpmKilowatts(points.getFirst().rpm()));
        for (int i = 0; i < points.size(); i++) {
            Point a = points.get(i);
            if (a.torqueNewtonMetres() > torque.value()) torque = new Peak(a.rpm(), a.torqueNewtonMetres());
            double pointPower = powerAtRpmKilowatts(a.rpm());
            if (pointPower > power.value()) power = new Peak(a.rpm(), pointPower);
            if (i + 1 == points.size()) continue;
            Point b = points.get(i + 1);
            double slope = (b.torqueNewtonMetres() - a.torqueNewtonMetres()) / (b.rpm() - a.rpm());
            if (slope < 0.0) {
                double intercept = a.torqueNewtonMetres() - slope * a.rpm();
                double stationaryRpm = -intercept / (2.0 * slope);
                if (stationaryRpm > a.rpm() && stationaryRpm < b.rpm()) {
                    double candidatePower = powerAtRpmKilowatts(stationaryRpm);
                    if (candidatePower > power.value()) power = new Peak(stationaryRpm, candidatePower);
                }
            }
        }
        return new Statistics(torque, power);
    }
}
