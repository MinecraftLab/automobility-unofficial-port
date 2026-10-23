package io.github.milkucha.momentum.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the actual transport; opening a world to LAN does not make its visitors local. */
@Mixin(value = ServerCommonPacketListenerImpl.class, remap = false)
public interface ServerConnectionAccessor {
    @Accessor("connection")
    Connection momentum$getConnection();
}
