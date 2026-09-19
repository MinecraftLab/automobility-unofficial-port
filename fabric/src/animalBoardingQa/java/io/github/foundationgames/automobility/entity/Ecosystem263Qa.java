package io.github.foundationgames.automobility.entity;

import io.github.foundationgames.automobility.automobile.AutomobileEngine;
import io.github.foundationgames.automobility.automobile.AutomobileFrame;
import io.github.foundationgames.automobility.automobile.AutomobileWheel;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.slf4j.LoggerFactory;

/** Optional real-addon regression coverage, never part of production artifacts. */
final class Ecosystem263Qa {
    static void run(MinecraftServer server) throws Exception {
        require(FabricLoader.getInstance().isModLoaded("momentum"), "Momentum is loaded");
        require(FabricLoader.getInstance().isModLoaded("riautomobility"), "RIAutomobility is loaded");
        int checks = 2;
        var source = server.createCommandSourceStack();
        for (String command : new String[]{"momentum status", "momentum preset sport", "momentum reload", "momentum sync", "momentum preset balanced"}) {
            require(server.getCommands().getDispatcher().execute(command, source) == 1, command);
            checks++;
        }
        var configClass = Class.forName("io.github.milkucha.momentum.config.MomentumConfig");
        var config = configClass.getMethod("get").invoke(null);
        require(configClass.getField("drivingPreset").get(config).toString().equals("BALANCED"), "server preset restored");
        checks++;

        var level = server.overworld();
        var frames = level.registryAccess().lookupOrThrow(AutomobileFrame.REGISTRY);
        for (String frame : new String[]{"wooden_doublemotorcar", "wooden_quadmotorcar", "lorry", "dmc12", "standard_formula"}) {
            require(!frames.getOrThrow(frameKey(frame)).value().empty(), "RIA frame registered: " + frame);
            checks++;
        }
        var quad = create(level, "wooden_quadmotorcar");
        var seatApi = Class.forName("com.sshakusora.riautomobility.seat.SeatSwitchingAutomobile");
        require(seatApi.isInstance(quad), "seat mixin installed");
        require((int)seatApi.getMethod("riautomobility$getSeatCount").invoke(quad) == 4, "quad has four built-in seats");
        checks += 2;
        var pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
        require(!pig.startRiding(quad), "RIA quad also rejects animals");
        pig.discard();
        checks++;
        var driver = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
        level.addFreshEntity(driver);
        require(driver.startRiding(quad), "quad driver boards");
        require((int)seatApi.getMethod("riautomobility$getSeatIndex", Entity.class).invoke(quad, driver) == 0, "driver starts in seat zero");
        require((boolean)seatApi.getMethod("riautomobility$cycleSeat", Entity.class).invoke(quad, driver), "seat cycle succeeds");
        require((int)seatApi.getMethod("riautomobility$getSeatIndex", Entity.class).invoke(quad, driver) == 1, "seat cycle moves to seat one");
        require(!(boolean)seatApi.getMethod("riautomobility$cycleSeat", Entity.class).invoke(quad, driver), "seat cycle cooldown enforced");
        checks += 5;
        for (int i = 0; i < 3; i++) {
            var rider = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
            level.addFreshEntity(rider);
            require(rider.startRiding(quad), "remaining quad seat " + i);
            checks++;
        }
        var extra = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
        require(!extra.startRiding(quad), "quad capacity enforced");
        extra.discard();
        checks++;
        for (var passenger : quad.getPassengers()) passenger.discard();
        quad.discard();

        var lorry = create(level, "lorry");
        var cargo = lorry.underlyingContainer();
        require(cargo != null && cargo.getContainerSize() == 54, "lorry cargo mixin exposes 54 slots");
        cargo.setItem(0, new ItemStack(Items.DIAMOND, 7));
        cargo.setItem(53, new ItemStack(Items.REDSTONE, 19));
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        require(lorry.save(output), "lorry NBT save succeeds");
        var input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult());
        var loaded = (AutomobileEntity)EntityType.loadEntityRecursive(input, level, EntitySpawnReason.LOAD, entity -> entity);
        require(loaded != null && loaded.underlyingContainer().getContainerSize() == 54, "lorry NBT restores cargo");
        require(loaded.underlyingContainer().getItem(0).is(Items.DIAMOND) && loaded.underlyingContainer().getItem(0).getCount() == 7, "first cargo stack preserved");
        require(loaded.underlyingContainer().getItem(53).is(Items.REDSTONE) && loaded.underlyingContainer().getItem(53).getCount() == 19, "last cargo stack preserved");
        checks += 5;
        // Clear disposable test cargo before discarding; no dropped-item side effects.
        cargo.clearContent();
        loaded.underlyingContainer().clearContent();
        loaded.discard();
        lorry.discard();
        LoggerFactory.getLogger("Ecosystem263Qa").info("AUTO263_ECOSYSTEM_PASS checks={}", checks);
    }

    private static ResourceKey<AutomobileFrame> frameKey(String id) {
        return ResourceKey.create(AutomobileFrame.REGISTRY, Identifier.fromNamespaceAndPath("riautomobility", id));
    }

    private static AutomobileEntity create(ServerLevel level, String frame) {
        var car = new AutomobileEntity(level);
        car.setComponents(level.registryAccess().lookupOrThrow(AutomobileFrame.REGISTRY).getOrThrow(frameKey(frame)),
                level.registryAccess().lookupOrThrow(AutomobileWheel.REGISTRY).getOrThrow(AutomobileWheel.STANDARD),
                level.registryAccess().lookupOrThrow(AutomobileEngine.REGISTRY).getOrThrow(AutomobileEngine.IRON));
        car.setPos(3.5, 200, 3.5);
        require(level.addFreshEntity(car), "test car added");
        return car;
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
