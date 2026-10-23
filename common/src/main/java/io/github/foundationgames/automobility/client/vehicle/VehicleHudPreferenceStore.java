package io.github.foundationgames.automobility.client.vehicle;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Objects;
import java.util.Set;

/** Rejected or externally changed local settings are preserved until explicit reload. */
public final class VehicleHudPreferenceStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().setStrictness(Strictness.STRICT).create();
    private static final Set<String> FIELDS = Set.of("enabled", "scalePercent", "position", "cameraDistanceBlocks");
    private final Path path;
    private boolean loaded, readable;
    private String lastReadContent;

    public VehicleHudPreferenceStore(Path path) { this.path = path; }

    public VehicleHudPreferences load() throws IOException {
        loaded = true;
        readable = false;
        lastReadContent = Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : null;
        if (lastReadContent == null) {
            readable = true;
            return VehicleHudPreferences.defaults();
        }
        try {
            JsonObject object = strictObject(lastReadContent);
            if (!FIELDS.containsAll(object.keySet())) throw new IllegalArgumentException("Unknown HUD settings fields");
            var defaults = VehicleHudPreferences.defaults();
            boolean enabled = defaults.enabled();
            int scale = defaults.scalePercent();
            int cameraDistance = defaults.cameraDistanceBlocks();
            var position = defaults.position();
            if (object.has("enabled")) {
                var value = object.get("enabled");
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
                    throw new IllegalArgumentException("Expected enabled boolean");
                enabled = value.getAsBoolean();
            }
            if (object.has("scalePercent")) {
                var value = object.get("scalePercent");
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
                    throw new IllegalArgumentException("Expected integer HUD scale");
                scale = value.getAsBigDecimal().intValueExact();
            }
            if (object.has("position")) {
                var value = object.get("position");
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                    throw new IllegalArgumentException("Expected HUD position name");
                position = VehicleHudPreferences.Position.valueOf(value.getAsString());
            }
            if (object.has("cameraDistanceBlocks")) {
                var value = object.get("cameraDistanceBlocks");
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
                    throw new IllegalArgumentException("Expected integer camera distance");
                cameraDistance = value.getAsBigDecimal().intValueExact();
            }
            var result = new VehicleHudPreferences(enabled, scale, position, cameraDistance).sanitized();
            readable = true;
            return result;
        } catch (RuntimeException error) {
            throw new IOException("Invalid or unsupported vehicle UI settings; preserving the file", error);
        }
    }

    private static JsonObject strictObject(String content) throws IOException {
        try (var reader = new JsonReader(new StringReader(content))) {
            reader.setStrictness(Strictness.STRICT);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) throw new IllegalArgumentException("Expected HUD settings object");
            var object = new JsonObject();
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (object.has(name)) throw new IllegalArgumentException("Duplicate HUD settings field");
                object.add(name, GSON.fromJson(reader, JsonElement.class));
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing HUD settings content");
            return object;
        }
    }

    public boolean readable() { return loaded && readable; }

    public void save(VehicleHudPreferences preferences) throws IOException {
        if (!loaded) load();
        if (!readable) throw new IOException("Unreadable vehicle UI settings must be preserved");
        String current;
        try {
            current = Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : null;
        } catch (IOException error) {
            readable = false;
            throw error;
        }
        if (!Objects.equals(current, lastReadContent)) {
            readable = false;
            throw new IOException("Vehicle UI settings changed externally; preserving the file");
        }
        String content = GSON.toJson(Objects.requireNonNull(preferences).sanitized()) + "\n";
        Path target = path.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString() + ".", ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            lastReadContent = content;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
