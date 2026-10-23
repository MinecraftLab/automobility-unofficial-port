package io.github.foundationgames.automobility.fabric.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Identity only; never changes the transport or existing entity synchronization. */
@Mixin(ServerCommonPacketListenerImpl.class)
public interface ServerContentConnectionAccess {
    @Accessor("connection") Connection automobility$getContentConnection();
}
