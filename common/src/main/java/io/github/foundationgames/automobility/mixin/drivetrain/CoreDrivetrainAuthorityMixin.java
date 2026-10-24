package io.github.foundationgames.automobility.mixin.drivetrain;

import io.github.foundationgames.automobility.automobile.drivetrain.CoreDrivetrainAuthority;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Target API pinned in evidence/authority-class-api-008.json; never trusts a C2S body pose. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CoreDrivetrainAuthorityMixin implements CoreDrivetrainAuthority.ConnectionAccess {
    @Shadow public ServerPlayer player;
    @Shadow private Entity lastVehicle;
    @Shadow private double vehicleFirstGoodX, vehicleFirstGoodY, vehicleFirstGoodZ;
    @Shadow private double vehicleLastGoodX, vehicleLastGoodY, vehicleLastGoodZ;
    @Shadow private boolean clientVehicleIsFloating;
    @Shadow private int aboveGroundVehicleTickCount;
    @Shadow private int tickCount;
    @Shadow private int vehiclePositionLastResetAt;
    @Unique private AutomobileEntity drivetrain$authorityVehicle;
    @Unique private long drivetrain$lastCorrection = Long.MIN_VALUE;

    @Override public void drivetrain$refreshAuthority(AutomobileEntity car, boolean active) {
        if (player.getRootVehicle() != car || car.getControllingPassenger() != player || player.level() != car.level()) return;
        boolean transition = drivetrain$authorityVehicle != car;
        if (!active && drivetrain$authorityVehicle != car) return;
        lastVehicle = car;
        vehicleFirstGoodX = vehicleLastGoodX = car.getX();
        vehicleFirstGoodY = vehicleLastGoodY = car.getY();
        vehicleFirstGoodZ = vehicleLastGoodZ = car.getZ();
        clientVehicleIsFloating = false;
        aboveGroundVehicleTickCount = 0;
        drivetrain$targetCacheRefresh();
        player.level().getChunkSource().move(player);
        long now = player.level().getGameTime();
        if (transition || !active || drivetrain$lastCorrection == Long.MIN_VALUE || now < drivetrain$lastCorrection || now - drivetrain$lastCorrection >= 1) {
            player.connection.send(ClientboundMoveVehiclePacket.fromEntity(car));
            drivetrain$lastCorrection = now;
        }
        drivetrain$authorityVehicle = active ? car : null;
    }
    @Unique private void drivetrain$targetCacheRefresh() { vehiclePositionLastResetAt = tickCount; }

    @Inject(method="handleMoveVehicle", at=@At(value="INVOKE",
            target="Lnet/minecraft/server/level/ServerPlayer;getRootVehicle()Lnet/minecraft/world/entity/Entity;", ordinal=0), cancellable=true)
    private void drivetrain$ignoreClientBody(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        // Thread hop, invalid-coordinate disconnect and teleport/client-loaded guards have already run.
        if (!(player.getRootVehicle() instanceof AutomobileEntity car) || car.getControllingPassenger() != player) return;
        if (car.drivetrainOwnsLongitudinalMotion()) {
            drivetrain$refreshAuthority(car, true);
            ci.cancel();
        } else if (drivetrain$authorityVehicle == car) {
            drivetrain$refreshAuthority(car, false); // Quiescent handoff resets vanilla's exact vehicle cache.
        }
    }
}
