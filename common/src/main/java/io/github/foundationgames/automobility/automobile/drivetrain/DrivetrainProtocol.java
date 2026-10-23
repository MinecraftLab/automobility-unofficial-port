package io.github.foundationgames.automobility.automobile.drivetrain;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Separate, direction-specific packets. The existing 86-byte automobile state envelope is untouched. */
public final class DrivetrainProtocol {
    public static final int LEGACY_SYNC_STATE_BYTES = 86;
    public static final int MAGIC = 0x4454524e; // DTRN
    public static final int SCHEMA_REVISION = 2;
    public static final int INTENT_KIND = 1, SNAPSHOT_KIND = 2;
    public static final int HEADER_BYTES = 8;
    public static final int INTENT_PACKET_BYTES = HEADER_BYTES + 83;
    public static final int MAX_PACKET_BYTES = 512;
    public static final int MAX_PROFILE_ID_BYTES = 128;
    public static final double MAX_ABSOLUTE_OMEGA = 100_000.0;
    public static final double MAX_ABSOLUTE_SPEED = 1_000.0;
    public static final double MAX_COAST_REGENERATION_TORQUE_NEWTON_METRES = 1_000_000.0;
    public static final int SHIFT_UP_EDGE = 1, SHIFT_DOWN_EDGE = 2, IGNITION_TOGGLE_EDGE = 4, START_HELD_BIT = 8;

    public enum RangeRequest { KEEP, DRIVE, NEUTRAL, REVERSE, PARK }

    /** Nonce, world and mechanical incarnation are issued by the server, never chosen by the client. */
    public record Identity(UUID vehicle, UUID incarnation, UUID worldInstance, long sessionNonce) {
        public Identity {
            requireId(vehicle, "vehicle"); requireId(incarnation, "incarnation"); requireId(worldInstance, "world instance");
            if (sessionNonce == 0) throw new IllegalArgumentException("zero session nonce");
        }
    }

    /** No authoritative physics, ignition state or resource fields; start/toggle are requests for the sole engine owner. */
    public record Intent(Identity identity, long sequence, long echoedServerTick,
                         float throttle, float clutchDepression, ClutchController.PedalMode clutchMode,
                         int keyEdges, RangeRequest rangeRequest, boolean startHeld) {
        public Intent(Identity identity, long sequence, long echoedServerTick, float throttle, float clutchDepression,
                      ClutchController.PedalMode clutchMode, int keyEdges, RangeRequest rangeRequest) {
            this(identity, sequence, echoedServerTick, throttle, clutchDepression, clutchMode, keyEdges, rangeRequest, false);
        }
        public Intent {
            Objects.requireNonNull(identity, "identity");
            positiveSequence(sequence); nonnegativeTick(echoedServerTick);
            Units.between(throttle, 0.0, 1.0, "throttle");
            Units.between(clutchDepression, 0.0, 1.0, "clutch depression");
            Objects.requireNonNull(clutchMode, "clutch mode");
            Objects.requireNonNull(rangeRequest, "range request");
            if (clutchMode == ClutchController.PedalMode.DIGITAL && clutchDepression != 0f && clutchDepression != 1f)
                throw new IllegalArgumentException("digital clutch must be held or released");
            if ((keyEdges & ~(SHIFT_UP_EDGE | SHIFT_DOWN_EDGE | IGNITION_TOGGLE_EDGE)) != 0
                    || (keyEdges & (SHIFT_UP_EDGE | SHIFT_DOWN_EDGE)) == (SHIFT_UP_EDGE | SHIFT_DOWN_EDGE))
                throw new IllegalArgumentException("unknown or conflicting shift edges");
        }
    }

