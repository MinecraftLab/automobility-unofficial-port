package io.github.foundationgames.automobility.fabric.controller.controlify;

import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.bind.InputBindingSupplier;
import dev.isxander.controlify.api.entrypoint.ControlifyEntrypoint;
import dev.isxander.controlify.api.entrypoint.InitContext;
import dev.isxander.controlify.api.entrypoint.PreInitContext;
import io.github.foundationgames.automobility.Automobility;

import java.util.HashSet;
import java.util.Set;

/** Stable entrypoint: never resolves a version-specific guide class before API detection. */
public class ControlifyCompat implements ControlifyEntrypoint {
    public static final Set<InputBindingSupplier> AUTOMOBILITY_BINDINGS = new HashSet<>();
    private static boolean initialized;

    public static boolean isInitialized() {
        return initialized;
    }

    @Override
    public void onControlifyPreInit(PreInitContext context) {
        initialized = false;
        AUTOMOBILITY_BINDINGS.clear();
        ControlifyController.clearBindings();
        var family = ControlifyApiFamily.detect(getClass().getClassLoader());
        if (family == ControlifyApiFamily.UNSUPPORTED) {
            Automobility.LOG.warn("Unsupported Controlify public API; Automobility controller integration disabled. Keyboard controls remain available.");
            return;
        }

        ControlifyEntrypoint adapter;
        if (family == ControlifyApiFamily.MODERN) {
            adapter = new ModernControlifyAdapter();
        } else {
            adapter = ControlifyApiFamily.createLegacyAdapter(
                    "io.github.foundationgames.automobility.fabric.controller.controlify.LegacyControlifyCompat",
                    getClass().getClassLoader(), ControlifyEntrypoint.class);
        }
        // Recognized-family failures deliberately propagate to Controlify's error log.
        adapter.onControlifyPreInit(context);
        initialized = true;
        Automobility.LOG.info("AUTOMOBILITY_CONTROLIFY_READY family={} bindings={}", family, AUTOMOBILITY_BINDINGS.size());
    }

    @Override
    public void onControlifyInit(InitContext context) {
    }

    @Override
    public void onControllersDiscovered(ControlifyApi controlify) {
    }
}
