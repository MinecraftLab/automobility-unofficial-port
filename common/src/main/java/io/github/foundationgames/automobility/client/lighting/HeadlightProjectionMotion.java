package io.github.foundationgames.automobility.client.lighting;

/** Private opt-in projection policy; never used for the body or physical lamps. */
final class HeadlightProjectionMotion {
    private HeadlightProjectionMotion() { }

    static float engineShake(boolean running, long time, float partial, boolean stabilized) {
        // Preserve original long+float promotion and operation order when disabled.
        return running && !stabilized ? (float)(Math.cos((time + partial) * 2.7) / 156) : 0;
    }

    static boolean reuseCandidate(boolean exactNativeCar, boolean running, boolean passenger, boolean stabilized) {
        // Only eligibility. The caller must still prove exact current beams, world,
        // native surfaces, model/terrain epochs, topology and complete work quota.
        return exactNativeCar && !passenger && (!running || stabilized);
    }
}
