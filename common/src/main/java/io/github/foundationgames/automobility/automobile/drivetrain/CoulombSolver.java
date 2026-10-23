package io.github.foundationgames.automobility.automobile.drivetrain;

/** Small event solver for three velocities and up to five bounded dry-friction constraints. */
final class CoulombSolver {
    static final int MAX_SEGMENTS = 64;
    private static final double RELATIVE_TOLERANCE = 1.0e-11;
    private static final double RANK_TOLERANCE = 1.0e-12;

    record Solution(double[] velocity, double[] work, double[] impulse, double[] dissipation, int segments) { }
    /** Optional event-boundary result; squared-force integrals support actual RMS-current accounting. */
    record StoppedSolution(Solution solution, double elapsedSeconds, boolean axisZeroEvent,
                           double[] forceSquareTimeIntegrals, double[] positiveExternalWork) { }
    private record Forces(double[] friction, double[] acceleration) { }

    private CoulombSolver() { }

    static Solution integrate(double[] initial, double[] inverseMass, double[] external,
                              double[][] rows, double[] capacities, double seconds) {
        return integrateInternal(initial, inverseMass, external, rows, capacities, seconds, -1, false).solution();
    }

    /** Measure positive external work separately from signed net work, without adding an event or changing motion. */
    static StoppedSolution integrateMeasured(double[] initial, double[] inverseMass, double[] external,
                                              double[][] rows, double[] capacities, double seconds) {
        Units.positive(seconds, "integration seconds");
        return integrateInternal(initial, inverseMass, external, rows, capacities, seconds, -1, true);
    }

    /** Stop after the first computed positive-time zero crossing of one velocity axis; never project it to zero. */
    static StoppedSolution integrateUntilAxisZero(double[] initial, double[] inverseMass, double[] external,
                                                 double[][] rows, double[] capacities, double seconds, int stopAxis) {
        Units.positive(seconds, "integration seconds");
        if (stopAxis < 0 || stopAxis >= 3) throw new IllegalArgumentException("stop axis must be 0..2");
        if (initial[stopAxis] == 0.0) {
            return new StoppedSolution(new Solution(initial.clone(), new double[3], new double[capacities.length],
                    new double[capacities.length], 0), 0.0, true, new double[capacities.length], new double[3]);
        }
        return integrateInternal(initial, inverseMass, external, rows, capacities, seconds, stopAxis, true);
    }

    private static StoppedSolution integrateInternal(double[] initial, double[] inverseMass, double[] external,
                                                     double[][] rows, double[] capacities, double seconds, int stopAxis, boolean measure) {
        double[] velocity = initial.clone();
        double[] work = new double[3];
        double[] impulse = new double[capacities.length];
        double[] dissipation = new double[capacities.length];
        double[] forceSquares = measure ? new double[capacities.length] : new double[0];
        double[] positiveWork = measure ? new double[3] : new double[0];
        double remaining = seconds;
        double elapsed = 0.0;
        int segments = 0;
        while (remaining > 0.0) {
            if (stopAxis >= 0 && velocity[stopAxis] == 0.0) {
                return new StoppedSolution(new Solution(velocity, work, impulse, dissipation, segments),
                        elapsed, true, forceSquares, positiveWork);
            }
            if (++segments > MAX_SEGMENTS) throw new IllegalArgumentException("friction event budget exceeded");
            Forces forces = chooseForces(velocity, inverseMass, external, rows, capacities, stopAxis);
            double[] acceleration = forces.acceleration();
            double duration = remaining;
            for (int i = 0; i < rows.length; i++) {
                if (capacities[i] == 0.0) continue;
                double slip = dot(rows[i], velocity);
                double derivative = dot(rows[i], acceleration);
                if (Math.abs(slip) <= tolerance(rows[i], velocity) || slip * derivative >= 0.0) continue;
                double crossing = -slip / derivative;
                if (crossing > 0.0 && crossing < duration) duration = crossing;
            }
            boolean stopAfterSegment = false;
            if (stopAxis >= 0 && ((velocity[stopAxis] > 0.0 && acceleration[stopAxis] < 0.0)
                    || (velocity[stopAxis] < 0.0 && acceleration[stopAxis] > 0.0))) {
                double crossing = -velocity[stopAxis] / acceleration[stopAxis];
                if (crossing == 0.0) throw new IllegalArgumentException("axis zero event underflow");
                if (crossing > 0.0 && crossing <= duration) { duration = crossing; stopAfterSegment = true; }
            }
            if (!(duration > 0.0) || remaining - duration == remaining) {
                throw new IllegalArgumentException("friction event cannot advance time");
            }

            double[] displacement = new double[3];
            for (int axis = 0; axis < 3; axis++) {
                displacement[axis] = velocity[axis] * duration + 0.5 * acceleration[axis] * duration * duration;
                work[axis] = Units.finite(work[axis] + external[axis] * displacement[axis], "external work");
                if (measure) positiveWork[axis] = Units.finite(positiveWork[axis] + positiveWork(external[axis],
                        velocity[axis], acceleration[axis], duration, displacement[axis]), "positive external work");
            }
            for (int i = 0; i < rows.length; i++) {
                double force = forces.friction()[i];
                double removed = force * dot(rows[i], displacement);
                // Only round-off at an event/zero constraint may be negative; never hide finite energy creation.
                double scale = Math.abs(force) * tolerance(rows[i], velocity) * duration;
                if (removed < -4.0 * scale - 1.0e-12 || !Double.isFinite(removed)) {
                    throw new IllegalArgumentException("friction performed positive work");
                }
                dissipation[i] = Units.finite(dissipation[i] + Math.max(0.0, removed), "dissipation");
                impulse[i] = Units.finite(impulse[i] + force * duration, "impulse");
                if (measure) forceSquares[i] = Units.finite(forceSquares[i] + force * force * duration, "squared force integral");
            }
            for (int axis = 0; axis < 3; axis++) {
                velocity[axis] = Units.finite(velocity[axis] + acceleration[axis] * duration, "velocity");
            }
            remaining -= duration;
            if (measure) elapsed = Units.finite(elapsed + duration, "measured elapsed seconds");
            if (stopAfterSegment) return new StoppedSolution(new Solution(velocity, work, impulse, dissipation, segments),
                    elapsed, true, forceSquares, positiveWork);
        }
        return new StoppedSolution(new Solution(velocity, work, impulse, dissipation, segments), seconds, false, forceSquares, positiveWork);
    }

