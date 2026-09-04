package io.github.foundationgames.automobility.entity;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

final class AutomobileComponentPersistenceTest {
    @Test
    void unresolvedIdSurvivesWhileTheRuntimeUsesAnEmptyFallback() {
        var missingId = Identifier.fromNamespaceAndPath("removed_datapack", "frame");

        assertEquals(missingId, AutomobileComponentPersistence.idToSave(Holder.direct("fallback"), missingId));
    }

    @Test
    void directFallbackWithoutAnUnresolvedIdStaysAbsent() {
        assertNull(AutomobileComponentPersistence.idToSave(Holder.direct("fallback"), null));
    }

    @Test
    void unresolvedAttachmentDataSurvivesAsAnOpaqueCopy() {
        var item = new CompoundTag();
        item.putString("id", "minecraft:diamond");
        var items = new ListTag();
        items.add(item);
        var unresolved = new CompoundTag();
        unresolved.putString("type", "example_addon:cargo_attachment");
        unresolved.put("Items", items);

        var preserved = AutomobileComponentPersistence.unresolvedAttachmentData(unresolved, false);
        assertEquals(unresolved, preserved);
        assertNotSame(unresolved, preserved);

        unresolved.putString("type", "example_addon:changed_after_read");
        assertEquals("example_addon:cargo_attachment", preserved.getStringOr("type", ""));

        var current = new CompoundTag();
        current.putString("type", "automobility:empty");
        assertEquals(preserved, AutomobileComponentPersistence.attachmentDataToSave(current, preserved));
    }

    @Test
    void resolvedOrMissingAttachmentTypeDoesNotShadowRuntimeData() {
        var known = new CompoundTag();
        known.putString("type", "automobility:chest");
        assertNull(AutomobileComponentPersistence.unresolvedAttachmentData(known, true));

        var missingType = new CompoundTag();
        assertNull(AutomobileComponentPersistence.unresolvedAttachmentData(missingType, false));

        var current = new CompoundTag();
        current.putString("type", "automobility:empty");
        var saved = AutomobileComponentPersistence.attachmentDataToSave(current, null);
        assertEquals(current, saved);
        assertNotSame(current, saved);
    }
}
