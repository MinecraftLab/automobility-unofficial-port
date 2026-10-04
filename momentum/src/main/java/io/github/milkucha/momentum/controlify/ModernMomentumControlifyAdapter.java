package io.github.milkucha.momentum.controlify;

import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.bind.InputBindingSupplier;
import dev.isxander.controlify.api.entrypoint.ControlifyEntrypoint;
import dev.isxander.controlify.api.entrypoint.InitContext;
import dev.isxander.controlify.api.entrypoint.PreInitContext;
import dev.isxander.controlify.bindings.BindContext;
import dev.isxander.controlify.controller.ControllerEntity;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.MomentumControllerInput;
import io.github.milkucha.momentum.MomentumCruiseControl;
import io.github.milkucha.momentum.config.MomentumConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Current contextual/radial-resource API; physical buttons remain unassigned. */
public final class ModernMomentumControlifyAdapter implements ControlifyEntrypoint {
    private InputBindingSupplier cruiseToggle;
    private InputBindingSupplier cruiseIncrease;
    private InputBindingSupplier cruiseDecrease;
    private InputBindingSupplier cruiseResume;

    @Override
    public void onControlifyPreInit(PreInitContext context) {
        var driving = new BindContext(id("driving"), ModernMomentumControlifyAdapter::isDriving);
        context.bindings().registerBindContext(driving);
        var category = Component.translatable("controlify.binding_category.driving");
        cruiseToggle = register(context, driving, category, "cruise_control");
        cruiseIncrease = register(context, driving, category, "cruise_increase");
        cruiseDecrease = register(context, driving, category, "cruise_decrease");
        cruiseResume = register(context, driving, category, "cruise_resume");

        context.contextualDomains().inGame().registerContributor((ctx, sink) -> {
            var player = ctx.player();
            AutomobileEntity auto = player != null && player.getVehicle() instanceof AutomobileEntity vehicle
                    ? vehicle : null;
            sink.contributeFact(id("driving_automobile"), auto != null && auto.isDriving(player)
                    && MomentumConfig.gameplay().enabled && MomentumConfig.gameplay().cruise.enabled);
            sink.contributeFact(id("cruise_active"), auto != null && MomentumCruiseControl.isActiveFor(auto));
            sink.contributeFact(id("cruise_resumable"), auto != null && MomentumCruiseControl.canResumeFor(auto));
        });
        MomentumControllerInput.install(this::pollActions);
    }

    private static InputBindingSupplier register(PreInitContext context, BindContext driving,
                                                 Component category, String action) {
        return context.bindings().registerBinding(builder -> builder
                .id(id(action)).name(Component.translatable("key.momentum." + action))
                .category(category).allowedContexts(driving).radialCandidate(true));
    }

    private MomentumControllerInput.ActionSnapshot pollActions() {
        ControlifyApi api = ControlifyApi.get();
        if (!api.currentInputMode().isController()) return MomentumControllerInput.none();
        return api.getCurrentController()
                .map(controller -> new MomentumControllerInput.ActionSnapshot(
                        justPressed(cruiseToggle, controller), justPressed(cruiseIncrease, controller),
                        justPressed(cruiseDecrease, controller), justPressed(cruiseResume, controller)))
                .orElseGet(MomentumControllerInput::none);
    }

    private static boolean justPressed(InputBindingSupplier binding, ControllerEntity controller) {
        return binding != null && binding.on(controller).justPressed();
    }

    private static boolean isDriving(Minecraft client) {
        return client.screen == null && client.player != null
                && client.player.getVehicle() instanceof AutomobileEntity auto && auto.isDriving(client.player)
                && MomentumConfig.gameplay().enabled && MomentumConfig.gameplay().cruise.enabled;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("momentum", path);
    }

    @Override
    public void onControlifyInit(InitContext context) {
    }

    @Override
    public void onControllersDiscovered(ControlifyApi controlify) {
    }
}
