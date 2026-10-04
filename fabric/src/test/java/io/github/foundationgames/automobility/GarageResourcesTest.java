package io.github.foundationgames.automobility;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.foundationgames.automobility.entity.GarageService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Resource contracts only; these checks do not claim native-screen or player acceptance. */
class GarageResourcesTest {
    @Test
    void blockAndItemResolveToTheSameVanillaTexturedGarageModel() throws IOException {
        var states = read("assets/automobility/blockstates/garage.json");
        assertEquals(Set.of(""), states.getAsJsonObject("variants").keySet());
        assertEquals("automobility:block/garage", states.getAsJsonObject("variants")
                .getAsJsonObject("").get("model").getAsString());

        var block = read("assets/automobility/models/block/garage.json");
        assertEquals("minecraft:block/cube_bottom_top", block.get("parent").getAsString());
        var textures = block.getAsJsonObject("textures");
        assertEquals(Set.of("side", "top", "bottom"), textures.keySet());
        assertEquals("minecraft:block/iron_block", textures.get("side").getAsString());
        assertEquals("minecraft:block/smithing_table_top", textures.get("top").getAsString());
        assertEquals("minecraft:block/smooth_stone", textures.get("bottom").getAsString());

        assertEquals("automobility:block/garage", read("assets/automobility/models/item/garage.json")
                .get("parent").getAsString());
        var item = read("assets/automobility/items/garage.json").getAsJsonObject("model");
        assertEquals("minecraft:model", item.get("type").getAsString());
        assertEquals("automobility:item/garage", item.get("model").getAsString());
    }

    @Test
    void recipeUpgradesTheExistingMechanicTableWithoutOptionalModIngredients() throws IOException {
        var recipe = read("data/automobility/recipe/garage.json");
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        var pattern = recipe.getAsJsonArray("pattern");
        assertEquals(3, pattern.size());
        assertEquals("I I", pattern.get(0).getAsString());
        assertEquals("IMI", pattern.get(1).getAsString());
        assertEquals("SSS", pattern.get(2).getAsString());
        var key = recipe.getAsJsonObject("key");
        assertEquals(Set.of("I", "M", "S"), key.keySet());
        assertEquals("minecraft:iron_ingot", key.get("I").getAsString());
        assertEquals("automobility:auto_mechanic_table", key.get("M").getAsString());
        assertEquals("minecraft:smooth_stone", key.get("S").getAsString());
        assertEquals("automobility:garage", recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());
    }

    @Test
    void garageDropsItselfAndAddsToThePickaxeTagWithoutReplacingOtherBlocks() throws IOException {
        var loot = read("data/automobility/loot_table/blocks/garage.json");
        assertEquals("minecraft:block", loot.get("type").getAsString());
        var pools = loot.getAsJsonArray("pools");
        assertEquals(1, pools.size());
        var pool = pools.get(0).getAsJsonObject();
        assertEquals(1, pool.get("rolls").getAsInt());
        assertEquals(1, pool.getAsJsonArray("entries").size());
        var entry = pool.getAsJsonArray("entries").get(0).getAsJsonObject();
        assertEquals("minecraft:item", entry.get("type").getAsString());
        assertEquals("automobility:garage", entry.get("name").getAsString());
        assertEquals(1, pool.getAsJsonArray("conditions").size());
        assertEquals("minecraft:survives_explosion", pool.getAsJsonArray("conditions").get(0)
                .getAsJsonObject().get("condition").getAsString());

        var tag = read("data/minecraft/tags/block/mineable/pickaxe.json");
        assertFalse(tag.get("replace").getAsBoolean());
        int garageEntries = 0;
        boolean mechanicTablePreserved = false;
        for (var value : tag.getAsJsonArray("values")) {
            if ("automobility:garage".equals(value.getAsString())) garageEntries++;
            if ("automobility:auto_mechanic_table".equals(value.getAsString())) mechanicTablePreserved = true;
        }
        assertEquals(1, garageEntries);
        assertTrue(mechanicTablePreserved);
    }

    @Test
    void everyServerStatusAndGarageInteractionHasEnglishAndCzechText() throws IOException {
        for (var locale : new String[] {"en_us", "cs_cz"}) {
            var language = read("assets/automobility/lang/" + locale + ".json");
            for (var status : GarageService.Status.values()) {
                assertText(language, "garage.automobility.status." + status.name().toLowerCase(Locale.ROOT));
            }
            assertText(language, "block.automobility.garage");
            for (var key : new String[] {"replace", "frame", "wheels", "engine", "base_estimates", "current",
                    "preview", "stat.acceleration", "stat.speed", "stat.handling", "stat.grip", "input", "output",
                    "required", "estimate_help", "no_vehicle", "multiple_vehicles"}) {
                assertText(language, "garage.automobility." + key);
            }
            assertTrue(language.get("garage.automobility.required").getAsString().contains("%s"));
            assertTrue(language.get("garage.automobility.status.missing_parts").getAsString().contains("%s"));
        }
    }

    private static void assertText(JsonObject language, String key) {
        assertTrue(language.has(key), "Missing garage translation: " + key);
        assertFalse(language.get(key).getAsString().isBlank(), "Blank garage translation: " + key);
    }

    private static JsonObject read(String relative) throws IOException {
        var current = Path.of("").toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("common/src/main/resources").resolve(relative);
            if (Files.isRegularFile(candidate)) {
                try (var reader = Files.newBufferedReader(candidate)) {
                    return JsonParser.parseReader(reader).getAsJsonObject();
                }
            }
            current = current.getParent();
        }
        return fail("Could not locate garage resource: " + relative);
    }
}
