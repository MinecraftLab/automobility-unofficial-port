package io.github.foundationgames.automobility.automobile.garage;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;

/** Owner contract for Core-coordinated, server-thread Garage operations. No registration unlocks an action alone. */
public interface GarageTransferParticipant {
    int API_VERSION = 1;
    enum Operation { STORE, DEPLOY, STORED_TUNE, ENGINE_SWAP }
    enum Decision { UNDECIDED, COMMIT, ROLLBACK }
    enum Verdict { READY, NO_FOOTPRINT, BUSY, UNSUPPORTED, INVALID_DATA, STALE }
    record Request(UUID transaction, long generation, Operation operation, UUID actor, UUID vehicle,
                   String dimension, String beforeEngine, String afterEngine) {
        public Request {
            if (transaction == null || generation < 1 || operation == null || actor == null || vehicle == null
                    || dimension == null || dimension.isBlank() || beforeEngine == null || afterEngine == null)
                throw new IllegalArgumentException("Complete Garage owner request required");
        }
    }
    interface Fence {
        UUID transactionId();
        long generation();
        boolean heldFor(AutomobileEntity car, UUID transaction, long generation);
        boolean intentDurableFor(AutomobileEntity car, UUID transaction, long generation);
        Decision decisionFor(AutomobileEntity car, UUID transaction, long generation);
    }
    record Inspection(Verdict verdict, CompoundTag capsule) {
        public Inspection { if (verdict == null || (verdict == Verdict.READY && (capsule == null || capsule.isEmpty()))) throw new IllegalArgumentException("READY needs a versioned exact-presence capsule"); capsule = capsule == null ? null : capsule.copy(); }
        @Override public CompoundTag capsule() { return capsule == null ? null : capsule.copy(); }
    }
    record Preparation(Verdict verdict, Prepared prepared) {
        public Preparation { if (verdict == null || (verdict == Verdict.READY) != (prepared != null)) throw new IllegalArgumentException(); }
    }
    /** All methods of every Prepared, including the ordinary prepare result, must tolerate repeated
     * calls for the same transaction/generation. Quiet application/restoration recognizes exact known
     * states without duplicate side effects; repeated closeLocal returns READY after successful close.
     * Finalized decisions are not replayed: subsequent normal fuel/inventory changes remain valid. */
    interface Prepared {
        /** API1 supports owner-state-preserving operations only: before and after owner raw envelopes are identical.
         * Return a defensive, versioned nonempty envelope including exact presence/type/value (also absence).
         * Core freezes and hashes it with owner id/version and Request before any mutation. */
        CompoundTag capsule();
        Verdict validateBeforeApply();
        Verdict applyQuiet();
        Verdict validateApplied();
        /** Core restores its components first. Refuse COMMIT; no public setters or publication. */
        Verdict restoreQuiet();
        /** Idempotent owner ACK after the matching durable Core decision; no client ACK required. */
        Verdict publish(Decision decision);
        /** Releases local resources only; never releases the persistent Core fence. */
        Verdict closeLocal();
    }
    String id();
    default int apiVersion() { return API_VERSION; }
    int capsuleVersion();
    /** Before any native load, including preview. Reject uncertain owner states without changing input.
     * NO_FOOTPRINT is not approval and never permits ordinary initialization. Safe absence uses READY + an absence capsule. */
    Inspection inspectRaw(ServerLevel level, Request request, CompoundTag originalRoot);
    /** expectedCapsule is nonnull for a stored/recovery carrier. Preparation must not mutate/publish. */
    Preparation prepare(Request request, AutomobileEntity carrier, Fence fence, CompoundTag expectedCapsule);
    /** Acquire fresh owner resources without mutation/publication; verify decision against Fence.
     * Recovery methods are idempotent across fresh instances: COMMIT finishes after-state, never before-state;
     * ROLLBACK restores before-state; publication never reapplies a debit/refund. Reject ambiguous state. */
    Preparation recover(Request request, AutomobileEntity carrier, Fence fence, CompoundTag capsule, Decision decision);
}
