package io.github.foundationgames.automobility.fabric.controller.controlify;

import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.entrypoint.ControlifyEntrypoint;
import dev.isxander.controlify.api.entrypoint.InitContext;
import dev.isxander.controlify.api.entrypoint.PreInitContext;
import dev.isxander.controlify.bindings.BindContext;
import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.network.chat.Component;

/** Current contextual API; loaded only after the stable entrypoint detects this API family. */
public final class ModernControlifyAdapter implements ControlifyEntrypoint {
    @Override
    public void onControlifyPreInit(PreInitContext context) {
        var bindings = context.bindings();
        var driving = new BindContext(Automobility.rl("driving"), client ->
                client.player != null && client.player.getVehicle() instanceof AutomobileEntity auto
                        && auto.isDriving(client.player));
        bindings.registerBindContext(driving);
        var category = Component.translatable("controlify.binding_category.driving");
        ControlifyController.accelerateBinding = bindings.registerBinding(builder -> builder
                .id(Automobility.rl("accelerate_automobile")).allowedContexts(driving).category(category));
        ControlifyController.brakeBinding = bindings.registerBinding(builder -> builder
                .id(Automobility.rl("brake_automobile")).allowedContexts(driving).category(category));
        ControlifyController.driftBinding = bindings.registerBinding(builder -> builder
                .id(Automobility.rl("drift_automobile")).allowedContexts(driving).category(category));
        ControlifyCompat.AUTOMOBILITY_BINDINGS.add(ControlifyController.accelerateBinding);
        ControlifyCompat.AUTOMOBILITY_BINDINGS.add(ControlifyController.brakeBinding);
        ControlifyCompat.AUTOMOBILITY_BINDINGS.add(ControlifyController.driftBinding);

        context.contextualDomains().inGame().registerContributor((ctx, sink) ->
                sink.contributeFact(Automobility.rl("driving_automobile"),
                        ctx.player() != null && ctx.player().getVehicle() instanceof AutomobileEntity auto
                                && auto.isDriving(ctx.player())));
    }

    @Override
    public void onControlifyInit(InitContext context) {
    }

    @Override
    public void onControllersDiscovered(ControlifyApi controlify) {
    }
}
