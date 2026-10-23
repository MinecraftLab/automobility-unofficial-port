package io.github.foundationgames.automobility.automobile.drivetrain.profile;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Strict data loading only. Never creates, rewrites or normalizes the user's configuration. */
public final class ProfileCatalog015{
    public static final String BUNDLED_RESOURCE="data/automobility/drivetrain/physical-profiles-v1.json";
    public record Configuration(Path path,Optional<ProfileJson.Read>read,String diagnostic){
        public Configuration{Objects.requireNonNull(path);Objects.requireNonNull(read);Objects.requireNonNull(diagnostic);}
        public boolean accepted(){return read.isPresent()&&read.get().accepted();}
    }
    private ProfileCatalog015(){}
    public static PhysicalDefinitions bundled(ClassLoader loader)throws IOException{
        try(var input=Objects.requireNonNull(loader).getResourceAsStream(BUNDLED_RESOURCE)){
            if(input==null)throw new FileNotFoundException(BUNDLED_RESOURCE);byte[] bytes=input.readNBytes(PhysicalDefinitions.MAX_DEFINITION_BYTES+1);
            var read=ProfileJson.read(bytes);if(!read.accepted())throw new IOException("invalid bundled physical profile definition");ProfileDefinitionCodec.encode(read.definitions());return read.definitions();
        }
    }
    /** Oversize/read-error diagnostics retain the original file on disk; no truncated image is offered for migration. */
    public static Configuration configuration(Path exactFile){
        Objects.requireNonNull(exactFile);
        try(var input=Files.newInputStream(exactFile)){
            byte[] bytes=input.readNBytes(PhysicalDefinitions.MAX_DEFINITION_BYTES+1);
            if(bytes.length>PhysicalDefinitions.MAX_DEFINITION_BYTES)return new Configuration(exactFile,Optional.empty(),"profile_config_oversized_preserved_on_disk");
            var read=ProfileJson.read(bytes);return new Configuration(exactFile,Optional.of(read),read.accepted()?"":"profile_config_opaque_preserved");
        }catch(NoSuchFileException absent){return new Configuration(exactFile,Optional.empty(),"profile_config_absent");}
        catch(IOException denied){return new Configuration(exactFile,Optional.empty(),"profile_config_unavailable_preserved_on_disk");}
    }
}