    /** Server-produced presentation/reconciliation snapshot; this kind is never accepted as C2S intent. */
    public record Snapshot(Identity identity, long sequence, long serverTick,
                           double engineOmega, double drivenWheelOmega, double speedMetresPerSecond,
                           int gear, int targetGear, AutomaticShiftController.Phase phase,
                           double phaseRemainingSeconds, float clutchEngagement, float load,
                           String profileId, String profileSha256, EngineController.Phase enginePhase,
                           PowertrainSimulation.ControlMode controlMode, boolean parkingEngaged, boolean predictionPowerPermitted,
                           double acceptedCoastRegenerationTorqueNm) {
        public Snapshot(Identity identity, long sequence, long serverTick, double engineOmega, double drivenWheelOmega,
                        double speedMetresPerSecond, int gear, int targetGear, AutomaticShiftController.Phase phase,
                        double phaseRemainingSeconds, float clutchEngagement, float load, String profileId, String profileSha256,
                        EngineController.Phase enginePhase, PowertrainSimulation.ControlMode controlMode, boolean parkingEngaged, boolean predictionPowerPermitted) {
            this(identity, sequence, serverTick, engineOmega, drivenWheelOmega, speedMetresPerSecond, gear, targetGear, phase,
                    phaseRemainingSeconds, clutchEngagement, load, profileId, profileSha256, enginePhase, controlMode, parkingEngaged, predictionPowerPermitted, 0);
        }
        public Snapshot(Identity identity, long sequence, long serverTick, double engineOmega, double drivenWheelOmega,
                        double speedMetresPerSecond, int gear, int targetGear, AutomaticShiftController.Phase phase,
                        double phaseRemainingSeconds, float clutchEngagement, float load, String profileId, String profileSha256,
                        EngineController.Phase enginePhase, PowertrainSimulation.ControlMode controlMode, boolean parkingEngaged) {
            this(identity, sequence, serverTick, engineOmega, drivenWheelOmega, speedMetresPerSecond, gear, targetGear, phase,
                    phaseRemainingSeconds, clutchEngagement, load, profileId, profileSha256, enginePhase, controlMode, parkingEngaged, false, 0);
        }
        public Snapshot(Identity identity, long sequence, long serverTick, double engineOmega, double drivenWheelOmega,
                        double speedMetresPerSecond, int gear, int targetGear, AutomaticShiftController.Phase phase,
                        double phaseRemainingSeconds, float clutchEngagement, float load, String profileId, String profileSha256) {
            this(identity, sequence, serverTick, engineOmega, drivenWheelOmega, speedMetresPerSecond, gear, targetGear, phase,
                    phaseRemainingSeconds, clutchEngagement, load, profileId, profileSha256,
                    EngineController.Phase.OFF, PowertrainSimulation.ControlMode.AUTOMATIC, false, false, 0);
        }
        public Snapshot {
            Objects.requireNonNull(identity, "identity");
            positiveSequence(sequence); nonnegativeTick(serverTick);
            Units.between(engineOmega, -MAX_ABSOLUTE_OMEGA, MAX_ABSOLUTE_OMEGA, "engine omega");
            Units.between(drivenWheelOmega, -MAX_ABSOLUTE_OMEGA, MAX_ABSOLUTE_OMEGA, "wheel omega");
            Units.between(speedMetresPerSecond, -MAX_ABSOLUTE_SPEED, MAX_ABSOLUTE_SPEED, "body speed");
            Units.between(phaseRemainingSeconds, 0.0, 30.0, "phase seconds");
            Units.between(acceptedCoastRegenerationTorqueNm, 0, MAX_COAST_REGENERATION_TORQUE_NEWTON_METRES, "accepted coast regeneration torque");
            Units.between(clutchEngagement, 0.0, 1.0, "clutch engagement");
            Units.between(load, 0.0, 1.0, "load");
            Objects.requireNonNull(phase, "phase");
            if (gear < -1 || gear > 16 || targetGear < -1 || targetGear > 16)
                throw new IllegalArgumentException("gear outside bounded profile domain");
            if (phase == AutomaticShiftController.Phase.DRIVE && (gear != targetGear || phaseRemainingSeconds != 0.0)
                    || phase == AutomaticShiftController.Phase.TORQUE_CUT && gear == targetGear
                    || (phase == AutomaticShiftController.Phase.RATIO_SWITCH || phase == AutomaticShiftController.Phase.REENGAGE) && gear != targetGear)
                throw new IllegalArgumentException("incoherent ratio/phase snapshot");
            Objects.requireNonNull(enginePhase, "engine phase"); Objects.requireNonNull(controlMode, "control mode");
            if (parkingEngaged && gear != 0) throw new IllegalArgumentException("parking requires neutral mechanical ratio");
            Objects.requireNonNull(profileId, "profile ID");
            if (profileId.length() > MAX_PROFILE_ID_BYTES || !profileId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException("bounded namespaced ASCII profile ID required");
            Objects.requireNonNull(profileSha256, "profile hash");
            if (!profileSha256.matches("[0-9a-fA-F]{64}")) throw new IllegalArgumentException("SHA256 profile hash required");
            profileSha256 = profileSha256.toLowerCase(Locale.ROOT);
        }
    }

    /** Client observation ordering only; transport must additionally authenticate the server direction. */
    public record SnapshotGate(Identity identity, long lastSequence, long lastServerTick) {
        public SnapshotGate {
            Objects.requireNonNull(identity, "identity");
            if (lastSequence < 0 || lastServerTick < -1) throw new IllegalArgumentException("invalid snapshot cursor");
        }
        public static SnapshotGate open(Identity identity) { return new SnapshotGate(identity, 0, -1); }
        public SnapshotAcceptance receive(Snapshot snapshot) {
            Objects.requireNonNull(snapshot, "snapshot");
            boolean accepted = identity.equals(snapshot.identity()) && snapshot.sequence() > lastSequence
                    && snapshot.serverTick() >= lastServerTick;
            return new SnapshotAcceptance(accepted, accepted
                    ? new SnapshotGate(identity, snapshot.sequence(), snapshot.serverTick()) : this, accepted ? snapshot : null);
        }
    }

    public record SnapshotAcceptance(boolean accepted, SnapshotGate after, Snapshot snapshot) { }

    private DrivetrainProtocol() { }

    public static byte[] encodeIntent(Intent intent) {
        Objects.requireNonNull(intent, "intent");
        return writeFrame(INTENT_KIND, out -> {
            writeIdentity(out, intent.identity()); out.writeLong(intent.sequence()); out.writeLong(intent.echoedServerTick());
            out.writeFloat(intent.throttle()); out.writeFloat(intent.clutchDepression());
            out.writeByte(intent.clutchMode().ordinal()); out.writeByte(intent.keyEdges() | (intent.startHeld() ? START_HELD_BIT : 0));
            out.writeByte(intent.rangeRequest().ordinal());
        });
    }

    public static Intent decodeIntent(byte[] packet) {
        DataInputStream input = readFrame(packet, INTENT_KIND);
        if (packet.length != INTENT_PACKET_BYTES) throw new IllegalArgumentException("intent size mismatch");
        try {
            Identity identity = readIdentity(input); long sequence = input.readLong(), echo = input.readLong();
            float throttle = input.readFloat(), clutch = input.readFloat();
            var mode = enumValue(ClutchController.PedalMode.values(), input.readUnsignedByte());
            int keys = input.readUnsignedByte();
            if ((keys & ~15) != 0) throw new IllegalArgumentException("unknown key bits");
            Intent result = new Intent(identity, sequence, echo, throttle, clutch, mode,
                    keys & (SHIFT_UP_EDGE | SHIFT_DOWN_EDGE | IGNITION_TOGGLE_EDGE),
                    enumValue(RangeRequest.values(), input.readUnsignedByte()), (keys & START_HELD_BIT) != 0);
            requireEnd(input); return result;
        } catch (IOException error) { throw malformed(error); }
    }

    /** Register only on the server-to-client transport. There is deliberately no combined decode-any API. */
    public static byte[] encodeServerSnapshot(Snapshot state) {
        Objects.requireNonNull(state, "snapshot");
        return writeFrame(SNAPSHOT_KIND, out -> {
            writeIdentity(out, state.identity()); out.writeLong(state.sequence()); out.writeLong(state.serverTick());
            out.writeDouble(state.engineOmega()); out.writeDouble(state.drivenWheelOmega()); out.writeDouble(state.speedMetresPerSecond());
            out.writeByte(state.gear()); out.writeByte(state.targetGear()); out.writeByte(state.phase().ordinal());
            out.writeDouble(state.phaseRemainingSeconds()); out.writeFloat(state.clutchEngagement()); out.writeFloat(state.load());
            out.writeByte(state.enginePhase().ordinal()); out.writeByte(state.controlMode().ordinal()); out.writeByte(state.parkingEngaged() ? 1 : 0);
            out.writeByte(state.predictionPowerPermitted() ? 1 : 0);
            out.writeDouble(state.acceptedCoastRegenerationTorqueNm());
            byte[] name = state.profileId().getBytes(StandardCharsets.US_ASCII);
            out.writeByte(name.length); out.write(name); out.write(HexFormat.of().parseHex(state.profileSha256()));
        });
    }

    public static Snapshot decodeServerSnapshot(byte[] packet) {
        DataInputStream input = readFrame(packet, SNAPSHOT_KIND);
        try {
            Identity identity = readIdentity(input); long sequence = input.readLong(), tick = input.readLong();
            double engine = input.readDouble(), wheel = input.readDouble(), speed = input.readDouble();
            int gear = input.readByte(), target = input.readByte();
            var phase = enumValue(AutomaticShiftController.Phase.values(), input.readUnsignedByte());
            double remaining = input.readDouble(); float clutch = input.readFloat(), load = input.readFloat();
            var enginePhase = enumValue(EngineController.Phase.values(), input.readUnsignedByte());
            var controlMode = enumValue(PowertrainSimulation.ControlMode.values(), input.readUnsignedByte());
            int parking = input.readUnsignedByte(); if (parking > 1) throw new IllegalArgumentException("invalid parking flag");
            int prediction = input.readUnsignedByte(); if (prediction > 1) throw new IllegalArgumentException("invalid prediction permission flag");
            double coast = input.readDouble();
            int length = input.readUnsignedByte();
            if (length < 1 || length > MAX_PROFILE_ID_BYTES) throw new IllegalArgumentException("profile ID size");
            byte[] name = new byte[length], hash = new byte[32]; input.readFully(name); input.readFully(hash);
            Snapshot result = new Snapshot(identity, sequence, tick, engine, wheel, speed, gear, target, phase,
                    remaining, clutch, load, new String(name, StandardCharsets.US_ASCII), HexFormat.of().formatHex(hash), enginePhase, controlMode, parking == 1, prediction == 1, coast);
            requireEnd(input); return result;
        } catch (IOException error) { throw malformed(error); }
    }

    @FunctionalInterface private interface Writer { void write(DataOutputStream output) throws IOException; }

    private static byte[] writeFrame(int kind, Writer writer) {
        try {
            var bodyBytes = new ByteArrayOutputStream(); var body = new DataOutputStream(bodyBytes); writer.write(body);
            byte[] payload = bodyBytes.toByteArray();
            if (payload.length + HEADER_BYTES > MAX_PACKET_BYTES) throw new IllegalArgumentException("packet too large");
            var bytes = new ByteArrayOutputStream(); var output = new DataOutputStream(bytes);
            output.writeInt(MAGIC); output.writeByte(SCHEMA_REVISION); output.writeByte(kind); output.writeShort(payload.length); output.write(payload);
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }

    private static DataInputStream readFrame(byte[] packet, int kind) {
        Objects.requireNonNull(packet, "packet");
        if (packet.length < HEADER_BYTES || packet.length > MAX_PACKET_BYTES) throw new IllegalArgumentException("packet size");
        var input = new DataInputStream(new ByteArrayInputStream(packet));
        try {
            if (input.readInt() != MAGIC || input.readUnsignedByte() != SCHEMA_REVISION
                    || input.readUnsignedByte() != kind || input.readUnsignedShort() != packet.length - HEADER_BYTES)
                throw new IllegalArgumentException("packet magic/revision/kind/length mismatch");
            return input;
        } catch (IOException error) { throw malformed(error); }
    }

    private static void writeIdentity(DataOutputStream out, Identity identity) throws IOException {
        writeUuid(out, identity.vehicle()); writeUuid(out, identity.incarnation()); writeUuid(out, identity.worldInstance()); out.writeLong(identity.sessionNonce());
    }
    private static Identity readIdentity(DataInputStream in) throws IOException {
        return new Identity(readUuid(in), readUuid(in), readUuid(in), in.readLong());
    }
    private static void writeUuid(DataOutputStream out, UUID id) throws IOException { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); }
    private static UUID readUuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static <T> T enumValue(T[] values, int index) {
        if (index >= values.length) throw new IllegalArgumentException("unknown enum code"); return values[index];
    }
    private static void requireEnd(DataInputStream input) throws IOException { if (input.available() != 0) throw new IllegalArgumentException("trailing bytes"); }
    static void requireId(UUID id, String name) {
        Objects.requireNonNull(id, name);
        if (id.getMostSignificantBits() == 0 && id.getLeastSignificantBits() == 0) throw new IllegalArgumentException("zero " + name);
    }
    static void nonnegativeTick(long tick) { if (tick < 0) throw new IllegalArgumentException("negative server tick"); }
    private static void positiveSequence(long sequence) { if (sequence <= 0) throw new IllegalArgumentException("positive sequence required"); }
    private static IllegalArgumentException malformed(IOException error) { return new IllegalArgumentException("truncated drivetrain packet", error); }
}
