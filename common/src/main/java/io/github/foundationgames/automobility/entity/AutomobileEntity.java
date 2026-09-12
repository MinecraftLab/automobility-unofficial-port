package io.github.foundationgames.automobility.entity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.AutomobileData;
import io.github.foundationgames.automobility.automobile.AutomobileEngine;
import io.github.foundationgames.automobility.automobile.AutomobileFrame;
import io.github.foundationgames.automobility.automobile.AutomobileStats;
import io.github.foundationgames.automobility.automobile.AutomobileWheel;
import io.github.foundationgames.automobility.automobile.WheelBase;
import io.github.foundationgames.automobility.automobile.attachment.FrontAttachmentType;
import io.github.foundationgames.automobility.automobile.attachment.RearAttachmentType;
import io.github.foundationgames.automobility.automobile.attachment.front.FrontAttachment;
import io.github.foundationgames.automobility.automobile.attachment.rear.DeployableRearAttachment;
import io.github.foundationgames.automobility.automobile.attachment.rear.RearAttachment;
import io.github.foundationgames.automobility.automobile.render.RenderableAutomobile;
import io.github.foundationgames.automobility.block.AutomobileAssemblerBlock;
import io.github.foundationgames.automobility.block.LaunchGelBlock;
import io.github.foundationgames.automobility.block.OffRoadBlock;
import io.github.foundationgames.automobility.block.SpecialAutomobileColliderBlock;
import io.github.foundationgames.automobility.controller.AutomobileController;
import io.github.foundationgames.automobility.item.AutomobileInteractable;
import io.github.foundationgames.automobility.item.AutomobilityItems;
import io.github.foundationgames.automobility.particle.AutomobilityParticles;
import io.github.foundationgames.automobility.platform.Platform;
import io.github.foundationgames.automobility.screen.AutomobileContainerLevelAccess;
import io.github.foundationgames.automobility.sound.AutomobilitySounds;
import io.github.foundationgames.automobility.util.AUtils;
import io.github.foundationgames.automobility.util.duck.CollisionArea;
import io.github.foundationgames.automobility.util.network.ClientPackets;
import io.github.foundationgames.automobility.util.network.CommonPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.AgeableWaterCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class AutomobileEntity extends Entity implements RenderableAutomobile, EntityWithInventory, EntityWithContainer {
    public static Consumer<AutomobileEntity> engineSound = e -> {};
    public static Consumer<AutomobileEntity> skidSound = e -> {};
    public static Consumer<AutomobileEntity> hornSound = e -> {};

    private static final EntityDataAccessor<Float> REAR_ATTACHMENT_YAW = SynchedEntityData.defineId(AutomobileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> REAR_ATTACHMENT_ANIMATION = SynchedEntityData.defineId(AutomobileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FRONT_ATTACHMENT_ANIMATION = SynchedEntityData.defineId(AutomobileEntity.class, EntityDataSerializers.FLOAT);

    private static final EntityDataAccessor<Holder<AutomobileFrame>> FRAME_TYPE = SynchedEntityData.defineId(AutomobileEntity.class, AutomobileFrame.SERIALIZER);
    private static final EntityDataAccessor<Holder<AutomobileWheel>> WHEEL_TYPE = SynchedEntityData.defineId(AutomobileEntity.class, AutomobileWheel.SERIALIZER);
    private static final EntityDataAccessor<Holder<AutomobileEngine>> ENGINE_TYPE = SynchedEntityData.defineId(AutomobileEntity.class, AutomobileEngine.SERIALIZER);

    private RearAttachment rearAttachment;
    private FrontAttachment frontAttachment;
    private @Nullable Identifier unresolvedFrameId;
    private @Nullable Identifier unresolvedWheelId;
    private @Nullable Identifier unresolvedEngineId;
    private @Nullable CompoundTag unresolvedRearAttachmentData;
    private @Nullable CompoundTag unresolvedFrontAttachmentData;

    private final AutomobileStats stats = new AutomobileStats();

    public static final int SMALL_TURBO_TIME = 35;
    public static final int MEDIUM_TURBO_TIME = 70;
    public static final int LARGE_TURBO_TIME = 115;
    public static final float TERMINAL_VELOCITY = -1.2f;
    private static final int RECENT_DISMOUNT_GRACE_TICKS = 30;
    private static final int UNMANNED_COAST_SETTLE_MAX_TICKS = 120;
    private static final int POST_UNMANNED_COAST_GRACE_TICKS = 60;

    public final Input input = new Input();
    private boolean prevHoldDrift = input.holdingDrift;

    private long clientTime;

    private double trackedX;
    private double trackedY;
    private double trackedZ;
    private float trackedYaw;
    private int lerpTicks;

    private static final int CLIENT_SYNC_INTERVAL = 4;
    private static final int SERVER_SYNC_INTERVAL = 4;
    private static final int MAX_CLIENT_SYNCS_PER_TICK = 2;
    private static final int CLIENT_INPUT_TIMEOUT_TICKS = 20;
    private int clientSyncTicks = CLIENT_SYNC_INTERVAL;
    private int serverSyncTicks = 0;
    private long lastClientSyncTick = Long.MIN_VALUE;
    private long lastAcceptedClientInputTick = Long.MIN_VALUE;
    private int clientSyncsThisTick = 0;

    public final List<HitboxEntity> hitboxes = new ArrayList<>();
    private EntityDimensions size;
    private AABB cullingBox = new AABB(0, 0, 0, 0, 0, 0);

    private boolean dirty = false;

    private float engineSpeed = 0;
    private float boostSpeed = 0;
    private float speedDirection = 0;
    private float lastBoostSpeed = boostSpeed;

    private float lossySyncedEffectiveSpeed = 0;
    private float lastSyncedBoostSpeed = 0;
    private float lossySyncedBoostSpeed = 0;
    private float lastSyncedWheelAngle = 0;
    private float lossySyncedWheelAngle = 0;
    private int dataLerpTicks;

    private int boostTimer = 0;
    private float boostPower = 0;
    private int jumpCooldown = 0;

    private float hSpeed = 0;
    private float vSpeed = 0;

    private Vec3 addedVelocity = getDeltaMovement();

    private float steering = 0;
    private float lastSteering = steering;

    private float angularSpeed = 0;

    private float wheelAngle = 0;
    private float lastWheelAngle = 0;

    private final Displacement displacement = new AutomobileEntity.Displacement();
    private transient boolean clientAuthoritativePositionRefresh = false;
    private transient boolean clientRelativePositionRefresh = false;

    private boolean drifting = false;
    private boolean burningOut = false;
    private int driftDir = 0;
    private int turboCharge = 0;

    private boolean honking = false;

    private boolean automobileOnGround = true;
    private boolean wasOnGround = automobileOnGround;
    private boolean isFloorDirectlyBelow = true;
    private boolean touchingWall = false;
    private int hadVehicleCollision = 0;

    private Vec3 lastVelocity = Vec3.ZERO;
    private Vec3 lastMeasuredPos = Vec3.ZERO;

    private float measuredAngVel = 0;
    private float smoothAngVel = 0;

    private Vec3 prevTailPos = null;
    private transient Vec3 previousServerTickEndPosition = null;
    private transient ResourceKey<Level> previousServerTickEndDimension = null;
    private float prevYawForRotate = 0;

    private int slopeStickingTimer = 0;
    private float grip = 1;

    private int suspensionBounceTimer = 0;
    private int lastSusBounceTimer = suspensionBounceTimer;

    private final Deque<Double> prevYDisplacements = new ArrayDeque<>();

    private boolean offRoad = false;
    private Vector3f debrisColor = new Vector3f();

    private int fallTicks = 0;

    private int despawnTime = -1;
    private int despawnCountdown = 0;
    private boolean decorative = false;
    private final Map<UUID, Integer> recentDismounts = new HashMap<>();
    private int unmannedCoastSettleTicks = 0;

    private boolean wasEngineRunning = false;

    private float standStillTime = -1.3f;

    public void writeSyncStateData(FriendlyByteBuf buf) {
        buf.writeInt(boostTimer);
        buf.writeFloat(steering);
        buf.writeFloat(wheelAngle);
        buf.writeInt(turboCharge);
        buf.writeFloat(engineSpeed);
        buf.writeFloat(boostSpeed);
        buf.writeDouble(this.getX());
        buf.writeDouble(this.getY());
        buf.writeDouble(this.getZ());
        buf.writeFloat(this.getYRot());
        input.writePacket(buf);

        buf.writeBoolean(drifting);
        buf.writeBoolean(burningOut);

        displacement.writeSyncData(buf);
    }

    public void readSyncStateData(FriendlyByteBuf buf) {
        int syncedBoostTimer = buf.readInt();
        float syncedSteering = buf.readFloat();
        float syncedWheelAngle = buf.readFloat();
        int syncedTurboCharge = buf.readInt();
        float syncedEngineSpeed = buf.readFloat();
        float syncedBoostSpeed = buf.readFloat();
        double syncedX = buf.readDouble();
        double syncedY = buf.readDouble();
        double syncedZ = buf.readDouble();
        float syncedYaw = buf.readFloat();
        var syncedInput = new Input();
        syncedInput.readPacket(buf);

        boolean syncedDrifting = buf.readBoolean();
        boolean syncedBurningOut = buf.readBoolean();

        // Serverbound legacy packets are input envelopes, not authoritative snapshots. Keep this
        // guard even though CommonPackets now calls applyClientInputState directly, so another mod
        // cannot accidentally reintroduce client-authored physics through this public method.
        if (!this.level().isClientSide()) {
            buf.skipBytes(Double.BYTES + (4 * Float.BYTES));
            applyClientInputState(
                    syncedInput.accelerating,
                    syncedInput.braking,
                    syncedInput.steering,
                    syncedInput.holdingDrift,
                    syncedInput.holdingHorn
            );
            return;
        }

        boostTimer = syncedBoostTimer;
        steering = syncedSteering;
        wheelAngle = syncedWheelAngle;
        turboCharge = syncedTurboCharge;
        engineSpeed = syncedEngineSpeed;
        boostSpeed = syncedBoostSpeed;

        if (!(this.getControllingPassenger() instanceof Player player && player.isLocalPlayer())) {
            input.setInputs(
                    syncedInput.accelerating,
                    syncedInput.braking,
                    syncedInput.steering,
                    syncedInput.holdingDrift,
                    syncedInput.holdingHorn
            );
        }

        setDrifting(syncedDrifting);
        setBurningOut(syncedBurningOut);

        boolean hadSyncedDisplacement = this.displacement.hasSyncedData();
        Vec3 positionBeforeStoppedSync = this.position();
        this.displacement.readSyncData(buf, false);

        if (this.level().isClientSide()) {
            boolean stoppedSync = boostTimer == 0 && Math.abs(engineSpeed) < 1.0E-4f && Math.abs(boostSpeed) < 1.0E-4f && !input.accelerating && !input.braking;
            if (stoppedSync) {
                hSpeed = 0;
                vSpeed = 0;
                addedVelocity = Vec3.ZERO;
                lastVelocity = Vec3.ZERO;
                angularSpeed = 0;
                lerpTicks = 0;
                trackedX = syncedX;
                trackedY = syncedY;
                trackedZ = syncedZ;
                trackedYaw = syncedYaw;
                this.setPos(syncedX, syncedY, syncedZ);
                this.setYRot(syncedYaw);
                this.syncPacketPositionCodec(syncedX, syncedY, syncedZ);
                if (hadSyncedDisplacement
                        && AutomobilePositionUpdatePolicy.positionChanged(positionBeforeStoppedSync, this.position())) {
                    this.clientOnAuthoritativePositionApplied(positionBeforeStoppedSync);
                }
            }

            if (stoppedSync) {
                snapClientHitboxesFromAutomobile();
            } else {
                updateCullingBox();
            }
        }

        this.dataLerpTicks = CLIENT_SYNC_INTERVAL;
    }

    public boolean applyClientInputState(boolean accelerating, boolean braking, float inputSteering,
                                         boolean holdingDrift, boolean holdingHorn) {
        if (this.level().isClientSide() || !Float.isFinite(inputSteering)) {
            return false;
        }

        this.lastAcceptedClientInputTick = this.level().getGameTime();
        return this.input.setInputs(
                accelerating,
                braking,
                Mth.clamp(inputSteering, -1, 1),
                holdingDrift,
                holdingHorn
        );
    }

    @Override
    public void readAdditionalSaveData(ValueInput nbt) {
        var reg = nbt.lookup();
        var frameId = Identifier.tryParse(nbt.getStringOr("frame", ""));
        var wheelId = Identifier.tryParse(nbt.getStringOr("wheels", ""));
        var engineId = Identifier.tryParse(nbt.getStringOr("engine", ""));
        var frameEntry = frameId != null
                ? reg.lookupOrThrow(AutomobileFrame.REGISTRY).get(ResourceKey.create(AutomobileFrame.REGISTRY, frameId))
                : Optional.<Holder.Reference<AutomobileFrame>>empty();
        var wheelEntry = wheelId != null
                ? reg.lookupOrThrow(AutomobileWheel.REGISTRY).get(ResourceKey.create(AutomobileWheel.REGISTRY, wheelId))
                : Optional.<Holder.Reference<AutomobileWheel>>empty();
        var engineEntry = engineId != null
                ? reg.lookupOrThrow(AutomobileEngine.REGISTRY).get(ResourceKey.create(AutomobileEngine.REGISTRY, engineId))
                : Optional.<Holder.Reference<AutomobileEngine>>empty();
        setComponents(
                frameEntry.<Holder<AutomobileFrame>>map(r -> r).orElseGet(() -> Holder.direct(AutomobileFrame.EMPTY)),
                wheelEntry.<Holder<AutomobileWheel>>map(r -> r).orElseGet(() -> Holder.direct(AutomobileWheel.EMPTY)),
                engineEntry.<Holder<AutomobileEngine>>map(r -> r).orElseGet(() -> Holder.direct(AutomobileEngine.EMPTY))
        );
        this.unresolvedFrameId = frameId != null && frameEntry.isEmpty() ? frameId : null;
        this.unresolvedWheelId = wheelId != null && wheelEntry.isEmpty() ? wheelId : null;
        this.unresolvedEngineId = engineId != null && engineEntry.isEmpty() ? engineId : null;

        var rAtt = nbt.read("rearAttachment", CompoundTag.CODEC).orElseGet(CompoundTag::new);
        var rearType = RearAttachment.fromNbt(rAtt);
        if (rearType != null) {
            setRearAttachment(rearType);
            rearAttachment.readNbt(rAtt, this.level().registryAccess());
        } else {
            setRearAttachment(RearAttachmentType.EMPTY);
            this.unresolvedRearAttachmentData = AutomobileComponentPersistence.unresolvedAttachmentData(rAtt, false);
        }

        var fAtt = nbt.read("frontAttachment", CompoundTag.CODEC).orElseGet(CompoundTag::new);
        var frontType = FrontAttachment.fromNbt(fAtt);
        if (frontType != null) {
            setFrontAttachment(frontType);
            frontAttachment.readNbt(fAtt, this.level().registryAccess());
        } else {
            setFrontAttachment(FrontAttachmentType.EMPTY);
            this.unresolvedFrontAttachmentData = AutomobileComponentPersistence.unresolvedAttachmentData(fAtt, false);
        }

        engineSpeed = nbt.getFloatOr("engineSpeed", engineSpeed);
        boostSpeed = nbt.getFloatOr("boostSpeed", boostSpeed);
        boostTimer = nbt.getIntOr("boostTimer", boostTimer);
        boostPower = nbt.getFloatOr("boostPower", boostPower);
        speedDirection = nbt.getFloatOr("speedDirection", speedDirection);
        vSpeed = nbt.getFloatOr("verticalSpeed", vSpeed);
        hSpeed = nbt.getFloatOr("horizontalSpeed", hSpeed);
        addedVelocity = nbt.read("addedVelocity", CompoundTag.CODEC).map(AUtils::v3dFromNbt).orElse(addedVelocity);
        lastVelocity = nbt.read("lastVelocity", CompoundTag.CODEC).map(AUtils::v3dFromNbt).orElse(lastVelocity);
        angularSpeed = nbt.getFloatOr("angularSpeed", angularSpeed);
        steering = nbt.getFloatOr("steering", steering);
        wheelAngle = nbt.getFloatOr("wheelAngle", wheelAngle);
        drifting = nbt.getBooleanOr("drifting", drifting);
        driftDir = nbt.getIntOr("driftDir", driftDir);
        burningOut = nbt.getBooleanOr("burningOut", burningOut);
        honking = nbt.getBooleanOr("honking", honking);
        turboCharge = nbt.getIntOr("turboCharge", turboCharge);
        input.accelerating = nbt.getBooleanOr("accelerating", input.accelerating);
        input.braking = nbt.getBooleanOr("braking", input.braking);
        input.steering = nbt.getFloatOr("steeringInput", input.steering);
        input.holdingDrift = nbt.getBooleanOr("holdingDrift", input.holdingDrift);
        input.holdingHorn = nbt.getBooleanOr("holdingHorn", input.holdingHorn);
        fallTicks = nbt.getIntOr("fallTicks", fallTicks);
        despawnTime = nbt.getIntOr("despawnTime", despawnTime);
        despawnCountdown = nbt.getIntOr("despawnCountdown", despawnCountdown);
        decorative = nbt.getBooleanOr("decorative", decorative);
    }

    @Override
    public void addAdditionalSaveData(ValueOutput nbt) {
        var frameId = AutomobileComponentPersistence.idToSave(this.entityData.get(FRAME_TYPE), this.unresolvedFrameId);
        var wheelId = AutomobileComponentPersistence.idToSave(this.entityData.get(WHEEL_TYPE), this.unresolvedWheelId);
        var engineId = AutomobileComponentPersistence.idToSave(this.entityData.get(ENGINE_TYPE), this.unresolvedEngineId);
        if (frameId != null) nbt.putString("frame", frameId.toString());
        if (wheelId != null) nbt.putString("wheels", wheelId.toString());
        if (engineId != null) nbt.putString("engine", engineId.toString());
        nbt.store("rearAttachment", CompoundTag.CODEC, AutomobileComponentPersistence.attachmentDataToSave(
                rearAttachment.toNbt(), this.unresolvedRearAttachmentData));
        nbt.store("frontAttachment", CompoundTag.CODEC, AutomobileComponentPersistence.attachmentDataToSave(
                frontAttachment.toNbt(), this.unresolvedFrontAttachmentData));
        nbt.putFloat("engineSpeed", engineSpeed);
        nbt.putFloat("boostSpeed", boostSpeed);
        nbt.putInt("boostTimer", boostTimer);
        nbt.putFloat("boostPower", boostPower);
        nbt.putFloat("speedDirection", speedDirection);
        nbt.putFloat("verticalSpeed", vSpeed);
        nbt.putFloat("horizontalSpeed", hSpeed);
        nbt.store("addedVelocity", CompoundTag.CODEC, AUtils.v3dToNbt(addedVelocity));
        nbt.store("lastVelocity", CompoundTag.CODEC, AUtils.v3dToNbt(lastVelocity));
        nbt.putFloat("angularSpeed", angularSpeed);
        nbt.putFloat("steering", steering);
        nbt.putFloat("wheelAngle", wheelAngle);
        nbt.putBoolean("drifting", drifting);
        nbt.putInt("driftDir", driftDir);
        nbt.putBoolean("burningOut", burningOut);
        nbt.putBoolean("honking", honking);
        nbt.putInt("turboCharge", turboCharge);
        nbt.putBoolean("accelerating", input.accelerating);
        nbt.putBoolean("braking", input.braking);
        nbt.putFloat("steeringInput", input.steering);
        nbt.putBoolean("holdingDrift", input.holdingDrift);
        nbt.putBoolean("holdingHorn", input.holdingHorn);
        nbt.putInt("fallTicks", fallTicks);
        nbt.putInt("despawnTime", despawnTime);
        nbt.putInt("despawnCountdown", despawnCountdown);
        nbt.putBoolean("decorative", decorative);
    }

    public AutomobileEntity(EntityType<?> type, Level world) {
        super(type, world);

        this.setRearAttachment(RearAttachmentType.REGISTRY.getOrDefault(null));
        this.setFrontAttachment(FrontAttachmentType.REGISTRY.getOrDefault(null));

        this.size = type.getDimensions();
    }

    public AutomobileEntity(Level world) {
        this(AutomobilityEntities.AUTOMOBILE.require(), world);
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        if (level().isClientSide()) {
            ClientPackets.requestSyncAutomobileComponentsPacket(this);
        }
    }

    private void controllerAction(Consumer<AutomobileController> action) {
        if (this.level().isClientSide()) {
            if (this.getControllingPassenger() == Minecraft.getInstance().player) {
                action.accept(Platform.get().controller());
            }
        }
    }

    @Override
    public AutomobileFrame getFrame() {
        return this.entityData.get(FRAME_TYPE).value();
    }

    @Override
    public AutomobileWheel getWheels() {
        return this.entityData.get(WHEEL_TYPE).value();
    }

    @Override
    public AutomobileEngine getEngine() {
        return this.entityData.get(ENGINE_TYPE).value();
    }

    @Override
    public @Nullable RearAttachment getRearAttachment() {
        return rearAttachment;
    }

    @Override
    public @Nullable FrontAttachment getFrontAttachment() {
        return frontAttachment;
    }

    @Override
    public float getSteering(float tickDelta) {
        return Mth.lerp(tickDelta, lastSteering, steering);
    }

    @Override
    public float getWheelAngle(float tickDelta) {
        if (this.isLocalInstanceAuthoritative()) {
            return Mth.lerp(tickDelta, lastWheelAngle, wheelAngle);
        }

        return Mth.lerp(tickDelta, lastSyncedWheelAngle, lossySyncedWheelAngle);
    }

    public float getBoostSpeed(float tickDelta) {
        if (this.isLocalInstanceAuthoritative()) {
            return Mth.lerp(tickDelta, lastBoostSpeed, boostSpeed);
        }

        return Mth.lerp(tickDelta, lastSyncedBoostSpeed, lossySyncedBoostSpeed);
    }

    @Override
    public float getSuspensionBounce(float tickDelta) {
        return Mth.lerp(tickDelta, lastSusBounceTimer, suspensionBounceTimer);
    }

    @Override
    public boolean engineRunning() {
        boolean running = this.boostTimer > 0 || isVehicle();

        if (!running) {
            var fAtt = getFrontAttachment();
            if (fAtt != null) {
                running = fAtt.isProvidingAlternativeInputs(this, getFirstPassenger());
            }
        }

        return running;
    }

    @Override
    public int getTurboCharge() {
        return turboCharge;
    }

    @Override
    public long getTime() {
        return this.clientTime;
    }

    public float getHSpeed() {
        return hSpeed;
    }

    public float getVSpeed() {
        return vSpeed;
    }

    @Override
    public int getBoostTimer() {
        return boostTimer;
    }

    public float calculateEffectiveSpeed() {
        if (this.getControllingPassenger() instanceof Player player && player.isLocalPlayer()) {
            return (float) Math.max(this.addedVelocity.length(), Math.abs(this.hSpeed));
        }

        return (float) Math.max(this.addedVelocity.length(), Math.abs(this.engineSpeed + this.boostSpeed));
    }

    public float getEffectiveSpeed() {
        if (this.isLocalInstanceAuthoritative()) {
            return calculateEffectiveSpeed();
        }

        return this.lossySyncedEffectiveSpeed;
    }

    @Override
    public boolean automobileOnGround() {
        return automobileOnGround;
    }

    @Override
    public boolean debris() {
        return offRoad && hSpeed != 0;
    }

    @Override
    public Vector3f debrisColor() {
        return debrisColor;
    }

    public boolean burningOut() {
        return burningOut;
    }

    public boolean honking() {
        return honking;
    }

    private void setDrifting(boolean drifting) {
        if (this.level().isClientSide()) {
            if (!this.drifting && drifting) {
                skidSound.accept(this);
            }
        }

        this.drifting = drifting;
    }

    private void setBurningOut(boolean burningOut) {
        if (this.level().isClientSide() && !this.drifting && !this.burningOut && burningOut) {
            skidSound.accept(this);
        }

        if (this.burningOut != burningOut || (this.turboCharge >= LARGE_TURBO_TIME) != burningOut) {
            controllerAction(c -> c.updateMaxChargeRumbleState(burningOut));
        }

        this.burningOut = burningOut;
    }

    private void setHonking(boolean honking) {
        if (this.level().isClientSide() && !this.honking && honking) {
            hornSound.accept(this);
        }

        this.honking = honking;
    }

    public boolean isDrifting() {
        return this.drifting;
    }

    public boolean isBike() {
        return this.getFrame().model().wheelBase().wheelCount() == 2;
    }

    public boolean isDecorative() {
        return this.decorative;
    }

    public <T extends RearAttachment> void setRearAttachment(RearAttachmentType<T> rearAttachment) {
        if (rearAttachment == null) {
            return;
        }
        this.unresolvedRearAttachmentData = null;
        if (this.rearAttachment == null || this.rearAttachment.type != rearAttachment) {
            if (this.rearAttachment != null) {
                this.rearAttachment.onRemoved();
            }

            this.rearAttachment = rearAttachment.constructor().apply(rearAttachment, this);
            this.rearAttachment.setYaw(this.getYRot());

            if (!level().isClientSide() && !this.rearAttachment.isRideable() && this.getPassengers().size() > 1) {
                this.getPassengers().get(1).stopRiding();
            }

            syncAttachments();
        }
    }

    public <T extends FrontAttachment> void setFrontAttachment(FrontAttachmentType<T> frontAttachment) {
        if (frontAttachment == null) {
            return;
        }
        this.unresolvedFrontAttachmentData = null;
        if (this.frontAttachment == null || this.frontAttachment.type != frontAttachment) {
            if (this.frontAttachment != null) {
                this.frontAttachment.onRemoved();
            }
            this.frontAttachment = frontAttachment.constructor().apply(frontAttachment, this);

            syncAttachments();
        }
    }

    public void setComponents(Holder<AutomobileFrame> frame, Holder<AutomobileWheel> wheel, Holder<AutomobileEngine> engine) {
        this.unresolvedFrameId = null;
        this.unresolvedWheelId = null;
        this.unresolvedEngineId = null;
        this.entityData.set(FRAME_TYPE, frame);
        this.entityData.set(WHEEL_TYPE, wheel);
        this.entityData.set(ENGINE_TYPE, engine);
        this.stats.from(getFrame(), getWheels(), getEngine());

        this.size = getFrame().makeBounds();
        this.displacement.applyWheelbase(getFrame().model().wheelBase());
        this.refreshDimensions();
    }

    public void verifyHitboxesFor(AutomobileFrame frame) {
        this.hitboxes.removeIf(Entity::isRemoved);

        if (this.level().isClientSide()) {
            return;
        }

        var boxes = frame.hitboxes();
        if (boxes.isEmpty()) boxes = List.of(AutomobileFrame.Hitbox.DEFAULT);

        if (!this.hitboxesMatch(boxes)) {
            this.recreateHitboxes(boxes);
        }
    }

    private boolean hitboxesMatch(List<AutomobileFrame.Hitbox> boxes) {
        if (this.hitboxes.size() != boxes.size()) {
            return false;
        }

        for (int i = 0; i < boxes.size(); i++) {
            if (!this.hitboxes.get(i).matches(boxes.get(i))) {
                return false;
            }
        }

        return true;
    }

    public boolean registerClientHitbox(HitboxEntity candidate) {
        if (!this.level().isClientSide()) {
            return false;
        }

        this.hitboxes.removeIf(Entity::isRemoved);

        var boxes = this.getFrame().hitboxes();
        if (boxes.isEmpty()) boxes = List.of(AutomobileFrame.Hitbox.DEFAULT);

        boolean expected = false;
        for (var box : boxes) {
            if (candidate.matches(box)) {
                expected = true;
                break;
            }
        }
        if (!expected) {
            return false;
        }

        HitboxEntity duplicate = null;
        for (var existing : this.hitboxes) {
            if (existing != candidate && existing.matches(candidate)) {
                duplicate = existing;
                break;
            }
        }

        if (duplicate != null) {
            if (duplicate.getId() > candidate.getId()) {
                return false;
            }
            duplicate.remove(RemovalReason.DISCARDED);
            this.hitboxes.remove(duplicate);
        }

        if (!this.hitboxes.contains(candidate)) {
            this.hitboxes.add(candidate);
        }
        return true;
    }

    private void recreateHitboxes(List<AutomobileFrame.Hitbox> boxes) {
        if (this.level().isClientSide()) {
            return;
        }

        this.hitboxes.forEach(e -> e.remove(RemovalReason.DISCARDED));
        this.hitboxes.clear();

        for (var box : boxes) {
            var boxEntity = new HitboxEntity(this.level(), this, box);
            boxEntity.updatePositionFromAutomobile();
            this.hitboxes.add(boxEntity);

            this.level().addFreshEntity(boxEntity);
        }

        this.updateCullingBox();
    }

    private void recreateHitboxesFor(AutomobileFrame frame) {
        var boxes = frame.hitboxes();
        if (boxes.isEmpty()) boxes = List.of(AutomobileFrame.Hitbox.DEFAULT);

        this.recreateHitboxes(boxes);
    }

    @Override
    public float maxUpStep() {
        return this.getWheels().size();
    }

    public void forPlayersTrackingMe(boolean ignoreDriver, Consumer<ServerPlayer> action) {
        Platform.get().forEachTrackingPlayer(this, player -> {
            if (!ignoreDriver || !isDriving(player)) {
                action.accept(player);
            }
        });
    }

    public Vec3 getTailPos() {
        return this.position()
                .add(new Vec3(0, 0, this.getFrame().model().rearAttachmentPos() * 0.0625)
                        .yRot((float) Math.toRadians(180 - this.getYRot()))
                );
    }

    public Vec3 getHeadPos() {
        return this.position()
                .add(new Vec3(0, 0, this.getFrame().model().frontAttachmentPos() * 0.0625)
                        .yRot((float) Math.toRadians(-this.getYRot()))
                );
    }

    public void updateCullingBox() {
        this.cullingBox = super.getBoundingBox();
        for (var hitbox : this.hitboxes) {
            this.cullingBox = this.cullingBox.minmax(hitbox.getBoundingBox());
        }
    }

    public AABB getBoundingBoxForCulling() {
        return this.cullingBox;
    }

    public boolean hasSpaceForPassengers() {
        var frame = this.getFrame();
        int maxPassengers = this.rearAttachment.isRideable() ? 2 : 1;
        if (frame != null) {
            maxPassengers += frame.model().passengerSeats().size();
        }
        return this.getPassengers().size() < maxPassengers;
    }

    public boolean isDriving(@Nullable Entity entity) {
        if (entity != null && entity == getFirstPassenger()) {
            var fAtt = getFrontAttachment();
            if (fAtt != null) {
                return fAtt.canDrive(entity);
            }

            return true;
        }

        return false;
    }

    public void setSpeed(float horizontal, float vertical) {
        this.hSpeed = horizontal;
        this.vSpeed = vertical;
    }

    public void clientOnAboutToDismount() {
        ClientPackets.sendServerboundAutomobileSyncPacket(this);
    }

    private void lerpAutomobileData() {
        this.lastSyncedWheelAngle = this.lossySyncedWheelAngle;
        this.lastSyncedBoostSpeed = this.lossySyncedBoostSpeed;
        if (this.dataLerpTicks <= 0) return;

        this.lossySyncedEffectiveSpeed = this.lossySyncedEffectiveSpeed + (this.calculateEffectiveSpeed() - this.lossySyncedEffectiveSpeed) / (float)this.dataLerpTicks;
        this.lossySyncedBoostSpeed = this.lossySyncedBoostSpeed + (this.boostSpeed - this.lossySyncedBoostSpeed) / (float)this.dataLerpTicks;
        this.lossySyncedWheelAngle = this.lossySyncedWheelAngle + (this.wheelAngle - this.lossySyncedWheelAngle) / (float)this.dataLerpTicks;

        this.dataLerpTicks--;
    }

    @Override
    public void tick() {
        boolean first = this.firstTick;
        if (first) {
            this.lastMeasuredPos = this.position();
        }

        if (!this.level().isClientSide()) {
            // Also release animals restored from older saves or mounted by a forced command.
            // The immutable passenger list remains safe to iterate as riders dismount.
            for (Entity passenger : this.getPassengers()) {
                if (isAnimalPassenger(passenger)) {
                    passenger.stopRiding();
                }
            }
        }

        if (lastWheelAngle != wheelAngle) markDirty();
        lastWheelAngle = wheelAngle;

        if (!this.wasEngineRunning && this.engineRunning() && this.level().isClientSide()) {
            engineSound.accept(this);
        }
        this.wasEngineRunning = this.engineRunning();

        var fAtt = this.getFrontAttachment();
        boolean fAttDriving = fAtt != null && fAtt.isProvidingAlternativeInputs(this, getFirstPassenger());
        if (!(this.isVehicle() || this.isDriving(this.getFirstPassenger()) || fAttDriving)) {
            input.clearInputs();
        }
        expireStaleClientInput();

        if (this.jumpCooldown > 0) {
            this.jumpCooldown--;
        }
        tickRecentDismounts();

        super.tick();
        if (!this.rearAttachment.type.isEmpty()) this.rearAttachment.tick();
        if (!this.frontAttachment.type.isEmpty()) this.frontAttachment.tick();

        var prevPos = this.position();
        this.prevYawForRotate = getYRot();

        this.setHonking(this.input.holdingHorn);

        lerpAutomobileData();
        positionTrackingTick();
        collisionStateTick();
        steeringTick();
        driftingTick();
        burnoutTick();

        receiveVehicleCollisions();
        movementTick();
        if (this.isLocalInstanceAuthoritative()) {
            this.move(MoverType.SELF, this.getDeltaMovement());
        }
        postMovementTick();
        tickUnmannedCoastSettle();

        this.updateHitboxPositions();
        if (!level().isClientSide()) {
            this.resolveRecentDismountHitboxOverlap();

            // syncData runs before the common displacementTick below. Keep an already-dirty
            // stationary automobile from emitting its pre-teleport absolute render Y here.
            this.displacement.resetVerticalAfterPositionChange(this.getY());

            var tailPos = this.getTailPos();
            var currentDimension = this.level().dimension();
            boolean dimensionChanged = this.previousServerTickEndDimension != null
                    && !this.previousServerTickEndDimension.equals(currentDimension);

            if (RearAttachmentMovementPolicy.shouldRealign(
                    this.previousServerTickEndPosition,
                    prevPos,
                    dimensionChanged
            )) {
                this.rearAttachment.setYaw(this.getYRot());
            } else {
                var prevTailPos = this.prevTailPos != null ? this.prevTailPos : tailPos;
                this.rearAttachment.pull(prevTailPos.subtract(tailPos));
            }
            this.prevTailPos = tailPos;

            if (serverSyncTicks > 0) {
                serverSyncTicks--;
            }
            if (dirty && serverSyncTicks <= 0) {
                syncData();
                dirty = false;
            }
            if (this.hasSpaceForPassengers() && !decorative) {
                var touchingEntities = this.level().getEntities(this, this.getBoundingBox().inflate(0.2, 0, 0.2), EntitySelector.pushableBy(this));
                for (Entity entity : touchingEntities) {
                    if (!entity.hasPassenger(this)) {
                        if (!entity.isPassenger() && entity.getBbWidth() <= this.getBbWidth() && entity instanceof Mob && this.canAddPassenger(entity)) {
                            entity.startRiding(this);
                        }
                    }
                }
            }
            if (fAttDriving) {
                boolean wasHoldingHorn = this.input.holdingHorn;
                fAtt.provideAlternativeInputs(this, this.input, this.getFirstPassenger());
                this.despawnCountdown = 0;

                if (wasHoldingHorn != this.input.holdingHorn) {
                    this.syncData();
                }
            } else if (this.isVehicle()) {
                this.despawnCountdown = 0;
            } else if (this.despawnTime > 0) {
                this.despawnCountdown++;

                if (this.despawnCountdown >= this.despawnTime) {
                    this.destroyAutomobile(false, RemovalReason.DISCARDED);
                }
            }
        } else {
            clientTime++;

            lastSusBounceTimer = suspensionBounceTimer;
            if (suspensionBounceTimer > 0) {
                suspensionBounceTimer--;
            }

            if (Math.abs(this.hSpeed) < 0.05 && !this.burningOut && this.getControllingPassenger() instanceof Player) {
                this.standStillTime = AUtils.shift(this.standStillTime, 0.05f, 1f);
            } else {
                this.standStillTime = AUtils.shift(this.standStillTime, 0.15f, -1.3f);
            }

            if (this.getFirstPassenger() instanceof Player player && player.isLocalPlayer()) {
                this.clientSyncTicks--;
                if (this.clientSyncTicks <= 0) {
                    this.clientSyncTicks = CLIENT_SYNC_INTERVAL;
                    ClientPackets.sendServerboundAutomobileSyncPacket(this);
                }
            } else {
                this.clientSyncTicks = CLIENT_SYNC_INTERVAL;
            }
        }

        boolean authoritativePositionRefresh = this.clientAuthoritativePositionRefresh;
        boolean relativePositionRefresh = this.clientRelativePositionRefresh;
        boolean packetPositionRefresh = authoritativePositionRefresh || relativePositionRefresh;
        this.clientAuthoritativePositionRefresh = false;
        this.clientRelativePositionRefresh = false;
        if (authoritativePositionRefresh) {
            // A component sync may arrive on either side of an absolute vanilla position packet.
            // Resolve both orderings to the entity's authoritative Y before any local terrain scan.
            this.displacement.resetVerticalAfterPositionChange(this.getY());
        }
        displacementTick(AutomobileDisplacementUpdatePolicy.shouldUpdate(
                first,
                packetPositionRefresh,
                prevPos,
                this.position(),
                this.getYRot() != this.yRotO
        ));
        if (!this.level().isClientSide()) {
            this.previousServerTickEndPosition = this.position();
            this.previousServerTickEndDimension = this.level().dimension();
        }
    }

    public void positionTrackingTick() {
        if (this.isLocalInstanceAuthoritative()) {
            this.lerpTicks = 0;
            syncPacketPositionCodec(getX(), getY(), getZ());
        } else if (lerpTicks > 0) {
            this.setPos(
                    this.getX() + ((this.trackedX - this.getX()) / (double)this.lerpTicks),
                    this.getY() + ((this.trackedY - this.getY()) / (double)this.lerpTicks),
                    this.getZ() + ((this.trackedZ - this.getZ()) / (double)this.lerpTicks)
            );
            this.setYRot(this.getYRot() + (Mth.wrapDegrees(this.trackedYaw - this.getYRot()) / (float)this.lerpTicks));

            this.lerpTicks--;
        }
    }

    /** Reconciles render-only state after vanilla applies an absolute clientbound position. */
    public void clientOnAuthoritativePositionApplied(Vec3 previousPosition) {
        if (!this.level().isClientSide()
                || !AutomobilePositionUpdatePolicy.positionChanged(previousPosition, this.position())
                || !this.displacement.translateVerticalAfterPositionChange(previousPosition.y, this.getY())) {
            return;
        }

        // An absolute vanilla correction supersedes any target left by Automobility's movement sync.
        this.lerpTicks = 0;
        this.trackedX = this.getX();
        this.trackedY = this.getY();
        this.trackedZ = this.getZ();
        this.trackedYaw = this.getYRot();
        this.clientAuthoritativePositionRefresh = true;
    }

    /**
     * Keeps locally calculated body displacement attached to an unoccupied automobile when
     * vanilla applies a relative movement packet between entity ticks. This deliberately leaves
     * Automobility's interpolation target intact for ordinary driving.
     */
    public void clientOnRelativePositionApplied(Vec3 previousPosition) {
        if (!this.level().isClientSide()
                || !this.usesLocalDisplacement()
                || !AutomobilePositionUpdatePolicy.positionChanged(previousPosition, this.position())
                || !this.displacement.translateVerticalAfterPositionChange(previousPosition.y, this.getY())) {
            return;
        }

        this.clientRelativePositionRefresh = true;
    }

    public void markDirty() {
        dirty = true;
    }

    private void syncData() {
        forPlayersTrackingMe(true, player -> CommonPackets.sendClientboundAutomobileSyncPacket(this, player));
        this.serverSyncTicks = SERVER_SYNC_INTERVAL;
    }

    public boolean tryAcquireClientSyncSlot() {
        if (this.level().isClientSide()) {
            return false;
        }

        long currentTick = this.level().getGameTime();
        if (currentTick != this.lastClientSyncTick) {
            this.lastClientSyncTick = currentTick;
            this.clientSyncsThisTick = 0;
        }
        if (this.clientSyncsThisTick >= MAX_CLIENT_SYNCS_PER_TICK) {
            return false;
        }

        this.clientSyncsThisTick++;
        return true;
    }

    private void syncAttachments() {
        forPlayersTrackingMe(false, player -> CommonPackets.sendSyncAutomobileAttachmentsPacket(this, player));
    }

    public ItemStack asPrefabItem() {
        return new AutomobileData(Optional.empty(),
                this.entityData.get(FRAME_TYPE).unwrapKey().orElse(AutomobileFrame.EMPTY_KEY),
                this.entityData.get(WHEEL_TYPE).unwrapKey().orElse(AutomobileWheel.EMPTY_KEY),
                this.entityData.get(ENGINE_TYPE).unwrapKey().orElse(AutomobileEngine.EMPTY_KEY)
        ).asStack();
    }

    public void updateEngineSpeed(float speed) {
         if (!this.simulatesPhysicsState()) {
             return;
         }

         this.engineSpeed = speed;
    }

    public void updateBoostSpeed(float speed) {
        if (!this.simulatesPhysicsState()) {
            return;
        }

        this.boostSpeed = speed;
    }

    /**
     * The server owns gameplay-relevant speed state even while vanilla's validated vehicle move
     * packet remains the spatial authority for .31. Fully server-authoritative positioning needs
     * a correction/prediction protocol and is intentionally deferred until it can be tested with
     * two real clients without changing driving feel.
     */
    private boolean simulatesPhysicsState() {
        return !this.level().isClientSide() || this.isLocalInstanceAuthoritative();
    }

    public void movementTick() {
        Vec3 serverObservedMovement = this.level().isClientSide() ? Vec3.ZERO : this.getMeasuredMovement();

        // Handles boosting
        lastBoostSpeed = boostSpeed;
        if (boostTimer > 0) {
            boostTimer--;
            this.updateBoostSpeed(Math.min(boostPower, boostSpeed + 0.09f));
            if (engineSpeed < stats.getComfortableSpeed()) {
                engineSpeed += 0.012f;
            }
            markDirty();

            if (boostTimer == 0) {
                controllerAction(c -> c.updateBoostingRumbleState(false, 0));
            }
        } else {
            this.updateBoostSpeed(AUtils.zero(boostSpeed, 0.09f));
        }

        // Get block below's friction
        var blockBelow = BlockPos.containing(getX(), getY() - 0.05, getZ());
        this.grip = stats.calculateSurfaceGrip(
                (level().getBlockState(blockBelow).getBlock().getFriction() - 0.6f) / 0.4f);

        // Bounce on gel
        if (this.automobileOnGround && this.jumpCooldown <= 0 && level().getBlockState(this.blockPosition()).getBlock() instanceof LaunchGelBlock) {
            this.setSpeed(Math.max(this.getHSpeed(), 0.1f), Math.max(this.getVSpeed(), 0.9f));
            this.jumpCooldown = 5;
            this.automobileOnGround = false;
        }

        // Track the last position of the automobile
        this.lastMeasuredPos = position();

        // cumulative will be modified by the following code and then the automobile will be moved by it
        // Currently initialized with the value of addedVelocity (which is a general velocity vector applied to the automobile, i.e. for when it bumps into a wall and is pushed back)
        var cumulative = addedVelocity;

        // Reduce gravity underwater
        cumulative = cumulative.add(0, (vSpeed * (isUnderWater() ? 0.15f : 1)), 0);

        // This is the general direction the automobile will move, which is slightly offset to the side when drifting
        this.speedDirection = getYRot() - (drifting ? Math.min(turboCharge * 6, 43 + (-steering * 12)) * driftDir : -steering * 12); //MathHelper.lerp(grip, getYaw(), getYaw() - (drifting ? Math.min(turboCharge * 6, 43 + (-steering * 12)) * driftDir : -steering * 12));

        // Handle acceleration
        if (input.accelerating) {
            float speed = Math.max(this.engineSpeed, 0);
            // yeah ...
            double acc =
                    // The following conditions check whether the automobile should NOT receive normal acceleration
                    // It will not receive this acceleration if the automobile is steering or tight-drifting
                    (
                            (this.drifting && AUtils.haveSameSign(this.steering, this.driftDir)) ||
                            (!this.drifting && this.steering != 0 && hSpeed > 0.5)
                    ) ? (this.hSpeed < stats.getComfortableSpeed() ? 0.001 : 0) // This will supply a small amount of acceleration if the automobile is moving slowly only

                    // Otherwise, it will receive acceleration as normal
                    // It will receive this acceleration if the automobile is moving straight or wide-drifting (the latter slightly reduces acceleration)
                    : calculateAcceleration(speed, stats) * (drifting ? 0.86 : 1) * (engineSpeed > stats.getComfortableSpeed() ? 0.25f : 1) * grip;
            this.updateEngineSpeed(this.engineSpeed + (float) acc);
        }

        // Handle braking/reverse
        if (input.braking) {
            this.updateEngineSpeed(Math.max(this.engineSpeed - 0.15f, -0.25f));
        }
        // Handle when the automobile is rolling to a stop
        if (!input.accelerating && !input.braking) {
            this.updateEngineSpeed(AUtils.zero(this.engineSpeed, 0.025f));
        }

        // Slow the automobile a bit while steering and moving fast
        if (!drifting && steering != 0 && hSpeed > 0.8) {
            this.updateEngineSpeed(engineSpeed - (engineSpeed * 0.00042f));
        }

        if (this.burningOut()) {
            this.updateEngineSpeed(engineSpeed - (engineSpeed * 0.5f));
        }

        // Allows for the sticky slope effect to continue for a tick after not being on a slope
        // This prevents the automobile from randomly jumping if it's moving down a slope quickly
        var below = BlockPos.containing(getX(), getY() - 0.51, getZ());
        var state = level().getBlockState(below);
        if (state.is(Automobility.STICKY_SLOPES)) {
            slopeStickingTimer = 1;
        } else {
            slopeStickingTimer = Math.max(0, slopeStickingTimer - 1);
        }

        boolean wasOffRoad = this.offRoad && this.hSpeed > 0.01;

        // Handle being in off-road
        if (boostSpeed < 0.4f && level().getBlockState(blockPosition()).getBlock() instanceof OffRoadBlock block) {
            int layers = level().getBlockState(blockPosition()).getValue(OffRoadBlock.LAYERS);
            float cap = stats.getComfortableSpeed() * (1 - ((float)layers / 3.5f));
            this.updateEngineSpeed(Math.min(cap, engineSpeed));
            this.debrisColor = block.color;
            this.offRoad = true;
        } else this.offRoad = false;

        if ((this.offRoad && this.hSpeed > 0.01) != wasOffRoad) {
            controllerAction(c -> c.updateOffRoadRumbleState(this.offRoad));
        }

        // Set the horizontal speed
        if (!burningOut()) hSpeed = engineSpeed + boostSpeed;

        // Sticking to sticky slopes
        double lowestPrevYDisp = 0;
        for (double d : prevYDisplacements) {
            lowestPrevYDisp = Math.min(d, lowestPrevYDisp);
        }
        if (slopeStickingTimer > 0 && automobileOnGround && lowestPrevYDisp <= 0) {
            double cumulHSpeed = Math.sqrt((cumulative.x * cumulative.x) + (cumulative.z * cumulative.z));
            cumulative = cumulative.add(0, -(0.25 + cumulHSpeed), 0);
        }


        float angle = (float) Math.toRadians(-speedDirection);
        if (this.burningOut()) {
            if (Math.abs(hSpeed) > 0.02) {
                this.addedVelocity = new Vec3(Math.sin(angle) * hSpeed, 0, Math.cos(angle) * hSpeed);
                this.hSpeed = 0;
                cumulative = cumulative.add(addedVelocity);
            }
        } else {
            // Apply the horizontal speed to the cumulative movement
            cumulative = cumulative.add(Math.sin(angle) * hSpeed, 0, Math.cos(angle) * hSpeed);
        }

        cumulative = cumulative.scale(this.grip).add(this.lastVelocity.scale(1 - this.grip));
        if (cumulative.length() < 0.001) {
            cumulative = Vec3.ZERO;
        }

        // Turn the wheels
        float wheelCircumference = (float)(2 * (getWheels().model().radius() / 16) * Math.PI);
        if (hSpeed > 0) markDirty();
        wheelAngle += 300 * (hSpeed / wheelCircumference) + (hSpeed > 0 ? ((1 - grip) * 15) : 0); // made it a bit slower intentionally, also make it spin more when on slippery surface

        // Set the automobile's velocity
        if (this.isLocalInstanceAuthoritative()) {
            this.setDeltaMovement(cumulative);
        }
        lastVelocity = cumulative;

        // Damage and launch entities that are hit by a moving automobile
        if (!this.level().isClientSide()) {
            runOverEntities(AutomobileImpactPolicy.boundedVelocityForDamage(cumulative, serverObservedMovement));
        }
    }

    public void runOverEntities(Vec3 velocity) {
        float impactSpeed = AutomobileImpactPolicy.speedForDamage(velocity);
        if (impactSpeed == 0) {
            return;
        }

        var entitiesToHit = new HashSet<LivingEntity>();
        var velAdd = velocity.add(0, 0.1, 0).scale(3);

        for (var box : this.hitboxes) {
            var bbox = box.getBoundingBox().move(velocity.scale(0.5));
            for (var entity : level().getEntities(EntityTypeTest.forClass(Entity.class), bbox, entity -> entity != this && entity.getVehicle() != this)) {
                if (!entity.isInvulnerable() && !entity.isSpectator()) {
                    if (entity instanceof LivingEntity living && entity.getVehicle() != this && !this.isRecentlyDismounted(living)) {
                        entitiesToHit.add(living);
                    }
                }
            }
        }

        entitiesToHit.forEach(e -> {
            AutomobilityEntities.automobileDamageSource(level()).ifPresent(dmg -> e.hurt(dmg, impactSpeed * 10));
            e.push(velAdd.x, velAdd.y, velAdd.z);
        });
    }

    private void expireStaleClientInput() {
        if (this.level().isClientSide()
                || !(this.getFirstPassenger() instanceof Player player)
                || !this.isDriving(player)
                || AutomobileClientInputPolicy.isFresh(
                        this.lastAcceptedClientInputTick,
                        this.level().getGameTime(),
                        CLIENT_INPUT_TIMEOUT_TICKS)) {
            return;
        }

        if (this.input.accelerating || this.input.braking || this.input.steering != 0
                || this.input.holdingDrift || this.input.holdingHorn) {
            this.input.clearInputs();
            this.markDirty();
        }
    }

    public void receiveVehicleCollisions() {
        if (this.decorative) {
            return;
        }

        var collisions = new HashMap<AutomobileEntity, IncomingCollision>();

        for (var box : this.hitboxes) {
            var bbox = box.getBoundingBox().inflate(0.15);
            for (var hitbox : level().getEntities(EntityTypeTest.forClass(HitboxEntity.class), bbox, h -> h.automobile() != this)) {
                var auto = hitbox.automobile();
                var intersect = hitbox.getBoundingBox().inflate(0.15).intersect(bbox);

                var collDepth = new Vec3(intersect.getXsize(), 0, intersect.getZsize());
                if (auto == null || collisions.containsKey(auto) && collisions.get(auto).depth().lengthSqr() > collDepth.lengthSqr()) {
                    continue;
                }

                var momentum = auto.getMeasuredMovement();
                var origin = intersect.getCenter();

                collisions.put(auto, new IncomingCollision(collDepth, momentum, origin, auto.getFrame().weight()));
            }
        }

        hadVehicleCollision = Math.max(0, hadVehicleCollision - 1);
        for (var col : collisions.values()) {
            var meToCollision = col.origin().subtract(this.position()).multiply(1, 0, 1);
            double hitScale = hadVehicleCollision <= 0 ? 0.15 : 0.07;
            hitScale *= (1 + col.inertia() / this.getFrame().weight()) * 0.5;
            this.addedVelocity = this.addedVelocity.add(
                    meToCollision.reverse().normalize().scale(hitScale * (1 + 0.1 * Math.sqrt(col.velocity().length())) * col.depth().lengthSqr())
                            .multiply(1, 0, 1));

            if (hadVehicleCollision <= 0) {
                level().playLocalSound(this.getX(), this.getY(), this.getZ(), AutomobilitySounds.COLLISION.require(), SoundSource.AMBIENT, 0.22f, 0.7f + (0.06f * (this.getRandom().nextFloat() - 0.5f)), false);
                this.engineSpeed *= 0.6f;
                hadVehicleCollision = 12;
            }
        }
    }

    public Vec3 getMeasuredMovement() {
        return position().subtract(lastMeasuredPos);
    }

    public void postMovementTick() {
        float addedVelReduction = 0.1f;
        if (this.burningOut()) {
            addedVelReduction = 0.05f;
        }

        // Reduce the values of addedVelocity incrementally
        double addVelLen = addedVelocity.length();
        if (addVelLen > 0) addedVelocity = addedVelocity.scale(Math.max(0, addVelLen - addedVelReduction) / addVelLen);

        float angle = (float) Math.toRadians(-speedDirection);
        if (touchingWall && hSpeed > 0.1 && addedVelocity.length() <= 0) {
            this.updateEngineSpeed(engineSpeed / 3.6f);
            double knockSpeed = ((-0.2 * hSpeed) - 0.5);
            addedVelocity = addedVelocity.add(Math.sin(angle) * knockSpeed, 0, Math.cos(angle) * knockSpeed);

            level().playLocalSound(this.getX(), this.getY(), this.getZ(), AutomobilitySounds.COLLISION.require(), SoundSource.AMBIENT, 0.76f, 0.65f + (0.06f * (this.getRandom().nextFloat() - 0.5f)), true);

            if (isVehicle() && level().isClientSide()) {
                if (getPassengers().stream().anyMatch(p -> p instanceof LocalPlayer)) {
                    controllerAction(c -> c.crashRumble());
                }
            }

        }

        this.touchingWall = false;
        double yDisp = getMeasuredMovement().y();

        // Increment the falling timer
        if (!automobileOnGround && yDisp < 0) {
            fallTicks += 1;
        } else {
            fallTicks = 0;
        }

        // Handle launching off slopes
        double highestPrevYDisp = 0;
        for (double d : prevYDisplacements) {
            highestPrevYDisp = Math.max(d, highestPrevYDisp);
        }
        if (wasOnGround && !automobileOnGround && !isFloorDirectlyBelow) {
            vSpeed = (float)Mth.clamp(highestPrevYDisp, 0, hSpeed * 0.6f);
        }

        // Handles gravity
        vSpeed = Math.max(vSpeed - 0.08f, !automobileOnGround ? TERMINAL_VELOCITY : -0.01f);

        // Store previous y displacement to use when launching off slopes
        prevYDisplacements.push(yDisp);
        if (prevYDisplacements.size() > 2) {
            prevYDisplacements.removeLast();
        }

        float newAngularSpeed = this.angularSpeed;
        if (this.burningOut()) {
            float speed = (float) this.addedVelocity.length();
            float acc = (1.7f / (1 + this.getFrame().weight())) + (4 * speed);
            float lim = 9 + (4 * speed);
            if (this.steering != 0) {
                newAngularSpeed = Mth.clamp(newAngularSpeed + (acc * this.steering), -lim, lim);
            } else {
                newAngularSpeed = AUtils.shift(newAngularSpeed, acc * 0.5f, 0);
            }
        } else if (hSpeed != 0) {
            float traction = (1 / (1 + (4 * this.hSpeed))) + (0.3f * this.stats.getGrip());
            newAngularSpeed = AUtils.shift(newAngularSpeed, 6 * traction,
                    (drifting ? (((this.steering + (driftDir)) * driftDir * 2.5f + 1.5f) * driftDir) * (((1 - stats.getGrip()) + 2) / 2.5f) : this.steering * ((4f * Math.min(hSpeed, 1)) + (hSpeed > 0 ? 2 : -3.5f))) * ((stats.getHandling() + 1) / 2));
        } else {
            newAngularSpeed = AUtils.shift(newAngularSpeed, 3, 0);
        }

        this.angularSpeed = (newAngularSpeed * this.grip) + (this.angularSpeed * (1 - this.grip));
        if (Math.abs(this.angularSpeed) < 0.00003) {
            this.angularSpeed = 0;
        }

        float yawInc = angularSpeed;
        if (hSpeed == 0 && !this.burningOut()) {
            yawInc = 0;
        }

        // Turns the automobile
        if (this.isLocalInstanceAuthoritative()) {
            this.setYRot(getYRot() + yawInc);

            var first = this.getFirstPassenger();
            if (first != null && this.isDriving(first)) {
                if (inLockedViewMode()) {
                    whenRotatedSmooth(first);
                } else {
                    whenRotated(yawInc, first);
                }
            }
        }

        this.measuredAngVel = yawInc;
        if (this.automobileOnGround()) {
            this.smoothAngVel = Mth.lerp(0.34f, this.smoothAngVel, this.measuredAngVel);
        }
    }

    public void whenRotated(float dYaw, Entity e) {
        e.setYRot(Mth.wrapDegrees(e.getYRot() + dYaw));
        e.setYBodyRot(Mth.wrapDegrees(e.getYRot() + dYaw));
    }

    private void whenRotatedSmooth(Entity passenger) {
        double lockStrength = 0.36;

        if (this.drifting) {
            lockStrength = 0.16;
        } else if (this.burningOut()) {
            lockStrength = 0.43;
        }

        var selfDir = new Vec3(0, 0, 1).yRot((float) Math.toRadians(180 - this.getYRot()));
        var playerDir = new Vec3(0, 0, 1).yRot((float) Math.toRadians(180 - passenger.getYRot()));
        var newDir = playerDir.add(selfDir.scale(lockStrength));

        float rot = 180 - (float) Math.toDegrees(Math.atan2(newDir.x, newDir.z));
        passenger.setYRot(rot);
        passenger.setYBodyRot(rot);
    }

    @Override
    public void move(MoverType movementType, Vec3 movement) {
        if (movementType == MoverType.PLAYER) {
            this.setPos(this.position().add(movement));
            return;
        }

        super.move(movementType, movement);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false; // Riders shouldn't take fall damage
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        return false;
    }

    public boolean isOneOfMyHitboxes(Entity e) {
        return e instanceof HitboxEntity hitbox && hitbox.automobile() == this;
    }

    public void accumulateCollisionAreas(Collection<CollisionArea> areas) {
        this.level().getEntitiesOfClass(Entity.class, this.getBoundingBox().inflate(3, 3, 3),
                        e -> e != this &&
                                e.getVehicle() != this &&
                                !(e instanceof AutomobileEntity) &&
                                !this.isOneOfMyHitboxes(e))
                .forEach(e -> areas.add(CollisionArea.entity(e)));
    }

    public void displacementTick(boolean tick) {
        if (this.level().isClientSide()) {
            this.displacement.preTick();

            if (this.usesLocalDisplacement()) {
                if (tick) {
                    this.displacement.otherColliders.clear();
                    this.accumulateCollisionAreas(this.displacement.otherColliders);

                    this.displacement.tick(this.level(), this, this.position(), this.getYRot(), this.maxUpStep());
                }

                if (level().getBlockState(this.blockPosition()).getBlock() instanceof AutomobileAssemblerBlock) {
                    this.displacement.lastVertical = this.displacement.verticalTarget = (this.getY() - this.getWheels().model().radius() / 16);
                }
            } else if (!this.displacement.hasSyncedData()) {
                this.displacement.snapTo(this.getY(), new Quaternionf());
            }

            this.displacement.postTick();
        } else {
            this.displacement.resetVerticalAfterPositionChange(this.getY());
        }
    }

    private boolean usesLocalDisplacement() {
        return this.isLocalInstanceAuthoritative() || !this.isClientAuthoritative();
    }

    public Displacement getDisplacement() {
        return this.displacement;
    }

    public void collisionStateTick() {
        wasOnGround = automobileOnGround;
        automobileOnGround = false;
        isFloorDirectlyBelow = false;
        var b = getBoundingBox();
        var groundBox = new AABB(b.minX, b.minY - 0.04, b.minZ, b.maxX, b.minY, b.maxZ);
        var wid = (b.getXsize() + b.getZsize()) * 0.5f;
        var floorBox = new AABB(b.minX + (wid * 0.94), b.minY - 0.05, b.minZ + (wid * 0.94), b.maxX - (wid * 0.94), b.minY, b.maxZ - (wid * 0.94));
        var wallBox = b.deflate(0.05).move(this.lastVelocity.normalize().scale(0.12));
        var start = new BlockPos((int) Math.floor(b.minX - 0.1), (int) Math.floor(b.minY - 0.2), (int) Math.floor(b.minZ - 0.1));
        var end = new BlockPos((int) Math.floor(b.maxX + 0.1), (int) Math.floor(b.maxY + 0.2 + this.maxUpStep()), (int) Math.floor(b.maxZ + 0.1));
        var groundCuboid = Shapes.create(groundBox);
        var floorCuboid = Shapes.create(floorBox);
        var wallCuboid = Shapes.create(wallBox);
        var stepWallCuboid = wallCuboid.move(0, this.maxUpStep() - 0.05, 0);
        boolean wallHit = false;
        boolean stepWallHit = false;
        var shapeCtx = CollisionContext.of(this);
        if (this.level().hasChunksAt(start, end)) {
            var pos = new BlockPos.MutableBlockPos();
            for(int x = start.getX(); x <= end.getX(); ++x) {
                for(int y = start.getY(); y <= end.getY(); ++y) {
                    for(int z = start.getZ(); z <= end.getZ(); ++z) {
                        pos.set(x, y, z);
                        var state = this.level().getBlockState(pos);
                        var blockShape = state.getCollisionShape(this.level(), pos, shapeCtx).move(pos.getX(), pos.getY(), pos.getZ());
                        this.automobileOnGround |= Shapes.joinIsNotEmpty(blockShape, groundCuboid, BooleanOp.AND);
                        this.isFloorDirectlyBelow |= Shapes.joinIsNotEmpty(blockShape, floorCuboid, BooleanOp.AND);
                        wallHit |= Shapes.joinIsNotEmpty(blockShape, wallCuboid, BooleanOp.AND);
                        stepWallHit |= Shapes.joinIsNotEmpty(blockShape, stepWallCuboid, BooleanOp.AND);
                    }
                }
            }
        }
        this.touchingWall = (wallHit && stepWallHit);

        var otherColliders = new HashSet<CollisionArea>();
        this.accumulateCollisionAreas(otherColliders);
        this.automobileOnGround |= otherColliders.stream().anyMatch(col -> col.boxIntersects(groundBox));
    }

    public void lerpTo(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
        this.trackedX = x;
        this.trackedY = y;
        this.trackedZ = z;
        this.trackedYaw = yaw;
        this.lerpTicks = this.getType().updateInterval() + 1;
    }

    private float calculateAcceleration(float speed, AutomobileStats stats) {
        // A somewhat over-engineered function to accelerate the automobile, since I didn't want to add a hard speed cap
        return (1 / ((300 * speed) + (18.5f - (stats.getAcceleration() * 5.3f)))) * (0.9f * ((stats.getAcceleration() + 1) / 2));
    }

    public float getHandling() {
        return stats.getHandling();
    }

    public void provideClientInput(boolean fwd, boolean back, boolean left, boolean right, boolean space, boolean ctrl) {
        // Receives inputs client-side and sends them to the server
        if (this.input.setDigitalInputs(fwd, back, left, right, space, ctrl)) {
            ClientPackets.sendServerboundAutomobileSyncPacket(this);
        }
    }

    public void boost(float power, int time) {
        if (power > boostPower || time > boostTimer) {
            boostTimer = time;
            boostPower = power;
        }
        if (this.simulatesPhysicsState()) {
            this.updateEngineSpeed(Math.max(this.engineSpeed, this.stats.getComfortableSpeed() * 0.5f));
        }

        controllerAction(c -> c.updateBoostingRumbleState(true, power));
    }

    private void steeringTick() {
        // Adjust the steering based on the left/right inputs
        this.lastSteering = steering;
        this.steering = AUtils.shift(this.steering, 0.42f, this.input.steering);
    }

    private void consumeTurboCharge() {
        if (turboCharge > LARGE_TURBO_TIME) {
            boost(0.38f, 38);
        } else if (turboCharge > MEDIUM_TURBO_TIME) {
            boost(0.3f, 21);
        } else if (turboCharge > SMALL_TURBO_TIME) {
            boost(0.23f, 9);
        }
        turboCharge = 0;
    }

    private void driftingTick() {
        int prevTurboCharge = turboCharge;

        // Handles starting a drift
        if (!prevHoldDrift && input.holdingDrift) {
            if (steering != 0 && !drifting && hSpeed > 0.4f && automobileOnGround) {
                setDrifting(true);
                controllerAction(AutomobileController::driftChargeRumble);
                driftDir = steering > 0 ? 1 : -1;
                // Reduce speed when a drift starts, based on how long the last drift was for
                // This allows you to do a series of short drifts without tanking all your speed, while still reducing your speed when you begin the drift(s)
                this.updateEngineSpeed(engineSpeed - (0.028f * engineSpeed));
            } else if (steering == 0 && !this.level().isClientSide() && this.getRearAttachment() instanceof DeployableRearAttachment att) {
                att.deploy();
            }
        }

        // Handles drifting effects, ending a drift, and the drift timer (for drift turbos)
        if (drifting) {
            if (this.automobileOnGround()) createDriftParticles();
            // Ending a drift successfully, giving you a turbo boost
            if (prevHoldDrift && !input.holdingDrift) {
                setDrifting(false);
                controllerAction(c -> c.updateMaxChargeRumbleState(false));
                consumeTurboCharge();
            // Ending a drift unsuccessfully, not giving you a boost
            } else if (hSpeed < 0.33f) {
                setDrifting(false);
                controllerAction(c -> c.updateMaxChargeRumbleState(false));
                turboCharge = 0;
            }
            if (automobileOnGround) turboCharge += (input.steering * driftDir > 0) ? 2 : 1;
        }

        if (turboCharge == SMALL_TURBO_TIME || turboCharge == MEDIUM_TURBO_TIME || turboCharge == LARGE_TURBO_TIME) {
            controllerAction(AutomobileController::driftChargeRumble);
        }

        if (turboCharge >= LARGE_TURBO_TIME && prevTurboCharge < LARGE_TURBO_TIME) {
            controllerAction(c -> c.updateMaxChargeRumbleState(true));
        } else if (prevTurboCharge >= LARGE_TURBO_TIME && turboCharge < LARGE_TURBO_TIME) {
            controllerAction(c -> c.updateMaxChargeRumbleState(false));
        }

        this.prevHoldDrift = input.holdingDrift;
    }

    private void endBurnout() {
        setBurningOut(false);
        this.updateEngineSpeed(0);
    }

    private void burnoutTick() {
        if (this.burningOut()) {
            if (this.automobileOnGround()) {
                if (this.addedVelocity.length() > 0.05 || Math.abs(this.angularSpeed) > 0.05) {
                    createDriftParticles();
                }
                if (hSpeed < 0.08 && turboCharge <= SMALL_TURBO_TIME) turboCharge += 1;
            }
            if (!input.braking) {
                endBurnout();
                consumeTurboCharge();
            } else if (!input.accelerating) {
                endBurnout();
                this.turboCharge = 0;
            }
            this.wheelAngle += 20;
        } else if ((input.accelerating || hSpeed > 0.05) && input.braking) {
            setBurningOut(true);
            this.turboCharge = 0;
        }
    }

    public void createDriftParticles() {
        for (var wheel : this.getFrame().model().wheelBase().wheels()) {
            if (wheel.end() == WheelBase.WheelEnd.BACK) {
                var pos = new Vector3d(wheel.right() + ((wheel.right() > 0 ? 1 : -1) * this.getWheels().model().width() * wheel.scale()), 0, wheel.forward())
                        .mul(0.0625);

                this.localPosToWorldSpace(pos);
                pos.add(0, 0.4, 0);

                level().addParticle(AutomobilityParticles.DRIFT_SMOKE.require(), pos.x(), pos.y(), pos.z(), 0, 0, 0);
            }
        }
    }

    private static boolean inLockedViewMode() {
        return Platform.get().controller().inControllerMode();
    }

    @Override
    public float getAutomobileYaw(float tickDelta) {
        return getViewYRot(tickDelta);
    }

    @Override
    public float getRearAttachmentYaw(float tickDelta) {
        return this.rearAttachment.yaw(tickDelta);
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        final var firstPassenger = getFirstPassenger();

        boolean canDrive = true;
        var fAtt = getFrontAttachment();
        if (fAtt != null) {
            canDrive = fAtt.canDrive(firstPassenger);
        }

        if (firstPassenger instanceof LivingEntity living && canDrive) {
            return living;
        } else {
            return null;
        }
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return !isAnimalPassenger(passenger) && this.hasSpaceForPassengers();
    }

    private static boolean isAnimalPassenger(Entity passenger) {
        return passenger instanceof Animal
                || passenger instanceof WaterAnimal
                || passenger instanceof AgeableWaterCreature
                || passenger instanceof AmbientCreature;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        if (passenger.getVehicle() != this) {
            super.addPassenger(passenger);
        } else {
            if (this.passengers.isEmpty()) {
                this.passengers = ImmutableList.of(passenger);
            } else {
                var newPassengerList = Lists.newArrayList(this.passengers);
                if (!this.level().isClientSide() && passenger instanceof Player player &&
                        this.canKickPassengerOut(getFirstPassenger(), player)) {
                    newPassengerList.addFirst(passenger);
                } else newPassengerList.add(passenger);

                this.passengers = ImmutableList.copyOf(newPassengerList);
            }

            this.gameEvent(GameEvent.ENTITY_MOUNT, passenger);
        }

        this.recentDismounts.remove(passenger.getUUID());
    }

    @Override
    protected void removePassenger(Entity passenger) {
        boolean wasDriver = this.isDriving(passenger);
        boolean shouldCoastAfterDriverDismount = wasDriver && this.shouldCoastAfterDriverDismount();

        super.removePassenger(passenger);

        if (passenger instanceof LivingEntity) {
            int graceTicks = shouldCoastAfterDriverDismount
                    ? UNMANNED_COAST_SETTLE_MAX_TICKS + POST_UNMANNED_COAST_GRACE_TICKS
                    : RECENT_DISMOUNT_GRACE_TICKS;
            this.recentDismounts.put(passenger.getUUID(), graceTicks);
        }

        if (wasDriver) {
            this.stabilizeAfterDriverDismount(shouldCoastAfterDriverDismount);
        }
    }

    public boolean isRecentlyDismounted(Entity entity) {
        return this.recentDismounts.containsKey(entity.getUUID());
    }

    private void tickRecentDismounts() {
        if (this.recentDismounts.isEmpty()) {
            return;
        }

        this.recentDismounts.replaceAll((uuid, ticks) -> ticks - 1);
        this.recentDismounts.values().removeIf(ticks -> ticks <= 0);
    }

    private void stabilizeAfterDriverDismount(boolean shouldCoastAfterDriverDismount) {
        this.input.clearInputs();
        this.boostSpeed = 0;
        this.lastBoostSpeed = 0;
        this.lossySyncedEffectiveSpeed = this.calculateEffectiveSpeed();
        this.lastSyncedBoostSpeed = 0;
        this.lossySyncedBoostSpeed = 0;
        this.dataLerpTicks = 0;
        this.boostTimer = 0;
        this.boostPower = 0;
        this.hSpeed = this.engineSpeed;
        this.lastMeasuredPos = this.position();
        this.angularSpeed = 0;
        this.steering = 0;
        this.lastSteering = 0;
        this.setDrifting(false);
        this.setBurningOut(false);
        this.lerpTicks = 0;
        this.trackedX = this.getX();
        this.trackedY = this.getY();
        this.trackedZ = this.getZ();
        this.trackedYaw = this.getYRot();
        this.syncPacketPositionCodec(this.getX(), this.getY(), this.getZ());
        this.prevYDisplacements.clear();
        this.touchingWall = false;
        this.hadVehicleCollision = 0;
        this.unmannedCoastSettleTicks = shouldCoastAfterDriverDismount ? UNMANNED_COAST_SETTLE_MAX_TICKS : 1;
        this.markDirty();
        this.hurtMarked = true;
        this.updateHitboxPositions();
        if (!this.level().isClientSide()) {
            this.resolveRecentDismountHitboxOverlap();
            this.syncData();
        }
    }

    private boolean shouldCoastAfterDriverDismount() {
        return Math.abs(this.engineSpeed) > 0.035f
                || Math.abs(this.hSpeed) > 0.035f
                || this.addedVelocity.horizontalDistanceSqr() > 0.0009
                || this.getDeltaMovement().horizontalDistanceSqr() > 0.0009;
    }

    private void tickUnmannedCoastSettle() {
        if (this.unmannedCoastSettleTicks <= 0) {
            return;
        }
        if (this.isVehicle()) {
            this.unmannedCoastSettleTicks = 0;
            return;
        }

        this.unmannedCoastSettleTicks--;
        boolean horizontallyStopped = Math.abs(this.engineSpeed) < 0.035f
                && Math.abs(this.hSpeed) < 0.035f
                && this.addedVelocity.horizontalDistanceSqr() < 0.0009
                && this.getDeltaMovement().horizontalDistanceSqr() < 0.0009;
        boolean verticallyStable = this.automobileOnGround || this.isFloorDirectlyBelow || Math.abs(this.vSpeed) < 0.035f;
        if ((horizontallyStopped && verticallyStable) || this.unmannedCoastSettleTicks <= 0) {
            this.settleUnmannedCoast();
        }
    }

    private void settleUnmannedCoast() {
        this.unmannedCoastSettleTicks = 0;
        this.input.clearInputs();
        this.engineSpeed = 0;
        this.boostSpeed = 0;
        this.lastBoostSpeed = 0;
        this.lossySyncedEffectiveSpeed = 0;
        this.lastSyncedBoostSpeed = 0;
        this.lossySyncedBoostSpeed = 0;
        this.dataLerpTicks = 0;
        this.boostTimer = 0;
        this.boostPower = 0;
        this.hSpeed = 0;
        this.vSpeed = 0;
        this.addedVelocity = Vec3.ZERO;
        this.lastVelocity = Vec3.ZERO;
        this.lastMeasuredPos = this.position();
        this.angularSpeed = 0;
        this.steering = 0;
        this.lastSteering = 0;
        this.setDrifting(false);
        this.setBurningOut(false);
        this.setDeltaMovement(Vec3.ZERO);
        this.lerpTicks = 0;
        this.trackedX = this.getX();
        this.trackedY = this.getY();
        this.trackedZ = this.getZ();
        this.trackedYaw = this.getYRot();
        this.syncPacketPositionCodec(this.getX(), this.getY(), this.getZ());
        this.prevYDisplacements.clear();
        this.touchingWall = false;
        this.hadVehicleCollision = 0;
        this.markDirty();
        this.hurtMarked = true;
        if (!this.level().isClientSide()) {
            this.updateHitboxPositions();
            this.resolveRecentDismountHitboxOverlap();
            this.syncData();
        } else {
            this.updateHitboxPositions();
        }
    }

    private void resolveRecentDismountHitboxOverlap() {
        if (this.recentDismounts.isEmpty() || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        for (var uuid : new ArrayList<>(this.recentDismounts.keySet())) {
            var player = serverLevel.getServer().getPlayerList().getPlayer(uuid);
            if (player == null || player.level() != this.level() || player.isPassenger()) {
                continue;
            }

            Vec3 correction = Vec3.ZERO;
            AABB playerBox = player.getBoundingBox();
            for (var hitbox : this.hitboxes) {
                var hitboxBox = hitbox.getBoundingBox().inflate(0.04, 0, 0.04);
                if (!playerBox.intersects(hitboxBox)) {
                    continue;
                }

                var escape = minimalHorizontalEscape(playerBox, hitboxBox);
                playerBox = playerBox.move(escape);
                correction = correction.add(escape);
            }

            if (correction.horizontalDistanceSqr() > 1.0E-6) {
                player.setPos(player.getX() + correction.x, player.getY(), player.getZ() + correction.z);
            }
        }
    }

    private static Vec3 minimalHorizontalEscape(AABB subject, AABB obstacle) {
        double east = obstacle.maxX - subject.minX + 0.03;
        double west = obstacle.minX - subject.maxX - 0.03;
        double south = obstacle.maxZ - subject.minZ + 0.03;
        double north = obstacle.minZ - subject.maxZ - 0.03;

        double x = Math.abs(east) < Math.abs(west) ? east : west;
        double z = Math.abs(south) < Math.abs(north) ? south : north;
        if (Math.abs(x) < Math.abs(z)) {
            return new Vec3(x, 0, 0);
        }
        return new Vec3(0, 0, z);
    }

    private void updateHitboxPositions() {
        this.verifyHitboxesFor(this.getFrame());
        if (!this.level().isClientSide()) {
            this.hitboxes.forEach(HitboxEntity::updatePositionFromAutomobile);
        }
        this.updateCullingBox();
    }

    private void snapClientHitboxesFromAutomobile() {
        if (!this.level().isClientSide()) {
            return;
        }

        this.hitboxes.removeIf(Entity::isRemoved);
        this.hitboxes.forEach(HitboxEntity::updatePositionFromAutomobile);
        this.updateCullingBox();
    }

    @Override
    public boolean hasInventory(@Nullable Player player) {
        return this.getRearAttachment().hasMenu(player);
    }

    @Override
    public void openInventory(Player player) {
        var factory = this.getRearAttachment().createMenu(new AutomobileContainerLevelAccess(this));
        if (factory != null) {
            player.openMenu(factory);
        }
    }

    public float getStandStillTime() {
        return this.standStillTime;
    }

    public void playHitSound(Vec3 pos) {
        level().gameEvent(this, GameEvent.ENTITY_DAMAGE, pos);
        level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.COPPER_BREAK, SoundSource.AMBIENT, 1, 0.9f + (this.getRandom().nextFloat() * 0.2f));
    }

    private void dropParts(Vec3 pos) {
        this.entityData.get(FRAME_TYPE).unwrapKey().ifPresent(key ->
                level().addFreshEntity(new ItemEntity(level(), pos.x, pos.y, pos.z, AutomobilityItems.AUTOMOBILE_FRAME.require().createStack(key))));
        this.entityData.get(ENGINE_TYPE).unwrapKey().ifPresent(key ->
                level().addFreshEntity(new ItemEntity(level(), pos.x, pos.y, pos.z, AutomobilityItems.AUTOMOBILE_ENGINE.require().createStack(key))));

        this.entityData.get(WHEEL_TYPE).unwrapKey().ifPresent(key -> {
            var wheelStack = AutomobilityItems.AUTOMOBILE_WHEEL.require().createStack(key);
            wheelStack.setCount(this.getFrame().model().wheelBase().wheelCount());
            level().addFreshEntity(new ItemEntity(level(), pos.x, pos.y, pos.z, wheelStack));
        });
    }

    public void destroyRearAttachment(boolean drop) {
        if (drop) {
            var dropPos = this.rearAttachment.pos();
            level().addFreshEntity(new ItemEntity(level(), dropPos.x, dropPos.y, dropPos.z,
                    AutomobilityItems.REAR_ATTACHMENT.require().createStack(this.getRearAttachmentType())));
        }
        this.setRearAttachment(RearAttachmentType.EMPTY);
    }

    public void destroyFrontAttachment(boolean drop) {
        if (drop) {
            var dropPos = this.frontAttachment.pos();
            level().addFreshEntity(new ItemEntity(level(), dropPos.x, dropPos.y, dropPos.z,
                    AutomobilityItems.FRONT_ATTACHMENT.require().createStack(this.getFrontAttachmentType())));
        }
        this.setFrontAttachment(FrontAttachmentType.EMPTY);
    }

    public void destroyAutomobile(boolean drop, RemovalReason reason) {
        if (!this.rearAttachment.type.isEmpty()) {
            this.destroyRearAttachment(drop);
        }
        if (!this.frontAttachment.type.isEmpty()) {
            this.destroyFrontAttachment(drop);
        }
        if (drop) {
            this.dropParts(this.position().add(0, 0.3, 0));
        }
        this.remove(reason);
    }

    public boolean canKickPassengerOut(@Nullable Entity passenger, Player kickerOuter) {
        if (passenger != null) {
            if (passenger instanceof Player) {
                return false;
            }

            var fAtt = getFrontAttachment();
            if (fAtt != null && fAtt.canDrive(passenger)) {
                return kickerOuter.isCreative();
            }

            if (passenger.isInvulnerable()) {
                return !this.canAddPassenger(kickerOuter) && kickerOuter.isCreative();
            }
        }

        return true;
    }

    public InteractionResult handleInteraction(Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (this.hasInventory(player)) {
                if (!level().isClientSide()) {
                    openInventory(player);
                    return InteractionResult.PASS;
                } else {
                    return InteractionResult.SUCCESS;
                }
            }
        }

        var stack = player.getItemInHand(hand);
        boolean vulnerable = !this.isInvulnerable() || player.isCreative();

        if (vulnerable && stack.is(AutomobilityItems.CROWBAR.require())) {
            double playerAngle = Math.toDegrees(Math.atan2(player.getZ() - this.getZ(), player.getX() - this.getX()));
            double angleDiff = Mth.wrapDegrees(this.getYRot() - playerAngle);

            if (angleDiff < 0 && !this.frontAttachment.type.isEmpty()) {
                if (level().isClientSide()) {
                    return InteractionResult.SUCCESS;
                }

                this.destroyFrontAttachment(!player.isCreative());
                this.playHitSound(this.getHeadPos());

                return InteractionResult.PASS;
            } else if (!this.rearAttachment.type.isEmpty()) {
                if (level().isClientSide()) {
                    return InteractionResult.SUCCESS;
                }

                this.destroyRearAttachment(!player.isCreative());
                this.playHitSound(this.rearAttachment.pos());

                return InteractionResult.PASS;
            } else {
                if (level().isClientSide()) {
                    return InteractionResult.SUCCESS;
                }

                this.destroyAutomobile(!player.isCreative(), RemovalReason.KILLED);
                this.playHitSound(this.position());

                return InteractionResult.PASS;
            }
        }

        if (vulnerable && stack.getItem() instanceof AutomobileInteractable interactable) {
            return interactable.interactAutomobile(stack, player, hand, this);
        }

        if (!this.decorative) {
            if (!this.hasSpaceForPassengers()) {
                if (canKickPassengerOut(this.getFirstPassenger(), player)) {
                    if (!level().isClientSide()) {
                        this.getFirstPassenger().stopRiding();
                    }
                    return level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
                }
                return InteractionResult.PASS;
            }
            if (!level().isClientSide()) {
                player.startRiding(this);
            }
            return level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }

        return InteractionResult.PASS;
    }

    public Vec3 fixPassengerRidingOffset(Vec3 offset) {
        return offset.add(0, getWheels().model().radius(), 0).scale(1.0 / 16);
    }

    @Override
    public void positionRider(Entity passenger, Entity.MoveFunction moveFunc) {
        if (getFrame() == null) {
            super.positionRider(passenger, moveFunc);
        }

        var attPoint = passenger.getVehicleAttachmentPoint(this);
        double attYOffset = passenger.getEyeHeight() - attPoint.y();
        var frameModel = getFrame().model();

        if (this.hasPassenger(passenger)) {
            int idx = this.getPassengers().indexOf(passenger);
            var offset = Vec3.ZERO;

            if (idx == 0) {
                offset = this.fixPassengerRidingOffset(frameModel.driverSeatPos());
            } else if (idx <= frameModel.passengerSeats().size()) {
                offset = this.fixPassengerRidingOffset(frameModel.passengerSeats().get(idx - 1));
            } else {
                offset = new Vec3(0, 0, -this.getFrame().model().rearAttachmentPos() / 16)
                        .add(this.rearAttachment.scaledYawVec().yRot((float) Math.toRadians(this.getYRot())))
                        .add(0, this.rearAttachment.getPassengerHeightOffset(), 0);
            }

            var pos = new Vector3d(offset.x(), offset.y() + attYOffset, offset.z());
            this.localPosToWorldSpace(pos);
            pos.sub(attPoint.x(), attPoint.y() + attYOffset, attPoint.z());

            moveFunc.accept(passenger, pos.x(), pos.y(), pos.z());

            if (getControllingPassenger() != passenger) whenRotated(this.getYRot() - this.prevYawForRotate, passenger);
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        float maxWidth = this.getBbWidth();
        float maxHeight = this.getBbHeight();

        for (var box : this.getFrame().hitboxes()) {
            var origin = box.origin();
            if (Math.abs(origin.z()) > 0.5f * box.width()) {
                continue;
            }

            float width = ((float) Math.abs(origin.x()) + 0.5f * box.width()) * 2;
            if (width > maxWidth) maxWidth = width;

            float height = (float) (origin.y() + box.height() * 0.5);
            if (height > maxHeight) maxHeight = height;
        }

        var pos = new BlockPos.MutableBlockPos();
        double maxDismountHeight = this.getBoundingBox().maxY + 0.75;
        float preferredSide = passenger.getMainArm() == HumanoidArm.RIGHT ? 90 : -90;

        for (float sideOffset : DismountSearchPolicy.yawOffsets(preferredSide)) {
            float yaw = this.getYRot() + sideOffset;
            var dir = Entity.getCollisionHorizontalEscapeVector(maxWidth + 0.75, passenger.getBbWidth(), yaw);

            var scanPos = new Vector3d(
                    dir.x() + getX(),
                    dir.y() + this.displacement.getVertical(1),
                    dir.z() + getZ());

            for (var pose : passenger.getDismountPoses()) {
                pos.set(scanPos.x(), scanPos.y(), scanPos.z());

                while (true) {
                    double height = this.level().getBlockFloorHeight(pos);
                    if (pos.getY() + height > maxDismountHeight) break;

                    if (DismountHelper.isBlockFloorValid(height)) {
                        var bounds = passenger.getLocalBoundsForPose(pose);
                        var dismountPos = new Vec3(scanPos.x(), (double)pos.getY() + height, scanPos.z());
                        if (DismountHelper.canDismountTo(this.level(), passenger, bounds.move(dismountPos))) {
                            passenger.setPose(pose);
                            return dismountPos;
                        }
                    }

                    pos.move(Direction.UP);
                    if (pos.getY() > maxDismountHeight) break;
                }
            }
        }

        var fallback = new Vec3(
                this.getX(),
                Math.max(this.getBoundingBox().maxY, this.getY() + maxHeight),
                this.getZ()
        );
        for (var pose : passenger.getDismountPoses()) {
            var bounds = passenger.getLocalBoundsForPose(pose);
            if (DismountHelper.canDismountTo(this.level(), passenger, bounds.move(fallback))) {
                passenger.setPose(pose);
                return fallback;
            }
        }
        return fallback;
    }

    private boolean canCollideWithAutomobile(Entity entity) {
        if (entity instanceof HitboxEntity hitbox) {
            return hitbox.automobile() != this;
        }

        return false;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return this.canCollideWithAutomobile(entity);
    }

    @Override
    public boolean canBeCollidedWith(Entity entity) {
        return this.canCollideWithAutomobile(entity);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(REAR_ATTACHMENT_YAW, 0f);
        builder.define(REAR_ATTACHMENT_ANIMATION, 0f);
        builder.define(FRONT_ATTACHMENT_ANIMATION, 0f);

        builder.define(FRAME_TYPE, Holder.direct(AutomobileFrame.EMPTY));
        builder.define(WHEEL_TYPE, Holder.direct(AutomobileWheel.EMPTY));
        builder.define(ENGINE_TYPE, Holder.direct(AutomobileEngine.EMPTY));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        super.onSyncedDataUpdated(data);

        if (REAR_ATTACHMENT_YAW.equals(data)) {
            this.rearAttachment.onTrackedYawUpdated(getTrackedRearAttachmentYaw());
        } else if (REAR_ATTACHMENT_ANIMATION.equals(data)) {
            this.rearAttachment.onTrackedAnimationUpdated(getTrackedRearAttachmentAnimation());
        } else if (FRONT_ATTACHMENT_ANIMATION.equals(data)) {
            this.frontAttachment.onTrackedAnimationUpdated(getTrackedFrontAttachmentAnimation());
        } else if (FRAME_TYPE.equals(data)) {
            this.displacement.applyWheelbase(getFrame().model().wheelBase());

            this.size = getFrame().makeBounds();
            this.refreshDimensions();
        }

        if (FRAME_TYPE.equals(data) || WHEEL_TYPE.equals(data) || ENGINE_TYPE.equals(data)) {
            this.stats.from(getFrame(), getWheels(), getEngine());
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, entity);
    }

    public void setTrackedRearAttachmentYaw(float value) {
        this.entityData.set(REAR_ATTACHMENT_YAW, value);
    }

    public float getTrackedRearAttachmentYaw() {
        return this.entityData.get(REAR_ATTACHMENT_YAW);
    }

    public void setTrackedRearAttachmentAnimation(float animation) {
        this.entityData.set(REAR_ATTACHMENT_ANIMATION, animation);
    }

    public float getTrackedRearAttachmentAnimation() {
        return this.entityData.get(REAR_ATTACHMENT_ANIMATION);
    }

    public void setTrackedFrontAttachmentAnimation(float animation) {
        this.entityData.set(FRONT_ATTACHMENT_ANIMATION, animation);
    }

    public float getTrackedFrontAttachmentAnimation() {
        return this.entityData.get(FRONT_ATTACHMENT_ANIMATION);
    }

    public void bounce() {
        suspensionBounceTimer = 3;
        level().playLocalSound(this.getX(), this.getY(), this.getZ(), AutomobilitySounds.LANDING.require(), SoundSource.AMBIENT, 1, 1.5f + (0.15f * (this.getRandom().nextFloat() - 0.5f)), true);
        controllerAction(AutomobileController::groundThudRumble);
    }

    @Override
    public Vec3 collide(Vec3 mvmt) {
        var myCollider = this.getBoundingBox();

        var entityColliders = this.level().getEntityCollisions(this, myCollider.expandTowards(mvmt));
        var mvmtHit = mvmt.lengthSqr() == 0 ? mvmt : collideBoundingBox(this, mvmt, myCollider, this.level(), entityColliders);

        boolean movedIntoGround = mvmt.y != mvmtHit.y && mvmt.y < 0;

        if (this.maxUpStep() > 0 &&
                (movedIntoGround || this.onGround()) &&
                (mvmt.x != mvmtHit.x || mvmt.z != mvmtHit.z)) {
            double upStep = this.maxUpStep();
            var mvmtUpHit = moveAndCollide(this, new Vec3(mvmt.x, upStep, mvmt.z), myCollider, this.level(), entityColliders);
            var mvmtHorizHit = moveAndCollide(this, new Vec3(0, upStep, 0), myCollider.expandTowards(mvmt.x, 0, mvmt.z), this.level(), entityColliders);
            if (mvmtHorizHit.y < upStep) {
                var mvmtHorizThenUp = moveAndCollide(this, new Vec3(mvmt.x, 0, mvmt.z), myCollider.move(mvmtHorizHit), this.level(), entityColliders).add(mvmtHorizHit);
                if (mvmtHorizThenUp.horizontalDistanceSqr() > mvmtUpHit.horizontalDistanceSqr()) {
                    mvmtUpHit = mvmtHorizThenUp;
                }
            }

            if (mvmtUpHit.horizontalDistanceSqr() > mvmtHit.horizontalDistanceSqr()) {
                return mvmtUpHit.add(moveAndCollide(this, new Vec3(0, mvmt.y - mvmtUpHit.y, 0), myCollider.move(mvmtUpHit), this.level(), entityColliders));
            }
        }

        return mvmtHit;
    }

    public static Vec3 moveAndCollide(Entity entity, Vec3 mvmt, AABB myCollider, Level level, List<VoxelShape> nearbyEntities) {
        var colliders = collectColliders(entity, level, nearbyEntities, myCollider.expandTowards(mvmt));
        return collideWithShapes(mvmt, myCollider, colliders);
    }

    /**
     * Transform a local (model space) position into world space
     */
    public void localPosToWorldSpace(Vector3d position) {
        position.rotateY(Math.toRadians(-this.getYRot()));
        var disp = this.getDisplacement();

        var rot = new Quaternionf();
        disp.getAngular(1, rot);

        position.rotate(new Quaterniond(rot.x(), rot.y(), rot.z(), rot.w()));

        var pos = this.position();
        position.add(pos.x(), pos.y() + disp.getVerticalOffset(1, this), pos.z());
    }

    public void localPosToStableWorldSpace(Vector3d position) {
        position.rotateY(Math.toRadians(-this.getYRot()));

        var pos = this.position();
        position.add(pos.x(), pos.y(), pos.z());
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return size;
    }

    @Override
    public Container underlyingContainer() {
        if (getRearAttachment() instanceof Container container) {
            return container;
        }

        return null;
    }

    public static final class Displacement {
        private static final int SCAN_STEPS_PER_BLOCK = 20;
        private static final double INV_SCAN_STEPS = 1d / SCAN_STEPS_PER_BLOCK;

        private boolean wereAllOnGround = true;
        private double lastVertical = 0;
        private double currVertical = 0;
        private double verticalTarget = 0;
        private final Quaternionf lastAngular = new Quaternionf();
        private final Quaternionf currAngular = new Quaternionf();
        private final Quaternionf angularTarget = new Quaternionf();
        private final List<Vec3> scanPoints = new ArrayList<>();
        public final Set<CollisionArea> otherColliders = new HashSet<>();
        private boolean hasSyncedData = false;

        public void preTick() {
            this.lastVertical = currVertical;
            this.lastAngular.set(this.currAngular);
        }

        public void postTick() {
            float lerpAmount = 1;

            var rotation = angularTarget.mul(currAngular.invert(new Quaternionf()), new Quaternionf());
            var rotationInfo = new AxisAngle4f(rotation);
            if (rotationInfo.angle > 0) {
                lerpAmount = (float) Math.min(rotationInfo.angle, Math.toRadians(7)) / rotationInfo.angle;
                lerpAmount = Mth.clamp(lerpAmount, 0, 1);
                this.currAngular.slerp(this.angularTarget, lerpAmount);
            }

            this.currVertical = Mth.lerp(Mth.sqrt(lerpAmount), this.currVertical, this.verticalTarget);
        }

        public void tick(Level world, AutomobileEntity entity, Vec3 centerPos, double yaw, double stepHeight) {
            yaw = 360 - yaw;
            Vec3 lowestDisplacementPos = null;
            Vec3 highestDisplacementPos = null;
            var scannedPoints = new ArrayList<Vec3>();
            var colliders = new HashSet<CollisionArea>();
            boolean anyOnGround = false;
            boolean allOnGround = true;
            for (var scanPoint : scanPoints) {
                scanPoint = scanPoint
                        .yRot((float) Math.toRadians(yaw));
                var pointPos = scanPoint.add(centerPos);
                colliders.clear();
                colliders.addAll(this.otherColliders);

                double scanDist = scanPoint.length();

                int heightOffset = (int) Math.ceil(scanDist);
                var iter = new Cursor3D(
                        (int) Math.min(Math.floor(centerPos.x), Math.floor(pointPos.x)),
                        (int) Math.floor(centerPos.y) - heightOffset,
                        (int) Math.min(Math.floor(centerPos.z), Math.floor(pointPos.z)),
                        (int) Math.max(Math.floor(centerPos.x), Math.floor(pointPos.x)),
                        (int) Math.floor(centerPos.y) + heightOffset,
                        (int) Math.max(Math.floor(centerPos.z), Math.floor(pointPos.z))
                );

                var mpos = new BlockPos.MutableBlockPos();
                while (iter.advance()) {
                    mpos.set(iter.nextX(), iter.nextY(), iter.nextZ());
                    var state = world.getBlockState(mpos);
                    if (state.getBlock() instanceof SpecialAutomobileColliderBlock special) {
                        colliders.add(special.getCollisionArea(state, world, mpos, INV_SCAN_STEPS * 2));
                    } else {
                        var shape = state.getCollisionShape(world, mpos);
                        if (!shape.isEmpty()) {
                            if (shape == Shapes.block()) {
                                colliders.add(CollisionArea.box(mpos.getX(), mpos.getY() - (INV_SCAN_STEPS * 2), mpos.getZ(), mpos.getX() + 1, mpos.getY() + 1, mpos.getZ() + 1));
                            } else {
                                shape.move(mpos.getX(), mpos.getY(), mpos.getZ()).forAllBoxes(((minX, minY, minZ, maxX, maxY, maxZ) ->
                                        colliders.add(CollisionArea.box(minX, minY - (INV_SCAN_STEPS * 2), minZ, maxX, maxY, maxZ))));
                            }
                        }
                    }
                }

                var pointDir = new Vec3(scanPoint.x, 0, scanPoint.z).normalize().scale(INV_SCAN_STEPS);

                double pointY = centerPos.y;
                for (int i = 0; i < Math.ceil(scanDist * SCAN_STEPS_PER_BLOCK); i++) {
                    double pointX = centerPos.x + (i * pointDir.x);
                    double pointZ = centerPos.z + (i * pointDir.z);
                    double originalPointY = pointY;
                    pointY -= INV_SCAN_STEPS * 1.5;

                    boolean ground = false;
                    double nextPointY = pointY;

                    for (int j = 0; j < 2; j++) for (var col : colliders) {
                        if (col.isPointInside(pointX, nextPointY, pointZ)) {
                            nextPointY = Math.max(nextPointY, col.highestY(pointX, nextPointY, pointZ));
                            pointY = originalPointY;
                        }
                    }

                    if (nextPointY - pointY < (stepHeight + (INV_SCAN_STEPS * 1.5))) {
                        pointY = nextPointY;
                        ground = true;
                    }

                    if (ground) {
                        anyOnGround = true;
                    } else {
                        allOnGround = false;
                    }
                }

                pointPos = new Vec3(pointPos.x, pointY, pointPos.z);

                if (lowestDisplacementPos == null || pointPos.y < lowestDisplacementPos.y) {
                    lowestDisplacementPos = pointPos;
                }
                if (highestDisplacementPos == null || pointPos.y > highestDisplacementPos.y) {
                    highestDisplacementPos = pointPos;
                }

                scannedPoints.add(pointPos);
            }

            if (allOnGround && !wereAllOnGround) {
                entity.bounce();
            }
            wereAllOnGround = allOnGround;

            verticalTarget = centerPos.y;
            if (!anyOnGround) {
                return;
            }

            angularTarget.identity();

            if (lowestDisplacementPos != null) {
                var displacementCenterPos = new Vec3(centerPos.x, (lowestDisplacementPos.y + highestDisplacementPos.y) * 0.5, centerPos.z);
                var combinedNormals = Vec3.ZERO;
                int normalCount = 0;

                if (scannedPoints.size() == 2) {
                    var forward = highestDisplacementPos.subtract(lowestDisplacementPos);
                    var alongGround = forward.multiply(1, 0, 1);
                    var side = forward.cross(alongGround).normalize();

                    if (side.lengthSqr() > 0.0001) {
                        combinedNormals = forward.cross(side).normalize();
                        normalCount = 1;
                    }
                } else {
                    Vec3 positiveXOffset = null;
                    Vec3 negativeXOffset = null;
                    Vec3 positiveZOffset = null;
                    Vec3 negativeZOffset = null;

                    for (var pointPos : scannedPoints) {
                        var pointOffset = pointPos.subtract(displacementCenterPos);
                        if (pointOffset.x > 0) {
                            if (positiveXOffset != null) {
                                var normal = positiveXOffset.cross(pointOffset).normalize();
                                if (normal.y < 0) normal = normal.reverse();
                                combinedNormals = combinedNormals.add(normal);
                                normalCount++;
                                positiveXOffset = null;
                            } else positiveXOffset = pointOffset;
                        } else if (pointOffset.x < 0) {
                            if (negativeXOffset != null) {
                                var normal = negativeXOffset.cross(pointOffset).normalize();
                                if (normal.y < 0) normal = normal.reverse();
                                combinedNormals = combinedNormals.add(normal);
                                normalCount++;
                                negativeXOffset = null;
                            } else negativeXOffset = pointOffset;
                        } else if (pointOffset.z > 0) {
                            if (positiveZOffset != null) {
                                var normal = positiveZOffset.cross(pointOffset).normalize();
                                if (normal.y < 0) normal = normal.reverse();
                                combinedNormals = combinedNormals.add(normal);
                                normalCount++;
                                positiveZOffset = null;
                            } else positiveZOffset = pointOffset;
                        } else if (pointOffset.z < 0) {
                            if (negativeZOffset != null) {
                                var normal = negativeZOffset.cross(pointOffset).normalize();
                                if (normal.y < 0) normal = normal.reverse();
                                combinedNormals = combinedNormals.add(normal);
                                normalCount++;
                                negativeZOffset = null;
                            } else negativeZOffset = pointOffset;
                        }
                    }
                }

                combinedNormals = normalCount > 0 ? combinedNormals.scale(1f / normalCount) : new Vec3(0, 1, 0);

                var up = new Vector3f(0, 1, 0);
                var rotatedUp = new Vector3f((float) combinedNormals.x(), (float) combinedNormals.y(), (float) combinedNormals.z());

                if (entity.isBike()) {
                    double sign = entity.input.braking && !entity.input.accelerating ? 1 : -1;
                    var side = new Vec3(entity.smoothAngVel * sign * (entity.isDrifting() ? 0.16 : 0.08), 0, 0).yRot((float) -Math.toRadians(entity.getYRot()));
                    rotatedUp.add((float) side.x(), (float) side.y(), (float) side.z());

                    double overSpeed = entity.getMeasuredMovement().lengthSqr() - Math.pow(entity.stats.getComfortableSpeed() * 0.95, 2);
                    double boost = entity.getBoostTimer() - 5;
                    if (!entity.isDrifting() && boost > 0 && overSpeed > 0) {
                        double wheelie = -overSpeed * 0.65 * Math.clamp(Math.sqrt(boost * 0.2), 0, 1);
                        double base = entity.getFrame().model().wheelBase().longLength();
                        double sine = -wheelie / Math.sqrt(rotatedUp.lengthSquared() + wheelie * wheelie);

                        var back = new Vec3(0, 0, (float) wheelie).yRot((float) -Math.toRadians(entity.getYRot()));
                        rotatedUp.add((float) back.x(), (float) back.y(), (float) back.z());
                        displacementCenterPos = displacementCenterPos.add(0, base * sine * 0.5, 0);
                    }
                }

                rotatedUp.normalize();
                up.rotationTo(rotatedUp, this.angularTarget);

                verticalTarget = (float) displacementCenterPos.y;
            }
        }

        public void applyWheelbase(WheelBase wheelBase) {
            this.scanPoints.clear();
            for (WheelBase.WheelPos pos : wheelBase.wheels()) if (pos.end() != WheelBase.WheelEnd.NONE) {
                this.scanPoints.add(new Vec3(pos.right() / 16, 0, pos.forward() / 16));
            }
        }

        public void writeSyncData(FriendlyByteBuf buf) {
            buf.writeDouble(this.currVertical);
            buf.writeFloat(this.currAngular.x());
            buf.writeFloat(this.currAngular.y());
            buf.writeFloat(this.currAngular.z());
            buf.writeFloat(this.currAngular.w());
        }

        public void readSyncData(FriendlyByteBuf buf, boolean snap) {
            double vertical = buf.readDouble();
            var angular = new Quaternionf(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());

            if (snap || !this.hasSyncedData) {
                this.snapTo(vertical, angular);
            } else {
                this.verticalTarget = vertical;
                this.angularTarget.set(angular);
            }

            this.hasSyncedData = true;
        }

        public boolean hasSyncedData() {
            return this.hasSyncedData;
        }

        public void snapTo(double vertical, Quaternionf angular) {
            this.lastVertical = vertical;
            this.currVertical = vertical;
            this.verticalTarget = vertical;
            this.lastAngular.set(angular);
            this.currAngular.set(angular);
            this.angularTarget.set(angular);
        }

        boolean translateVerticalAfterPositionChange(double previousEntityY, double currentEntityY) {
            if (!Double.isFinite(currentEntityY)) {
                return false;
            }

            double deltaY = currentEntityY - previousEntityY;
            double translatedLast = this.lastVertical + deltaY;
            double translatedCurrent = this.currVertical + deltaY;
            double translatedTarget = this.verticalTarget + deltaY;
            if (!Double.isFinite(previousEntityY)
                    || !Double.isFinite(deltaY)
                    || !Double.isFinite(translatedLast)
                    || !Double.isFinite(translatedCurrent)
                    || !Double.isFinite(translatedTarget)) {
                return this.resetVerticalAfterPositionChange(currentEntityY);
            }

            this.lastVertical = translatedLast;
            this.currVertical = translatedCurrent;
            this.verticalTarget = translatedTarget;
            return true;
        }

        boolean resetVerticalAfterPositionChange(double currentEntityY) {
            if (!Double.isFinite(currentEntityY)) {
                return false;
            }

            this.lastVertical = currentEntityY;
            this.currVertical = currentEntityY;
            this.verticalTarget = currentEntityY;
            return true;
        }

        public double getVertical(float tickDelta) {
            return Mth.lerp(tickDelta, lastVertical, currVertical);
        }

        public float getVerticalOffset(float tickDelta, AutomobileEntity entity) {
            return (float) (getVertical(tickDelta) - entity.getPosition(tickDelta).y());
        }

        public void getAngular(float tickDelta, Quaternionf rot) {
            this.lastAngular.slerp(this.currAngular, tickDelta, rot);
        }
    }

    public static class Input {
        public boolean accelerating, braking, holdingDrift, holdingHorn;
        public float steering;

        public boolean setDigitalInputs(boolean fwd, boolean back, boolean left, boolean right, boolean space, boolean ctrl) {
            boolean isLeft = steering < 0;
            boolean isRight = steering > 0;

            boolean changed = fwd != this.accelerating || back != this.braking || left != isLeft || right != isRight ||
                    space != this.holdingDrift || ctrl != this.holdingHorn;

            this.accelerating = fwd;
            this.braking = back;
            this.steering = (left ? -1 : 0) + (right ? 1 : 0);
            this.holdingDrift = space;
            this.holdingHorn = ctrl;

            return changed;
        }

        public boolean setInputs(boolean fwd, boolean back, float side, boolean space, boolean ctrl) {
            boolean changed = fwd != this.accelerating || back != this.braking || this.steering != side ||
                    space != this.holdingDrift || ctrl != this.holdingHorn;

            this.accelerating = fwd;
            this.braking = back;
            this.steering = side;
            this.holdingDrift = space;
            this.holdingHorn = ctrl;

            return changed;
        }

        public void clearInputs() {
            this.accelerating = false;
            this.braking = false;
            this.steering = 0;
            this.holdingDrift = false;
            this.holdingHorn = false;
        }

        public void writePacket(FriendlyByteBuf buf) {
            buf.writeBoolean(this.accelerating);
            buf.writeBoolean(this.braking);
            buf.writeFloat(this.steering);
            buf.writeBoolean(this.holdingDrift);
            buf.writeBoolean(this.holdingHorn);
        }

        public void readPacket(FriendlyByteBuf buf) {
            this.accelerating = buf.readBoolean();
            this.braking = buf.readBoolean();
            this.steering = buf.readFloat();
            this.holdingDrift = buf.readBoolean();
            this.holdingHorn = buf.readBoolean();
        }
    }

    public record IncomingCollision(Vec3 depth, Vec3 velocity, Vec3 origin, float inertia) {
    }
}
