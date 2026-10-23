package io.github.milkucha.momentum;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.milkucha.momentum.config.MomentumConfig;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

/** Legacy JSON keys seed defaults; Minecraft's saved controls own subsequent remaps. */
public final class MomentumKeyMappings {
    private static KeyMapping brake, drift;

    private MomentumKeyMappings() {}

    public static void initialize(KeyMapping.Category category) {
        var legacy = MomentumConfig.get();
        brake = register("key.momentum.brake", legacy.brakeKey, category);
        drift = register("key.momentum.handbrake_drift", legacy.driftKey, category);
    }

    private static KeyMapping register(String name, int legacyKey, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, InputConstants.Type.KEYBOARD, io.github.milkucha.momentum.input.LegacyKeyCodes.toScancode(legacyKey), category));
    }

    public static MomentumInputPolicy.Pedals pedals(boolean controllerBrake, boolean controllerDrift) {
        return MomentumInputPolicy.pedals(brake != null && brake.isDown(), drift != null && drift.isDown(),
                sameBinding(brake, drift), controllerBrake, controllerDrift);
    }

    public static boolean sameBinding(KeyMapping first, KeyMapping second) {
        if (first == null || second == null || first.isUnbound() || second.isUnbound()) return false;
        return KeyMappingHelper.getBoundKeyOf(first).equals(KeyMappingHelper.getBoundKeyOf(second));
    }

    public static Component brakeBinding() { return binding(brake); }
    public static Component driftBinding() { return binding(drift); }
    private static Component binding(KeyMapping mapping) {
        return mapping == null ? InputConstants.UNKNOWN.getDisplayName() : mapping.getTranslatedKeyMessage();
    }
}
