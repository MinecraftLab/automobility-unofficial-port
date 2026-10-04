package io.github.foundationgames.automobility.entity;

import io.github.foundationgames.automobility.automobile.AutomobileFrame;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class HitboxEntity extends Entity implements EntityWithContainer {
    public static final EntityDataAccessor<Integer> AUTOMOBILE = SynchedEntityData.defineId(HitboxEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Vector3fc> ORIGIN = SynchedEntityData.defineId(HitboxEntity.class, EntityDataSerializers.VECTOR3);
    public static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(HitboxEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(HitboxEntity.class, EntityDataSerializers.FLOAT);

    private EntityDimensions size;

    public HitboxEntity(Level level, AutomobileEntity automobile, AutomobileFrame.Hitbox hitbox) {
        super(AutomobilityEntities.HITBOX.require(), level);
        this.setRequiresPrecisePosition(true);

        this.entityData.set(AUTOMOBILE, automobile.getId());
        this.entityData.set(ORIGIN, new Vector3f((float) hitbox.origin().x(), (float) hitbox.origin().y(), (float) hitbox.origin().z()));
        this.entityData.set(WIDTH, hitbox.width());
        this.entityData.set(HEIGHT, hitbox.height());

        this.size = EntityDimensions.scalable(hitbox.width(), hitbox.height());
    }

    public HitboxEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.setRequiresPrecisePosition(true);
        this.size = EntityDimensions.scalable(1.1f, 0.7f);
    }

    public AutomobileEntity automobile() {
        var entity = this.level().getEntity(this.entityData.get(AUTOMOBILE));
        if (entity instanceof AutomobileEntity auto) {
            return auto;
        }
        return null;
    }

    public Vector3d boxOrigin() {
        var o = this.entityData.get(ORIGIN);
        return new Vector3d(o.x(), o.y(), o.z());
    }

    public float width() {
        return this.size.width();
    }

    public float height() {
        return this.size.height();
    }

    public boolean matches(AutomobileFrame.Hitbox hitbox) {
        var origin = this.boxOrigin();
        return Math.abs(origin.x - hitbox.origin().x()) < 0.0001
                && Math.abs(origin.y - hitbox.origin().y()) < 0.0001
                && Math.abs(origin.z - hitbox.origin().z()) < 0.0001
                && Math.abs(this.width() - hitbox.width()) < 0.0001
                && Math.abs(this.height() - hitbox.height()) < 0.0001;
    }

    public boolean matches(HitboxEntity hitbox) {
        var origin = this.boxOrigin();
        var otherOrigin = hitbox.boxOrigin();
        return Math.abs(origin.x - otherOrigin.x) < 0.0001
                && Math.abs(origin.y - otherOrigin.y) < 0.0001
                && Math.abs(origin.z - otherOrigin.z) < 0.0001
                && Math.abs(this.width() - hitbox.width()) < 0.0001
                && Math.abs(this.height() - hitbox.height()) < 0.0001;
    }

    @Override
    public void tick() {
        var automobile = automobile();

        if (automobile == null) {
            if (!this.level().isClientSide()) {
                this.remove(RemovalReason.DISCARDED);
            }

            return;
        }

        if (!this.level().isClientSide()) {
            if (!automobile.isAlive() || !automobile.hitboxes.contains(this)) {
                this.remove(RemovalReason.DISCARDED);
                return;
            }
        } else {
            if (!automobile.registerClientHitbox(this)) {
                this.remove(RemovalReason.DISCARDED);
                return;
            }
        }

        if (!this.level().isClientSide()) {
            this.updatePositionFromAutomobile();
        }
        super.tick();
    }

    public void updatePositionFromAutomobile() {
        var automobile = automobile();
        if (automobile == null) {
            return;
        }

        var pos = this.boxOrigin();
        automobile.localPosToStableWorldSpace(pos);
        this.setPos(pos.x(), pos.y() - this.size.height() * 0.5, pos.z());
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return this.size;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 hitLocation) {
        var automobile = automobile();
        if (automobile == null) return InteractionResult.PASS;

        return automobile.handleInteraction(player, hand);
    }

    @Override
    public @Nullable ItemStack getPickResult() {
        var automobile = automobile();
        if (automobile == null) return super.getPickResult();

        return automobile.asPrefabItem();
    }

    @Override
    public Component getName() {
        var auto = automobile();
        if (auto != null) {
            return auto.getName();
        }

        return super.getName();
    }

    private boolean canCollideWithAutomobileHitbox(@Nullable Entity other) {
        var automobile = automobile();
        // Minecraft 26.2 uses a null argument as a general "is this entity a hard
        // collision" query (notably from natural spawning and Lithium's optimized
        // collision iterator). An active automobile hitbox must answer yes without
        // dereferencing that nullable query target.
        if (other == null) {
            return HitboxCollisionPolicy.answersGeneralHardCollisionQuery(automobile != null);
        }
        if (other instanceof HitboxEntity hitbox) {
            return automobile != null && hitbox.automobile() != automobile;
        }

        return automobile != null
                && other.getVehicle() != automobile
                && !(other instanceof AutomobileEntity)
                && Boat.canVehicleCollide(this, other);
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return this.canCollideWithAutomobileHitbox(other);
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return this.canCollideWithAutomobileHitbox(other);
    }

    @Override
    public boolean isPickable() {
        // Client hitboxes can briefly outlive their parent automobile after entity tracking desync.
        // Keep those placeholders inert so they cannot mount the player into an invisible vehicle.
        return !this.isRemoved() && automobile() != null;
    }

    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.setPos(x, y, z);
        this.syncPacketPositionCodec(x, y, z);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        var automobile = automobile();
        return automobile != null && automobile.hurtServer(level, damageSource, amount);
    }

    @Override
    protected void lerpPositionAndRotationStep(int steps, double targetX, double targetY, double targetZ, double targetYRot, double targetXRot) {
        this.setPos(targetX, targetY, targetZ);
        this.syncPacketPositionCodec(targetX, targetY, targetZ);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(AUTOMOBILE, Integer.MIN_VALUE);
        builder.define(ORIGIN, new Vector3f());
        builder.define(WIDTH, 1.1f);
        builder.define(HEIGHT, 0.7f);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> dataAccessor) {
        super.onSyncedDataUpdated(dataAccessor);

        if (WIDTH.equals(dataAccessor) || HEIGHT.equals(dataAccessor)) {
            this.size = EntityDimensions.scalable(this.entityData.get(WIDTH), this.entityData.get(HEIGHT));
            this.refreshDimensions();
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput compound) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput compound) {
    }

    @Override
    public Container underlyingContainer() {
        var auto = automobile();
        if (auto != null) {
            return auto.underlyingContainer();
        }

        return null;
    }
}