    /** Integral of max(0, F*v(t)) for one constant-force/acceleration segment, including an internal sign change. */
    private static double positiveWork(double force, double velocity, double acceleration, double seconds, double displacement) {
        if (force == 0.0) return 0.0;
        double finalVelocity = Units.finite(velocity + acceleration * seconds, "measured velocity");
        if ((velocity < 0.0 && finalVelocity > 0.0) || (velocity > 0.0 && finalVelocity < 0.0)) {
            double crossing = -velocity / acceleration;
            if (crossing > 0.0 && crossing < seconds) {
                double firstWork = force * (velocity * crossing + 0.5 * acceleration * crossing * crossing);
                double rest = seconds - crossing;
                double secondWork = force * (0.5 * acceleration * rest * rest);
                return Math.max(0.0, Units.finite(firstWork, "first signed work"))
                        + Math.max(0.0, Units.finite(secondWork, "second signed work"));
            }
        }
        return Math.max(0.0, Units.finite(force * displacement, "signed work"));
    }

    private static Forces chooseForces(double[] velocity, double[] inverseMass, double[] external,
                                        double[][] rows, double[] capacities, int exactAxis) {
        int[] zeroRows = new int[capacities.length];
        int zeroCount = 0;
        double[] sliding = new double[capacities.length];
        for (int i = 0; i < rows.length; i++) {
            if (capacities[i] == 0.0) continue;
            // A nonzero stop-axis rotation must slide, however small; otherwise regen could become a static hold.
            // This branch is absent in the original default integration mode.
            if (exactAxis >= 0 && velocity[exactAxis] != 0.0 && parallelToAxis(rows[i], exactAxis)) {
                double direction = Math.signum(rows[i][exactAxis]) * Math.signum(velocity[exactAxis]);
                sliding[i] = Math.copySign(capacities[i], direction);
                continue;
            }
            double slip = dot(rows[i], velocity);
            if (Math.abs(slip) <= tolerance(rows[i], velocity)) zeroRows[zeroCount++] = i;
            else sliding[i] = Math.copySign(capacities[i], slip);
        }
        int combinations = 1;
        for (int i = 0; i < zeroCount; i++) combinations *= 3;

        // At most 3^5 candidates. A singular free solution is accepted only after full residual/cap checks.
        // Bound assignments also cover vertices when another singular particular solution is infeasible.
        for (int candidate = 0; candidate < combinations; candidate++) {
            double[] friction = sliding.clone();
            int[] states = new int[zeroCount];
            int[] freeRows = new int[capacities.length];
            int freeCount = 0;
            int digits = candidate;
            for (int z = 0; z < zeroCount; z++) {
                int state = digits % 3;
                digits /= 3;
                states[z] = state;
                int row = zeroRows[z];
                if (state == 0) freeRows[freeCount++] = row;
                else friction[row] = state == 1 ? capacities[row] : -capacities[row];
            }
            double[] baseAcceleration = acceleration(inverseMass, external, rows, friction);
            if (freeCount > 0) {
                double[][] matrix = new double[freeCount][freeCount];
                double[] rhs = new double[freeCount];
                double[] normalization = new double[freeCount];
                for (int f = 0; f < freeCount; f++) {
                    double diagonal = massDot(rows[freeRows[f]], rows[freeRows[f]], inverseMass);
                    normalization[f] = Math.sqrt(Units.positive(diagonal, "constraint inverse inertia"));
                    rhs[f] = dot(rows[freeRows[f]], baseAcceleration) / normalization[f];
                }
                for (int f = 0; f < freeCount; f++) {
                    for (int g = 0; g < freeCount; g++) {
                        matrix[f][g] = massDot(rows[freeRows[f]], rows[freeRows[g]], inverseMass)
                                / normalization[f] / normalization[g];
                    }
                }
                double[] solution = solve(matrix, rhs);
                if (solution == null) continue;
                boolean withinCaps = true;
                for (int f = 0; f < freeCount; f++) {
                    int row = freeRows[f];
                    double value = solution[f] / normalization[f];
                    double capTolerance = RELATIVE_TOLERANCE * Math.max(1.0, capacities[row]);
                    if (!Double.isFinite(value) || Math.abs(value) > capacities[row] + capTolerance) {
                        withinCaps = false;
                        break;
                    }
                    friction[row] = Units.clamp(value, -capacities[row], capacities[row]);
                }
                if (!withinCaps) continue;
            }
            double[] resultAcceleration = acceleration(inverseMass, external, rows, friction);
            boolean valid = true;
            for (int z = 0; z < zeroCount; z++) {
                int row = zeroRows[z];
                double relativeAcceleration = dot(rows[row], resultAcceleration);
                double accelerationTolerance = forceTolerance(rows[row], inverseMass, external, rows, friction);
                if ((states[z] == 0 && Math.abs(relativeAcceleration) > accelerationTolerance)
                        || (states[z] == 1 && relativeAcceleration < -accelerationTolerance)
                        || (states[z] == 2 && relativeAcceleration > accelerationTolerance)) {
                    valid = false;
                    break;
                }
            }
            if (valid) return new Forces(friction, resultAcceleration);
        }
        throw new IllegalArgumentException("no consistent bounded friction force solution");
    }

