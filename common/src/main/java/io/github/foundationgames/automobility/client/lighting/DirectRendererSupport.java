package io.github.foundationgames.automobility.client.lighting;

import java.util.function.Supplier;

/** No optional/game types. Observation is registered only after the admitted backend was already loaded. */
public final class DirectRendererSupport {
    public record Observation(boolean enabled, boolean armed, long pushes, long vertices) {}
    public record Snapshot(boolean admitted, String reason, Observation observation) {}
    private static boolean admitted;
    private static String reason = "not_initialized";
    private static Supplier<Observation> observation;
    private DirectRendererSupport() {}
    public static void admission(boolean allowed, String explanation) {
        admitted=allowed; reason=explanation; observation=null;
    }
    public static void observation(Supplier<Observation> provider) { observation=provider; }
    public static void unavailable(String explanation) { reason=explanation; observation=null; }
    public static Snapshot snapshot() {
        Observation value=null;
        if (observation!=null) try { value=observation.get(); } catch (RuntimeException | LinkageError ignored) { }
        return new Snapshot(admitted,reason,value);
    }
}
