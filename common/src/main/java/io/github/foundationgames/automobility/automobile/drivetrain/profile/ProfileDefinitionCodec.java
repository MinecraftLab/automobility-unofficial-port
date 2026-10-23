package io.github.foundationgames.automobility.automobile.drivetrain.profile;

import java.io.*;
import java.security.*;
import java.util.*;

/** Canonical UTF-8 JSON definition framed with a version and exact length; SHA includes every physics/load field. */
public final class ProfileDefinitionCodec{
    private static final int MAGIC=0x41504431, VERSION=1;
    private ProfileDefinitionCodec(){}
    public static byte[]encode(PhysicalDefinitions definition){
        byte[] json=ProfileJson.write(Objects.requireNonNull(definition));if(json.length>PhysicalDefinitions.MAX_DEFINITION_BYTES-12||!ProfileJson.read(json).accepted())throw new IllegalArgumentException("definition packet/aggregate complexity bound");
        try{var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeInt(MAGIC);out.writeInt(VERSION);out.writeInt(json.length);out.write(json);return bytes.toByteArray();}catch(IOException impossible){throw new AssertionError(impossible);}
    }
    public static PhysicalDefinitions decode(byte[] bytes){
        Objects.requireNonNull(bytes);if(bytes.length<12||bytes.length>PhysicalDefinitions.MAX_DEFINITION_BYTES)throw new IllegalArgumentException("definition packet bound");
        try{var in=new DataInputStream(new ByteArrayInputStream(bytes));if(in.readInt()!=MAGIC||in.readInt()!=VERSION)throw new IllegalArgumentException("definition version");int size=in.readInt();if(size<0||size!=in.available())throw new IllegalArgumentException("definition length/trailing bytes");
            var read=ProfileJson.read(in.readNBytes(size));if(!read.accepted())throw new IllegalArgumentException("definition schema");
            if(!Arrays.equals(bytes,encode(read.definitions())))throw new IllegalArgumentException("noncanonical definition packet");return read.definitions();
        }catch(IOException malformed){throw new IllegalArgumentException("definition framing",malformed);}
    }
    public static String hash(byte[] canonical){decode(canonical);try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    static String checkHash(String hash){if(hash==null||!hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("definition SHA256");return hash;}
}
