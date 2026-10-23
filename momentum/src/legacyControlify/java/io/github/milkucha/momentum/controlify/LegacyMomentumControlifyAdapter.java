package io.github.milkucha.momentum.controlify;

import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.bind.InputBindingSupplier;
import dev.isxander.controlify.api.entrypoint.ControlifyEntrypoint;
import dev.isxander.controlify.api.entrypoint.InitContext;
import dev.isxander.controlify.api.entrypoint.PreInitContext;
import dev.isxander.controlify.api.guide.ActionLocation;
import dev.isxander.controlify.api.guide.Fact;
import dev.isxander.controlify.api.guide.InGameCtx;
import dev.isxander.controlify.api.guide.Rule;
import dev.isxander.controlify.bindings.BindContext;
import dev.isxander.controlify.bindings.RadialIcons;
import dev.isxander.controlify.controller.ControllerEntity;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.MomentumControllerInput;
import io.github.milkucha.momentum.MomentumCruiseControl;
import io.github.milkucha.momentum.config.MomentumConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

/** First-class, optional Controlify bindings for Momentum's cruise controls. */
public final class LegacyMomentumControlifyAdapter implements ControlifyEntrypoint {
    private static final Identifier INCREASE_ICON = id("radial/cruise_increase");
    private static final Identifier DECREASE_ICON = id("radial/cruise_decrease");

    private InputBindingSupplier cruiseToggle;
    private InputBindingSupplier cruiseIncrease;
    private InputBindingSupplier cruiseDecrease;
    private InputBindingSupplier cruiseResume;

    @Override
    public void onControlifyPreInit(PreInitContext context) {
        BindContext drivingContext = new BindContext(id("driving"), LegacyMomentumControlifyAdapter::isDriving);
        context.bindings().registerBindContext(drivingContext);

        Component category = Component.translatable("controlify.binding_category.driving");
        cruiseToggle = register(context, drivingContext, category, "cruise_control",
                RadialIcons.getItem(Items.COMPASS));
        cruiseIncrease = register(context, drivingContext, category, "cruise_increase", INCREASE_ICON);
        cruiseDecrease = register(context, drivingContext, category, "cruise_decrease", DECREASE_ICON);
        cruiseResume = register(context, drivingContext, category, "cruise_resume",
                RadialIcons.getItem(Items.CLOCK));

        context.bindings().registerRadialIcon(INCREASE_ICON, (graphics, x, y, tickDelta) -> {
            int color = 0xFF77E2B8;
            graphics.fill(x + 2, y + 7, x + 14, y + 9, color);
            graphics.fill(x + 7, y + 2, x + 9, y + 14, color);
        });
        context.bindings().registerRadialIcon(DECREASE_ICON, (graphics, x, y, tickDelta) ->
                graphics.fill(x + 2, y + 7, x + 14, y + 9, 0xFFF0C36A));

        registerGuides(context);
        MomentumControllerInput.install(this::pollActions);
    }

    private void registerGuides(PreInitContext context) {
        Fact<InGameCtx> driving = Fact.of(id("driving_automobile"), ctx ->
                ctx.player().getVehicle() instanceof AutomobileEntity auto
                        && auto.isDriving(ctx.player())
                        && MomentumCruiseControl.isEnabled());
        Fact<InGameCtx> active = Fact.of(id("cruise_active"), ctx ->
                ctx.player().getVehicle() instanceof AutomobileEntity auto
                        && MomentumCruiseControl.isActiveFor(auto));
        Fact<InGameCtx> resumable = Fact.of(id("cruise_resumable"), ctx ->
                ctx.player().getVehicle() instanceof AutomobileEntity auto
                        && MomentumCruiseControl.canResumeFor(auto));

        context.guideRegistries().inGame().registerFact(driving);
        context.guideRegistries().inGame().registerFact(active);
        context.guideRegistries().inGame().registerFact(resumable);

        registerGuide(context, cruiseToggle, driving, null, "cruise_control");
        registerGuide(context, cruiseIncrease, driving, active, "cruise_increase");
        registerGuide(context, cruiseDecrease, driving, active, "cruise_decrease");
        registerGuide(context, cruiseResume, driving, resumable, "cruise_resume");
    }

    private static void registerGuide(PreInitContext context,
                                      InputBindingSupplier binding,
                                      Fact<InGameCtx> driving,
                                      Fact<InGameCtx> state,
                                      String translationSuffix) {
        var builder = Rule.builder()
                .binding(binding)
                .where(ActionLocation.RIGHT)
                .when(driving)
                .then(Component.translatable("key.momentum." + translationSuffix));
        if (state != null) {
            builder.when(state);
        }
        context.guideRegistries().inGame().registerDynamicRule(builder.build());
    }

    private static InputBindingSupplier register(PreInitContext context,
                                                 BindContext drivingContext,
                                                 Component category,
                                                 String action,
                                                 Identifier radialIcon) {
        return context.bindings().registerBinding(builder -> builder
                .id(id(action))
                .name(Component.translatable("key.momentum." + action))
                .category(category)
                .allowedContexts(drivingContext)
                .radialCandidate(radialIcon));
    }

    private MomentumControllerInput.ActionSnapshot pollActions() {
        ControlifyApi api = ControlifyApi.get();
        if (!api.currentInputMode().isController()) {
            return MomentumControllerInput.none();
        }
        return api.getCurrentController()
                .map(controller -> new MomentumControllerInput.ActionSnapshot(
                        justPressed(cruiseToggle, controller),
                        justPressed(cruiseIncrease, controller),
                        justPressed(cruiseDecrease, controller),
                        justPressed(cruiseResume, controller)))
                .orElseGet(MomentumControllerInput::none);
    }

    private static boolean justPressed(InputBindingSupplier binding, ControllerEntity controller) {
        return binding != null && binding.on(controller).justPressed();
    }

    private static boolean isDriving(net.minecraft.client.Minecraft client) {
        return client.gui.screen() == null
                && client.player != null
                && client.player.getVehicle() instanceof AutomobileEntity auto
                && auto.isDriving(client.player)
                && MomentumCruiseControl.isEnabled();
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
