package io.github.foundationgames.automobility.api.dashboard;

import java.util.Objects;
import java.util.Optional;

/** One exact vehicle and output generation own metadata, analysis and optional local cabin response. */
public record DashboardSnapshot(DashboardMediaSnapshot media, DashboardAnalysisSnapshot analysis,
        DashboardDecorationSnapshot decoration, Optional<DashboardCassetteSnapshot> cassette,Optional<DashboardCassetteMotion027> cassetteMotion) {
    public DashboardSnapshot(DashboardMediaSnapshot media,DashboardAnalysisSnapshot analysis,DashboardDecorationSnapshot decoration,Optional<DashboardCassetteSnapshot> cassette){
        this(media,analysis,decoration,cassette,Optional.empty());
    }
    public DashboardSnapshot(DashboardMediaSnapshot media, DashboardAnalysisSnapshot analysis, DashboardDecorationSnapshot decoration) {
        this(media, analysis, decoration, Optional.empty());
    }
    public DashboardSnapshot(DashboardMediaSnapshot media, DashboardAnalysisSnapshot analysis) {
        this(media, analysis, DashboardDecorationSnapshot.off(media.key()));
    }
    public DashboardSnapshot {
        Objects.requireNonNull(media); Objects.requireNonNull(analysis); Objects.requireNonNull(decoration); Objects.requireNonNull(cassette); Objects.requireNonNull(cassetteMotion);
        if (!media.key().equals(analysis.key()) || !media.key().equals(decoration.key()))
            throw new IllegalArgumentException("dashboard key mismatch");
        if (cassette.isPresent() && !media.key().equals(cassette.get().key())) throw new IllegalArgumentException("cassette car mismatch");
        if(cassetteMotion.isPresent()){
            var motion=cassetteMotion.get();var clock=media.clock();
            if(cassette.isEmpty()||!motion.matches(cassette.get())||!analysis.available()
                    ||media.sourceKind()!=DashboardMediaSnapshot.SourceKind.CUSTOM||media.revision()!=motion.emitterRevision()
                    ||!media.trackId().equals(motion.trackSha256())||clock.kind()!=DashboardClock.Kind.FINITE
                    ||clock.positionMillis()!=motion.positionMillis()||clock.durationMillis()!=motion.durationMillis()||clock.paused()!=motion.paused())
                throw new IllegalArgumentException("cassette accepted playback mismatch");
        }
        if (decoration.intensity() > 0 && (!analysis.available()
                || analysis.outputId() != decoration.outputId() || analysis.generation() != decoration.generation()))
            throw new IllegalArgumentException("cabin decoration output mismatch");
    }
    public VehicleDashboardKey key() { return media.key(); }
}
