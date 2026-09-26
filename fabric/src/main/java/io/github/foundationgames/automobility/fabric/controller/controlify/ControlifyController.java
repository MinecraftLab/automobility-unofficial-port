package io.github.foundationgames.automobility.fabric.controller.controlify;

import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.bind.InputBinding;
import dev.isxander.controlify.api.bind.InputBindingSupplier;
import dev.isxander.controlify.rumble.BasicRumbleEffect;
import dev.isxander.controlify.rumble.ContinuousRumbleEffect;
import dev.isxander.controlify.rumble.RumbleState;
import io.github.foundationgames.automobility.controller.AutomobileController;

public class ControlifyController implements AutomobileController {
    static InputBindingSupplier accelerateBinding, brakeBinding, driftBinding;

    static void clearBindings() {
        accelerateBinding = brakeBinding = driftBinding = null;
    }
    private ContinuousRumbleEffect maxChargeRumble = null, boostingRumble = null, offRoadRumble = null;

    @Override
    public boolean accelerating() {
        return isDown(accelerateBinding);
    }

    @Override
    public boolean braking() {
        return isDown(brakeBinding);
    }

    @Override
    public boolean drifting() {
        return isDown(driftBinding);
    }

    @Override
    public boolean inControllerMode() {
        return ControlifyCompat.isInitialized() && ControlifyApi.get().currentInputMode().isController();
    }

    private boolean isDown(InputBindingSupplier binding) {
        return binding != null && inControllerMode() && ControlifyApi.get().getCurrentController()
                .map(binding::on)
                .map(InputBinding::digitalNow)
                .orElse(false);
    }

    @Override
    public void crashRumble() {
        if (!ControlifyCompat.isInitialized()) return;
        ControlifyApi.get().getCurrentController().ifPresent(controller -> {
            controller.rumble().ifPresent(c -> c.rumbleManager().play(
                    BasicRumbleEffect.byTime(
                            t -> new RumbleState(t < 0.25 ? 1 : 0, (1 - t) * 0.7f),
                            5
                    )
            ));
        });
    }

    @Override
    public void groundThudRumble() {
        if (!ControlifyCompat.isInitialized()) return;
        ControlifyApi.get().getCurrentController().ifPresent(controller -> {
            controller.rumble().ifPresent(c -> c.rumbleManager().play(
                    BasicRumbleEffect.constant(0.1f, 0.3f, 3)
            ));
        });
    }

    @Override
    public void driftChargeRumble() {
        if (!ControlifyCompat.isInitialized()) return;
        ControlifyApi.get().getCurrentController().ifPresent(controller -> {
            controller.rumble().ifPresent(c -> c.rumbleManager().play(
                    BasicRumbleEffect.byTime(
                            t -> new RumbleState(t < 0.15 ? 0.4f : 0, (1 - t) * 0.2f),
                            6
                    )
            ));
        });
    }

    @Override
    public void updateMaxChargeRumbleState(boolean maxCharge) {
        if (!ControlifyCompat.isInitialized()) return;
        if (maxCharge) {
            if (maxChargeRumble != null && !maxChargeRumble.isFinished())
                return;

            maxChargeRumble = ContinuousRumbleEffect.builder()
                    .constant(0f, 0.05f)
                    .build();
            ControlifyApi.get().getCurrentController().ifPresent(controller ->
                    controller.rumble().ifPresent(c -> c.rumbleManager().play(maxChargeRumble)));
        } else if (maxChargeRumble != null) {
            maxChargeRumble.stop();
        }
    }

    @Override
    public void updateBoostingRumbleState(boolean boosting, float boostPower) {
        if (!ControlifyCompat.isInitialized()) return;
        if (boosting) {
            if (boostingRumble != null && !boostingRumble.isFinished())
                return;

            boostingRumble = ContinuousRumbleEffect.builder()
                    .constant(0.1f * boostPower, 0.5f * boostPower)
                    .build();
            ControlifyApi.get().getCurrentController().ifPresent(controller ->
                    controller.rumble().ifPresent(c -> c.rumbleManager().play(boostingRumble)));
        } else if (boostingRumble != null) {
            boostingRumble.stop();
        }
    }

    @Override
    public void updateOffRoadRumbleState(boolean inOffRoad) {
        if (!ControlifyCompat.isInitialized()) return;
        if (inOffRoad) {
            if (offRoadRumble != null && !offRoadRumble.isFinished())
                return;

            offRoadRumble = ContinuousRumbleEffect.builder()
                    .constant(0.04f, 0.02f)
                    .build();
            ControlifyApi.get().getCurrentController().ifPresent(controller ->
                    controller.rumble().ifPresent(c -> c.rumbleManager().play(offRoadRumble)));
        } else if (offRoadRumble != null) {
            offRoadRumble.stop();
        }
    }
}
