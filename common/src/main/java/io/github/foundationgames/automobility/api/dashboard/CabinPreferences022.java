package io.github.foundationgames.automobility.api.dashboard;

import java.io.*;
import java.util.*;

/** Per-car cosmetic preferences. Unknown or noncanonical raw is opaque, never quietly rewritten. */
public final class CabinPreferences022 {
    public static final int MAX_BYTES=256;
    private static final int MAGIC=0x43414231,VERSION=1;
    public enum Page{COMBINED,RADIO,POWER}
    public enum Lighting{AUTO,DAY,NIGHT}
    public enum Status{ABSENT,KNOWN,OPAQUE}
    public record Settings(long revision,boolean enabled,Page page,float dayBrightness,float nightBrightness,int accentRgb,Lighting lighting){
        public Settings(long revision,boolean enabled,Page page,float dayBrightness,float nightBrightness,int accentRgb){this(revision,enabled,page,dayBrightness,nightBrightness,accentRgb,Lighting.AUTO);}
        public Settings{Objects.requireNonNull(page);Objects.requireNonNull(lighting);if(revision<0||!unit(dayBrightness)||!unit(nightBrightness)||(accentRgb&0xff000000)!=0)throw new IllegalArgumentException("cabin cosmetic settings");}
        public static Settings defaults(){return new Settings(0,true,Page.COMBINED,.85f,.35f,0xffb449);}
        public Settings next(boolean enabled,Page page,float day,float night,int accent){if(revision==Long.MAX_VALUE)throw new IllegalStateException("cabin revision exhausted");return new Settings(revision+1,enabled,page,day,night,accent,lighting);}
    }
    public static final class Read {
        private final Status status;private final Settings settings;private final byte[]raw;
        private Read(Status status,Settings settings,byte[]raw){this.status=status;this.settings=settings;this.raw=raw.clone();}
        public Status status(){return status;}public Optional<Settings>settings(){return Optional.ofNullable(settings);}public byte[]raw(){return raw.clone();}
        public boolean writable(){return status!=Status.OPAQUE&&settings.revision()<Long.MAX_VALUE;}
    }
    public static Read read(byte[]raw){
        Objects.requireNonNull(raw);if(raw.length==0)return new Read(Status.ABSENT,Settings.defaults(),raw);
        if(raw.length>MAX_BYTES)return new Read(Status.OPAQUE,null,raw);
        try{var in=new DataInputStream(new ByteArrayInputStream(raw));if(in.readInt()!=MAGIC)return new Read(Status.OPAQUE,null,raw);int version=in.readUnsignedByte();if(version!=VERSION&&version!=2)return new Read(Status.OPAQUE,null,raw);
            long revision=in.readLong();int enabled=in.readUnsignedByte(),page=in.readUnsignedByte();if(enabled>1||page>=Page.values().length)return new Read(Status.OPAQUE,null,raw);
            float day=in.readFloat(),night=in.readFloat();int accent=in.readInt(),lighting=version==2?in.readUnsignedByte():0;if(lighting>=Lighting.values().length)return new Read(Status.OPAQUE,null,raw);
            var settings=new Settings(revision,enabled==1,Page.values()[page],day,night,accent,Lighting.values()[lighting]);
            return in.available()==0&&Arrays.equals(raw,encode(settings))?new Read(Status.KNOWN,settings,raw):new Read(Status.OPAQUE,null,raw);
        }catch(IOException|IllegalArgumentException malformed){return new Read(Status.OPAQUE,null,raw);}
    }
    public static byte[]encode(Settings settings){try{var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeInt(MAGIC);out.writeByte(settings.lighting()==Lighting.AUTO?VERSION:2);out.writeLong(settings.revision());out.writeBoolean(settings.enabled());out.writeByte(settings.page().ordinal());out.writeFloat(settings.dayBrightness());out.writeFloat(settings.nightBrightness());out.writeInt(settings.accentRgb());if(settings.lighting()!=Lighting.AUTO)out.writeByte(settings.lighting().ordinal());return bytes.toByteArray();}catch(IOException impossible){throw new AssertionError(impossible);}}
    /** Whole-car raw state is compared by the MC owner immediately before this proposal is installed. */
    public static boolean accepts(Read current,long expectedRevision,Settings proposal){return current.writable()&&current.settings().orElseThrow().revision()==expectedRevision&&expectedRevision<Long.MAX_VALUE&&proposal.revision()==expectedRevision+1;}
    private static boolean unit(float x){return Float.isFinite(x)&&x>=0&&x<=1;}
    private CabinPreferences022(){}
}
