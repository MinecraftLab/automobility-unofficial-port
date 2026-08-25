package io.github.milkucha.momentum;

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

final class MomentumTranslationResourcesTest {
    private static final Set<String> REQUIRED_LOCALES = Set.of(
            "cs_cz", "de_de", "en_us", "es_es", "fr_fr", "hi_in", "it_it",
            "ja_jp", "ko_kr", "pl_pl", "pt_br", "ru_ru", "uk_ua", "ur_pk",
            "vi_vn", "zh_cn", "zh_tw");
    private static final Pattern FORMAT_TOKEN = Pattern.compile("%(?:\\d+\\$)?[A-Za-z%]");

    @Test
    void canonicalLocalesAreCompleteAndSafe() throws IOException {
        Path langDir = findLanguageDirectory();
        JsonObject english = readJson(langDir.resolve("en_us.json"));

        Set<String> actualLocales = new TreeSet<>();
        try (var files = Files.list(langDir)) {
            files.filter(path -> path.toString().endsWith(".json"))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                    .forEach(actualLocales::add);
        }
        assertEquals(new TreeSet<>(REQUIRED_LOCALES), actualLocales,
                "Momentum must ship the canonical 17-locale set");

        for (String locale : actualLocales) {
            JsonObject translation = readJson(langDir.resolve(locale + ".json"));
            Set<String> missing = new TreeSet<>(english.keySet());
            missing.removeAll(translation.keySet());
            Set<String> unexpected = new TreeSet<>(translation.keySet());
            unexpected.removeAll(english.keySet());
            assertTrue(missing.isEmpty() && unexpected.isEmpty(),
                    locale + " must match en_us keys; missing=" + missing + ", unexpected=" + unexpected);

            boolean differsFromEnglish = false;
            for (String key : english.keySet()) {
                assertTrue(translation.get(key).isJsonPrimitive()
                                && translation.get(key).getAsJsonPrimitive().isString(),
                        locale + " must contain a string for " + key);
                String translated = translation.get(key).getAsString();
                assertFalse(translated.isBlank(), locale + " contains a blank value for " + key);
                assertFalse(FORMAT_TOKEN.matcher(translated).replaceAll("").contains("%"),
                        locale + " contains an invalid format token for " + key);
                String source = english.get(key).getAsString();
                assertEquals(formatTokens(source), formatTokens(translated),
                        locale + " must preserve format tokens for " + key);
                differsFromEnglish |= !source.equals(translated);
            }
            if (!locale.equals("en_us")) {
                assertTrue(differsFromEnglish, locale + " must not be a complete copy of en_us");
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
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(
                    "momentum/src/main/resources/assets/momentum/lang");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return fail("Could not locate Momentum language resources");
    }
}
