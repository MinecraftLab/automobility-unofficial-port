package io.github.foundationgames.automobility.entity;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
