package io.github.foundationgames.automobility.api.dashboard;

import java.util.Objects;
import java.util.Optional;

/** Immutable read projection. Volume, personal mute and transport remain distinct. */
public record DashboardMediaSnapshot(VehicleDashboardKey key, SourceKind sourceKind, Status status,
        long revision, String stationId, String stationName, String trackId, String title, String artist,
        String logoTexture, int accentArgb, DashboardClock clock, float deviceGain, float personalGain,
        Optional<DashboardProgramMetadata> program) {
    public enum SourceKind { OFF, BUNDLED, CUSTOM, PROGRAM, LIVE, UNKNOWN }
    public enum Status { PLAYING, PAUSED, MUTED, OFF, LIVE, CONNECTING, STOPPED, CONSENT_REQUIRED, UNAVAILABLE }

    public DashboardMediaSnapshot {
        Objects.requireNonNull(key); Objects.requireNonNull(sourceKind); Objects.requireNonNull(status);
        Objects.requireNonNull(clock); Objects.requireNonNull(program);
        stationId = text(stationId, 256); stationName = text(stationName, 256); trackId = text(trackId, 256);
        title = text(title, 1024); artist = text(artist, 512); logoTexture = text(logoTexture, 512);
        if (revision < 0 || !gain(deviceGain) || !gain(personalGain)
                || sourceKind == SourceKind.LIVE && clock.kind() != DashboardClock.Kind.LIVE
                || sourceKind == SourceKind.PROGRAM && program.isEmpty()
                || sourceKind != SourceKind.PROGRAM && program.isPresent()) throw new IllegalArgumentException("invalid dashboard media snapshot");
    }

    public boolean personallyMuted() { return personalGain == 0f; }
    private static boolean gain(float value) { return Float.isFinite(value) && value >= 0f && value <= 1f; }
    private static String text(String value, int maximum) {
        Objects.requireNonNull(value);
        if (value.length() > maximum || value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("invalid dashboard text");
        return value;
    }
}
