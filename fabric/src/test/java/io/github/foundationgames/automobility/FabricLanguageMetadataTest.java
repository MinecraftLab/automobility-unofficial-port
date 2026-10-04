package io.github.foundationgames.automobility;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.minecraft.SharedConstants;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.client.resources.metadata.language.LanguageMetadataSection;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/** Actual Minecraft metadata/resource loading only; not native glyph shaping or linguistic acceptance. */
@Isolated("LanguageManager changes Minecraft's global Language and older I18n state")
@Execution(ExecutionMode.SAME_THREAD)
class FabricLanguageMetadataTest {
    private static final String GARAGE_KEY = "garage.automobility.input";
    private static final String VANILLA_KEY = "container.inventory";
    @TempDir Path temporary;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void onlyFabricDeclaresUrduWithoutOverridingMinecraftResources() throws Exception {
        var source = sourceMetadata();
        var json = readJson(source);
        assertEquals(Set.of("pack", "language"), json.keySet());
        assertEquals(Set.of("description", "min_format", "max_format"), json.getAsJsonObject("pack").keySet());
        assertEquals("automobility", json.getAsJsonObject("pack").get("description").getAsString());
        assertEquals(Set.of("ur_pk"), json.getAsJsonObject("language").keySet());
        var urdu = json.getAsJsonObject("language").getAsJsonObject("ur_pk");
        assertEquals(Set.of("name", "region", "bidirectional"), urdu.keySet());
        assertEquals("اردو", urdu.get("name").getAsString());
        assertEquals("پاکستان", urdu.get("region").getAsString());
        assertTrue(urdu.getAsJsonPrimitive("bidirectional").isBoolean());
        assertTrue(urdu.get("bidirectional").getAsBoolean());
        try (var input = Files.newInputStream(source)) {
            var section = ResourceMetadata.fromJsonStream(input).getSection(LanguageMetadataSection.TYPE).orElseThrow();
            assertEquals(Set.of("ur_pk"), section.languages().keySet());
            assertEquals("اردو", section.languages().get("ur_pk").name());
            assertEquals("پاکستان", section.languages().get("ur_pk").region());
            assertTrue(section.languages().get("ur_pk").bidirectional());
        }
        assertFalse(Files.exists(repository().resolve("common/src/main/resources/pack.mcmeta")));
        for (var root : List.of("common/src/main/resources", "fabric/src/main/resources")) {
            assertFalse(Files.exists(repository().resolve(root).resolve("assets/minecraft")));
        }
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(processedResources().resolve("pack.mcmeta")));
    }

    @Test
    void realClientAndServerMetadataCodecsAcceptBothCurrentPackFormats() throws Exception {
        var version = SharedConstants.getCurrentVersion();
        var client = version.packVersion(PackType.CLIENT_RESOURCES);
        var data = version.packVersion(PackType.SERVER_DATA);
        var minimum = client.compareTo(data) <= 0 ? client : data;
        var maximum = client.compareTo(data) >= 0 ? client : data;
        for (var type : PackType.values()) {
            try (var input = Files.newInputStream(sourceMetadata())) {
                var section = ResourceMetadata.fromJsonStream(input).getSection(PackMetadataSection.forPackType(type)).orElseThrow();
                assertEquals("automobility", section.description().getString());
                assertEquals(minimum, section.supportedFormats().minInclusive());
                assertEquals(maximum, section.supportedFormats().maxInclusive());
                assertEquals(PackCompatibility.COMPATIBLE,
                        PackCompatibility.forVersion(section.supportedFormats(), version.packVersion(type)));
            }
        }
    }

    @Test
    void actualResourceManagerLoadsUrduRtlAndKeepsVanillaEnglishFallback() throws Exception {
        try (var globals = GlobalLanguageState.capture(); var fixture = fixture(true)) {
            var result = new AtomicReference<ClientLanguage>();
            var manager = manager("ur_pk", result::set);
            var loaded = reload(manager, result, fixture.resources());
            assertEquals("ur_pk", manager.getSelected());
            assertNotNull(manager.getLanguage("ur_pk"));
            assertTrue(manager.getLanguage("ur_pk").bidirectional());
            assertTrue(loaded.isDefaultRightToLeft());
            assertEquals(fixture.urdu().get(GARAGE_KEY).getAsString(), loaded.getOrDefault(GARAGE_KEY));
            assertEquals(fixture.vanillaEnglish().get(VANILLA_KEY).getAsString(), loaded.getOrDefault(VANILLA_KEY));
            assertEquals(loaded.getOrDefault(GARAGE_KEY), I18n.get(GARAGE_KEY));
            assertEquals(loaded.getOrDefault(VANILLA_KEY), I18n.get(VANILLA_KEY));
        }
    }

    @Test
    void selectingEnglishAgainRestoresLtrAndDoesNotKeepUrduText() throws Exception {
        try (var globals = GlobalLanguageState.capture(); var fixture = fixture(true)) {
            var result = new AtomicReference<ClientLanguage>();
            var manager = manager("ur_pk", result::set);
            assertTrue(reload(manager, result, fixture.resources()).isDefaultRightToLeft());
            manager.setSelected("en_us");
            var english = reload(manager, result, fixture.resources());
            assertEquals("en_us", manager.getSelected());
            assertFalse(english.isDefaultRightToLeft());
            assertEquals(fixture.modEnglish().get(GARAGE_KEY).getAsString(), english.getOrDefault(GARAGE_KEY));
            assertNotEquals(fixture.urdu().get(GARAGE_KEY).getAsString(), english.getOrDefault(GARAGE_KEY));
            assertEquals(english.getOrDefault(GARAGE_KEY), I18n.get(GARAGE_KEY));
            assertEquals(fixture.vanillaEnglish().get(VANILLA_KEY).getAsString(), english.getOrDefault(VANILLA_KEY));
        }
    }

    @Test
    void anUrduFileWithoutLanguageMetadataDoesNotBecomeSelectable() throws Exception {
        try (var globals = GlobalLanguageState.capture(); var fixture = fixture(false)) {
            assertTrue(Files.isRegularFile(fixture.modRoot().resolve("assets/automobility/lang/ur_pk.json")));
            var result = new AtomicReference<ClientLanguage>();
            var manager = manager("ur_pk", result::set);
            var fallback = reload(manager, result, fixture.resources());
            assertEquals("ur_pk", manager.getSelected(), "The requested code remains selected even though it is undeclared");
            assertNull(manager.getLanguage("ur_pk"));
            assertFalse(manager.getLanguages().containsKey("ur_pk"));
            assertFalse(fallback.isDefaultRightToLeft());
            assertEquals(fixture.modEnglish().get(GARAGE_KEY).getAsString(), fallback.getOrDefault(GARAGE_KEY));
            assertNotEquals(fixture.urdu().get(GARAGE_KEY).getAsString(), fallback.getOrDefault(GARAGE_KEY));
            assertEquals(fixture.vanillaEnglish().get(VANILLA_KEY).getAsString(), fallback.getOrDefault(VANILLA_KEY));
        }
    }

    private static LanguageManager manager(String selected, Consumer<ClientLanguage> callback) {
        return new LanguageManager(selected, callback);
    }

    private static ClientLanguage reload(LanguageManager manager, AtomicReference<ClientLanguage> result,
                                         MultiPackResourceManager resources) {
        var previous = Language.getInstance();
        result.set(null);
        manager.onResourceManagerReload(resources);
        var loaded = result.get();
        assertNotNull(loaded, "Actual successful reload callback required; logged/swallowed failure is not PASS");
        assertNotSame(previous, loaded, "Each reload must deliver fresh selected-language state");
        assertSame(loaded, Language.getInstance());
        return loaded;
    }

    private Fixture fixture(boolean declaration) throws Exception {
        var modRoot = temporary.resolve("automobility");
        var languages = modRoot.resolve("assets/automobility/lang");
        Files.createDirectories(languages);
        var metadata = readJson(processedResources().resolve("pack.mcmeta"));
        if (!declaration) metadata.remove("language");
        Files.writeString(modRoot.resolve("pack.mcmeta"), metadata.toString(), StandardCharsets.UTF_8);
        for (var locale : List.of("en_us", "ur_pk")) {
            var relative = "assets/automobility/lang/" + locale + ".json";
            var processed = processedResources().resolve(relative);
            assertArrayEquals(Files.readAllBytes(repository().resolve("common/src/main/resources").resolve(relative)),
                    Files.readAllBytes(processed));
            Files.copy(processed, languages.resolve(locale + ".json"));
        }
        // Exact English payload from the JAR that defined Minecraft Language, not arbitrary classpath pack order.
        var vanillaRoot = temporary.resolve("vanilla-english");
        var englishPath = vanillaRoot.resolve("assets/minecraft/lang/en_us.json");
        Files.createDirectories(englishPath.getParent());
        var minecraftJar = Path.of(Language.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertTrue(Files.isRegularFile(minecraftJar), "Expected the actual Minecraft classes JAR");
        try (var jar = new ZipFile(minecraftJar.toFile())) {
            var entry = jar.getEntry("assets/minecraft/lang/en_us.json");
            assertNotNull(entry, "Actual vanilla English resource required");
            try (var input = jar.getInputStream(entry)) { Files.copy(input, englishPath); }
        }
        var english = readJson(languages.resolve("en_us.json"));
        var urdu = readJson(languages.resolve("ur_pk.json"));
        var vanilla = readJson(englishPath);
        assertFalse(english.has(VANILLA_KEY));
        assertFalse(urdu.has(VANILLA_KEY));
        assertTrue(vanilla.has(VANILLA_KEY));
        var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES,
                List.of(pack("vanilla-english-fixture", vanillaRoot), pack("automobility-fixture", modRoot)));
        return new Fixture(resources, modRoot, english, urdu, vanilla);
    }

    private static PathPackResources pack(String id, Path root) {
        return new PathPackResources(new PackLocationInfo(id, Component.literal(id), PackSource.DEFAULT, Optional.empty()), root);
    }

    private record Fixture(MultiPackResourceManager resources, Path modRoot, JsonObject modEnglish,
                           JsonObject urdu, JsonObject vanillaEnglish) implements AutoCloseable {
        @Override public void close() { resources.close(); }
    }

    private record GlobalLanguageState(Language language, Field legacyField, Object legacyLanguage,
                                       String previousI18nText) implements AutoCloseable {
        static GlobalLanguageState capture() throws Exception {
            var language = Language.getInstance();
            Field field;
            try { field = I18n.class.getDeclaredField("language"); field.setAccessible(true); }
            catch (NoSuchFieldException absentOnNewerMinecraft) { field = null; }
            return new GlobalLanguageState(language, field, field == null ? null : field.get(null), I18n.get(VANILLA_KEY));
        }
        @Override public void close() throws Exception {
            Language.inject(language);
            if (legacyField != null) legacyField.set(null, legacyLanguage);
            assertSame(language, Language.getInstance());
            if (legacyField != null) assertSame(legacyLanguage, legacyField.get(null));
            assertEquals(previousI18nText, I18n.get(VANILLA_KEY));
        }
    }

    private static Path sourceMetadata() { return repository().resolve("fabric/src/main/resources/pack.mcmeta"); }
    private static Path processedResources() { return repository().resolve("fabric/build/resources/main"); }
    private static Path repository() {
        var directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            if (Files.isRegularFile(directory.resolve("fabric/build.gradle.kts"))) return directory;
            directory = directory.getParent();
        }
        return fail("Cannot locate this worktree's Fabric resources");
    }

    private static JsonObject readJson(Path path) throws IOException {
        try (var input = new JsonReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
            var value = readValue(input);
            assertEquals(JsonToken.END_DOCUMENT, input.peek(), path + ": trailing JSON");
            return value.getAsJsonObject();
        }
    }

    private static JsonElement readValue(JsonReader input) throws IOException {
        return switch (input.peek()) {
            case BEGIN_OBJECT -> {
                var result = new JsonObject(); input.beginObject();
                while (input.hasNext()) {
                    var key = input.nextName();
                    assertFalse(result.has(key), "Duplicate metadata/translation key: " + key);
                    result.add(key, readValue(input));
                }
                input.endObject(); yield result;
            }
            case BEGIN_ARRAY -> {
                var result = new JsonArray(); input.beginArray();
                while (input.hasNext()) result.add(readValue(input));
                input.endArray(); yield result;
            }
            case STRING -> new JsonPrimitive(input.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(input.nextString()));
            case BOOLEAN -> new JsonPrimitive(input.nextBoolean());
            default -> fail("Unexpected JSON token: " + input.peek());
        };
    }
}
