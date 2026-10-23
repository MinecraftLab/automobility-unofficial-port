package io.github.foundationgames.automobility.util.duck;
import net.minecraft.nbt.CompoundTag;
/** Saved in the same vanilla playerdata image as the inventory. */
public interface GaragePlayerReceipt {
    java.util.UUID automobility$garageSaveNonce();
    void automobility$garageSaveNonce(java.util.UUID nonce);
    CompoundTag automobility$garageReceipt();
    void automobility$garageReceipt(CompoundTag receipt);
}
