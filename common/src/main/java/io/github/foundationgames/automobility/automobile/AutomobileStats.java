package io.github.foundationgames.automobility.automobile;

import io.github.foundationgames.automobility.Automobility;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.function.Consumer;

public class AutomobileStats implements StatContainer<AutomobileStats> {
    static final float MIN_ACCELERATION = -1f;
    static final float MIN_HANDLING = 0.01f;

    public static final Identifier ID = Automobility.rl("automobile");
    public static final DisplayStat<AutomobileStats> STAT_ACCELERATION = new DisplayStat<>("acceleration", AutomobileStats::getAcceleration);
    public static final DisplayStat<AutomobileStats> STAT_COMFORTABLE_SPEED = new DisplayStat<>("comfortable_speed", stats -> stats.getComfortableSpeed() * 20);
    public static final DisplayStat<AutomobileStats> STAT_HANDLING = new DisplayStat<>("handling", AutomobileStats::getHandling);
    public static final DisplayStat<AutomobileStats> STAT_GRIP = new DisplayStat<>("grip", AutomobileStats::getGrip);

    private float acceleration = 0;     // Lower-bounded so acceleration cannot reverse the vehicle
    private float comfortableSpeed = 0; // Blocks per Tick
    private float handling = MIN_HANDLING; // Kept positive for steering and autopilot consumers
    private float grip = 0;             // Raw combined stat; surface movement applies a safe blend

    public AutomobileStats() {
    }

    public void from(AutomobileFrame frame, AutomobileWheel wheel, AutomobileEngine engine) {
        this.acceleration = Math.max(MIN_ACCELERATION,
                (1 - ((frame.weight() + wheel.size()) / 2)) + (2 * engine.torque()) / 3);
        this.comfortableSpeed = ((engine.speed() * 3) + ((engine.speed() * frame.weight()) * 2) + (engine.speed() * wheel.size())) / 5.7f;
        this.handling = Math.max(MIN_HANDLING,
                ((1 - wheel.size()) + (1 - frame.weight()) + 2) / 4);
        this.grip = (wheel.grip() + frame.weight()) / 2;
    }

    public float calculateSurfaceGrip(float normalizedSurfaceFriction) {
        float blend = 1 - (Mth.clamp(normalizedSurfaceFriction, 0, 1) * (1 - this.grip * 0.8f));
        blend = Mth.clamp(blend, 0, 1);
        return blend * blend;
    }

    public float getAcceleration() {
        return acceleration;
    }

    public float getComfortableSpeed() {
        return comfortableSpeed;
    }

    /** Unscaled parts threshold for previews. Runtime getComfortableSpeed retains its hooks. */
    public float getBaseComfortableSpeed() {
        return comfortableSpeed;
    }

    public float getHandling() {
        return handling;
    }

    public float getGrip() {
        return grip;
    }

    @Override
    public Identifier containerId() {
        return ID;
    }

    @Override
    public void forEachStat(Consumer<DisplayStat<AutomobileStats>> action) {
        action.accept(STAT_ACCELERATION);
        action.accept(STAT_COMFORTABLE_SPEED);
        action.accept(STAT_HANDLING);
        action.accept(STAT_GRIP);
    }
}
