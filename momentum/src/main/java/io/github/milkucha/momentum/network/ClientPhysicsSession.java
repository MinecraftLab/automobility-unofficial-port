package io.github.milkucha.momentum.network;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Current client connection's physics eligibility. This class deliberately has
 * no Minecraft client references so server-side gameplay code may safely load
 * it. Mutations are dispatched onto the client thread by the network hooks.
 */
public final class ClientPhysicsSession {
    private static final AtomicLong NEXT_NONCE = new AtomicLong(ThreadLocalRandom.current().nextLong());
    private static long sessionNonce;
    private static boolean remoteSession;
    private static boolean compatible;
    private static boolean presenceKnown;
    private static boolean serverHasMomentum;

    private ClientPhysicsSession() {
    }

    public static void beginRemoteSession() {
        long next = NEXT_NONCE.incrementAndGet();
        if (next == 0) {
            next = NEXT_NONCE.incrementAndGet();
        }
        sessionNonce = next;
        remoteSession = true;
        compatible = false;
        presenceKnown = false;
        serverHasMomentum = false;
    }

    public static void endSession() {
        sessionNonce = 0;
        remoteSession = false;
        compatible = false;
        presenceKnown = false;
        serverHasMomentum = false;
    }

    public static void receiveStatus(int revision, long nonce, boolean accepted) {
        if (remoteSession && nonce != 0 && nonce == sessionNonce) {
            compatible = accepted && PhysicsContract.accepts(revision);
        }
    }

    public static void setServerHasMomentum(boolean present) {
        presenceKnown = remoteSession;
        serverHasMomentum = remoteSession && present;
    }

    public static boolean physicsAllowed(boolean hasSnapshot) {
        return !remoteSession || (presenceKnown && serverHasMomentum && compatible && hasSnapshot);
    }

    public static boolean blocksDriving(boolean hasSnapshot) {
        return blocksDriving(hasSnapshot, true);
    }

    public static boolean blocksDriving(boolean hasSnapshot, boolean serverPhysicsEnabled) {
        if (!remoteSession) return false;
        if (!presenceKnown) return true;
        if (!serverHasMomentum) return false;
        // Only an actual server snapshot can exempt disabled server physics.
        // This permits Core driving, not unacknowledged Momentum simulation.
        if (hasSnapshot && !serverPhysicsEnabled) return false;
        return !physicsAllowed(hasSnapshot);
    }

    public static long nonce() {
        return sessionNonce;
    }

    public static boolean isRemoteSession() {
        return remoteSession;
    }

    public static boolean isCompatible() {
        return compatible;
    }

    public static boolean serverHasMomentum() {
        return serverHasMomentum;
    }

    public static boolean presenceKnown() {
        return presenceKnown;
    }
}
