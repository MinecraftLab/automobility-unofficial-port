package io.github.foundationgames.automobility;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Translation resource contracts; not native font, rendering or linguistic acceptance. */
class GarageLocalizationTest {
    private static final List<String> LOCALES = List.of(
            "en_us", "cs_cz", "de_de", "es_es", "fr_fr", "hi_in", "it_it", "ja_jp",
            "ko_kr", "pl_pl", "pt_br", "ru_ru", "uk_ua", "ur_pk", "vi_vn", "zh_cn", "zh_tw");
    private static final Set<String> ARGUMENT_KEYS = Set.of(
            "garage.automobility.required", "garage.automobility.status.missing_parts");

    @Test
    void everySupportedLocaleHasExactlyTheCompleteGarageContract() throws IOException {
        var english = garage(readLanguage("en_us"));
        assertEquals(28, english.size(), "Review the expected Garage contract when adding text");
        for (var locale : LOCALES) {
            var actual = garage(readLanguage(locale));
            assertEquals(english.keySet(), actual.keySet(), locale + ": missing or extra Garage keys");
            for (var entry : actual.entrySet()) {
                var value = entry.getValue();
                assertFalse(value.isBlank(), locale + ": blank " + entry.getKey());
                assertFalse(value.contains("\uFFFD"), locale + ": replacement character " + entry.getKey());
                assertTrue(value.codePoints().noneMatch(cp -> Character.isISOControl(cp)
                                || (cp >= 0x202A && cp <= 0x202E) || (cp >= 0x2066 && cp <= 0x2069)),
                        locale + ": unexpected control character " + entry.getKey());
            }
        }
    }

    @Test
    void garageArgumentsKeepTheMinecraftStringPlaceholderContract() throws IOException {
        for (var locale : LOCALES) {
            for (var entry : garage(readLanguage(locale)).entrySet()) {
                var value = entry.getValue();
                int expected = ARGUMENT_KEYS.contains(entry.getKey()) ? 1 : 0;
                assertEquals(expected, (value.length() - value.replace("%s", "").length()) / 2,
                        locale + ": wrong argument count " + entry.getKey());
                assertFalse(value.replace("%s", "").contains("%"),
                        locale + ": unsupported format token " + entry.getKey());
                var formatted = value.replace("%s", "4");
                assertFalse(formatted.contains("%s"), locale + ": unresolved argument");
            }
        }
    }

    @Test
    void nonEnglishLocalesDoNotFallBackToAnEnglishGarageBlock() throws IOException {
        var english = garage(readLanguage("en_us"));
        for (var locale : LOCALES) {
            if (locale.equals("en_us")) continue;
            var actual = garage(readLanguage(locale));
            long localized = english.entrySet().stream()
                    .filter(entry -> !entry.getValue().equals(actual.get(entry.getKey()))).count();
            // Individual shared words can legitimately match English; an untranslated block cannot.
            assertTrue(localized >= 20, locale + ": most Garage strings are English copies");
            assertTrue(actual.get("garage.automobility.estimate_help").contains("Momentum"),
                    locale + ": preserve the optional mod's proper name");
        }
    }

    private static Map<String, String> garage(Map<String, String> language) {
        return language.entrySet().stream().filter(entry -> entry.getKey().equals("block.automobility.garage")
                        || entry.getKey().startsWith("garage.automobility."))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Map<String, String> readLanguage(String locale) throws IOException {
        var directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            var path = directory.resolve("common/src/main/resources/assets/automobility/lang/" + locale + ".json");
            if (Files.isRegularFile(path)) {
                var result = new LinkedHashMap<String, String>();
                try (var input = new JsonReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
                    input.beginObject();
                    while (input.hasNext()) {
                        var key = input.nextName();
                        assertEquals(JsonToken.STRING, input.peek(), locale + ": non-string value " + key);
                        assertFalse(result.containsKey(key), locale + ": duplicate key " + key);
                        result.put(key, input.nextString());
                    }
                    input.endObject();
                    assertEquals(JsonToken.END_DOCUMENT, input.peek(), locale + ": trailing JSON");
                }
                return result;
            }
            directory = directory.getParent();
        }
        return fail("Cannot locate language file: " + locale);
    }
}
