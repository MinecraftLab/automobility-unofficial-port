package io.github.foundationgames.automobility.automobile.drivetrain.metadata;

import io.github.foundationgames.automobility.automobile.drivetrain.*;
import io.github.foundationgames.automobility.automobile.drivetrain.profile.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded S2C sidecar. This cannot author input, mechanics or owner storage; C2S intent and legacy payload stay unchanged. */
public record RuntimeMetadata018(ProfilePayloadApproval approval,long serverTick,Measured measured){
    public static final int MAX_BYTES=2048;
    private static final int MAGIC=0x414d4431,VERSION=1;
    /** Only PAID/UNMETERED authorize reporting powered work; the remaining states carry no measured power. */
    public enum Availability{PAID,UNMETERED,COAST,UNAVAILABLE,UNKNOWN_PROFILE,OWNER_UNAVAILABLE,DENIED}
    public enum Kind{COMBUSTION,ELECTRIC}
    /** Nm are measured interval means (EV drive command is constant except permitted cut); W are actual signed shaft-work/dt. */
    public record Measured(Availability availability,Kind kind,double combustionTorqueNm,double driveTorqueNm,double regenerationTorqueNm,
            double combustionPowerWatts,double electricShaftPowerWatts){
        public Measured{
            Objects.requireNonNull(availability);Objects.requireNonNull(kind);
            bound(combustionTorqueNm,0,20000);bound(driveTorqueNm,-20000,20000);bound(regenerationTorqueNm,-20000,20000);bound(combustionPowerWatts,-1e9,1e9);bound(electricShaftPowerWatts,-1e9,1e9);
            if(kind==Kind.COMBUSTION&&(driveTorqueNm!=0||regenerationTorqueNm!=0||electricShaftPowerWatts!=0)||kind==Kind.ELECTRIC&&(combustionTorqueNm!=0||combustionPowerWatts!=0))throw new IllegalArgumentException("telemetry shaft domains");
            if(availability!=Availability.PAID&&availability!=Availability.UNMETERED&&(combustionTorqueNm!=0||driveTorqueNm!=0||regenerationTorqueNm!=0||combustionPowerWatts!=0||electricShaftPowerWatts!=0))throw new IllegalArgumentException("unavailable measured telemetry");
        }
        public static Measured unavailable(Kind kind){return new Measured(Availability.UNAVAILABLE,kind,0,0,0,0,0);}
        public static Measured unavailable(Kind kind,Availability reason){if(reason==Availability.PAID||reason==Availability.UNMETERED)throw new IllegalArgumentException("powered availability needs actual measurements");return new Measured(reason,kind,0,0,0,0,0);}
        private static void bound(double value,double min,double max){if(!Double.isFinite(value)||value<min||value>max)throw new IllegalArgumentException("measured SI telemetry bound");}
        public boolean available(){return availability==Availability.PAID||availability==Availability.UNMETERED;}
    }
    public RuntimeMetadata018{Objects.requireNonNull(approval);Objects.requireNonNull(measured);if(serverTick<0)throw new IllegalArgumentException("metadata server tick");}
    /** Must be checked before server publication and before exposing a client pair. */
    public boolean pairs(DrivetrainProtocol.Snapshot snapshot){
        return snapshot!=null&&approval.identity().equals(snapshot.identity())&&approval.snapshotSequence()==snapshot.sequence()&&serverTick==snapshot.serverTick()
                &&approval.engineId().equals(snapshot.profileId())&&approval.definition().sha256().equals(snapshot.profileSha256());
    }
    public static byte[]encode(RuntimeMetadata018 value){
        try{var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeInt(MAGIC);out.writeInt(VERSION);var p=value.approval();var id=p.identity();uuid(out,id.vehicle());uuid(out,id.incarnation());uuid(out,id.worldInstance());out.writeLong(id.sessionNonce());out.writeLong(p.snapshotSequence());out.writeLong(value.serverTick());
            uuid(out,p.definition().worldSession());out.writeLong(p.definition().epoch());text(out,p.definition().sha256(),64);text(out,p.frameId(),128);text(out,p.wheelId(),128);text(out,p.engineId(),128);out.writeBoolean(p.physicalMotor().isPresent());if(p.physicalMotor().isPresent())uuid(out,p.physicalMotor().get());
            var mass=p.payload();out.writeDouble(mass.knownPassengerKg());out.writeDouble(mass.knownCargoKg());out.writeDouble(mass.knownAttachmentKg());out.writeInt(mass.unmodelledPassengerCount());out.writeInt(mass.unmodelledItemCount());out.writeInt(mass.opaqueContainerCount());out.writeBoolean(mass.unmodelledAttachment());out.writeBoolean(mass.incompleteObservation());out.writeBoolean(mass.poweredPredictionPermitted());
            var m=value.measured();out.writeByte(m.availability().ordinal());out.writeByte(m.kind().ordinal());out.writeDouble(m.combustionTorqueNm());out.writeDouble(m.driveTorqueNm());out.writeDouble(m.regenerationTorqueNm());out.writeDouble(m.combustionPowerWatts());out.writeDouble(m.electricShaftPowerWatts());byte[] result=bytes.toByteArray();if(result.length>MAX_BYTES)throw new IllegalArgumentException("metadata byte bound");return result;
        }catch(IOException impossible){throw new AssertionError(impossible);}
    }
    public static RuntimeMetadata018 decode(byte[]bytes){
        Objects.requireNonNull(bytes);if(bytes.length<8||bytes.length>MAX_BYTES)throw new IllegalArgumentException("metadata byte bound");
        try{var in=new DataInputStream(new ByteArrayInputStream(bytes));if(in.readInt()!=MAGIC||in.readInt()!=VERSION)throw new IllegalArgumentException("metadata version");var identity=new DrivetrainProtocol.Identity(uuid(in),uuid(in),uuid(in),in.readLong());long sequence=in.readLong(),tick=in.readLong();var definition=new ProfileViews.Reference(uuid(in),in.readLong(),text(in,64));String frame=text(in,128),wheel=text(in,128),engine=text(in,128);var motor=in.readBoolean()?Optional.of(uuid(in)):Optional.<UUID>empty();
            var mass=new PayloadMassModel.Result(in.readDouble(),in.readDouble(),in.readDouble(),in.readInt(),in.readInt(),in.readInt(),in.readBoolean(),in.readBoolean(),in.readBoolean());
            int availability=in.readUnsignedByte(),kind=in.readUnsignedByte();if(availability>=Availability.values().length||kind>=Kind.values().length)throw new IllegalArgumentException("metadata enumeration");
            var measured=new Measured(Availability.values()[availability],Kind.values()[kind],in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble());
            var result=new RuntimeMetadata018(new ProfilePayloadApproval(identity,sequence,definition,frame,wheel,engine,motor,mass),tick,measured);if(in.available()!=0||!Arrays.equals(bytes,encode(result)))throw new IllegalArgumentException("metadata trailing/noncanonical bytes");return result;
        }catch(IOException malformed){throw new IllegalArgumentException("metadata framing",malformed);}
    }
    private static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    private static UUID uuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    private static void text(DataOutputStream out,String value,int max)throws IOException{byte[] raw=value.getBytes(StandardCharsets.US_ASCII);if(raw.length>max||!new String(raw,StandardCharsets.US_ASCII).equals(value))throw new IllegalArgumentException("metadata ASCII string");out.writeShort(raw.length);out.write(raw);}
    private static String text(DataInputStream in,int max)throws IOException{int size=in.readUnsignedShort();if(size>max||size>in.available())throw new IllegalArgumentException("metadata string bound");byte[] raw=in.readNBytes(size);for(byte b:raw)if(b<0)throw new IllegalArgumentException("metadata ASCII");return new String(raw,StandardCharsets.US_ASCII);}
}
