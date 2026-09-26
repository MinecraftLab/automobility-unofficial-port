package io.github.milkucha.momentum.controlify;

import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.entrypoint.ControlifyEntrypoint;
import dev.isxander.controlify.api.entrypoint.InitContext;
import dev.isxander.controlify.api.entrypoint.PreInitContext;
import io.github.foundationgames.automobility.fabric.controller.controlify.ControlifyApiFamily;
import io.github.milkucha.momentum.Momentum;
import io.github.milkucha.momentum.MomentumControllerInput;

/** Stable optional entrypoint; version-specific APIs remain in lazily loaded delegates. */
public final class MomentumControlifyCompat implements ControlifyEntrypoint {
    private static boolean initialized;

    public static boolean isInitialized() {
        return initialized;
    }

    @Override
    public void onControlifyPreInit(PreInitContext context) {
        initialized = false;
        MomentumControllerInput.install(MomentumControllerInput::none);
        var family = ControlifyApiFamily.detect(getClass().getClassLoader());
        if (family == ControlifyApiFamily.UNSUPPORTED) {
            Momentum.LOGGER.warn("Unsupported Controlify public API; Momentum controller integration disabled. Keyboard controls remain available.");
            return;
        }

        ControlifyEntrypoint adapter;
        if (family == ControlifyApiFamily.MODERN) {
            adapter = new ModernMomentumControlifyAdapter();
        } else {
            adapter = ControlifyApiFamily.createLegacyAdapter(
                    "io.github.milkucha.momentum.controlify.LegacyMomentumControlifyAdapter",
                    getClass().getClassLoader(), ControlifyEntrypoint.class);
        }
        // Do not convert a recognized-family regression into a false successful initialization.
        adapter.onControlifyPreInit(context);
        initialized = true;
        Momentum.LOGGER.info("MOMENTUM_CONTROLIFY_READY family={} bindings=4", family);
    }

    @Override
    public void onControlifyInit(InitContext context) {
    }

    @Override
    public void onControllersDiscovered(ControlifyApi controlify) {
    }
}
