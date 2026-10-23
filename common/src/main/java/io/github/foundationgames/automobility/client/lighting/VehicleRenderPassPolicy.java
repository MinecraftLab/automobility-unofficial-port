package io.github.foundationgames.automobility.client.lighting;

/** Distinguishes normal shadow suppression from an unreadable optional API. */
public final class VehicleRenderPassPolicy {
    public enum Pass { MAIN, SHADOW, API_UNAVAILABLE }
    private VehicleRenderPassPolicy() {}
    public static Pass evaluate(boolean irisInstalled, boolean apiAvailable, Boolean shadowResult) {
        if (!irisInstalled) return Pass.MAIN;
        if (!apiAvailable || shadowResult == null) return Pass.API_UNAVAILABLE;
        return shadowResult ? Pass.SHADOW : Pass.MAIN;
    }
}
