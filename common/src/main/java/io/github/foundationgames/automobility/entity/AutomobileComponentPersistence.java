package io.github.foundationgames.automobility.entity;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

final class AutomobileComponentPersistence {
    private AutomobileComponentPersistence() {
    }

    static @Nullable Identifier idToSave(Holder<?> component, @Nullable Identifier unresolvedId) {
        return component.unwrapKey().map(key -> key.identifier()).orElse(unresolvedId);
    }

    static @Nullable CompoundTag unresolvedAttachmentData(CompoundTag data, boolean typeResolved) {
        var typeId = data.getStringOr("type", "");
        if (typeResolved || typeId.isBlank() || Identifier.tryParse(typeId) == null) {
            return null;
        }

        return data.copy();
    }

    static CompoundTag attachmentDataToSave(CompoundTag currentData, @Nullable CompoundTag unresolvedData) {
        return (unresolvedData != null ? unresolvedData : currentData).copy();
    }
}
