package io.github.foundationgames.automobility.automobile.light;

/** Server clock only. Never changes the selected mode, engine, physics or chunk loading. */
public final class VehicleUnattendedLights {
    public record Saved(long vacantAt, boolean suppressed) {}
    private long vacantAt = -1;
    private boolean suppressed;
    public boolean suppressed() { return suppressed; }
    public Saved save() { return new Saved(vacantAt, suppressed); }

    public boolean tick(long tick, boolean occupied, boolean selectedOn, VehicleUnattendedLightSettings effective) {
        if (occupied || !selectedOn) { vacantAt = -1; suppressed = false; return false; }
        if (suppressed) return true;
        if (tick < 0) { suppressed = true; return true; }
        if (vacantAt < 0) vacantAt = tick;
        if (effective.sanitized().seconds() < 0) return false;
        // A clock rollback cannot give an abandoned car a fresh illumination grant.
        if (tick < vacantAt || tick - vacantAt >= effective.sanitized().seconds() * 20L) suppressed = true;
        return suppressed;
    }
    public void explicitAction(long tick, boolean occupied, boolean selectedOn) {
        suppressed = false; vacantAt = occupied || !selectedOn ? -1 : tick;
    }
    public void restore(Saved saved, boolean sameSession, boolean selectedOn) {
        vacantAt = sameSession ? Math.max(-1, saved.vacantAt()) : -1;
        suppressed = selectedOn && (!sameSession || saved.suppressed());
    }
    public void restoreLegacy() { vacantAt = -1; suppressed = false; }
}
