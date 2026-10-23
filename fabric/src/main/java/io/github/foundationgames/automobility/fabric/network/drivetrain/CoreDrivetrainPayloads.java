package io.github.foundationgames.automobility.fabric.network.drivetrain;

import io.github.foundationgames.automobility.automobile.drivetrain.DrivetrainProtocol;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Bounded optional play capability, separate from existing content/physics handshakes and legacy sync. */
public final class CoreDrivetrainPayloads {
    public static final int CHALLENGE = 0, CAPABILITY = 1, GRANT = 2, INTENT = 3, SNAPSHOT = 4, END = 5;
    public static final CustomPacketPayload.Type<Packet> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("automobility", "drivetrain_session_v1"));
    public record Packet(int kind, byte[] body) implements CustomPacketPayload {
        public Packet {
            Objects.requireNonNull(body);
            if (kind < CHALLENGE || kind > END || body.length > DrivetrainProtocol.MAX_PACKET_BYTES) throw new IllegalArgumentException("bounded drivetrain payload required");
            body = body.clone();
        }
        @Override public byte[] body() { return body.clone(); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static final StreamCodec<FriendlyByteBuf, Packet> CODEC = new StreamCodec<>() {
        @Override public Packet decode(FriendlyByteBuf buffer) {
            int kind = buffer.readUnsignedByte(), length = buffer.readVarInt();
            if (length < 0 || length > DrivetrainProtocol.MAX_PACKET_BYTES || buffer.readableBytes() != length) throw new IllegalArgumentException("payload length");
            byte[] body = new byte[length]; buffer.readBytes(body); return new Packet(kind, body);
        }
        @Override public void encode(FriendlyByteBuf buffer, Packet packet) {
            buffer.writeByte(packet.kind()); buffer.writeVarInt(packet.body.length); buffer.writeBytes(packet.body);
        }
    };
    public record Capability(int revision, long nonce) { }
    public record Grant(long connectionNonce, int entityId, String dimension, DrivetrainProtocol.Identity identity, long echoTick, boolean controlling) { }
    private CoreDrivetrainPayloads() { }
    public static Packet capability(int kind, long nonce) {
        if ((kind != CHALLENGE && kind != CAPABILITY) || nonce == 0) throw new IllegalArgumentException("capability kind/nonce");
        return write(kind, out -> { out.writeInt(DrivetrainProtocol.SCHEMA_REVISION); out.writeLong(nonce); });
    }
    public static Capability readCapability(Packet packet, int expected) {
        try {
            var input = reader(packet, expected); var result = new Capability(input.readInt(), input.readLong()); end(input);
            if (result.revision() != DrivetrainProtocol.SCHEMA_REVISION || result.nonce() == 0) throw new IllegalArgumentException("capability revision/nonce");
            return result;
        } catch (IOException error) { throw malformed(error); }
    }
    public static Packet grant(long connectionNonce, int entityId, String dimension, DrivetrainProtocol.Identity identity, long tick, boolean controlling) {
        if (connectionNonce == 0 || entityId < 0 || tick < 0 || dimension.length() > 128 || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("grant fields");
        return write(GRANT, out -> {
            out.writeLong(connectionNonce); out.writeInt(entityId); byte[] name = dimension.getBytes(StandardCharsets.US_ASCII);
            out.writeByte(name.length); out.write(name); identity(out, identity); out.writeLong(tick); out.writeByte(controlling ? 1 : 0);
        });
    }
    public static Grant readGrant(Packet packet) {
        try {
            var input = reader(packet, GRANT); long connection = input.readLong(); int entity = input.readInt(), length = input.readUnsignedByte();
            if (length < 3 || length > 128) throw new IllegalArgumentException("dimension length");
            byte[] name = new byte[length]; input.readFully(name); String dimension = new String(name, StandardCharsets.US_ASCII);
            var identity = identity(input); long tick = input.readLong(); int control = input.readUnsignedByte(); end(input);
            if (connection == 0 || entity < 0 || tick < 0 || control > 1 || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("grant fields");
            return new Grant(connection, entity, dimension, identity, tick, control == 1);
        } catch (IOException error) { throw malformed(error); }
    }
    public static Packet end(DrivetrainProtocol.Identity identity) { return write(END, out -> identity(out, identity)); }
    public static DrivetrainProtocol.Identity readEnd(Packet packet) {
        try { var input = reader(packet, END); var result = identity(input); end(input); return result; }
        catch (IOException error) { throw malformed(error); }
    }
    private interface Writer { void write(DataOutputStream output) throws IOException; }
    private static Packet write(int kind, Writer writer) {
        try { var bytes = new ByteArrayOutputStream(); writer.write(new DataOutputStream(bytes)); return new Packet(kind, bytes.toByteArray()); }
        catch (IOException impossible) { throw new AssertionError(impossible); }
    }
    private static DataInputStream reader(Packet packet, int expected) {
        if (packet.kind() != expected) throw new IllegalArgumentException("payload direction/kind");
        return new DataInputStream(new ByteArrayInputStream(packet.body()));
    }
    private static void identity(DataOutputStream out, DrivetrainProtocol.Identity value) throws IOException {
        uuid(out, value.vehicle()); uuid(out, value.incarnation()); uuid(out, value.worldInstance()); out.writeLong(value.sessionNonce());
    }
    private static DrivetrainProtocol.Identity identity(DataInputStream input) throws IOException { return new DrivetrainProtocol.Identity(uuid(input), uuid(input), uuid(input), input.readLong()); }
    private static void uuid(DataOutputStream out, UUID id) throws IOException { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); }
    private static UUID uuid(DataInputStream input) throws IOException { return new UUID(input.readLong(), input.readLong()); }
    private static void end(DataInputStream input) throws IOException { if (input.available() != 0) throw new IllegalArgumentException("trailing payload"); }
    private static IllegalArgumentException malformed(IOException error) { return new IllegalArgumentException("truncated drivetrain payload", error); }
}
