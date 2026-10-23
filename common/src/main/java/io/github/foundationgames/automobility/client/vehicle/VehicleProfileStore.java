package io.github.foundationgames.automobility.client.vehicle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import io.github.foundationgames.automobility.automobile.driving.VehicleDrivingMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Bounded, atomic local profile library. Failed reads preserve the original file. */
public final class VehicleProfileStore {
    public static final int MAX_PROFILES = 16;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    public VehicleProfileStore(Path path) { this.path = path; }

    public List<VehicleConfigurationProfile> load() throws IOException {
        if (!Files.exists(path)) return List.of();
        try {
            if (Files.size(path) > 65536) throw new IOException("Profile library too large");
            var stored = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var entry : stored.getAsJsonArray("profiles")) {
                var profile = entry.getAsJsonObject();
                for (String key : new String[]{"signalAutoCancel", "comfortSignals", "lockSignalConfirmation"}) {
                    if (!profile.has(key)) {
                        // Schema 1 predates these options. Migrate only the parsed copy, never the original file.
                        profile.addProperty(key, !key.equals("lockSignalConfirmation"));
                    } else {
                        var value = profile.get(key);
                        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
                            throw new IOException("Invalid vehicle signal preference: " + key);
                    }
                }
                if (!profile.has("drivingMode")) {
                    // Old schema-1 profiles follow the server; loading never rewrites the original library.
                    profile.addProperty("drivingMode", VehicleDrivingMode.INHERIT.name());
                } else {
                    var value = profile.get("drivingMode");
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                        throw new IOException("Invalid vehicle driving preference");
                    // Reject unknown names before Gson can turn them into a null enum value.
                    VehicleDrivingMode.valueOf(value.getAsString());
                }
            }
            var data = JSON.fromJson(stored, Stored.class);
            if (data == null || data.version != 1) throw new IOException("Unsupported profile library");
            return validated(data.profiles);
        } catch (RuntimeException error) { throw new IOException("Invalid profile library", error); }
    }
    public void save(List<VehicleConfigurationProfile> profiles) throws IOException {
        var data = new Stored(1, validated(profiles));
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.writeString(temporary, JSON.toJson(data) + "\n", StandardCharsets.UTF_8);
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException error) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    public static List<VehicleConfigurationProfile> put(List<VehicleConfigurationProfile> existing, VehicleConfigurationProfile profile) {
        var result = new ArrayList<>(existing);
        result.removeIf(p -> p.name().equalsIgnoreCase(profile.name()));
        result.add(profile); return validated(result);
    }
    private static List<VehicleConfigurationProfile> validated(List<VehicleConfigurationProfile> profiles) {
        var result = List.copyOf(profiles);
        if (result.size() > MAX_PROFILES) throw new IllegalArgumentException("Too many profiles");
        var names = new HashSet<String>();
        for (var profile : result) if (!names.add(profile.name().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Duplicate profile");
        return result;
    }
    private record Stored(int version, List<VehicleConfigurationProfile> profiles) {}
}
