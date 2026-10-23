package io.github.foundationgames.automobility.entity;

import io.github.foundationgames.automobility.util.duck.GarageRawInput;
import net.minecraft.nbt.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import java.util.*;

/** The world-loader boundary precedes EntityType construction and recursive passenger loading. */
public final class GarageWorldAdmission {
    private GarageWorldAdmission() {}
    private static final ThreadLocal<Deque<Origin>> ORIGINS=ThreadLocal.withInitial(ArrayDeque::new);
    public static final class RetentionFailure extends Error {
        public RetentionFailure(Throwable cause){super("Garage raw retention failed; original entity chunk is NOT SAVED",cause);}
    }
    public static final class Origin implements AutoCloseable {
        final Object storage;final ServerLevel level;final ChunkPos chunk;final CompoundTag original;
        final List<AutomobileEntity> bound=new ArrayList<>();boolean success;
        Origin(Object storage,ServerLevel level,ChunkPos chunk,Optional<CompoundTag> raw){this.storage=storage;this.level=level;this.chunk=chunk;this.original=raw.map(CompoundTag::copy).orElseGet(CompoundTag::new);ORIGINS.get().push(this);}
        public void complete(){success=true;}
        public void failure(Throwable error){
            if(error instanceof RetentionFailure){GarageFailedOrigins.fail(storage,chunk.pack());GarageTransferRuntime.retentionFailed(level.getServer(),storage,error);}
        }
        @Override public void close(){
            if(ORIGINS.get().peek()!=this)throw new IllegalStateException("Garage origin scope order");
            ORIGINS.get().pop();if(!success)for(var car:bound)GarageTransferRuntime.unload(car);
            if(ORIGINS.get().isEmpty())ORIGINS.remove();
        }
    }
    public static Origin origin(Object storage,ServerLevel level,ChunkPos chunk,Optional<CompoundTag> raw){return new Origin(storage,level,chunk,raw);}
    static Origin current(){return ORIGINS.get().peek();}
    static void bound(AutomobileEntity car){var scope=current();if(scope!=null)scope.bound.add(car);}
    public static CompoundTag raw(ValueInput input){return input instanceof GarageRawInput raw?raw.automobility$garageRawCopy():null;}
    static UUID uuid(CompoundTag raw){
        var tag=raw.get("UUID");return tag==null?null:UUIDUtil.CODEC.parse(NbtOps.INSTANCE,tag).result().orElse(null);
    }
    /** True only after the complete rejected input has a verified durable receipt. */
    public static boolean reject(ValueInput input,Level level){return reject(input,level,false);}
    public static boolean reject(ValueInput input,Level level,boolean explicitAutomobile){
        if(!(level instanceof ServerLevel server))return false;
        var raw=raw(input);if(raw==null)return false;
        if(!explicitAutomobile&&!"automobility:automobile".equals(raw.getStringOr("id","")))return false;
        var id=uuid(raw);if(id==null){if(raw.contains(GarageCarrierMarker.KEY))throw new RetentionFailure(new java.io.IOException("Protected incoming UUID is invalid"));return false;}
        return GarageTransferRuntime.rejectIncoming(server,id,raw,current());
    }
}
