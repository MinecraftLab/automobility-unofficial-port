package io.github.foundationgames.automobility.mixin.drivetrain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import io.github.foundationgames.automobility.automobile.drivetrain.*;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Never normalizes a raw unknown tag and has no setComponents/physics hook. */
@Mixin(AutomobileEntity.class)
public abstract class CoreDrivetrainSettingsMixin implements CoreDrivetrainSettingsAccess {
    @Unique private Tag drivetrain$settings;
    @Override public Tag drivetrain$rawSettings() { return drivetrain$settings == null ? null : drivetrain$settings.copy(); }
    @Override public void drivetrain$replaceSettings(DrivetrainSettingsEnvelope settings, CoreDrivetrainBridge.SettingsPermit permit) {
        permit.require((AutomobileEntity)(Object)this);
        drivetrain$settings = new ByteArrayTag(settings.saveRaw());
    }
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"), require = 1)
    private void drivetrain$readSettings(ValueInput input, CallbackInfo ci) {
        var raw = input.read(SETTINGS_KEY, Codec.PASSTHROUGH).orElse(null);
        drivetrain$settings = raw == null ? null : raw.convert(NbtOps.INSTANCE).getValue().copy();
    }
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"), require = 1)
    private void drivetrain$writeSettings(ValueOutput output, CallbackInfo ci) {
        if (drivetrain$settings != null) output.store(SETTINGS_KEY, Codec.PASSTHROUGH, new Dynamic<>(NbtOps.INSTANCE, drivetrain$settings.copy()));
    }
}
