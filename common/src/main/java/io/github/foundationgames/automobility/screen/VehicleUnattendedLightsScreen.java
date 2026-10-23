package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.automobile.light.VehicleLightMode;
import io.github.foundationgames.automobility.automobile.light.VehicleUnattendedLightSettings;
import io.github.foundationgames.automobility.client.vehicle.VehicleClientControls;
import io.github.foundationgames.automobility.client.vehicle.VehicleEffectsLayout;
import io.github.foundationgames.automobility.client.vehicle.VehicleUnattendedLightsClient;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Per-car server controls; never predicts a successful setting or fakes a capability. */
public final class VehicleUnattendedLightsScreen extends Screen {
    private final Screen parent;
    private final AutomobileEntity car;
    private VehicleEffectsLayout layout;
    private Button delay, work, rearm;
    private int ticks;
    public VehicleUnattendedLightsScreen(Screen parent, AutomobileEntity car) {
        super(text("title")); this.parent = parent; this.car = car;
    }
    @Override protected void init() {
        layout = VehicleEffectsLayout.resolve(width, height);
        if (layout.small()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> onClose())
                    .bounds(Math.max(0,width/2-60),Math.max(0,height-26),Math.min(120,width),20).build());
            return;
        }
        delay = button(62, Component.empty(), () -> {
            var state = VehicleUnattendedLightsClient.state(car);
            if (state != null) VehicleUnattendedLightsClient.request(car, VehicleUnattendedLightSettings.nextDelay(
                    state.selectedSeconds(),state.maximumSeconds(),state.allowUnlimited()));
        }, "delay_help");
        work = button(88, text("work"), () -> {
            var state = VehicleUnattendedLightsClient.state(car);
            if (state != null) VehicleUnattendedLightsClient.request(car, Math.min(1800,state.maximumSeconds()));
        }, "work_help");
        rearm = button(114, text("rearm"), () -> VehicleClientControls.requestLightMode(car,
                car.getVehicleLightMode() == VehicleLightMode.OFF ? VehicleLightMode.LOW : car.getVehicleLightMode()), "rearm_help");
        button(layout.height()-28, Component.translatable("gui.back"), this::onClose, "help");
        VehicleUnattendedLightsClient.request(car,null);
        update();
    }
    private Button button(int y, Component label, Runnable action, String help) {
        return addRenderableWidget(Button.builder(label, ignored -> action.run())
                .bounds(layout.left()+16,layout.top()+y,layout.width()-32,20).tooltip(Tooltip.create(text(help))).build());
    }
    private void update() {
        var state = VehicleUnattendedLightsClient.state(car);
        boolean allowed = VehicleClientControls.isDriver(minecraft,car) && car.getVehicleAccess().mayConfigure(minecraft.player.getUUID());
        if (delay != null) {
            delay.active = allowed && state != null && (state.maximumSeconds() > 0 || state.allowUnlimited());
            work.active = allowed && state != null && state.maximumSeconds() > 0;
            rearm.active = allowed;
            delay.setMessage(state == null ? text(ticks < 40 ? "waiting" : "unavailable")
                    : Component.translatable("vehicle.automobility.unattended.delay", delayName(state.selectedSeconds())));
        }
    }
    @Override public void tick() {
        if (!VehicleClientControls.canUseMenu(minecraft,car)) { onClose(); return; }
        if (++ticks % 20 == 0) VehicleUnattendedLightsClient.request(car,null);
        update();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0,0,width,height,0xEE162128);
        if (layout.small()) graphics.textWithWordWrap(font, Component.translatable("vehicle.automobility.too_small"),
                12,12,Math.max(1,width-24),0xFFE4EDF1);
        else {
            int x=layout.left()+16;
            graphics.text(font,title,x,layout.top()+10,0xFFE4EDF1,false);
            graphics.textWithWordWrap(font,text("help"),x,layout.top()+27,layout.width()-32,0xFFA7BAC5);
            var state=VehicleUnattendedLightsClient.state(car);
            Component status = state == null ? text(ticks < 40 ? "waiting" : "unavailable")
                    : Component.translatable("vehicle.automobility.unattended.effective",delayName(state.effectiveSeconds()),delayName(state.maximumSeconds()));
            graphics.textWithWordWrap(font,status,x,layout.top()+145,layout.width()-32,0xFF8DD4E7);
            graphics.textWithWordWrap(font,text("restore_help"),x,layout.top()+174,layout.width()-32,0xFFA7BAC5);
        }
        super.extractRenderState(graphics,mouseX,mouseY,delta);
    }
    private static Component delayName(int seconds) {
        return seconds < 0 ? text("never") : seconds == 0 ? text("immediate")
                : Component.translatable("vehicle.automobility.unattended." + (seconds % 60 == 0 ? "minutes" : "seconds"),
                        seconds % 60 == 0 ? seconds/60 : seconds);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }
    private static Component text(String key) { return Component.translatable("vehicle.automobility.unattended."+key); }
}
