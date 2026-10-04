package io.github.milkucha.momentum.accessor;

/**
 * Exposes private steering state from AutomobileEntity for the debug HUD overlay.
 * AutomobileEntityMixin implements this interface via @Unique methods.
 * Cast any AutomobileEntity to SteeringDebugAccessor to read values.
 */
public interface SteeringDebugAccessor {
    float momentum$getSteering();
    float momentum$getHSpeed();
    float momentum$getAngularSpeed();
    boolean momentum$isDrifting();
    boolean momentum$isOnGround();
    boolean momentum$isArcadeDriftActive();
    float   momentum$getArcadeDriftOffset();
    boolean momentum$isResponsiveDriftActive();
    float   momentum$getResponsiveDriftOffset();
    float   momentum$getEngineSpeed();
    void momentum$setRemoteInputState(boolean brake, boolean drift);
}
