package io.github.foundationgames.automobility.entity;

import java.io.IOException;
import java.util.function.BooleanSupplier;

/** Never expose upgraded parked data before its real inventory exchange has committed. */
public final class GarageStoredTransaction {
    private GarageStoredTransaction() {}
    @FunctionalInterface public interface Writer { void write(GarageStorage.Entry entry) throws IOException; }
    public record Result(GarageService.Status status, GarageStorage.Entry retained, boolean inventoryCommitted) {}

    public static Result apply(GarageStorage.Entry original, GarageStorage.Entry replacement,
                               Writer writer, BooleanSupplier inventoryCommit) throws IOException {
        if (original.state() != GarageStorage.State.PARKED || replacement.state() != GarageStorage.State.PARKED
                || !original.id().equals(replacement.id()) || !original.owner().equals(replacement.owner())
                || !original.dimension().equals(replacement.dimension()) || original.x() != replacement.x() || original.z() != replacement.z())
            throw new IllegalArgumentException("Stored tuning must preserve the original vehicle identity and origin");
        // DEPLOYING is an existing, non-deployable recovery state. A failed rollback never
        // leaves an unpaid upgrade PARKED, even if the disk also rejects the rollback write.
        var quarantine = replacement.withState(GarageStorage.State.DEPLOYING);
        writer.write(quarantine);
        final boolean committed;
        try { committed = inventoryCommit.getAsBoolean(); }
        catch (RuntimeException error) {
            // A foreign inventory callback may have failed part-way through its own changes.
            // Keep the durable quarantine; do not guess at or overwrite the player's inventory.
            return new Result(GarageService.Status.RECOVERY_REQUIRED, quarantine, false);
        }
        if (!committed) {
            try {
                writer.write(original);
                return new Result(GarageService.Status.STALE, original, false);
            } catch (IOException | RuntimeException error) {
                return new Result(GarageService.Status.RECOVERY_REQUIRED, quarantine, false);
            }
        }
        try {
            writer.write(replacement);
            return new Result(GarageService.Status.APPLIED, replacement, true);
        } catch (IOException | RuntimeException error) {
            return new Result(GarageService.Status.RECOVERY_REQUIRED, quarantine, true);
        }
    }
}
