package io.github.milkucha.momentum.qa;

import io.github.milkucha.momentum.MomentumControllerInput;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Opt-in, packaged-client registration/resource probe. Never drives or fakes a controller. */
public final class ControlifyIntegrationQa implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("MomentumControlifyIntegrationQa");
    static final Set<String> CORE_IDS = Set.of("automobility:accelerate_automobile",
            "automobility:brake_automobile", "automobility:drift_automobile");
    static final Set<String> MOMENTUM_IDS = Set.of("momentum:cruise_control", "momentum:cruise_increase",
            "momentum:cruise_decrease", "momentum:cruise_resume");
    static final Set<String> EXPECTED_IDS;
    static {
        Set<String> ids = new HashSet<>(CORE_IDS);
        ids.addAll(MOMENTUM_IDS);
        EXPECTED_IDS = Set.copyOf(ids);
    }
    private long started;
    private static final int STABLE_TITLE_TICKS = 40;
    private int readyTicks;
    private int exitReadyTicks;
    private int checks;
    private boolean running;
    private boolean finished;
    private boolean reloadInProgress;
    private boolean exitPending;
    private String family;

    public static boolean enabled() {
        return Boolean.getBoolean("automobility.qa.controlify")
                || "1".equals(System.getenv("MOMENTUM_CONTROLIFY_QA"));
    }

    @Override
    public void onInitializeClient() {
        if (!enabled()) {
            LOG.info("CONTROLIFY_INTEGRATION_QA_DISABLED explicit opt-in required");
            return;
        }
        Path directory = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
        Path allowed = Path.of("C:/Users/david/Automobility-36-Parity-QA-20260926").toAbsolutePath().normalize();
        if (!directory.startsWith(allowed) || !directory.toString().contains("runs")) {
            throw new IllegalStateException("Controlify integration QA refuses a non-isolated game directory");
        }
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            started = System.nanoTime();
            family = detectFamily();
            LOG.info("CONTROLIFY_INTEGRATION_QA_STARTED family={} directory={} physicalController=NOT_PROVEN", family, directory);
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        if (started == 0) return;
        if (finished) {
            exitWhenIdle(client);
            return;
        }
        if (System.nanoTime() - started > TimeUnit.SECONDS.toNanos(120)) {
            fail(client, new IllegalStateException("120-second QA deadline exceeded; family=" + family + " running=" + running));
            return;
        }
        if (running) return;
        // CLIENT_STARTED precedes completion of initial resource/model loading. A pass
        // (especially NONE) must not stop native/resource workers during that startup.
        if (!titleReady(client)) {
            readyTicks = 0;
            return;
        }
        if (++readyTicks < STABLE_TITLE_TICKS) return;
        try {
            String expected = System.getProperty("automobility.qa.controlify.expected", family);
            require(expected.equalsIgnoreCase(family), "Expected family " + expected + ", loaded " + family);
            if (family.equals("MODERN") && !Boolean.TRUE.equals(invokeModern("resourcesReady"))) return;
            running = true;
            LOG.info("CONTROLIFY_INTEGRATION_QA_TITLE_READY family={} stableTicks={} overlay=false", family, readyTicks);
            checks += commonChecks();
            if (!family.equals("MODERN")) {
                pass(client, 0);
                return;
            }
            checks += (Integer) invokeModern("verify", "initial");
            // The application thread must remain free to apply the asynchronous reload.
            reloadInProgress = true;
            client.reloadResourcePacks().whenComplete((ignored, error) -> client.execute(() -> {
                reloadInProgress = false;
                if (finished) return;
                try {
                    if (error != null) throw new IllegalStateException("Resource reload failed", error);
                    checks += commonChecks();
                    checks += (Integer) invokeModern("verify", "reloaded");
                    pass(client, 2);
                } catch (Throwable failure) {
                    fail(client, failure);
                }
            }));
        } catch (Throwable failure) {
            fail(client, failure);
        }
    }

    private int commonChecks() throws ReflectiveOperationException {
        if (family.equals("NONE")) {
            require(!FabricLoader.getInstance().isModLoaded("controlify"), "Controlify unexpectedly loaded");
            require(MomentumControllerInput.poll().equals(MomentumControllerInput.none()), "No-Controlify bridge emitted actions");
            return 2;
        }
        require(!family.equals("UNSUPPORTED"), "Unsupported Controlify family cannot pass QA");
        Class<?> core = Class.forName("io.github.foundationgames.automobility.fabric.controller.controlify.ControlifyCompat");
        Class<?> momentum = Class.forName("io.github.milkucha.momentum.controlify.MomentumControlifyCompat");
        require(Boolean.TRUE.equals(core.getMethod("isInitialized").invoke(null)), "Core facade did not complete");
        require(Boolean.TRUE.equals(momentum.getMethod("isInitialized").invoke(null)), "Momentum facade did not complete");

        Class<?> bindApi = Class.forName("dev.isxander.controlify.api.bind.ControlifyBindApi");
        Object api = bindApi.getMethod("get").invoke(null);
        List<String> actual;
        try (Stream<?> ids = (Stream<?>) bindApi.getMethod("getAllBindIds").invoke(api)) {
            actual = ids.map(Object::toString).filter(EXPECTED_IDS::contains).toList();
        }
        require(actual.size() == 7 && Set.copyOf(actual).equals(EXPECTED_IDS), "Expected seven unique binding IDs: " + actual);
        Class<?> supplier = Class.forName("dev.isxander.controlify.api.bind.InputBindingSupplier");
        Set<String> coreIds = new HashSet<>();
        for (Object binding : (Set<?>) core.getField("AUTOMOBILITY_BINDINGS").get(null)) {
            coreIds.add(supplier.getMethod("bindId").invoke(binding).toString());
        }
        require(coreIds.equals(CORE_IDS), "Core published supplier set is incomplete: " + coreIds);
        Class<?> controller = Class.forName("io.github.foundationgames.automobility.fabric.controller.controlify.ControlifyController");
        for (String fieldName : List.of("accelerateBinding", "brakeBinding", "driftBinding")) {
            Field field = controller.getDeclaredField(fieldName);
            field.setAccessible(true);
            require(field.get(null) != null, "Core controller supplier is null: " + fieldName);
        }
        Field source = MomentumControllerInput.class.getDeclaredField("source");
        source.setAccessible(true);
        String sourceType = source.get(null).getClass().getName();
        String adapter = family.equals("MODERN") ? "ModernMomentumControlifyAdapter" : "LegacyMomentumControlifyAdapter";
        require(sourceType.contains(adapter), "Momentum bridge is not supplied by the completed adapter: " + sourceType);
        LOG.info("CONTROLIFY_INTEGRATION_QA_REGISTRATIONS family={} ids={} source={} facadeCompletion=true coreSuppliersReady=true",
                family, actual, sourceType);
        return 8;
    }

    private static String detectFamily() {
        if (!FabricLoader.getInstance().isModLoaded("controlify")) return "NONE";
        ClassLoader loader = ControlifyIntegrationQa.class.getClassLoader();
        try {
            Class.forName("dev.isxander.controlify.api.contextual.ContextualDomainRegistry", false, loader);
            return "MODERN";
        } catch (ClassNotFoundException ignored) {
            try {
                Class.forName("dev.isxander.controlify.api.guide.Fact", false, loader);
                return "LEGACY";
            } catch (ClassNotFoundException absent) {
                return "UNSUPPORTED";
            }
        }
    }

    private static Object invokeModern(String methodName, String... arguments) throws ReflectiveOperationException {
        Class<?> modern = Class.forName("io.github.milkucha.momentum.qa.ModernChecks", true,
                ControlifyIntegrationQa.class.getClassLoader());
        var method = arguments.length == 0 ? modern.getDeclaredMethod(methodName)
                : modern.getDeclaredMethod(methodName, String.class);
        method.setAccessible(true);
        return method.invoke(null, (Object[]) arguments);
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private void pass(Minecraft client, int reloadPasses) {
        finished = true;
        LOG.info("CONTROLIFY_INTEGRATION_QA_PASS family={} checks={} bindings={} guides={} radial={} reloadPasses={} physicalController=NOT_PROVEN",
                family, checks, family.equals("NONE") ? 0 : 7,
                family.equals("MODERN") ? "7" : "NOT_CHECKED", family.equals("MODERN") ? "4" : "NOT_CHECKED", reloadPasses);
        maybeExit(client);
    }

    private void fail(Minecraft client, Throwable error) {
        finished = true;
        if (error instanceof InvocationTargetException invocation && invocation.getCause() != null) error = invocation.getCause();
        LOG.error("CONTROLIFY_INTEGRATION_QA_FAILED family=" + family + " checks=" + checks, error);
        maybeExit(client);
    }

    private boolean titleReady(Minecraft client) {
        return !reloadInProgress && client.level == null
                && client.gui.screen() instanceof TitleScreen && client.gui.overlay() == null;
    }

    private void maybeExit(Minecraft client) {
        exitPending = Boolean.getBoolean("automobility.qa.controlify.exit");
        exitReadyTicks = 0;
    }

    private void exitWhenIdle(Minecraft client) {
        if (!exitPending) return;
        // Even a failed deadline must not shut the client down mid-reload. The isolated
        // process runner owns its outer timeout if a loading screen never settles.
        if (!titleReady(client)) {
            exitReadyTicks = 0;
            return;
        }
        if (++exitReadyTicks < STABLE_TITLE_TICKS) return;
        exitPending = false;
        LOG.info("CONTROLIFY_INTEGRATION_QA_IDLE_EXIT family={} stableTicks={} overlay=false", family, exitReadyTicks);
        client.stop();
    }
}
