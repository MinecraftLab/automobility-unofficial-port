package io.github.foundationgames.automobility.mixin;
import io.github.foundationgames.automobility.entity.GarageInventoryExchange;
import io.github.foundationgames.automobility.util.duck.GaragePlayerReceipt;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerPlayer.class)
public abstract class GaragePlayerReceiptMixin implements GaragePlayerReceipt {
    @Unique private CompoundTag automobility$receipt=new CompoundTag();
    @Unique private java.util.UUID automobility$saveNonce;
    public java.util.UUID automobility$garageSaveNonce(){return automobility$saveNonce;}
    public void automobility$garageSaveNonce(java.util.UUID value){automobility$saveNonce=value;}
    public CompoundTag automobility$garageReceipt(){return automobility$receipt.copy();}
    public void automobility$garageReceipt(CompoundTag value){automobility$receipt=value.copy();}
    @Inject(method="readAdditionalSaveData",at=@At("TAIL"))
    private void automobility$readReceipt(ValueInput input,CallbackInfo ci){
        try{automobility$saveNonce=java.util.UUID.fromString(input.getStringOr("automobility:garageSaveNonce",""));}catch(IllegalArgumentException invalid){automobility$saveNonce=null;}
        var raw=input.read(GarageInventoryExchange.PLAYER_KEY,CompoundTag.CODEC);
        automobility$receipt=raw.map(CompoundTag::copy).orElseGet(CompoundTag::new);
        // Preserve a malformed present field as an invalid receipt; never silently reset payment identity.
        if(raw.isEmpty()&&input.read(GarageInventoryExchange.PLAYER_KEY,com.mojang.serialization.Codec.PASSTHROUGH).isPresent())
            automobility$receipt.putBoolean("malformed",true);
    }
    @Inject(method="addAdditionalSaveData",at=@At("TAIL"))
    private void automobility$saveReceipt(ValueOutput output,CallbackInfo ci){
        if(automobility$saveNonce!=null)output.putString("automobility:garageSaveNonce",automobility$saveNonce.toString());
        if(!automobility$receipt.isEmpty())output.store(GarageInventoryExchange.PLAYER_KEY,CompoundTag.CODEC,automobility$receipt.copy());
    }
}
