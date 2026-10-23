package io.github.foundationgames.automobility.entity;

import net.minecraft.nbt.*;
import java.util.*;

/** Keeps unknown addon root fields without resurrecting deliberately absent vanilla/known owner keys. */
public final class GarageOpaqueState {
    private GarageOpaqueState(){}
    private static final Set<String> VANILLA=Set.of("id","UUID","Pos","Motion","Rotation","FallDistance","Fire","Air","OnGround","Invulnerable","PortalCooldown","CustomName","CustomNameVisible","Silent","NoGravity","Glowing","Tags","Passengers","HasVisualFire","VisualFire","IsAirborne");
    public static CompoundTag capture(CompoundTag original,CompoundTag rendered){
        var unknown=new CompoundTag();for(var key:original.keySet())if(!rendered.contains(key)&&!VANILLA.contains(key)
                &&!key.startsWith("AutoFuel")&&!key.equals("AutoTrip")&&!key.startsWith("powerworks:")&&!key.equals(GarageCarrierMarker.KEY))unknown.put(key,original.get(key).copy());return unknown;
    }
    public static void appendMissing(CompoundTag output,CompoundTag opaque){for(var key:opaque.keySet())if(!output.contains(key))output.put(key,opaque.get(key).copy());}
    public static boolean presentExactly(CompoundTag output,CompoundTag opaque){for(var key:opaque.keySet())if(!Objects.equals(output.get(key),opaque.get(key)))return false;return true;}
}
