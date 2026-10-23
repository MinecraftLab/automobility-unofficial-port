package io.github.foundationgames.automobility.client.vehicle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Set;

/** A rejected or externally changed configuration is preserved until explicitly reloaded. */
public final class VehicleEffectsPreferenceStore {
    public static final int SCHEMA_VERSION = 2;
    public static final String FILE_NAME = "automobility-vehicle-effects.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().setStrictness(Strictness.STRICT).create();
    private static final Set<String> V1_FIELDS = Set.of("schemaVersion", "quality", "signalVolumePercent");
    private static final Set<String> V2_FIELDS = Set.of("schemaVersion", "quality", "signalVolumePercent", "customEnabled", "custom");
    private static final Set<String> CUSTOM_FIELDS = Set.of("priorityFullCars", "visibleFullCars", "coneCars",
            "spillPreset", "driftParticlesPercent", "exhaustSmoke");
    private final Path path;
    private boolean loaded, readable;
    private String lastReadContent;
    private int lastReadSchema;

    public VehicleEffectsPreferenceStore(Path path) { this.path = path; }

    public VehicleEffectsPreferences load() throws IOException {
        loaded = true;
        readable = false;
        lastReadSchema = 0;
        lastReadContent = Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : null;
        if (lastReadContent == null) {
            readable = true;
            return VehicleEffectsPreferences.defaults();
        }
        try {
            JsonObject object = strictObject(lastReadContent);
            int schema = integer(object.get("schemaVersion"));
            if (schema != 1 && schema != SCHEMA_VERSION)
                throw new IllegalArgumentException("Unsupported vehicle effects settings version");
            if (!(schema == 1 ? V1_FIELDS : V2_FIELDS).containsAll(object.keySet()))
                throw new IllegalArgumentException("Unknown settings fields");
            var defaults = VehicleEffectsPreferences.defaults();
            var quality = defaults.quality();
            if (object.has("quality")) {
                var value = object.get("quality");
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                    throw new IllegalArgumentException("Expected quality name");
                quality = VehicleLightQuality.valueOf(value.getAsString());
            }
            int volume = object.has("signalVolumePercent")
                    ? integer(object.get("signalVolumePercent")) : defaults.signalVolumePercent();
            var custom = VehicleCustomGraphicsSettings.forPreset(quality);
            boolean customEnabled = schema >= 2 && object.has("customEnabled") && bool(object.get("customEnabled"));
            if (schema >= 2 && object.has("custom")) {
                var value = object.get("custom");
                if (!value.isJsonObject() || !CUSTOM_FIELDS.equals(value.getAsJsonObject().keySet()))
                    throw new IllegalArgumentException("Expected complete supported custom settings");
                var data = value.getAsJsonObject();
                custom = new VehicleCustomGraphicsSettings(integer(data.get("priorityFullCars")),
                        integer(data.get("visibleFullCars")), integer(data.get("coneCars")),
                        VehicleCustomGraphicsSettings.Spill.valueOf(string(data.get("spillPreset"))),
                        integer(data.get("driftParticlesPercent")), bool(data.get("exhaustSmoke")));
            } else if (customEnabled) throw new IllegalArgumentException("Missing custom settings");
            var preferences = new VehicleEffectsPreferences(quality, volume, customEnabled, custom).sanitized();
            lastReadSchema = schema;
            readable = true;
            return preferences;
        } catch (RuntimeException error) {
            throw new IOException("Invalid or unsupported vehicle effects settings; preserving the file", error);
        }
    }

    private static JsonObject strictObject(String content) throws IOException {
        try (var reader = new JsonReader(new StringReader(content))) {
            reader.setStrictness(Strictness.STRICT);
            var object = strictObject(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing settings content");
            return object;
        }
    }

    private static JsonObject strictObject(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) throw new IllegalArgumentException("Expected settings object");
        var object = new JsonObject();
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (object.has(name)) throw new IllegalArgumentException("Duplicate settings field");
            object.add(name, reader.peek() == JsonToken.BEGIN_OBJECT ? strictObject(reader)
                    : GSON.fromJson(reader, JsonElement.class));
        }
        reader.endObject();
        return object;
    }

    private static boolean bool(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException("Expected boolean");
        return value.getAsBoolean();
    }

    private static String string(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected string");
        return value.getAsString();
    }

    private static int integer(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Expected integer");
        return value.getAsBigDecimal().intValueExact();
    }

    public boolean readable() { return loaded && readable; }

    public void save(VehicleEffectsPreferences preferences) throws IOException {
        if (!loaded) load();
        if (!readable) throw new IOException("Unreadable vehicle effects settings must be preserved");
        String current = Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : null;
        if (!Objects.equals(current, lastReadContent)) {
            readable = false;
            throw new IOException("Vehicle effects settings changed externally; preserving the file");
        }
        var safe = Objects.requireNonNull(preferences, "preferences").sanitized();
        var object = new JsonObject();
        object.addProperty("schemaVersion", SCHEMA_VERSION);
        object.addProperty("quality", safe.quality().name());
        object.addProperty("signalVolumePercent", safe.signalVolumePercent());
        object.addProperty("customEnabled", safe.customEnabled());
        var custom = new JsonObject();
        custom.addProperty("priorityFullCars", safe.custom().priorityFullCars());
        custom.addProperty("visibleFullCars", safe.custom().visibleFullCars());
        custom.addProperty("coneCars", safe.custom().coneCars());
        custom.addProperty("spillPreset", safe.custom().spill().name());
        custom.addProperty("driftParticlesPercent", safe.custom().driftParticlesPercent());
        custom.addProperty("exhaustSmoke", safe.custom().exhaustSmoke());
        object.add("custom", custom);
        String content = GSON.toJson(object) + "\n";
        Path target = path.toAbsolutePath();
        Files.createDirectories(target.getParent());
        if (lastReadSchema == 1 && current != null) preserveVersionOne(target, current);
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString() + ".", ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            if (!Objects.equals(Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : null, lastReadContent)) {
                readable = false;
                throw new IOException("Vehicle effects settings changed while saving; preserving the file");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            lastReadContent = content;
            lastReadSchema = SCHEMA_VERSION;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void preserveVersionOne(Path target, String content) throws IOException {
        byte[] original = content.getBytes(StandardCharsets.UTF_8);
        final String digest;
        try {
            digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(original));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        Path backup = target.resolveSibling(target.getFileName() + ".schema1." + digest + ".bak");
        if (Files.exists(backup)) {
            if (!java.util.Arrays.equals(original, Files.readAllBytes(backup)))
                throw new IOException("Existing migration backup differs; preserving both files");
        } else Files.write(backup, original, java.nio.file.StandardOpenOption.CREATE_NEW);
    }
}
