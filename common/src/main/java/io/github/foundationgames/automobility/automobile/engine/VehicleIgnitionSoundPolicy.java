package io.github.foundationgames.automobility.automobile.engine;

/** Recognizes the existing registered EV without linking optional Powerworks classes. */
public final class VehicleIgnitionSoundPolicy {
    private VehicleIgnitionSoundPolicy() {}
    public static boolean electric(String namespace, String enginePath) {
        return "powerworks".equals(namespace) && "electric".equals(enginePath);
    }
}
