package io.github.foundationgames.automobility.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

final class AutomobilityBlockItemTranslationTest {
    @Test
    void registeredBlockItemsUseTheExistingBlockTranslationNamespace() throws ReflectiveOperationException {
        var itemKey = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath("automobility", "automobile_assembler"));
        var effectiveDescriptionId = Item.Properties.class.getDeclaredMethod("effectiveDescriptionId");
        effectiveDescriptionId.setAccessible(true);

        assertEquals("block.automobility.automobile_assembler",
                effectiveDescriptionId.invoke(AutomobilityBlockItems.properties(itemKey)));
    }

    @Test
    void everyBlockItemRegistrationUsesTheCentralPropertiesFactory() throws IOException {
        String source = Files.readString(findBlocksSource());
        assertFalse(source.contains("new Item.Properties().setId(itemKey)"),
                "Block-item registrations must not bypass AutomobilityBlockItems");
        assertEquals(6, source.split("AutomobilityBlockItems\\.properties\\(itemKey\\)", -1).length - 1,
                "Update the block-item registration contract when Automobility content changes");
    }

    private static Path findBlocksSource() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(
                    "common/src/main/java/io/github/foundationgames/automobility/block/AutomobilityBlocks.java");
            if (Files.isRegularFile(candidate)) return candidate;
            current = current.getParent();
        }
        return fail("Could not locate AutomobilityBlocks.java");
    }
}
