package io.github.foundationgames.automobility.entity;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

final class AutomobileComponentPersistence {
    private AutomobileComponentPersistence() {
    }

    static @Nullable Identifier idToSave(Holder<?> component, @Nullable Identifier unresolvedId) {
        return component.unwrapKey().map(key -> key.identifier()).orElse(unresolvedId);
    }
}
