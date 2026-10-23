package io.github.milkucha.momentum.hud;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import io.github.foundationgames.automobility.client.vehicle.UnifiedVehicleHud;
import io.github.foundationgames.automobility.client.vehicle.VehicleUiState;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.MomentumCruiseControl;
import io.github.milkucha.momentum.config.MomentumConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Momentum supplies its accepted readout; core owns the common presentation and placement. */
public final class MomentumHudContributor implements UnifiedVehicleHud.DrivingContributor {
    private static MomentumConfig.BarHud previous;
    public static void register() {
        String raw=VehicleUiState.legacyInput("momentum.json");
        try {
            if(raw!=null) {
                var json=JsonParser.parseString(raw).getAsJsonObject();
                if(json.has("barHud")) previous=MomentumConfig.sanitizeHudLayout(new Gson().fromJson(json.get("barHud"),MomentumConfig.BarHud.class));
            }
        } catch(RuntimeException error) {io.github.milkucha.momentum.Momentum.LOGGER.warn("Preserved legacy dashboard could not be read",error);}
        UnifiedVehicleHud.register(new MomentumHudContributor());
    }
    public static MomentumConfig.BarHud layoutPreferences() {
        return !VehicleUiState.unified() && previous!=null?previous:MomentumConfig.get().barHud;
    }
    public static boolean dashboardEnabled() {return layoutPreferences().enabled;}
    @Override public boolean enabled() {return MomentumConfig.get().dashboardEnabled();}
    @Override public net.minecraft.network.chat.Component controlBinding(String action,net.minecraft.network.chat.Component fallback) {
        return switch(action) {
            case "brake" -> io.github.milkucha.momentum.MomentumKeyMappings.brakeBinding();
            case "drift" -> io.github.milkucha.momentum.MomentumKeyMappings.driftBinding();
            default -> fallback;
        };
    }
    @Override public int speedKmh(AutomobileEntity auto) {return BarHud.displayedSpeed(auto);}
    @Override public boolean cruiseActive(AutomobileEntity auto) {return MomentumCruiseControl.isActiveFor(auto);}
    @Override public int cruiseTarget() {return Math.round(MomentumCruiseControl.getTargetKmh());}
    @Override public java.util.List<net.minecraft.network.chat.Component> warnings(AutomobileEntity auto) {
        var client=Minecraft.getInstance();var b=MomentumConfig.get().barHud;
        if(client.player==null || !ReverseReleaseHintLayout.visible(auto.isDriving(client.player),client.screen!=null,
                enabled(),b.compactDashboard,b.reverseReleaseHint,auto.waitingForReverseRelease(),auto.rawBrakePedalHeld(),ReverseReleaseHintLayout.stationary(auto.getEffectiveSpeed()))) return java.util.List.of();
        return java.util.List.of(net.minecraft.network.chat.Component.translatable("hint.momentum.reverse_release.title"),net.minecraft.network.chat.Component.translatable("hint.momentum.reverse_release.action"));
    }
}
