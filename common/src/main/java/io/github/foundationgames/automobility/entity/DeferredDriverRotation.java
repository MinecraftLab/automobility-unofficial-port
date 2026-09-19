package io.github.foundationgames.automobility.entity;

import org.jetbrains.annotations.Nullable;

/** A one-shot handoff from an automobile tick to its local driver's following ride tick. */
final class DeferredDriverRotation {
    @Nullable private Object driver;
    private int vehicleTick;
    @Nullable private Rotation rotation;

    void queue(Object driver, int vehicleTick, float yawDelta, boolean lockedView) {
        this.driver = driver;
        this.vehicleTick = vehicleTick;
        this.rotation = new Rotation(yawDelta, lockedView);
    }

    @Nullable
    Rotation consume(Object driver, int vehicleTick, boolean stillDriving) {
        Rotation pending = this.rotation;
        boolean applicable = stillDriving && driver == this.driver && vehicleTick == this.vehicleTick;
        clear();
        return applicable ? pending : null;
    }

    void clear() {
        this.driver = null;
        this.rotation = null;
    }

    record Rotation(float yawDelta, boolean lockedView) { }
}
