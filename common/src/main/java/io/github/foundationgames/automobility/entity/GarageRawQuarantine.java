package io.github.foundationgames.automobility.entity;

import net.minecraft.nbt.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Last authority for a rejected incoming raw carrier. Never deserialize it merely to retain it. */
public final class GarageRawQuarantine {
    private final Path directory;
    public GarageRawQuarantine(Path directory){this.directory=directory.toAbsolutePath().normalize();}
    private static byte[] bytes(CompoundTag tag)throws IOException {
        if(tag.sizeInBytes()>GarageTransferJournal.MAX_BYTES)throw new IOException("Quarantine record too large");
        var output=new ByteArrayOutputStream();try(var data=new DataOutputStream(output)){NbtIo.write(tag,data);}
        if(output.size()>GarageTransferJournal.MAX_BYTES)throw new IOException("Quarantine record too large");return output.toByteArray();
    }
    private static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    public Path retain(UUID vehicle,String dimension,CompoundTag original,String reason)throws IOException {
        Objects.requireNonNull(vehicle);Objects.requireNonNull(dimension);Objects.requireNonNull(original);
        if(reason==null||reason.isBlank()||reason.length()>256)throw new IllegalArgumentException("Bounded quarantine reason required");
        var payload=new CompoundTag();payload.putString("vehicle",vehicle.toString());payload.putString("dimension",dimension);payload.put("raw",original.copy());
        var content=bytes(payload);var digest=hash(content);Files.createDirectories(directory);var target=directory.resolve(vehicle+"-"+digest+".nbt");
        if(Files.exists(target)){if(!read(target).equals(payload))throw new IOException("Existing quarantine record changed");return target;}
        var envelope=new CompoundTag();envelope.putInt("schema",1);envelope.putByteArray("payload",content);envelope.putString("sha256",digest);envelope.putString("reason",reason);
        var encoded=bytes(envelope);long total=encoded.length;int count=1;
        try(var stream=Files.list(directory)){
            for(var file:stream.limit(GarageTransferJournal.MAX_RECORDS*4L+1).toList()){
                if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Nonregular quarantine record");
                total=Math.addExact(total,Files.size(file));count++;
            }
        }
        if(count>GarageTransferJournal.MAX_RECORDS||total>GarageTransferJournal.MAX_TOTAL_BYTES)throw new IOException("Quarantine capacity exceeded; incoming carrier must not be discarded");
        var pending=directory.resolve(target.getFileName()+".pending-"+UUID.randomUUID());
        try(var out=Files.newOutputStream(pending,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){out.write(encoded);}
        try(var channel=FileChannel.open(pending,StandardOpenOption.WRITE)){channel.force(true);}
        try{Files.move(pending,target,StandardCopyOption.ATOMIC_MOVE);}catch(IOException error){
            if(!Files.exists(target)||!read(target).equals(payload))throw error;
        }
        if(!read(target).equals(payload))throw new IOException("Quarantine readback mismatch");return target;
    }
    public static CompoundTag read(Path file)throws IOException {
        if(Files.size(file)>GarageTransferJournal.MAX_BYTES)throw new IOException("Quarantine record too large");
        try(var input=new DataInputStream(Files.newInputStream(file))){
            var envelope=NbtIo.read(input,NbtAccounter.create(GarageTransferJournal.MAX_BYTES));
            if(input.read()!=-1||!envelope.keySet().equals(Set.of("schema","payload","sha256","reason"))
                    ||!(envelope.get("schema") instanceof IntTag)||envelope.getIntOr("schema",0)!=1
                    ||!(envelope.get("sha256") instanceof StringTag)||!(envelope.get("reason") instanceof StringTag))throw new IOException("Unknown quarantine envelope");
            var content=envelope.getByteArray("payload").orElseThrow();
            if(!hash(content).equals(envelope.getStringOr("sha256","")))throw new IOException("Quarantine checksum mismatch");
            try(var data=new DataInputStream(new ByteArrayInputStream(content))){
                var result=NbtIo.read(data,NbtAccounter.create(GarageTransferJournal.MAX_BYTES));
                if(data.read()!=-1||!result.keySet().equals(Set.of("vehicle","dimension","raw"))||!(result.get("vehicle") instanceof StringTag)
                        ||!(result.get("dimension") instanceof StringTag)||!(result.get("raw") instanceof CompoundTag))throw new IOException("Malformed quarantine payload");
                var identity=result.getStringOr("vehicle","");var vehicle=UUID.fromString(identity);
                var dimension=result.getStringOr("dimension","");var parsed=net.minecraft.resources.Identifier.tryParse(dimension);
                if(!vehicle.toString().equals(identity)||parsed==null||!parsed.toString().equals(dimension)
                        ||!file.getFileName().toString().equals(identity+"-"+hash(content)+".nbt"))throw new IOException("Quarantine filename/identity mismatch");
                return result;
            }
        }catch(RuntimeException error){throw new IOException("Malformed quarantine record",error);}
    }
}
