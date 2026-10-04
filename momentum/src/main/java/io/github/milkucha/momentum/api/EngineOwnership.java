package io.github.milkucha.momentum.api;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;

/** Registry keys, never component values or model identifiers, identify an owned engine. */
final class EngineOwnership {
    private final Set<ResourceKey<?>> engines = ConcurrentHashMap.newKeySet();

    void register(ResourceKey<?> engine) {
        engines.add(Objects.requireNonNull(engine, "engine"));
    }

    boolean owns(Holder<?> engine) {
        return engine != null && engine.unwrapKey().map(this::ownsKey).orElse(false);
    }

    boolean ownsKey(ResourceKey<?> engine) {
        return engine != null && engines.contains(engine);
    }
}
