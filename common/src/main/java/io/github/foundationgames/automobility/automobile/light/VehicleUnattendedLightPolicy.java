package io.github.foundationgames.automobility.automobile.light;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import io.github.foundationgames.automobility.Automobility;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Set;

/** Server administrator limits; independent of the engine shutdown policy. */
public final class VehicleUnattendedLightPolicy {
    public record Limits(int maximumSeconds, boolean allowUnlimited) {
        public Limits {
            if (maximumSeconds < 0 || maximumSeconds > VehicleUnattendedLightSettings.MAX_SECONDS)
                throw new IllegalArgumentException("Invalid unattended light limit");
        }
    }
    private static final Limits DEFAULTS = new Limits(3600, false);
    private static Limits limits = DEFAULTS;
    private VehicleUnattendedLightPolicy() {}
    public static Limits limits() { return limits; }
    public static VehicleUnattendedLightSettings apply(VehicleUnattendedLightSettings selected) {
        return selected.restricted(limits.maximumSeconds(), limits.allowUnlimited());
    }
    public static Limits decode(String text) {
        final com.google.gson.JsonObject object;
        try (var reader = new com.google.gson.stream.JsonReader(new java.io.StringReader(text))) {
            reader.setStrictness(com.google.gson.Strictness.STRICT);
            object = new com.google.gson.JsonObject();
            reader.beginObject();
            var gson = new com.google.gson.GsonBuilder().setStrictness(com.google.gson.Strictness.STRICT).create();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (object.has(name)) throw new IllegalArgumentException("Duplicate light policy field");
                object.add(name, gson.fromJson(reader, com.google.gson.JsonElement.class));
            }
            reader.endObject();
            if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT)
                throw new IllegalArgumentException("Trailing light policy data");
        } catch (java.io.IOException error) { throw new IllegalArgumentException("Invalid light policy",error); }
        if (!object.keySet().equals(Set.of("maximumUnattendedLightSeconds", "allowUnlimitedWorkingLights")))
            throw new IllegalArgumentException("Unsupported light policy fields");
        var maximum = object.get("maximumUnattendedLightSeconds");
        var allow = object.get("allowUnlimitedWorkingLights");
        if (!maximum.isJsonPrimitive() || !maximum.getAsJsonPrimitive().isNumber()
                || !allow.isJsonPrimitive() || !allow.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException("Invalid light policy types");
        return new Limits(maximum.getAsBigDecimal().intValueExact(), allow.getAsBoolean());
    }
    public static void load() {
        limits = DEFAULTS;
        var file = FabricLoader.getInstance().getConfigDir().resolve("automobility-vehicle-lights.json");
        try {
            if (Files.exists(file)) limits = decode(Files.readString(file));
            else {
                Files.createDirectories(file.getParent());
                Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                        "maximumUnattendedLightSeconds", limits.maximumSeconds(),
                        "allowUnlimitedWorkingLights", limits.allowUnlimited())) + "\n", StandardOpenOption.CREATE_NEW);
            }
        } catch (Exception error) {
            Automobility.LOG.warn("Could not read unattended light policy; using defaults and preserving the file", error);
        }
    }
}
