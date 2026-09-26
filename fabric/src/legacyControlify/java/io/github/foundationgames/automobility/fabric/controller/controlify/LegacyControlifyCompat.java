package io.github.foundationgames.automobility.fabric.controller.controlify;

import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.bind.ControlifyBindApi;
import dev.isxander.controlify.api.bind.InputBindingSupplier;
import dev.isxander.controlify.api.entrypoint.ControlifyEntrypoint;
import dev.isxander.controlify.api.entrypoint.InitContext;
import dev.isxander.controlify.api.entrypoint.PreInitContext;
import dev.isxander.controlify.api.guide.ActionLocation;
import dev.isxander.controlify.api.guide.Fact;
import dev.isxander.controlify.api.guide.InGameCtx;
import dev.isxander.controlify.api.guide.Rule;
import dev.isxander.controlify.bindings.BindContext;
import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Set;

public class LegacyControlifyCompat implements ControlifyEntrypoint {

    @Override
    public void onControlifyPreInit(PreInitContext context) {
        var bindings = context.bindings();
        var drivingContextId = Automobility.rl("driving");
        BindContext drivingCtx = new BindContext(drivingContextId, mc -> {
            if (mc.player != null) {
                var veh = mc.player.getVehicle();
                return veh instanceof AutomobileEntity auto && auto.isDriving(mc.player);
            }
            return false;
        });
        bindings.registerBindContext(drivingCtx);

        Component category = Component.translatable("controlify.binding_category.driving");

        ControlifyController.accelerateBinding = bindings.registerBinding(builder -> builder
                .id(Automobility.rl("accelerate_automobile"))
                .allowedContexts(drivingCtx)
                .category(category));
        ControlifyController.brakeBinding = bindings.registerBinding(builder -> builder
                .id(Automobility.rl("brake_automobile"))
                .allowedContexts(drivingCtx)
                .category(category));
        ControlifyController.driftBinding = bindings.registerBinding(builder -> builder
                .id(Automobility.rl("drift_automobile"))
                .allowedContexts(drivingCtx)
                .category(category));

        ControlifyCompat.AUTOMOBILITY_BINDINGS.clear();
        ControlifyCompat.AUTOMOBILITY_BINDINGS.add(ControlifyController.accelerateBinding);
        ControlifyCompat.AUTOMOBILITY_BINDINGS.add(ControlifyController.brakeBinding);
        ControlifyCompat.AUTOMOBILITY_BINDINGS.add(ControlifyController.driftBinding);

        Fact<InGameCtx> drivingAutomobile = Fact.of(Automobility.rl("driving_automobile"), ctx ->
                ctx.player().getVehicle() instanceof AutomobileEntity auto && auto.isDriving(ctx.player()));
        context.guideRegistries().inGame().registerFact(drivingAutomobile);
        registerGuideRule(context, drivingAutomobile, ControlifyController.accelerateBinding, "accelerate_automobile");
        registerGuideRule(context, drivingAutomobile, ControlifyController.brakeBinding, "brake_automobile");
        registerGuideRule(context, drivingAutomobile, ControlifyController.driftBinding, "drift_automobile");
    }

    private static void registerGuideRule(PreInitContext context, Fact<InGameCtx> drivingAutomobile, InputBindingSupplier binding, String key) {
        context.guideRegistries().inGame().registerDynamicRule(Rule.builder()
                .binding(binding)
                .where(ActionLocation.LEFT)
                .when(drivingAutomobile)
                .then(Component.translatable("controlify.binding.automobility." + key))
                .build());
    }

    @Override
    public void onControlifyInit(InitContext context) {
    }

    @Override
    public void onControllersDiscovered(ControlifyApi controlify) {
    }
}
