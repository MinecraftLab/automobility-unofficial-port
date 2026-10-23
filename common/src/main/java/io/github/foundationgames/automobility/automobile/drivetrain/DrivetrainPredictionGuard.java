package io.github.foundationgames.automobility.automobile.drivetrain;

/** Pure guards over accepted S2C observations. They never grant server energy or mutate any owner. */
public final class DrivetrainPredictionGuard {
    private DrivetrainPredictionGuard() { }
    /** A free/zero quote cannot revive a latch closed by owner denial or a new control epoch. */
    public static boolean afterOwnerStep(boolean previouslyPermitted, boolean paid, boolean nonzeroActuatorDemand, boolean authorityReady) {
        return authorityReady && paid && (previouslyPermitted || nonzeroActuatorDemand);
    }
    public static boolean fresh(DrivetrainProtocol.Snapshot observation, DrivetrainProtocol.Identity controllingIdentity,
            String profileId, String profileHash, long receivedAtClientTick, long clientTick, int maximumAgeTicks) {
        if (maximumAgeTicks < 0 || maximumAgeTicks > 100) throw new IllegalArgumentException("bounded prediction age");
        return observation != null && controllingIdentity != null && observation.identity().equals(controllingIdentity)
                && observation.profileId().equals(profileId) && observation.profileSha256().equals(profileHash)
                && receivedAtClientTick >= 0 && clientTick >= receivedAtClientTick
                && clientTick - receivedAtClientTick <= maximumAgeTicks;
    }
    public static boolean power(DrivetrainProtocol.Snapshot observation, DrivetrainProtocol.Identity controllingIdentity,
            String profileId, String profileHash, long receivedAtClientTick, long clientTick, int maximumAgeTicks) {
        return fresh(observation, controllingIdentity, profileId, profileHash, receivedAtClientTick, clientTick, maximumAgeTicks)
                && observation.predictionPowerPermitted();
    }
    public static double coastTorque(DrivetrainProtocol.Snapshot observation, DrivetrainProtocol.Identity controllingIdentity,
            String profileId, String profileHash, long receivedAtClientTick, long clientTick, int maximumAgeTicks,
            long controlContextChangedAtTick, boolean engineOn, double throttle, boolean grounded,
            boolean physicalBrake, boolean handbrake, boolean hazard, double currentCurveCapacityNm) {
        Units.between(throttle, 0, 1, "prediction throttle");
        Units.nonnegative(currentCurveCapacityNm, "current regeneration capacity");
        if (!fresh(observation, controllingIdentity, profileId, profileHash, receivedAtClientTick, clientTick, maximumAgeTicks)
                || receivedAtClientTick <= controlContextChangedAtTick || !engineOn || throttle != 0 || !grounded
                || physicalBrake || handbrake || hazard) return 0;
        return Math.min(observation.acceptedCoastRegenerationTorqueNm(), currentCurveCapacityNm);
    }
}
