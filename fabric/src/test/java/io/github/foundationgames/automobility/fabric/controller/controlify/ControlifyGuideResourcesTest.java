package io.github.foundationgames.automobility.fabric.controller.controlify;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

final class ControlifyGuideResourcesTest {
    @Test
    void coreRulesAddThreeStableDrivingBindingsWithoutReplacingVanilla() throws Exception {
        Path resources = Path.of("src/main/resources");
        var root = JsonParser.parseString(Files.readString(resources.resolve(
                "assets/controlify/contextual/guide/in_game.json"))).getAsJsonObject();
        assertFalse(root.has("replace") && root.get("replace").getAsBoolean());
        Set<String> actual = new TreeSet<>();
        for (var entry : root.getAsJsonArray("rules")) {
            var rule = entry.getAsJsonObject();
            String binding = rule.get("for").getAsString();
            assertTrue(actual.add(binding));
            assertEquals("left", rule.get("where").getAsString());
            assertEquals("automobility:driving_automobile", rule.get("if").getAsString());
            assertEquals("controlify.binding.automobility." + binding.substring(binding.indexOf(':') + 1),
                    rule.getAsJsonObject("then").get("translate").getAsString());
        }
        assertEquals(Set.of("automobility:accelerate_automobile", "automobility:brake_automobile",
                "automobility:drift_automobile"), actual);
    }

    @Test
    void stableFacadeBytecodeDoesNotLinkAnyLegacyGuideType() throws Exception {
        try (var stream = getClass().getResourceAsStream("ControlifyCompat.class")) {
            assertNotNull(stream);
            String constantPool = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertFalse(constantPool.contains("dev/isxander/controlify/api/guide/"));
            assertFalse(constantPool.contains("dev/isxander/controlify/api/contextual/"));
        }
    }
}
