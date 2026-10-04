package io.github.foundationgames.automobility.block.entity;

import io.github.foundationgames.automobility.automobile.AutomobileEngine;
import io.github.foundationgames.automobility.automobile.AutomobileFrame;
import io.github.foundationgames.automobility.automobile.AutomobileStats;
import io.github.foundationgames.automobility.automobile.AutomobileWheel;
import io.github.foundationgames.automobility.automobile.attachment.front.FrontAttachment;
import io.github.foundationgames.automobility.automobile.attachment.rear.RearAttachment;
import io.github.foundationgames.automobility.automobile.render.RenderableAutomobile;
import io.github.foundationgames.automobility.block.AutomobileAssemblerBlock;
import io.github.foundationgames.automobility.block.AutomobilityBlocks;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.item.AutomobileEngineItem;
import io.github.foundationgames.automobility.item.AutomobileFrameItem;
import io.github.foundationgames.automobility.item.AutomobileWheelItem;
import io.github.foundationgames.automobility.item.AutomobilityItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class AutomobileAssemblerBlockEntity extends BlockEntity implements RenderableAutomobile {
    protected Holder<AutomobileFrame> frame = Holder.direct(AutomobileFrame.EMPTY);
    protected Holder<AutomobileEngine> engine = Holder.direct(AutomobileEngine.EMPTY);
    protected Holder<AutomobileWheel> wheel = Holder.direct(AutomobileWheel.EMPTY);
    protected int wheelCount = 0;

    public final List<Component> label = new ArrayList<>();
    protected final AutomobileStats stats = new AutomobileStats();

    public AutomobileAssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(AutomobilityBlocks.AUTOMOBILE_ASSEMBLER_ENTITY.require(), pos, state);
    }

    @Override
    public AutomobileFrame getFrame() {
        return frame.value();
    }

    @Override
    public AutomobileWheel getWheels() {
        return wheel.value();
    }

    @Override
    public AutomobileEngine getEngine() {
        return engine.value();
    }

    @Override
    public @Nullable RearAttachment getRearAttachment() {
        return null;
    }

    @Override
    public @Nullable FrontAttachment getFrontAttachment() {
        return null;
    }

    private void partChanged() {
        this.sync();
        this.setChanged();
        this.level.gameEvent(GameEvent.BLOCK_CHANGE, this.getBlockPos(), new GameEvent.Context(null, this.getBlockState()));
    }

    protected InteractionResult handleItemInteract(Player player, ItemStack stack) {
        // Returns success on the server since the client is never 100% confident that the action was valid
        // Subsequent handling is performed with the action result

        if (stack.is(AutomobilityItems.CROWBAR.require())) {
            if (!level.isClientSide()) {
                this.dropParts();
                this.partChanged();
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.FAIL;
        }
        if (this.frame.value().isEmpty() && stack.getItem() instanceof AutomobileFrameItem frameItem) {
            if (!level.isClientSide()) {
                this.frame = frameItem.lookupComponent(stack, getLevel().registryAccess());
                if (!player.isCreative()) {
                    stack.shrink(1);
                }
                this.partChanged();
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.FAIL;
        }
        if (!this.frame.value().isEmpty()) {
            if (this.engine.value().isEmpty() && stack.getItem() instanceof AutomobileEngineItem engineItem) {
                if (!level.isClientSide()) {
                    this.engine = engineItem.lookupComponent(stack, getLevel().registryAccess());
                    if (!player.isCreative()) {
                        stack.shrink(1);
                    }
                    this.partChanged();
                    return InteractionResult.SUCCESS;
                }
                return InteractionResult.FAIL;
            }
            if (stack.getItem() instanceof AutomobileWheelItem wheelItem) {
                if (!level.isClientSide()) {
                    var wheelType = wheelItem.lookupComponent(stack, getLevel().registryAccess());
                    if (this.wheel.value().isEmpty()) {
                        this.wheel = wheelType;
                        this.wheelCount = 0; // Fix wheel count if ever invalid
                    }
                    if (this.wheel == wheelType && this.wheelCount < this.frame.value().model().wheelBase().wheelCount()) {
                        this.wheelCount++;
                        if (!player.isCreative()) {
                            stack.shrink(1);
                        }
                        this.partChanged();
                        return InteractionResult.SUCCESS;
                    }
                } else {
                    return InteractionResult.FAIL;
                }
            }
        }
        if ((!this.level.isClientSide() && stack.is(AutomobilityItems.FRONT_ATTACHMENT.require())) || stack.is(AutomobilityItems.REAR_ATTACHMENT.require())) {
            player.sendOverlayMessage(AutomobileAssemblerBlock.INCOMPLETE_AUTOMOBILE_DIALOG);
        }

        return InteractionResult.FAIL;
    }

    public InteractionResult interact(Player player, ItemStack stack, InteractionHand hand) {
        var result = this.handleItemInteract(player, stack);

        if (!this.level.isClientSide() && result.consumesAction()) {
            if (!isComplete()) {
                level.playSound(null, this.worldPosition, SoundEvents.COPPER_PLACE, SoundSource.BLOCKS, 0.7f, 0.6f + (this.level.getRandom().nextFloat() * 0.15f));
            }

            tryConstructAutomobile();
            return InteractionResult.SUCCESS;
        }
        return result;
    }

    protected Vec3 centerPos() {
        return new Vec3(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.75, this.worldPosition.getZ() + 0.5);
    }

    public boolean isComplete() {
        return !this.frame.value().isEmpty() &&
               !this.engine.value().isEmpty() &&
               ((!this.wheel.value().isEmpty()) && (this.wheelCount >= this.frame.value().model().wheelBase().wheelCount()));
    }

    public void tryConstructAutomobile() {
        if (this.isComplete()) {
            var pos = this.centerPos();
            var auto = new AutomobileEntity(this.level);
            auto.setPos(pos);
            auto.setYRot(this.getAutomobileYaw(0));
            auto.setXRot(0);
            auto.setComponents(this.frame, this.wheel, this.engine);
            level.addFreshEntity(auto);

            level.players().forEach(p -> {
                if (p instanceof ServerPlayer player && p.blockPosition().distSqr(this.worldPosition) < 80000) {
                    player.connection.send(new ClientboundLevelParticlesPacket(ParticleTypes.EXPLOSION, false, false, pos.x, pos.y + 0.47, pos.z, 0, 0, 0, 0, 1));
                }
            });
            level.playSound(null, this.worldPosition, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.23f, 0.5f);

            this.clear();
        }
    }

    public void dropParts() {
        var pos = this.centerPos();

        this.frame.unwrapKey().ifPresent(key ->
                this.level.addFreshEntity(new ItemEntity(level, pos.x, pos.y, pos.z, AutomobilityItems.AUTOMOBILE_FRAME.require().createStack(key))));
        this.engine.unwrapKey().ifPresent(key ->
                this.level.addFreshEntity(new ItemEntity(level, pos.x, pos.y, pos.z, AutomobilityItems.AUTOMOBILE_ENGINE.require().createStack(key))));

        this.wheel.unwrapKey().ifPresent(key -> {
            var wheelStack = AutomobilityItems.AUTOMOBILE_WHEEL.require().createStack(key);
            wheelStack.setCount(this.wheelCount);
            this.level.addFreshEntity(new ItemEntity(level, pos.x, pos.y, pos.z, wheelStack));
        });

        this.clear();
    }

    public void clear() {
        this.frame = Holder.direct(AutomobileFrame.EMPTY);
        this.wheel = Holder.direct(AutomobileWheel.EMPTY);
        this.engine = Holder.direct(AutomobileEngine.EMPTY);
        this.wheelCount = 0;
    }

    private boolean hasAllParts() {
        return !this.frame.value().isEmpty() && !this.wheel.value().isEmpty() && !this.engine.value().isEmpty();
    }

    private void onComponentsUpdated() {
        if (level == null || level.isClientSide()) {
            this.label.clear();
            if (this.hasAllParts()) {
                this.stats.from(this.frame.value(), this.wheel.value(), this.engine.value());
                this.stats.appendTexts(this.label::add, this.stats);
            }
        }
    }

    private void sync() {
        if (this.level instanceof ServerLevel sWorld) {
            sWorld.getChunkSource().blockChanged(this.worldPosition);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);

        var registries = input.lookup();
        this.frame = registries.lookupOrThrow(AutomobileFrame.REGISTRY)
                .get(ResourceKey.create(AutomobileFrame.REGISTRY, Identifier.tryParse(input.getStringOr("frame", ""))))
                .map(r -> (Holder<AutomobileFrame>)r)
                .orElseGet(() -> Holder.direct(AutomobileFrame.EMPTY));
        this.engine = registries.lookupOrThrow(AutomobileEngine.REGISTRY)
                .get(ResourceKey.create(AutomobileEngine.REGISTRY, Identifier.tryParse(input.getStringOr("engine", ""))))
                .map(r -> (Holder<AutomobileEngine>)r)
                .orElseGet(() -> Holder.direct(AutomobileEngine.EMPTY));

        var wheelNbt = input.childOrEmpty("wheels");
        this.wheel = registries.lookupOrThrow(AutomobileWheel.REGISTRY)
                .get(ResourceKey.create(AutomobileWheel.REGISTRY, Identifier.tryParse(wheelNbt.getStringOr("type", ""))))
                .map(r -> (Holder<AutomobileWheel>)r)
                .orElseGet(() -> Holder.direct(AutomobileWheel.EMPTY));
        this.wheelCount = wheelNbt.getIntOr("count", 0);

        onComponentsUpdated();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);

        this.frame.unwrapKey().ifPresent(k -> output.putString("frame", k.identifier().toString()));
        this.engine.unwrapKey().ifPresent(k -> output.putString("engine", k.identifier().toString()));

        var wheelNbt = output.child("wheels");
        this.wheel.unwrapKey().ifPresent(k -> wheelNbt.putString("type", k.identifier().toString()));
        wheelNbt.putInt("count", this.wheelCount);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        this.saveAdditional(output);
        return output.buildResult();
    }

    protected boolean powered() {
        var state = this.level.getBlockState(this.worldPosition);
        return state.hasProperty(AutomobileAssemblerBlock.POWERED) && state.getValue(AutomobileAssemblerBlock.POWERED);
    }

    @Override
    public float getAutomobileYaw(float tickDelta) {
        var state = this.level.getBlockState(this.worldPosition);
        return state.hasProperty(AutomobileAssemblerBlock.FACING) ? (state.getValue(AutomobileAssemblerBlock.FACING).toYRot() - 90) : 0;
    }

    @Override
    public int getWheelCount() {
        return this.wheelCount;
    }

    @Override
    public float getRearAttachmentYaw(float tickDelta) {
        return 0;
    }

    @Override
    public float getWheelAngle(float tickDelta) {
        return this.powered() ? (this.getTime() + tickDelta) * 36 : 0;
    }

    @Override
    public float getSteering(float tickDelta) {
        return 0;
    }

    @Override
    public float getSuspensionBounce(float tickDelta) {
        return 0;
    }

    @Override
    public boolean engineRunning() {
        return this.powered();
    }

    @Override
    public int getBoostTimer() {
        return this.powered() ? 1 : 0;
    }

    @Override
    public int getTurboCharge() {
        return 0;
    }

    @Override
    public long getTime() {
        return this.level.getGameTime();
    }

    @Override
    public boolean automobileOnGround() {
        return false;
    }

    @Override
    public boolean debris() {
        return false;
    }

    @Override
    public Vector3f debrisColor() {
        return null;
    }
}
