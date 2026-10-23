package io.github.foundationgames.automobility.api.dashboard;

import java.util.Objects;

/** Immutable presentation of one accepted read; never extrapolates or owns a media clock. */
public record CabinMediaPresentation060(String station, String title, String artist, String logoTexture,
                                        DashboardClock.Kind clockKind, DisplayStatus status, boolean progressAvailable, double progress) {
    public CabinMediaPresentation060 {
        Objects.requireNonNull(station); Objects.requireNonNull(title); Objects.requireNonNull(artist);
        Objects.requireNonNull(logoTexture); Objects.requireNonNull(clockKind); Objects.requireNonNull(status);
        if (!Double.isFinite(progress) || progress < 0 || progress > 1
                || progressAvailable && clockKind != DashboardClock.Kind.FINITE
                || !progressAvailable && progress != 0) throw new IllegalArgumentException("invalid captured progress");
    }

    public enum DisplayStatus {
        PLAY("PLAY"), PAUSE("PAUSE"), MUTE("MUTE"), STOP("STOP"), LIVE("LIVE"),
        CONNECTING("CONNECTING"), CONSENT_REQUIRED("CONSENT"), UNAVAILABLE("UNAVAILABLE"), OFF("OFF"), UNKNOWN("RADIO --");
        private final String fallback;
        DisplayStatus(String fallback) { this.fallback = fallback; }
        public String fallback() { return fallback; }
    }

    public static CabinMediaPresentation060 capture(DashboardMediaSnapshot media) {
        Objects.requireNonNull(media);
        boolean sourceAvailable = media.sourceKind() != DashboardMediaSnapshot.SourceKind.UNKNOWN
                && media.sourceKind() != DashboardMediaSnapshot.SourceKind.OFF
                && media.status() != DashboardMediaSnapshot.Status.UNAVAILABLE
                && media.status() != DashboardMediaSnapshot.Status.OFF;
        boolean finite = sourceAvailable && media.clock().kind() == DashboardClock.Kind.FINITE
                && switch (media.status()) {
                    case PLAYING, PAUSED, MUTED, STOPPED -> true;
                    default -> false;
                };
        String station = media.program().filter(value -> !value.redacted())
                .map(DashboardProgramMetadata::visibleName).filter(value -> !value.isBlank()).orElse(media.stationName());
        String logo = sourceAvailable && canonicalTextureId(media.logoTexture()) ? media.logoTexture() : "";
        return new CabinMediaPresentation060(station, media.title(), media.artist(), logo,
                media.clock().kind(), status(media), finite, finite ? media.clock().progress() : 0);
    }

    private static DisplayStatus status(DashboardMediaSnapshot media) {
        if (media.sourceKind() == DashboardMediaSnapshot.SourceKind.OFF || media.status() == DashboardMediaSnapshot.Status.OFF)
            return DisplayStatus.OFF;
        return switch (media.status()) {
            case CONNECTING -> DisplayStatus.CONNECTING;
            case CONSENT_REQUIRED -> DisplayStatus.CONSENT_REQUIRED;
            case UNAVAILABLE -> DisplayStatus.UNAVAILABLE;
            case PAUSED -> DisplayStatus.PAUSE;
            case MUTED -> DisplayStatus.MUTE;
            case STOPPED -> DisplayStatus.STOP;
            case LIVE -> media.clock().paused() ? DisplayStatus.PAUSE
                    : media.deviceGain() == 0 || media.personallyMuted() ? DisplayStatus.MUTE : DisplayStatus.LIVE;
            default -> {
                if (media.sourceKind() == DashboardMediaSnapshot.SourceKind.UNKNOWN) yield DisplayStatus.UNKNOWN;
                if (media.clock().paused()) yield DisplayStatus.PAUSE;
                if (media.deviceGain() == 0 || media.personallyMuted()) yield DisplayStatus.MUTE;
                if (media.clock().kind() == DashboardClock.Kind.FINITE && media.clock().positionMillis() == media.clock().durationMillis())
                    yield DisplayStatus.STOP;
                yield media.clock().kind() == DashboardClock.Kind.LIVE ? DisplayStatus.LIVE : DisplayStatus.PLAY;
            }
        };
    }

    public record TextBudget(int titlePixels, int artistPixels) { }

    /** Shares one fixed text row without letting a long title consume the artist's space. */
    public static TextBudget textBudget(int width, int separatorWidth, int titleWidth, int artistWidth) {
        if (width < 0 || separatorWidth < 0 || titleWidth < 0 || artistWidth < 0)
            throw new IllegalArgumentException("negative text width");
        int available = Math.max(0, width - separatorWidth);
        int title = Math.min(titleWidth, available / 2);
        int artist = Math.min(artistWidth, available - title);
        return new TextBudget(Math.min(titleWidth, available - artist), artist);
    }

    /** A local resource identifier only. HTTP, pack traversal and unqualified external strings are not textures. */
    private static boolean canonicalTextureId(String value) {
        if (!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) return false;
        String path = value.substring(value.indexOf(':') + 1);
        if (path.startsWith("/") || path.contains("//")) return false;
        for (String part : path.split("/", -1)) if (part.isEmpty() || part.equals(".") || part.equals("..")) return false;
        return true;
    }
}
