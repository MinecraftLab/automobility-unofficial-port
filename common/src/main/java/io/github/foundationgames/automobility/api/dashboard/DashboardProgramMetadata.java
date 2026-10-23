package io.github.foundationgames.automobility.api.dashboard;

import java.util.Objects;
import java.util.UUID;

/** Public receiver projection only. Private ownership, DJ and invitation data have no field here. */
public record DashboardProgramMetadata(UUID programId, String visibleName, long playbackRevision, long tuningRevision) {
    public DashboardProgramMetadata {
        Objects.requireNonNull(programId, "programId");
        Objects.requireNonNull(visibleName, "visibleName");
        if (visibleName.codePointCount(0, visibleName.length()) > 48
                || visibleName.codePoints().anyMatch(Character::isISOControl)
                || playbackRevision < 0 || tuningRevision < 1) throw new IllegalArgumentException("invalid public program projection");
    }

    public static DashboardProgramMetadata redacted(UUID id, long playbackRevision, long tuningRevision) {
        return new DashboardProgramMetadata(id, "", playbackRevision, tuningRevision);
    }

    public boolean redacted() { return visibleName.isEmpty(); }
}
