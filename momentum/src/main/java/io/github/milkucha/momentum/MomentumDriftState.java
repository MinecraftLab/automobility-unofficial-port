package io.github.milkucha.momentum;

/**
 * Client-side drift key state for Momentum.
 *
 * driftKeyHeld is written by START_CLIENT_TICK (client thread) and read by
 * AutomobileEntityMixin drift state machines which run on both the client
 * entity and the integrated-server entity. volatile ensures cross-thread
 * visibility, matching the pattern used by MomentumBrakeState.
 *
 * Which drift behaviour is active is determined by MomentumConfig.ODrift.Profile:
 *   VANILLA  - Automobility-transplanted drift
 *   ARCADE   - slip-angle arcade drift
 *   RESPONSIVE - steering-driven deep slide
 *
 * prevDriftKeyHeld is intentionally NOT here - rising/falling edge tracking
 * is done via @Unique instance fields on each AutomobileEntity instance, so
 * client and server entities track their own edges independently and never
 * race on a shared static.
 */
public class MomentumDriftState {
    public static volatile boolean driftKeyHeld = false;
}
