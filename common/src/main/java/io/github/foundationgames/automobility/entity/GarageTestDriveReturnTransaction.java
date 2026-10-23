package io.github.foundationgames.automobility.entity;

import java.util.function.BooleanSupplier;

/** Two existing entities move; rollback never creates or deserializes a replacement entity. */
public final class GarageTestDriveReturnTransaction {
    public record Result(boolean committed,boolean restored) {}
    private GarageTestDriveReturnTransaction() {}
    public static Result move(BooleanSupplier car,BooleanSupplier player,BooleanSupplier restoreCar,BooleanSupplier restorePlayer) {
        try { if(car.getAsBoolean()&&player.getAsBoolean()) return new Result(true,false); }
        catch(RuntimeException ignored) { /* A hook may fail after moving one entity. */ }
        boolean carRestored=attempt(restoreCar),playerRestored=attempt(restorePlayer);
        return new Result(false,carRestored&&playerRestored);
    }
    private static boolean attempt(BooleanSupplier operation) { try { return operation.getAsBoolean(); } catch(RuntimeException ignored) { return false; } }
}