    private static boolean parallelToAxis(double[] row, int axis) {
        if (row[axis] == 0.0) return false;
        for (int other = 0; other < 3; other++) if (other != axis && row[other] != 0.0) return false;
        return true;
    }

    private static double[] acceleration(double[] inverseMass, double[] external, double[][] rows, double[] friction) {
        double[] result = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            double force = external[axis];
            for (int row = 0; row < rows.length; row++) force -= rows[row][axis] * friction[row];
            result[axis] = Units.finite(force * inverseMass[axis], "acceleration");
        }
        return result;
    }

    /** Rank-revealing Gauss-Jordan; a consistent singular system uses zero nonpivot variables. */
    private static double[] solve(double[][] matrix, double[] rhs) {
        int size = rhs.length;
        double[][] augmented = new double[size][size + 1];
        for (int row = 0; row < size; row++) {
            System.arraycopy(matrix[row], 0, augmented[row], 0, size);
            augmented[row][size] = rhs[row];
        }
        int[] pivots = new int[size];
        int rank = 0;
        for (int col = 0; col < size; col++) {
            int pivot = rank;
            for (int row = rank; row < size; row++) {
                if (Math.abs(augmented[row][col]) > Math.abs(augmented[pivot][col])) pivot = row;
            }
            if (Math.abs(augmented[pivot][col]) <= RANK_TOLERANCE) continue;
            double[] swap = augmented[rank]; augmented[rank] = augmented[pivot]; augmented[pivot] = swap;
            double divisor = augmented[rank][col];
            for (int k = col; k <= size; k++) augmented[rank][k] /= divisor;
            for (int row = 0; row < size; row++) {
                if (row == rank) continue;
                double factor = augmented[row][col];
                for (int k = col; k <= size; k++) augmented[row][k] -= factor * augmented[rank][k];
            }
            pivots[rank++] = col;
            if (rank == size) break;
        }
        double rhsScale = 1.0;
        for (double value : rhs) rhsScale = Math.max(rhsScale, Math.abs(value));
        for (int row = rank; row < size; row++) {
            if (Math.abs(augmented[row][size]) > RELATIVE_TOLERANCE * rhsScale) return null;
        }
        double[] result = new double[size];
        for (int row = 0; row < rank; row++) result[pivots[row]] = augmented[row][size];
        return result;
    }

    private static double forceTolerance(double[] row, double[] inverseMass, double[] external,
                                         double[][] rows, double[] friction) {
        double scale = 1.0;
        for (int axis = 0; axis < 3; axis++) {
            double magnitude = Math.abs(external[axis]);
            for (int i = 0; i < rows.length; i++) magnitude += Math.abs(rows[i][axis] * friction[i]);
            scale += Math.abs(row[axis]) * inverseMass[axis] * magnitude;
        }
        return RELATIVE_TOLERANCE * scale;
    }

    private static double tolerance(double[] row, double[] velocity) {
        double scale = 1.0;
        for (int axis = 0; axis < 3; axis++) scale += Math.abs(row[axis] * velocity[axis]);
        return RELATIVE_TOLERANCE * scale;
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double massDot(double[] a, double[] b, double[] inverseMass) {
        return a[0] * b[0] * inverseMass[0] + a[1] * b[1] * inverseMass[1] + a[2] * b[2] * inverseMass[2];
    }
}
