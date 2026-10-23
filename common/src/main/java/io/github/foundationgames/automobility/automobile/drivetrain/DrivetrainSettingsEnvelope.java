package io.github.foundationgames.automobility.automobile.drivetrain;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Stable user settings only. Quiet decode preserves exact raw bytes, including unsupported schemas. */
public final class DrivetrainSettingsEnvelope {
    public static final int MAGIC = 0x44534554; // DSET, deliberately distinct from network messages
    public static final int SCHEMA_REVISION = 1;
    public static final int MAX_RAW_BYTES = 256;
    private static final int HEADER_BYTES = 7, KNOWN_BODY_BYTES = 22;
    public enum TransmissionMode { AUTOMATIC, MANUAL }
    public enum Status { ABSENT, KNOWN, OPAQUE_UNSUPPORTED, OPAQUE_MALFORMED }
    public enum GarageDecision { UNDECIDED, COMMIT, ROLLBACK }

    /** No selected gear/range, active input, shaft state, phase, ignition, resource or identity fields. */
    public record Settings(TransmissionMode transmissionMode, double clutchReleaseSeconds, double analogSlewSeconds,
                           float assistanceStrength, boolean stallEnabled, boolean downshiftProtection) {
        public Settings {
            Objects.requireNonNull(transmissionMode, "transmission mode");
            Units.between(clutchReleaseSeconds, 0.01, 10.0, "release seconds");
            Units.between(analogSlewSeconds, 0.0, 10.0, "analog slew seconds");
            Units.between(assistanceStrength, 0.0, 1.0, "assistance strength");
        }
    }

    /** Supplied only by the adapter after consulting its actual durable garage journal/fence. */
    public record GarageReceipt(UUID transaction, UUID vehicle, long generation, GarageDecision decision) {
        public GarageReceipt {
            DrivetrainProtocol.requireId(transaction, "transaction"); DrivetrainProtocol.requireId(vehicle, "vehicle");
            if (generation <= 0) throw new IllegalArgumentException("positive garage generation required");
            Objects.requireNonNull(decision, "decision");
        }
        public void requireCommitFor(UUID expectedVehicle) {
            if (decision != GarageDecision.COMMIT || !vehicle.equals(expectedVehicle))
                throw new IllegalArgumentException("matching durable COMMIT required");
        }
    }

    /** Pure reset instruction, not a velocity/resource mutation. Existing raw settings remain byte-identical. */
    public record RuntimeRebind(DrivetrainSettingsEnvelope preservedSettings, GarageReceipt receipt,
                                boolean clearHeldControls, boolean discardShaftAndShiftRuntime) { }

    private final byte[] raw;
    private final Status status;
    private final Settings settings;

    private DrivetrainSettingsEnvelope(byte[] raw, Status status, Settings settings) {
        this.raw = raw.clone(); this.status = status; this.settings = settings;
    }
    public static DrivetrainSettingsEnvelope absent() { return new DrivetrainSettingsEnvelope(new byte[0], Status.ABSENT, null); }
    public Status status() { return status; }
    public Optional<Settings> settings() { return Optional.ofNullable(settings); }
    public byte[] saveRaw() { return raw.clone(); }

    /** Oversized data is rejected for host quarantine; bounded unknown/malformed data is retained verbatim. */
    public static DrivetrainSettingsEnvelope decodeQuiet(byte[] original) {
        Objects.requireNonNull(original, "raw settings");
        if (original.length > MAX_RAW_BYTES) throw new IllegalArgumentException("oversized settings must be quarantined by host");
        if (original.length == 0) return absent();
        byte[] raw = original.clone();
        if (raw.length < HEADER_BYTES) return opaque(raw, Status.OPAQUE_MALFORMED);
        try {
            var input = new DataInputStream(new ByteArrayInputStream(raw));
            int magic = input.readInt(), revision = input.readUnsignedByte(), length = input.readUnsignedShort();
            if (magic != MAGIC || length != raw.length - HEADER_BYTES) return opaque(raw, Status.OPAQUE_MALFORMED);
            if (revision != SCHEMA_REVISION) return opaque(raw, Status.OPAQUE_UNSUPPORTED);
            if (length != KNOWN_BODY_BYTES) return opaque(raw, Status.OPAQUE_MALFORMED);
            int mode = input.readUnsignedByte();
            if (mode >= TransmissionMode.values().length) return opaque(raw, Status.OPAQUE_MALFORMED);
            double release = input.readDouble(), slew = input.readDouble(); float assistance = input.readFloat(); int flags = input.readUnsignedByte();
            if ((flags & ~3) != 0) return opaque(raw, Status.OPAQUE_MALFORMED);
            var settings = new Settings(TransmissionMode.values()[mode], release, slew, assistance, (flags & 1) != 0, (flags & 2) != 0);
            if (input.available() != 0) return opaque(raw, Status.OPAQUE_MALFORMED);
            return new DrivetrainSettingsEnvelope(raw, Status.KNOWN, settings);
        } catch (IOException | IllegalArgumentException malformed) { return opaque(raw, Status.OPAQUE_MALFORMED); }
    }

    /** An explicit accepted settings action, never called as a quiet-load fallback. */
    public DrivetrainSettingsEnvelope replaceExplicit(Settings replacement) { return of(replacement); }

    public static DrivetrainSettingsEnvelope of(Settings settings) {
        Objects.requireNonNull(settings, "settings");
        try {
            var bytes = new ByteArrayOutputStream(); var output = new DataOutputStream(bytes);
            output.writeInt(MAGIC); output.writeByte(SCHEMA_REVISION); output.writeShort(KNOWN_BODY_BYTES);
            output.writeByte(settings.transmissionMode().ordinal()); output.writeDouble(settings.clutchReleaseSeconds());
            output.writeDouble(settings.analogSlewSeconds()); output.writeFloat(settings.assistanceStrength());
            output.writeByte((settings.stallEnabled() ? 1 : 0) | (settings.downshiftProtection() ? 2 : 0));
            return new DrivetrainSettingsEnvelope(bytes.toByteArray(), Status.KNOWN, settings);
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }

    public RuntimeRebind afterGarageCommit(GarageReceipt receipt, UUID expectedVehicle) {
        Objects.requireNonNull(receipt, "receipt"); receipt.requireCommitFor(expectedVehicle);
        return new RuntimeRebind(this, receipt, true, true);
    }

    private static DrivetrainSettingsEnvelope opaque(byte[] raw, Status status) { return new DrivetrainSettingsEnvelope(raw, status, null); }
}
