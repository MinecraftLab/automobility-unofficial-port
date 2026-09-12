package io.github.foundationgames.automobility.entity;

import com.mojang.authlib.GameProfile;
import io.github.foundationgames.automobility.automobile.attachment.RearAttachmentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/** Isolated dedicated-server checks; this source set is never included in the mod JAR. */
public final class AnimalBoardingQa implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var logger = LoggerFactory.getLogger("AnimalBoardingQa");
            int checks = 0;
            try {
                var level = server.overworld();
                level.getChunk(0, 0);
                for (int x = 0; x < 8; x++) {
                    for (int z = 0; z < 8; z++) {
                        level.setBlockAndUpdate(new BlockPos(x, 199, z), Blocks.STONE.defaultBlockState());
                    }
                }
                var auto = new AutomobileEntity(level);
                auto.setPos(3.5, 200, 3.5);
                level.addFreshEntity(auto);

                var player = new ServerPlayer(server, level,
                        new GameProfile(UUID.randomUUID(), "Auto34SeatCheck"),
                        ClientInformation.createDefault());
                require(auto.canAddPassenger(player), "player may use an empty car");
                checks++;

                List<EntityType<?>> animalTypes = List.of(
                        EntityTypes.PIG, EntityTypes.COW, EntityTypes.SHEEP, EntityTypes.CHICKEN,
                        EntityTypes.WOLF, EntityTypes.CAT, EntityTypes.HORSE, EntityTypes.BEE,
                        EntityTypes.FOX, EntityTypes.RABBIT, EntityTypes.FROG, EntityTypes.AXOLOTL,
                        EntityTypes.COD, EntityTypes.SQUID, EntityTypes.GLOW_SQUID, EntityTypes.DOLPHIN,
                        EntityTypes.BAT);
                for (var type : animalTypes) {
                    Entity animal = type.create(level, EntitySpawnReason.COMMAND);
                    require(animal != null, "created " + type);
                    require(!auto.canAddPassenger(animal), "seat rejects " + type);
                    require(!animal.startRiding(auto), "normal mount rejects " + type);
                    require(!animal.isPassenger() && auto.getPassengers().isEmpty(), "rejection is atomic " + type);
                    animal.discard();
                    checks++;
                }

                // Keep automatic pickup available for non-animal mobs.
                var pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
                pig.setNoAi(true);
                pig.setPos(auto.position());
                level.addFreshEntity(pig);
                auto.tick();
                require(!pig.isPassenger() && auto.getPassengers().isEmpty(), "touching pig does not auto-board");
                pig.discard();
                checks++;

                var driver = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
                driver.setNoAi(true);
                driver.setPos(auto.position());
                level.addFreshEntity(driver);
                auto.tick();
                require(driver.getVehicle() == auto, "touching villager still auto-boards");
                require(!auto.canAddPassenger(player), "normal one-seat capacity retained");
                checks++;

                auto.setRearAttachment(RearAttachmentType.PASSENGER_SEAT);
                require(auto.canAddPassenger(player), "player can use free rear seat");
                var rearAnimal = EntityTypes.CHICKEN.create(level, EntitySpawnReason.COMMAND);
                require(!rearAnimal.startRiding(auto), "animal cannot take free rear seat");
                rearAnimal.discard();
                var secondVillager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
                secondVillager.setNoAi(true);
                secondVillager.setPos(auto.position());
                level.addFreshEntity(secondVillager);
                require(secondVillager.startRiding(auto), "non-animal rear passenger accepted");
                require(!auto.canAddPassenger(player), "two-seat capacity retained");
                checks++;

                // Forced mounts match vanilla's load path, which bypasses canAddPassenger.
                var legacyPig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
                var legacyChicken = EntityTypes.CHICKEN.create(level, EntitySpawnReason.COMMAND);
                require(legacyPig.startRiding(auto, true, false), "legacy pig forced mount");
                require(legacyChicken.startRiding(auto, true, false), "legacy chicken forced mount");
                var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                require(auto.save(output), "save automobile with mixed passengers");
                var saved = output.buildResult();
                for (Entity passenger : auto.getPassengers()) passenger.discard();
                auto.discard();

                var input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved);
                var restored = (AutomobileEntity) EntityType.loadEntityRecursive(
                        input, level, EntitySpawnReason.LOAD, entity -> entity);
                require(restored != null && restored.getPassengers().size() == 4, "mixed passengers restored from NBT");
                level.addFreshEntityWithPassengers(restored);
                var restoredAnimals = restored.getPassengers().stream()
                        .filter(entity -> entity.getType() == EntityTypes.PIG || entity.getType() == EntityTypes.CHICKEN)
                        .toList();
                require(restoredAnimals.size() == 2, "two saved animals present before tick");
                restored.tick();
                require(restored.getPassengers().size() == 2
                        && restored.getPassengers().stream().allMatch(entity -> entity.getType() == EntityTypes.VILLAGER),
                        "tick releases only animals, retaining both villagers");
                for (Entity animal : restoredAnimals) {
                    require(!animal.isPassenger() && animal.isAlive() && !animal.isRemoved(),
                            "restored animal dismounted alive");
                    animal.discard();
                }
                checks++;
                for (Entity passenger : restored.getPassengers()) passenger.discard();
                restored.discard();
                player.discard();
                logger.info("AUTO34_ANIMAL_BOARDING_PASS checks={}", checks);
            } catch (Throwable failure) {
                logger.error("AUTO34_ANIMAL_BOARDING_FAIL after checks=" + checks, failure);
            } finally {
                server.halt(false);
            }
        });
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
