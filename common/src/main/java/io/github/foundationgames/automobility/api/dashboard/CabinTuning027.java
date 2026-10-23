package io.github.foundationgames.automobility.api.dashboard;
import java.io.*;
import java.util.*;

/** Cosmetics only. Its separate whole-car key does not migrate or overwrite display, media or energy state. */
public final class CabinTuning027 {
    private static final int MAGIC=0x43545531;
    public static final int MAX_BYTES=64;
    public enum Face{RETRO,CASSETTE,DIGITAL}
    public enum Speakers{COMPACT,TOURING,STUDIO}
    public enum Status{ABSENT,KNOWN,OPAQUE}
    public record Settings(long revision,Face face,Speakers speakers,int accentRgb){
        public Settings{Objects.requireNonNull(face);Objects.requireNonNull(speakers);if(revision<0||accentRgb != -1&&(accentRgb&0xff000000)!=0)throw new IllegalArgumentException("tuning revision/color");}
        public static Settings defaults(){return new Settings(0,Face.CASSETTE,Speakers.COMPACT,-1);}
        public Settings next(Face face,Speakers speakers){if(revision==Long.MAX_VALUE)throw new IllegalStateException("tuning revision exhausted");return new Settings(revision+1,face,speakers,accentRgb);}
        public Settings nextAccent(int rgb){if(revision==Long.MAX_VALUE)throw new IllegalStateException("tuning revision exhausted");return new Settings(revision+1,face,speakers,rgb);}
    }
    public static final class Read{
        private final Status status;private final Settings settings;private final byte[]raw;
        private Read(Status status,Settings settings,byte[]raw){this.status=status;this.settings=settings;this.raw=raw.clone();}
        public Status status(){return status;}public Optional<Settings>settings(){return Optional.ofNullable(settings);}
        public byte[]raw(){return raw.clone();}public boolean writable(){return settings!=null&&settings.revision()<Long.MAX_VALUE;}
    }
    public static Read read(byte[]raw){
        Objects.requireNonNull(raw);if(raw.length==0)return new Read(Status.ABSENT,Settings.defaults(),raw);
        if(raw.length>MAX_BYTES)return new Read(Status.OPAQUE,null,raw);
        try{var in=new DataInputStream(new ByteArrayInputStream(raw));
            if(in.readInt()!=MAGIC||in.readUnsignedByte()!=1)return new Read(Status.OPAQUE,null,raw);
            long revision=in.readLong();int face=in.readUnsignedByte(),speakers=in.readUnsignedByte();
            if(face>=Face.values().length||speakers>=Speakers.values().length)return new Read(Status.OPAQUE,null,raw);
            var s=new Settings(revision,Face.values()[face],Speakers.values()[speakers],in.readInt());
            if(in.available()!=0||!Arrays.equals(raw,encode(s)))return new Read(Status.OPAQUE,null,raw);
            return new Read(Status.KNOWN,s,raw);
        }catch(IOException|IllegalArgumentException invalid){return new Read(Status.OPAQUE,null,raw);}
    }
    public static byte[]encode(Settings s){try{var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeInt(MAGIC);out.writeByte(1);out.writeLong(s.revision());out.writeByte(s.face().ordinal());out.writeByte(s.speakers().ordinal());out.writeInt(s.accentRgb());return bytes.toByteArray();}catch(IOException impossible){throw new AssertionError(impossible);}}
    public static boolean accepts(Read current,long revision,Settings proposal){
        return current.writable()&&current.settings().orElseThrow().revision()==revision&&revision<Long.MAX_VALUE&&proposal.revision()==revision+1;
    }
    private CabinTuning027(){}
}
