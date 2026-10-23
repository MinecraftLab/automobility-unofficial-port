package io.github.foundationgames.automobility.client.vehicle;
import java.util.Set;
/** Exact named exceptions, never blanket exceptions for S, Space or another mod. */
public final class VehicleKeyConflictPolicy {
    public enum Kind { CONFLICT, SHARED, CONTEXTUAL }
    private VehicleKeyConflictPolicy() {}
    public static boolean owned(String name) {return name.startsWith("key.automobility.") || name.startsWith("key.momentum.");}
    public static Kind classify(String a,String b) {
        var pair=Set.of(a,b);
        if(pair.equals(Set.of("key.momentum.brake","key.back")) || pair.equals(Set.of("key.momentum.handbrake_drift","key.jump"))) return Kind.SHARED;
        if(pair.equals(Set.of("key.momentum.cruise_resume","key.automobility.garage_test_drive_return"))) return Kind.CONTEXTUAL;
        return Kind.CONFLICT;
    }
}
