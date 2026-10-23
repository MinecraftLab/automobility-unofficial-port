package io.github.foundationgames.automobility.entity;
import net.minecraft.nbt.*;
import java.io.*;
import java.security.*;
import java.util.*;
/** Digest encoding is independent of CompoundTag map iteration; list order, NBT types and zero sign stay exact. */
public final class GarageCanonicalNbt {
    private GarageCanonicalNbt(){}
    public static String digest(CompoundTag value)throws IOException {
        if(value.sizeInBytes()>GarageTransferJournal.MAX_BYTES)throw new IOException("Engine context exceeds journal bound");
        var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){write(value,out,0);}
        if(bytes.size()>GarageTransferJournal.MAX_BYTES)throw new IOException("Encoded engine context exceeds journal bound");
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static void write(Tag value,DataOutput out,int depth)throws IOException {
        if(depth>128)throw new IOException("Engine context nesting exceeds supported bound");out.writeByte(value.getId());
        if(value instanceof CompoundTag compound){
            var keys=new TreeSet<>(compound.keySet());out.writeInt(keys.size());
            for(var key:keys){out.writeUTF(key);write(Objects.requireNonNull(compound.get(key)),out,depth+1);}
        }else if(value instanceof ListTag list){
            out.writeInt(list.size());for(var child:list)write(child,out,depth+1);
        }else value.write(out);
    }
}
