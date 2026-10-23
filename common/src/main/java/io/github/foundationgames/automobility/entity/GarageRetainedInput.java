package io.github.foundationgames.automobility.entity;

import net.minecraft.nbt.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import java.io.IOException;
import java.util.*;

/** Strict boot admission of a retained raw input, before it can become an authority/fence. */
public final class GarageRetainedInput {
    private GarageRetainedInput(){}
    private static final Set<String> KEYS=Set.of("runtimeSchema","processedEntity","ambiguous","sourceChunkX","sourceChunkZ","originalDiskChunk","source","observedTransaction","observedGeneration");
    public static boolean ambiguous(CompoundTag payload,UUID vehicle,String dimension)throws IOException {
        if(vehicle==null||Identifier.tryParse(dimension)==null||!Identifier.tryParse(dimension).toString().equals(dimension)
                ||!KEYS.containsAll(payload.keySet())||!(payload.get("runtimeSchema") instanceof IntTag)||payload.getIntOr("runtimeSchema",0)!=1
                ||(!ByteTag.valueOf(true).equals(payload.get("ambiguous"))&&!ByteTag.valueOf(false).equals(payload.get("ambiguous"))))throw new IOException("Invalid retained Garage schema");
        var raw=payload.getCompound("processedEntity").orElseThrow(()->new IOException("Missing retained entity"));
        var uuid=raw.get("UUID");
        if(uuid==null||!vehicle.equals(UUIDUtil.CODEC.parse(NbtOps.INSTANCE,uuid).result().orElse(null))
                ||(raw.contains("id")&&(! (raw.get("id") instanceof StringTag)||!"automobility:automobile".equals(raw.getStringOr("id","")))))throw new IOException("Retained entity identity changed");
        if(payload.contains("source")){
            if(!"DIRECT_FACTORY".equals(payload.getStringOr("source",""))||payload.contains("sourceChunkX")||payload.contains("sourceChunkZ")||payload.contains("originalDiskChunk"))throw new IOException("Invalid retained direct origin");
        }else{
            if(!(payload.get("sourceChunkX") instanceof IntTag)||!(payload.get("sourceChunkZ") instanceof IntTag))throw new IOException("Missing retained chunk origin");
            var original=payload.getCompound("originalDiskChunk").orElseThrow(()->new IOException("Missing original disk chunk"));
            var position=original.getIntArray("Position").orElseThrow(()->new IOException("Missing original chunk coordinates"));
            if(position.length!=2||position[0]!=payload.getIntOr("sourceChunkX",0)||position[1]!=payload.getIntOr("sourceChunkZ",0))throw new IOException("Retained chunk coordinates changed");
        }
        if(payload.contains("observedTransaction")||payload.contains("observedGeneration")){
            var text=payload.getStringOr("observedTransaction","");
            try{if(!(payload.get("observedTransaction") instanceof StringTag)||!UUID.fromString(text).toString().equals(text)
                    ||!(payload.get("observedGeneration") instanceof LongTag)||payload.getLongOr("observedGeneration",0)<=0)throw new IOException("Invalid retained transaction identity");}
            catch(IllegalArgumentException error){throw new IOException("Invalid retained transaction UUID",error);}
        }
        return payload.getBooleanOr("ambiguous",true);
    }
}
