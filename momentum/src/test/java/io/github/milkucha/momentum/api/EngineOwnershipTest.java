package io.github.milkucha.momentum.api;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EngineOwnershipTest {
    private static final ResourceKey<Registry<String>> ENGINES = ResourceKey.createRegistryKey(
            Identifier.fromNamespaceAndPath("automobility", "automobile_engine"));
    private static final ResourceKey<Registry<String>> MODELS = ResourceKey.createRegistryKey(
            Identifier.fromNamespaceAndPath("example", "models"));
    private static final Identifier ELECTRIC = Identifier.fromNamespaceAndPath("example", "electric");

    @Test
    void onlyTheRegisteredEngineKeyOwnsDrive() {
        var ownership = new EngineOwnership();
        ownership.register(ResourceKey.create(ENGINES, ELECTRIC));
        assertTrue(ownership.ownsKey(ResourceKey.create(ENGINES, ELECTRIC)));
        assertFalse(ownership.ownsKey(ResourceKey.create(ENGINES,
                Identifier.fromNamespaceAndPath("example", "combustion"))));
        assertFalse(ownership.ownsKey(ResourceKey.create(MODELS, ELECTRIC)));
    }

    @Test
    void directValuesAndModelAliasesDoNotClaimOwnership() {
        var ownership = new EngineOwnership();
        ownership.register(ResourceKey.create(ENGINES, ELECTRIC));
        assertFalse(ownership.owns(Holder.direct(ELECTRIC.toString())));
        assertFalse(ownership.owns(null));
        assertFalse(ownership.ownsKey(null));
    }

    @Test
    void registrationIsIdempotentAndRejectsNull() {
        var ownership = new EngineOwnership();
        var key = ResourceKey.create(ENGINES, ELECTRIC);
        ownership.register(key);
        ownership.register(key);
        assertTrue(ownership.ownsKey(key));
        assertThrows(NullPointerException.class, () -> ownership.register(null));
    }
}
