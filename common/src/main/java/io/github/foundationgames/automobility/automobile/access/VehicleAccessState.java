package io.github.foundationgames.automobility.automobile.access;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Server-owned access. Existing unclaimed cars retain their public behavior. */
public record VehicleAccessState(int version, UUID owner, boolean locked, boolean publicDrive,
                                 boolean publicConfigure, boolean sharedConfigure,
                                 List<TrustedDriver> trusted, Feedback feedback) {
    private static final Gson JSON = new Gson();
    public static final int MAX_TRUSTED = 8, MAX_JSON = 4096;
    public static final VehicleAccessState DEFAULT = new VehicleAccessState(1, null, false, true, true, false, List.of(), Feedback.CLICK);
    public static final VehicleAccessState QUARANTINED = new VehicleAccessState(1, new UUID(0, 0), true, false, false, false, List.of(), Feedback.SILENT);

    public VehicleAccessState {
        if (version != 1) throw new IllegalArgumentException("Unsupported access version");
        trusted = List.copyOf(Objects.requireNonNull(trusted));
        feedback = Objects.requireNonNull(feedback).withoutHorn();
        if (trusted.size() > MAX_TRUSTED) throw new IllegalArgumentException("Too many shared keys");
        var unique = new HashSet<UUID>();
        for (var driver : trusted) {
            if (!unique.add(driver.id()) || driver.id().equals(owner)) throw new IllegalArgumentException("Duplicate shared key");
        }
        if (owner == null && (locked || !trusted.isEmpty())) throw new IllegalArgumentException("Unclaimed car cannot lock or share keys");
        if (!publicDrive) publicConfigure = false;
    }

    public record TrustedDriver(UUID id, String name) {
        public TrustedDriver {
            Objects.requireNonNull(id); Objects.requireNonNull(name);
            if (name.isBlank() || name.length() > 36 || name.chars().anyMatch(c -> Character.isISOControl(c)))
                throw new IllegalArgumentException("Invalid shared-key label");
        }
    }

    public enum Feedback { SILENT, CLICK, HORN, CLICK_AND_HORN;
        public boolean click() { return this == CLICK || this == CLICK_AND_HORN; }
        /** Legacy names and wire IDs remain readable; locking never operates the vehicle horn. */
        public boolean horn() { return false; }
        public Feedback withoutHorn() {
            return this == HORN ? SILENT : this == CLICK_AND_HORN ? CLICK : this;
        }
        public static Feedback byId(int id) {
            if (id < 0 || id >= values().length) throw new IllegalArgumentException("Invalid lock feedback");
            return values()[id].withoutHorn();
        }
    }

    public boolean isOwner(UUID player) { return player != null && player.equals(owner); }
    public boolean hasKey(UUID player) { return isOwner(player) || trusted.stream().anyMatch(k -> k.id().equals(player)); }
    public boolean mayDrive(UUID player) { return player != null && (owner == null || hasKey(player) || (!locked && publicDrive)); }
    public boolean mayConfigure(UUID player) {
        return player != null && (owner == null || isOwner(player)
                || (sharedConfigure && hasKey(player)) || (!locked && publicConfigure));
    }

    public VehicleAccessState claim(UUID player) {
        if (owner != null) throw new IllegalStateException("Already claimed");
        return new VehicleAccessState(1, Objects.requireNonNull(player), false, false, false, false, List.of(), feedback);
    }
    public VehicleAccessState withLocked(boolean value) { return copy(value, publicDrive, publicConfigure, sharedConfigure, trusted, feedback); }
    public VehicleAccessState withPublicDrive(boolean value) { return copy(locked, value, publicConfigure, sharedConfigure, trusted, feedback); }
    public VehicleAccessState withPublicConfigure(boolean value) { return copy(locked, publicDrive, value, sharedConfigure, trusted, feedback); }
    public VehicleAccessState withSharedConfigure(boolean value) { return copy(locked, publicDrive, publicConfigure, value, trusted, feedback); }
    public VehicleAccessState withFeedback(Feedback value) { return copy(locked, publicDrive, publicConfigure, sharedConfigure, trusted, value); }
    public VehicleAccessState addKey(UUID id, String name) {
        var entries = new ArrayList<>(trusted);
        entries.removeIf(k -> k.id().equals(id));
        entries.add(new TrustedDriver(id, name));
        return copy(locked, publicDrive, publicConfigure, sharedConfigure, entries, feedback);
    }
    public VehicleAccessState removeKey(UUID id) {
        return copy(locked, publicDrive, publicConfigure, sharedConfigure, trusted.stream().filter(k -> !k.id().equals(id)).toList(), feedback);
    }
    private VehicleAccessState copy(boolean lock, boolean drive, boolean configure, boolean shared, List<TrustedDriver> keys, Feedback sound) {
        return new VehicleAccessState(1, owner, lock, drive, configure, shared, keys, sound);
    }
    public String toJson() { return JSON.toJson(this); }
    public static VehicleAccessState fromJson(String value) {
        if (value == null || value.length() > MAX_JSON) throw new IllegalArgumentException("Invalid access data size");
        return Objects.requireNonNull(JSON.fromJson(value, VehicleAccessState.class));
    }
}
