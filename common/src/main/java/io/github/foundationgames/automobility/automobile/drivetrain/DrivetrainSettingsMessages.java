package io.github.foundationgames.automobility.automobile.drivetrain;

import java.io.*;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Bounded preference actions, separate from mechanical input and raw NBT preservation. */
public final class DrivetrainSettingsMessages {
    public static final int MAGIC = 0x44535046, REVISION = 2, MAX_BYTES = 128;
    public enum Kind { QUERY, UPDATE, REPLY }
    public enum Status { CURRENT, SAVED, FORBIDDEN, STALE, MOVING, UNRECOGNIZED, POLICY_UNAVAILABLE, NEUTRAL_REQUIRED }
    public record Message(Kind kind, UUID request, UUID vehicle, int entityId, long token, Status status,
                          boolean resetUnrecognized, boolean mayConfigure, Optional<DrivetrainSettingsEnvelope.Settings> settings) {
        public Message {
            Objects.requireNonNull(kind); Objects.requireNonNull(status); Objects.requireNonNull(settings);
            DrivetrainProtocol.requireId(request, "settings request"); DrivetrainProtocol.requireId(vehicle, "settings vehicle");
            if (entityId < 0) throw new IllegalArgumentException("entity id");
            if (kind == Kind.QUERY && (token != 0 || status != Status.CURRENT || resetUnrecognized || mayConfigure || settings.isPresent())
                    || kind == Kind.UPDATE && (token == 0 || status != Status.CURRENT || mayConfigure || settings.isEmpty())
                    || kind == Kind.REPLY && (resetUnrecognized || mayConfigure && token == 0)) throw new IllegalArgumentException("settings message direction/fields");
        }
    }
    private DrivetrainSettingsMessages() { }
    public static Message query(UUID request, UUID vehicle, int entity) {
        return new Message(Kind.QUERY, request, vehicle, entity, 0, Status.CURRENT, false, false, Optional.empty());
    }
    public static byte[] encode(Message value) {
        try {
            var bytes = new ByteArrayOutputStream(); var output = new DataOutputStream(bytes);
            output.writeInt(MAGIC); output.writeByte(REVISION); output.writeByte(value.kind().ordinal());
            uuid(output, value.request()); uuid(output, value.vehicle()); output.writeInt(value.entityId()); output.writeLong(value.token());
            output.writeByte(value.status().ordinal()); output.writeByte((value.resetUnrecognized() ? 1 : 0) | (value.mayConfigure() ? 2 : 0));
            byte[] settings = value.settings().map(DrivetrainSettingsEnvelope::of).map(DrivetrainSettingsEnvelope::saveRaw).orElseGet(() -> new byte[0]);
            output.writeByte(settings.length); output.write(settings);
            if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("settings frame too large");
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }
    public static Message decode(byte[] bytes) {
        Objects.requireNonNull(bytes);
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("settings frame too large");
        try {
            var input = new DataInputStream(new ByteArrayInputStream(bytes));
            if (input.readInt() != MAGIC || input.readUnsignedByte() != REVISION) throw new IllegalArgumentException("settings revision");
            int kind = input.readUnsignedByte(); if (kind >= Kind.values().length) throw new IllegalArgumentException("settings kind");
            UUID request = uuid(input), vehicle = uuid(input); int entity = input.readInt(); long token = input.readLong();
            int status = input.readUnsignedByte(), flags = input.readUnsignedByte(), length = input.readUnsignedByte();
            if (status >= Status.values().length || (flags & ~3) != 0 || length != input.available()) throw new IllegalArgumentException("settings framing");
            byte[] settingsBytes = new byte[length]; input.readFully(settingsBytes);
            Optional<DrivetrainSettingsEnvelope.Settings> settings = Optional.empty();
            if (length > 0) settings = Optional.of(DrivetrainSettingsEnvelope.decodeQuiet(settingsBytes).settings()
                    .orElseThrow(() -> new IllegalArgumentException("unknown settings action schema")));
            return new Message(Kind.values()[kind], request, vehicle, entity, token, Status.values()[status],
                    (flags & 1) != 0, (flags & 2) != 0, settings);
        } catch (IOException truncated) { throw new IllegalArgumentException("truncated settings message", truncated); }
    }
    private static void uuid(DataOutputStream output, UUID id) throws IOException { output.writeLong(id.getMostSignificantBits()); output.writeLong(id.getLeastSignificantBits()); }
    private static UUID uuid(DataInputStream input) throws IOException { return new UUID(input.readLong(), input.readLong()); }
}
