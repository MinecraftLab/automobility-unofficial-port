package io.github.foundationgames.automobility.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Bounded, per-vehicle write-ahead journal in the owning world's data directory.
 * A successful deployment retains its complete snapshot as a non-deployable receipt.
 * This is deliberately not a claim of atomicity with Minecraft's independent chunk saves.
 * An interrupted transfer fails closed; it never silently clones an entity from a receipt.
 */
public final class GarageStorage {
    public static final int MAX_STORED = 32, MAX_RECORDS = 256, MAX_BYTES = 1_048_576;
    public enum State { PARKED, DEPLOYING, DEPLOYED }
    public record Entry(UUID id, UUID owner, String name, String dimension, int x, int z,
                        State state, CompoundTag entity) {
        public Entry withState(State next) { return new Entry(id, owner, name, dimension, x, z, next, entity); }
    }
    private GarageStorage() {}

    private static Path directory(ServerLevel level, UUID owner) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data")
                .resolve("automobility-garage").resolve(owner.toString());
    }
    private static Path path(ServerLevel level, UUID owner, UUID car) {
        return directory(level, owner).resolve(car + ".nbt");
    }

    public static List<Entry> list(ServerLevel level, UUID owner) throws IOException {
        var directory = directory(level, owner);
        if (!Files.exists(directory)) return List.of();
        List<Path> paths;
        try (var stream = Files.list(directory)) {
            paths = stream.filter(p -> p.getFileName().toString().endsWith(".nbt"))
                    .sorted().limit(MAX_RECORDS + 1L).toList();
        }
        if (paths.size() > MAX_RECORDS) throw new IOException("Garage receipt capacity exceeded");
        var entries = new ArrayList<Entry>();
        for (var path : paths) {
            var entry = read(path);
            if (!entry.owner().equals(owner) || !path.getFileName().toString().equals(entry.id() + ".nbt"))
                throw new IOException("Garage receipt identity mismatch");
            if (entry.state() != State.DEPLOYED) entries.add(entry);
        }
        entries.sort(Comparator.comparing(e -> e.id().toString()));
        return List.copyOf(entries);
    }

    public static Entry get(ServerLevel level, UUID owner, UUID car) throws IOException {
        var path = path(level, owner, car);
        if (!Files.exists(path)) return null;
        var entry = read(path);
        if (!entry.id().equals(car) || !entry.owner().equals(owner)) throw new IOException("Garage identity mismatch");
        return entry;
    }

    static Entry read(Path path) throws IOException {
        if (Files.size(path) > MAX_BYTES) throw new IOException("Garage receipt too large");
        try {
            CompoundTag tag;
            try (var input = new DataInputStream(Files.newInputStream(path))) {
                tag = NbtIo.read(input, NbtAccounter.create(MAX_BYTES));
            }
            return decodeEntry(tag);
        } catch (RuntimeException error) { throw new IOException("Invalid garage receipt", error); }
    }

    /** Write before removing/spawning anything; no non-atomic replacement fallback. */
    public static void write(ServerLevel level, Entry entry) throws IOException {
        Files.createDirectories(directory(level, entry.owner()));
        var destination = path(level, entry.owner(), entry.id());
        if (!Files.exists(destination)) {
            try (var files = Files.list(destination.getParent())) {
                if (files.filter(p -> p.getFileName().toString().endsWith(".nbt")).limit(MAX_RECORDS).count() >= MAX_RECORDS)
                    throw new IOException("Garage receipt capacity exceeded");
            }
        }
        writeFile(destination, entry);
    }

    static void writeFile(Path destination, Entry entry) throws IOException {
        var root = encodeEntry(entry);
        if (root.sizeInBytes() > MAX_BYTES) throw new IOException("Garage snapshot too large");
        var temporary = destination.resolveSibling(destination.getFileName() + ".pending");
        NbtIo.write(root, temporary);
        try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    static Entry decodeEntry(CompoundTag tag) throws IOException {
        try {
        if (tag.getIntOr("version", 0) != 1) throw new IllegalArgumentException("Unknown garage format");
        return new Entry(UUID.fromString(tag.getStringOr("id", "")), UUID.fromString(tag.getStringOr("owner", "")),
                tag.getStringOr("name", "Automobile"), tag.getStringOr("dimension", ""),
                tag.getIntOr("x", 0), tag.getIntOr("z", 0), State.valueOf(tag.getStringOr("state", "")),
                tag.getCompound("entity").orElseThrow());
        }catch(RuntimeException error){throw new IOException("Invalid Garage receipt image",error);}
    }
    static CompoundTag encodeEntry(Entry entry) {
        var root = new CompoundTag();
        root.putInt("version", 1);
        root.putString("id", entry.id().toString()); root.putString("owner", entry.owner().toString());
        root.putString("name", entry.name()); root.putString("dimension", entry.dimension());
        root.putInt("x", entry.x()); root.putInt("z", entry.z());
        root.putString("state", entry.state().name()); root.put("entity", entry.entity().copy());
        return root;
    }

    public static Entry snapshot(AutomobileEntity car, UUID owner) throws IOException {
        var level = (ServerLevel)car.level();
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        if (!car.save(output)) throw new IOException("Vehicle refused serialization");
        var nbt = output.buildResult();
        if (nbt.sizeInBytes() > MAX_BYTES - 2048) throw new IOException("Vehicle snapshot too large");
        return new Entry(car.getUUID(), owner, GarageService.displayName(car).getString(), level.dimension().identifier().toString(),
                car.blockPosition().getX(), car.blockPosition().getZ(), State.PARKED, nbt);
    }

    /** Overlay the entity's known changed fields while preserving opaque fields from inactive addons. */
    public static Entry updated(Entry original, CompoundTag before, AutomobileEntity car) throws IOException {
        var after = snapshot(car, original.owner());
        return new Entry(original.id(), original.owner(), after.name(), original.dimension(), original.x(), original.z(),
                State.PARKED, preserveOpaque(original.entity(), before, after.entity()));
    }

    static CompoundTag preserveOpaque(CompoundTag original, CompoundTag before, CompoundTag after) {
        var merged = original.copy();
        for (var key : before.keySet()) if (!after.contains(key)) merged.remove(key);
        for (var key : after.keySet()) {
            var originalChild = original.getCompound(key); var beforeChild = before.getCompound(key); var afterChild = after.getCompound(key);
            if (originalChild.isPresent() && beforeChild.isPresent() && afterChild.isPresent())
                merged.put(key, preserveOpaque(originalChild.get(), beforeChild.get(), afterChild.get()));
            else merged.put(key, after.get(key).copy());
        }
        return merged;
    }

    /** Presence or a successfully decoded value is not a qualified lossless transfer. */
    public static boolean canRestore(Entry entry) {
        return entry != null && entry.entity() != null
                && !GarageStoredAddonData.requiresQualifiedTransfer(entry.entity().keySet());
    }

    public static final class IntegrationUnavailableException extends IOException {
        public IntegrationUnavailableException() { super("Stored addon state requires a qualified lossless transfer"); }
    }

    public static AutomobileEntity restore(ServerLevel level, Entry entry) throws IOException {
        if (!canRestore(entry)) throw new IntegrationUnavailableException();
        try {
            var input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), entry.entity().copy());
            var restored = EntityType.loadEntityRecursive(input, level, EntitySpawnReason.LOAD, entity -> entity);
            if (!(restored instanceof AutomobileEntity car) || !car.getUUID().equals(entry.id())
                    || car.isVehicle() || car.isPassenger() || !car.getVehicleAccess().isOwner(entry.owner()))
                throw new IOException("Stored vehicle identity/access changed");
            return car;
        } catch (RuntimeException error) { throw new IOException("Unable to read stored vehicle", error); }
    }

    /** Requiring the original chunk already loaded never causes remote chunk generation/loading. */
    public static boolean originalChunkLoaded(ServerLevel current, Entry entry) {
        for (var level : current.getServer().getAllLevels()) {
            if (level.dimension().identifier().toString().equals(entry.dimension()))
                return level.hasChunk(entry.x() >> 4, entry.z() >> 4)
                        && level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(entry.x() >> 4, entry.z() >> 4));
        }
        return false;
    }

    public static boolean uuidPresent(ServerLevel level, UUID id) {
        for (var dimension : level.getServer().getAllLevels()) if (dimension.getEntity(id) != null) return true;
        return false;
    }
}
