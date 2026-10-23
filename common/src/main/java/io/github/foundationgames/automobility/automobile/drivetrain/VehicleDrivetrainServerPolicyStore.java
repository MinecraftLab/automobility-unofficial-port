package io.github.foundationgames.automobility.automobile.drivetrain;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/** Small server configuration. Invalid/future files remain untouched and suspend new mechanics. */
public final class VehicleDrivetrainServerPolicyStore {
    private static Optional<DrivetrainSettingsPolicy> current = Optional.of(DrivetrainSettingsPolicy.defaults());
    private VehicleDrivetrainServerPolicyStore() { }
    public static Optional<DrivetrainSettingsPolicy> current() { return current; }
    public static void load(Path file) throws IOException {
        current = Optional.empty();
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, """
                    {
                      "schema": 1,
                      "manualAllowed": true,
                      "minimumAssistance": 0,
                      "minimumClutchReleaseSeconds": 0.01,
                      "maximumClutchReleaseSeconds": 10,
                      "maximumAnalogSlewSeconds": 10,
                      "requireStall": false,
                      "requireDownshiftProtection": false
                    }
                    """, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        }
        if (!Files.isRegularFile(file) || Files.size(file) > 16384) throw new IOException("Invalid drivetrain policy file size");
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (number(json, "schema") != 1) throw new IllegalArgumentException("unsupported policy schema");
            var policy = new DrivetrainSettingsPolicy(bool(json, "manualAllowed"), (float)number(json, "minimumAssistance"),
                    number(json, "minimumClutchReleaseSeconds"), number(json, "maximumClutchReleaseSeconds"),
                    number(json, "maximumAnalogSlewSeconds"), bool(json, "requireStall"), bool(json, "requireDownshiftProtection"));
            current = Optional.of(policy);
        } catch (RuntimeException invalid) { throw new IOException("Invalid drivetrain policy; preserving original file", invalid); }
    }
    private static boolean bool(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isBoolean()) throw new IllegalArgumentException(key);
        return json.get(key).getAsBoolean();
    }
    private static double number(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isNumber()) throw new IllegalArgumentException(key);
        return json.get(key).getAsDouble();
    }
}
