package io.github.foundationgames.automobility;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TranslationResourcesTest {
    private static final Pattern FORMAT_TOKEN = Pattern.compile("%(?:\\d+\\$)?[A-Za-z%]");
    private static final Set<String> EXPECTED_LOCALES = Set.of(
            "cs_cz", "de_de", "en_us", "es_es", "fr_fr", "hi_in", "it_it",
            "ja_jp", "ko_kr", "pl_pl", "pt_br", "ru_ru", "uk_ua", "ur_pk",
            "vi_vn", "zh_cn", "zh_tw");

    @Test
    void translationsMatchEnglishKeysAndPreserveFormatTokens() throws IOException {
        var langDir = findLanguageDirectory();
        var english = readJson(langDir.resolve("en_us.json"));

        try (var files = Files.list(langDir)) {
            var translations = files.filter(path -> path.toString().endsWith(".json")).sorted().toList();
            var actualLocales = translations.stream()
                    .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                    .collect(java.util.stream.Collectors.toSet());
            assertEquals(EXPECTED_LOCALES, actualLocales,
                    "Automobility must ship the complete Rosenvik locale set");

            for (var file : translations) {
                var translation = readJson(file);
                var locale = file.getFileName().toString();

                var missing = new TreeSet<>(english.keySet());
                missing.removeAll(translation.keySet());
                var unexpected = new TreeSet<>(translation.keySet());
                unexpected.removeAll(english.keySet());
                assertTrue(missing.isEmpty() && unexpected.isEmpty(),
                        locale + " must match en_us.json keys; missing=" + missing + ", unexpected=" + unexpected);

                for (var key : translation.keySet()) {
                    var translated = translation.get(key).getAsString();
                    assertFalse(translated.isBlank(), locale + " contains a blank translation for " + key);
                    assertFalse(FORMAT_TOKEN.matcher(translated).replaceAll("").contains("%"),
                            locale + " contains an invalid format token for " + key);
                    var source = english.get(key).getAsString();
                    assertEquals(formatTokens(source), formatTokens(translated),
                            locale + " must preserve format tokens for " + key);
                }

                if (!locale.equals("en_us.json")) {
                    assertFalse(translation.equals(english),
                            locale + " must not be an exact copy of en_us.json");
                }
            }
        }
    }

    private static JsonObject readJson(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static List<String> formatTokens(String value) {
        return FORMAT_TOKEN.matcher(value).results()
                .map(result -> result.group().toLowerCase(Locale.ROOT))
                .sorted()
                .toList();
    }

    private static Path findLanguageDirectory() {
        var current = Path.of("").toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("common/src/main/resources/assets/automobility/lang");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return fail("Could not locate Automobility language resources");
    }
}
