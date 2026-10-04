package io.github.foundationgames.automobility.block;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.block.entity.AutomobileAssemblerBlockEntity;
import io.github.foundationgames.automobility.block.entity.AutopilotSignBlockEntity;
import io.github.foundationgames.automobility.item.AutopilotSignBlockItem;
import io.github.foundationgames.automobility.item.CreativeTabQueue;
import io.github.foundationgames.automobility.item.DashPanelItem;
import io.github.foundationgames.automobility.item.SlopeBlockItem;
import io.github.foundationgames.automobility.item.SteepSlopeBlockItem;
import io.github.foundationgames.automobility.item.TooltipBlockItem;
import io.github.foundationgames.automobility.platform.Platform;
import io.github.foundationgames.automobility.util.AUtils;
import io.github.foundationgames.automobility.util.Eventual;
import io.github.foundationgames.automobility.util.RegistryQueue;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.BiFunction;
import java.util.function.Function;

public enum AutomobilityBlocks {;
    public static final Eventual<Block> GARAGE = register("garage", key -> new GarageBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).setId(key)), Automobility.TAB);
    public static final Eventual<Block> AUTO_MECHANIC_TABLE = register("auto_mechanic_table", key -> new AutoMechanicTableBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK.weathering().unaffected()).setId(key)), Automobility.TAB);
    public static final Eventual<Block> AUTOMOBILE_ASSEMBLER = register("automobile_assembler", key -> new AutomobileAssemblerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.ANVIL).setId(key)), Automobility.TAB);
    public static final Eventual<Block> AUTOPILOT_SIGN = register("autopilot_sign", key -> new AutopilotSignBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_TRAPDOOR)
            .lightLevel(s -> 1).emissiveRendering(s -> true).noCollision().setId(key)),
            (b, itemKey) -> new AutopilotSignBlockItem(b, AutomobilityBlockItems.properties(itemKey)), Automobility.TAB);
    public static final Eventual<Block> AUTOMOBILE_PRESSURE_PLATE = register("automobile_pressure_plate", key -> new AutomobilePressurePlateBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE).setId(key)), Automobility.TAB);

    public static final Eventual<Block> SLOPE = register("slope", key -> new SlopeBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).setId(key)), (b, itemKey) -> new SlopeBlockItem(b, AutomobilityBlockItems.properties(itemKey)), Automobility.TAB);
    public static final Eventual<Block> STEEP_SLOPE = register("steep_slope", key -> new SteepSlopeBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).setId(key)), (b, itemKey) -> new SteepSlopeBlockItem(b, AutomobilityBlockItems.properties(itemKey)), Automobility.TAB);

    public static final Eventual<Block> SLOPE_WITH_DASH_PANEL = register("slope_with_dash_panel", key -> new SlopeWithDashPanelBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
            .lightLevel(s -> s.getValue(DashPanelBlock.POWERED) ? 0 : 1).emissiveRendering(s -> !s.getValue(DashPanelBlock.POWERED)).setId(key)));
    public static final Eventual<Block> STEEP_SLOPE_WITH_DASH_PANEL = register("steep_slope_with_dash_panel", key -> new SteepSlopeWithDashPanelBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
            .lightLevel(s -> s.getValue(DashPanelBlock.POWERED) ? 0 : 1).emissiveRendering(s -> !s.getValue(DashPanelBlock.POWERED)).setId(key)));
    public static final Eventual<Block> DASH_PANEL = register("dash_panel", key -> new DashPanelBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
            .lightLevel(s -> s.getValue(DashPanelBlock.POWERED) ? 0 : 1).emissiveRendering(s -> !s.getValue(DashPanelBlock.POWERED)).noCollision().setId(key)), (b, itemKey) -> new DashPanelItem(b, AutomobilityBlockItems.properties(itemKey)), Automobility.TAB);

    public static final Eventual<Block> GRASS_OFF_ROAD = register("grass_off_road", key -> new OffRoadBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GRASS_BLOCK).noCollision().setId(key), AUtils.colorFromInt(0x406918)), Automobility.TAB);
    public static final Eventual<Block> DIRT_OFF_ROAD = register("dirt_off_road", key -> new OffRoadBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT).noCollision().setId(key), AUtils.colorFromInt(0x594227)), Automobility.TAB);
    public static final Eventual<Block> SAND_OFF_ROAD = register("sand_off_road", key -> new OffRoadBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SAND).noCollision().setId(key), AUtils.colorFromInt(0xC2B185)), Automobility.TAB);
    public static final Eventual<Block> SNOW_OFF_ROAD = register("snow_off_road", key -> new OffRoadBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SNOW_BLOCK).noCollision().setId(key), AUtils.colorFromInt(0xD0E7ED)), Automobility.TAB);

    public static final Eventual<Block> LAUNCH_GEL = register("launch_gel", key -> new LaunchGelBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CLAY).strength(0.1f).sound(SoundType.HONEY_BLOCK).noCollision().setId(key)), Automobility.TAB);

    public static final Eventual<Block> ALLOW = register("allow", key -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.BEDROCK).sound(SoundType.METAL).setId(key)),
            (b, itemKey) -> new TooltipBlockItem(b, Component.translatable("tooltip.block.automobility.allow").withStyle(ChatFormatting.AQUA), AutomobilityBlockItems.properties(itemKey)));

    public static final Eventual<BlockEntityType<AutomobileAssemblerBlockEntity>> AUTOMOBILE_ASSEMBLER_ENTITY = RegistryQueue.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Automobility.rl("automobile_assembler"), () -> Platform.get().blockEntity(AutomobileAssemblerBlockEntity::new, AUTOMOBILE_ASSEMBLER.require()));
    public static final Eventual<BlockEntityType<AutopilotSignBlockEntity>> AUTOPILOT_SIGN_ENTITY = RegistryQueue.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Automobility.rl("autopilot_sign"), () -> Platform.get().blockEntity(AutopilotSignBlockEntity::new, AUTOPILOT_SIGN.require()));

    public static void init() {
    }

    public static Eventual<Block> register(String name, Function<ResourceKey<Block>, Block> block) {
        var id = Automobility.rl(name);
        var key = ResourceKey.create(Registries.BLOCK, id);
        return RegistryQueue.register(BuiltInRegistries.BLOCK, id, () -> block.apply(key));
    }

    public static Eventual<Block> register(String name, Function<ResourceKey<Block>, Block> block, CreativeTabQueue group) {
        return register(name, block, (b, itemKey) -> new BlockItem(b, AutomobilityBlockItems.properties(itemKey)), group);
    }

    public static Eventual<Block> register(String name, Function<ResourceKey<Block>, Block> block, BiFunction<Block, ResourceKey<Item>, BlockItem> item, CreativeTabQueue tab) {
        var blockPromise = register(name, block);
        var id = Automobility.rl(name);
        var itemKey = ResourceKey.create(Registries.ITEM, id);
        var itemPromise = RegistryQueue.register(BuiltInRegistries.ITEM, id, () -> item.apply(blockPromise.require(), itemKey));

        if (tab != null) {
            tab.queue(itemPromise);
        }

        return blockPromise;
    }

    public static Eventual<Block> register(String name, Function<ResourceKey<Block>, Block> block, BiFunction<Block, ResourceKey<Item>, BlockItem> item) {
        return register(name, block, item, null);
    }
}
