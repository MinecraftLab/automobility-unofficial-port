package io.github.foundationgames.automobility.api.dashboard;

import java.util.Objects;

/** Read-only HUD rows. Missing media metadata is not evidence of a missing tuned station. */
public final class DashboardHudPresentation060 {
    public record Rows(boolean visible, boolean idle, String heading, String primary,
            String secondary, String logoTexture) {}

    private DashboardHudPresentation060() {}

    public static Rows resolve(DashboardMediaSnapshot media) {
        Objects.requireNonNull(media, "media");
        boolean noSource = media.stationId().isBlank() && media.trackId().isBlank()
                && media.stationName().isBlank() && media.title().isBlank()
                && media.artist().isBlank() && media.program().isEmpty();
        // The physical cassette reader can provide a valid car/slot snapshot before
        // any playback exists. Keep UNKNOWN honest instead of labelling a station unavailable.
        if (media.sourceKind() == DashboardMediaSnapshot.SourceKind.UNKNOWN && noSource)
            return new Rows(false, true, "", "", "", "");
        boolean idle = media.sourceKind() == DashboardMediaSnapshot.SourceKind.OFF
                || media.status() == DashboardMediaSnapshot.Status.OFF
                || noSource && media.status() == DashboardMediaSnapshot.Status.STOPPED;
        if (idle) return new Rows(true, true, "", "", "", "");
        String heading = media.program().filter(program -> !program.redacted())
                .map(DashboardProgramMetadata::visibleName).orElse(media.stationName());
        if (heading.isBlank()) heading = media.stationId();
        return new Rows(true, false, heading, media.title(), media.artist(),
                safeLogoTexture(media.logoTexture()));
    }

    /** Only a nonempty, fully qualified local resource ID may reach Minecraft's parser. */
    public static String safeLogoTexture(String value) {
        Objects.requireNonNull(value, "logoTexture");
        if (value.length() > 512 || !value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) return "";
        String path = value.substring(value.indexOf(':') + 1);
        for (String segment : path.split("/", -1))
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) return "";
        return value;
    }
}
