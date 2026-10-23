package io.github.foundationgames.automobility.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import io.github.foundationgames.automobility.automobile.AutomobileFrame;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** All candidate geometry is detached. A failed search never moves the live car or player. */
public final class GarageReturnPlacement {
    public record Placement(Vec3 car,float yaw,Vec3 player) {}
    private GarageReturnPlacement() {}
    public static Optional<Placement> find(ServerPlayer player,AutomobileEntity car,BlockPos origin,Vec3 preferredPlayer) {
        var level=(ServerLevel)player.level(); if(car.level()!=level) return Optional.empty();
        var parts=car.garageComponents(); if(parts==null||!parts.complete()) return Optional.empty();
        // Geometry only: even a detached entity's setComponents can invoke addon callbacks.
        var frame=parts.frame().value();
        var state=level.getBlockState(origin);
        var facing=state.hasProperty(HorizontalDirectionalBlock.FACING)?state.getValue(HorizontalDirectionalBlock.FACING):Direction.NORTH;
        for(var direction:List.of(facing,facing.getClockWise(),facing.getCounterClockWise(),facing.getOpposite())) for(int distance:new int[]{4,5,6}) {
            var position=origin.relative(direction,distance);
            var carPosition=new Vec3(position.getX()+.5,position.getY(),position.getZ()+.5); float yaw=direction.toYRot();
            double lift=GaragePlacementPolicy.liftForGround(position.getY(),bounds(frame,carPosition,yaw).minY);
            if(!Double.isFinite(lift)) continue;
            carPosition=carPosition.add(0,lift,0);
            if(carPosition.distanceToSqr(Vec3.atCenterOf(origin))>36) continue;
            var carBounds=bounds(frame,carPosition,yaw).inflate(.05,0,.05);
            if(!loaded(level,carBounds)||!level.getWorldBorder().isWithinBounds(carBounds)
                    ||!permitted(level,player,carBounds)||occupied(level,car,player,carBounds)||!level.noCollision(car,carBounds)
                    ||level.noCollision(car,carBounds.move(0,-.2,0))) continue;
            var exits=new ArrayList<Vec3>(); exits.add(preferredPlayer);
            exits.add(Vec3.atBottomCenterOf(origin.above()));
            double separation=Math.max(carBounds.getXsize(),carBounds.getZsize())/2+.8;
            for(var side:List.of(direction.getClockWise(),direction.getCounterClockWise(),direction.getOpposite(),direction))
                exits.add(new Vec3(carPosition.x+side.getStepX()*separation,position.getY(),carPosition.z+side.getStepZ()*separation));
            for(var exit:exits) {
                if(!withinServiceRange(Vec3.atCenterOf(origin),carPosition,exit)) continue;
                var box=player.getDimensions(Pose.STANDING).makeBoundingBox(exit);
                if(box.intersects(carBounds)||!loaded(level,box)||!level.getWorldBorder().isWithinBounds(box)
                        ||!permitted(level,player,box)||occupied(level,car,player,box)||!level.noCollision(player,box)||level.noCollision(player,box.move(0,-.2,0))) continue;
                return Optional.of(new Placement(carPosition,yaw,exit));
            }
        }
        return Optional.empty();
    }
    /** Geometry-only menu query: no entity construction, load or owner callbacks. */
    public static Optional<Placement> findDeployment(ServerPlayer player,GarageService.Components parts,BlockPos origin){
        var level=player.level();if(parts==null||!parts.complete())return Optional.empty();
        var frame=parts.frame().value();var state=level.getBlockState(origin);
        var facing=state.hasProperty(HorizontalDirectionalBlock.FACING)?state.getValue(HorizontalDirectionalBlock.FACING):Direction.NORTH;
        for(var direction:List.of(facing,facing.getClockWise(),facing.getCounterClockWise(),facing.getOpposite()))for(int distance:new int[]{4,5,6}){
            var pos=origin.relative(direction,distance);float yaw=direction.toYRot();var at=new Vec3(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
            double lift=GaragePlacementPolicy.liftForGround(pos.getY(),bounds(frame,at,yaw).minY);if(!Double.isFinite(lift))continue;at=at.add(0,lift,0);
            if(!withinServiceRange(Vec3.atCenterOf(origin),at,player.position()))continue;
            var box=bounds(frame,at,yaw).inflate(.05,0,.05);
            if(!loaded(level,box)||!level.getWorldBorder().isWithinBounds(box)||!permitted(level,player,box)
                    ||box.intersects(player.getBoundingBox())||occupied(level,null,player,box)||!level.noCollision(null,box)||level.noCollision(null,box.move(0,-.2,0)))continue;
            return Optional.of(new Placement(at,yaw,player.position()));
        }return Optional.empty();
    }
    public static boolean finite(Vec3 p) { return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z); }
    /** Automobile collision filtering intentionally ignores mobs; workshop placement must not. */
    private static boolean occupied(ServerLevel level,AutomobileEntity car,ServerPlayer driver,AABB box) {
        return !level.getEntities(car,box,entity->entity!=driver&&entity.isAlive()&&!entity.isSpectator()
                &&!(entity instanceof HitboxEntity hitbox&&hitbox.automobile()==car)
                &&(entity instanceof AutomobileEntity||entity.canBeCollidedWith(car==null?driver:car)||entity.isPickable()||entity.isPushable())).isEmpty();
    }
    public static boolean withinServiceRange(Vec3 origin,Vec3 car,Vec3 player) {
        return finite(origin)&&finite(car)&&finite(player)&&car.distanceToSqr(origin)<=36
                &&player.distanceToSqr(origin)<=64&&player.distanceToSqr(car)<=64;
    }
    /** Newly deployed cars have not ticked their automobile ground flag yet. Check their actual colliders. */
    public static boolean supported(AutomobileEntity car) {
        if(!(car.level() instanceof ServerLevel level)||car.garageComponents()==null) return false;
        var bounds=bounds(car.getFrame(),car.position(),car.getYRot()).inflate(.05,0,.05);
        return loaded(level,bounds)&&level.noCollision(car,bounds)&&!level.noCollision(car,bounds.move(0,-.2,0));
    }
    /** Detached activation uses a neutral collision context, never a fenced car's filters. */
    public static boolean supported(ServerLevel level,GarageService.Components parts,Vec3 position,float yaw){
        if(parts==null||!parts.complete()||!finite(position)||!Float.isFinite(yaw))return false;
        var box=bounds(parts.frame().value(),position,yaw).inflate(.05,0,.05);
        return loaded(level,box)&&level.getWorldBorder().isWithinBounds(box)&&level.noCollision(null,box)&&!level.noCollision(null,box.move(0,-.2,0));
    }
    public static boolean loaded(ServerLevel level,AABB bounds) {
        if(!bounded(bounds)) return false;
        if(!level.hasChunksAt(BlockPos.containing(bounds.minX,bounds.minY-1,bounds.minZ),BlockPos.containing(bounds.maxX,bounds.maxY,bounds.maxZ))) return false;
        for(int x=((int)Math.floor(bounds.minX))>>4;x<=((int)Math.floor(bounds.maxX))>>4;x++)
            for(int z=((int)Math.floor(bounds.minZ))>>4;z<=((int)Math.floor(bounds.maxZ))>>4;z++) if(!level.areEntitiesLoaded(ChunkPos.pack(x,z))) return false;
        return true;
    }
    /** Bound server query cost for unusual datapack frames as well as malformed geometry. */
    static boolean bounded(AABB box) {
        return finite(new Vec3(box.minX,box.minY,box.minZ))&&finite(new Vec3(box.maxX,box.maxY,box.maxZ))
                &&box.getXsize()>0&&box.getYsize()>0&&box.getZsize()>0
                &&box.getXsize()<=16&&box.getYsize()<=16&&box.getZsize()<=16;
    }
    private static boolean permitted(ServerLevel level,ServerPlayer player,AABB box) {
        for(int x=(int)Math.floor(box.minX);x<=(int)Math.floor(box.maxX);x++)
            for(int y=(int)Math.floor(box.minY);y<=(int)Math.floor(box.maxY);y++)
                for(int z=(int)Math.floor(box.minZ);z<=(int)Math.floor(box.maxZ);z++) if(!level.mayInteract(player,new BlockPos(x,y,z))) return false;
        return true;
    }
    private static AABB bounds(AutomobileFrame frame,Vec3 position,float rotation) {
        var result=frame.makeBounds().makeBoundingBox(position); double yaw=Math.toRadians(-rotation),cos=Math.cos(yaw),sin=Math.sin(yaw);
        for(var box:frame.hitboxes().isEmpty()?List.of(AutomobileFrame.Hitbox.DEFAULT):frame.hitboxes()) {
            var o=box.origin(); double x=position.x+o.x*cos+o.z*sin,y=position.y+o.y,z=position.z+o.z*cos-o.x*sin,half=box.width()/2;
            result=result.minmax(new AABB(x-half,y-box.height()/2,z-half,x+half,y+box.height()/2,z+half));
        }
        return result;
    }
}
