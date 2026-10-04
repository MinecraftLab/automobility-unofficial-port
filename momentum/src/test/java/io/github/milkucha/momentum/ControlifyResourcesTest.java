package io.github.milkucha.momentum;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

final class ControlifyResourcesTest {
    private static final Set<String> BINDINGS = Set.of("momentum:cruise_control", "momentum:cruise_increase",
            "momentum:cruise_decrease", "momentum:cruise_resume");

    @Test
    void modernGuideRulesPreserveContextAndAvailability() throws Exception {
        var root = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/controlify/contextual/guide/in_game.json"))).getAsJsonObject();
        assertFalse(root.has("replace") && root.get("replace").getAsBoolean());
        Set<String> actual = new TreeSet<>();
        for (var entry : root.getAsJsonArray("rules")) {
            var rule = entry.getAsJsonObject();
            String binding = rule.get("for").getAsString();
            assertTrue(actual.add(binding));
            assertEquals("right", rule.get("where").getAsString());
            assertEquals("key.momentum." + binding.substring(binding.indexOf(':') + 1),
                    rule.getAsJsonObject("then").get("translate").getAsString());
            if (binding.equals("momentum:cruise_control")) {
                assertEquals("momentum:driving_automobile", rule.get("if").getAsString());
            } else {
                Set<String> conditions = new TreeSet<>();
                rule.getAsJsonObject("if").getAsJsonArray("all_of").forEach(fact -> conditions.add(fact.getAsString()));
                assertEquals(Set.of("momentum:driving_automobile", binding.endsWith("resume")
                        ? "momentum:cruise_resumable" : "momentum:cruise_active"), conditions);
            }
        }
        assertEquals(BINDINGS, actual);
    }

    @Test
    void radialIconsUseBindingKeysAndExactlyOneCodecSupportedContent() throws Exception {
        var icons = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/controlify/radial_icons.json"))).getAsJsonObject();
        assertEquals(BINDINGS, icons.keySet());
        Set<String> models = new TreeSet<>();
        for (String binding : BINDINGS) {
            var icon = icons.getAsJsonObject(binding);
            assertEquals(Set.of("model"), icon.keySet());
            assertTrue(icon.get("model").getAsString().startsWith("minecraft:"));
            models.add(icon.get("model").getAsString());
        }
        assertEquals(4, models.size(), "Every cruise action needs a distinct usable icon");
    }

    @Test
    void stableFacadeDoesNotLinkEitherGuideGeneration() throws Exception {
        try (var stream = getClass().getResourceAsStream("controlify/MomentumControlifyCompat.class")) {
            assertNotNull(stream);
            String constants = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertFalse(constants.contains("dev/isxander/controlify/api/guide/"));
            assertFalse(constants.contains("dev/isxander/controlify/api/contextual/"));
        }
    }
}
