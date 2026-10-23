package io.github.foundationgames.automobility.mixin.dashboard;
import io.github.foundationgames.automobility.api.dashboard.GarageCabinTuningAccess027;
import io.github.foundationgames.automobility.entity.*;
import io.github.foundationgames.automobility.screen.GarageScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(value=GarageScreenHandler.class,remap=false)
public abstract class GarageCabinTuningAccessMixin027 implements GarageCabinTuningAccess027 {
    @Invoker(value="accessReason",remap=false)
    public abstract GarageService.Status cabinTuning$accessReason(AutomobileEntity car);
}
