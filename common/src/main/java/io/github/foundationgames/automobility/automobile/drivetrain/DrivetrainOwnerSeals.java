package io.github.foundationgames.automobility.automobile.drivetrain;

import java.io.IOException;
import java.util.*;

/** Strict data-only owner pins. The bootstrap implementation is part of the immutable Core content seal. */
public record DrivetrainOwnerSeals(Map<String, String> jars, String nestedParent, String nestedMember) {
    public static final String RESOURCE = "META-INF/automobility/drivetrain-owner-seals.properties";
    private static final Set<String> KEYS = Set.of("schema", "core.version", "core.minecraft", "pumps_and_pistons.sha256",
            "pumps_and_pistons_automobility.sha256", "powerworks.sha256", "pumps_and_pistons_automobility.parent", "pumps_and_pistons_automobility.member");
    public DrivetrainOwnerSeals { jars = Map.copyOf(jars); }
    public static DrivetrainOwnerSeals decode(byte[] raw, String coreVersion, String minecraftVersion) throws IOException {
        if (raw == null || raw.length == 0 || raw.length > 8192) throw new IOException("owner seal size");
        for (byte value : raw) if (value < 0 || value == 127 || value < 32 && value != '\r' && value != '\n' && value != '\t') throw new IOException("owner seal ASCII");
        var values = new HashMap<String, String>();
        for (String original : new String(raw, java.nio.charset.StandardCharsets.UTF_8).split("\n", -1)) {
            String line = original.strip(); if (line.isEmpty() || line.startsWith("#")) continue;
            int split = line.indexOf('='); if (split < 1 || line.indexOf('=', split + 1) >= 0 || line.indexOf('\\') >= 0) throw new IOException("owner seal line");
            String key = line.substring(0, split).strip(), value = line.substring(split + 1).strip();
            if (!KEYS.contains(key) || value.isEmpty() || values.putIfAbsent(key, value) != null) throw new IOException("owner seal key");
        }
        if (!values.keySet().equals(KEYS) || !"1".equals(values.get("schema")) || !coreVersion.equals(values.get("core.version"))
                || !minecraftVersion.equals(values.get("core.minecraft"))) throw new IOException("owner seal schema/target");
        var jars = new HashMap<String, String>();
        for (String id : List.of("pumps_and_pistons", "pumps_and_pistons_automobility", "powerworks")) {
            String digest = values.get(id + ".sha256");
            if (!digest.matches("[0-9A-F]{64}") || digest.equals("0".repeat(64))) throw new IOException("unbound owner digest");
            jars.put(id, digest);
        }
        String parent = values.get("pumps_and_pistons_automobility.parent"), member = values.get("pumps_and_pistons_automobility.member");
        if (!parent.equals("pumps_and_pistons") || !member.equals("META-INF/jars/pumps-and-pistons-automobility-1.12.0-dev.1+" + minecraftVersion + ".jar")) throw new IOException("owner nested identity");
        return new DrivetrainOwnerSeals(jars, parent, member);
    }
}
