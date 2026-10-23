package io.github.milkucha.momentum.network;

/**
 * Semantic driving-physics agreement, independent of payload codecs and release
 * versions. Change this revision when peers can no longer simulate the same
 * authoritative gameplay settings.
 */
public final class PhysicsContract {
    public static final int REVISION = 4;

    private PhysicsContract() {
    }

    public static boolean accepts(int revision) {
        return revision == REVISION;
    }

    public static boolean accepts(int revision, long nonce,
                                  boolean canReceiveStatus, boolean canReceiveConfig) {
        return accepts(revision) && nonce != 0 && canReceiveStatus && canReceiveConfig;
    }
}
