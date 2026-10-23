package io.github.foundationgames.automobility.util.network.content;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.server.level.ServerPlayer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Loader boundary. Defaults fail closed; dedicated common code never references client/Fabric APIs. */
public final class Mx5MovingPartsSync {
    public static Consumer<AutomobileEntity> serverChanged = ignored -> { };
    public static Predicate<ServerPlayer> peerCompatible = ignored -> false;
    public static Consumer<Mx5Action> clientRequest = ignored -> { };
    private Mx5MovingPartsSync() { }
}
