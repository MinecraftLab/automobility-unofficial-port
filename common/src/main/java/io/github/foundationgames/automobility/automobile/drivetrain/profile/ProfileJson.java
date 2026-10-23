package io.github.foundationgames.automobility.automobile.drivetrain.profile;

import io.github.foundationgames.automobility.automobile.drivetrain.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Strict bounded configuration codec; rejected input is returned byte-exact and is never rewritten/migrated. */
public final class ProfileJson {
    public record Read(byte[] original,PhysicalDefinitions definitions,String rejection){
        public Read{original=original.clone();Objects.requireNonNull(rejection);}
        @Override public byte[] original(){return original.clone();}
        public boolean accepted(){return definitions!=null;}
    }
    private ProfileJson(){}
    public static Read read(byte[] original){
        Objects.requireNonNull(original);
        try{
            if(original.length>PhysicalDefinitions.MAX_DEFINITION_BYTES)throw new IllegalArgumentException("configuration byte bound");
            var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
            var parser=new Parser(decoder.decode(ByteBuffer.wrap(original)).toString());var root=object(parser.parse());
            keys(root,"schema","frames","frameBindings","wheels","engines","frontMassKg","rearMassKg","loadRules");
            if(number(root.get("schema"))!=1)throw new IllegalArgumentException("unsupported profile schema");
            var frames=new TreeMap<String,PhysicalDefinitions.Frame>();
            object(root.get("frames")).forEach((id,raw)->{
                var f=object(raw);keys(f,"chassisMassKg","maxPayloadKg","dragAreaSquareMetres","rollingResistanceCoefficient","serviceBrakeTorqueNm","parkingHoldingTorqueNm","mounts");
                var mounts=new ArrayList<PhysicalDefinitions.Mount>();for(var rawMount:array(f.get("mounts"))){var m=array(rawMount);if(m.size()!=3||!(m.get(2) instanceof Boolean driven))throw new IllegalArgumentException("mount triple");mounts.add(new PhysicalDefinitions.Mount(number(m.get(0)),number(m.get(1)),driven));}
                frames.put(id,new PhysicalDefinitions.Frame(n(f,"chassisMassKg"),n(f,"maxPayloadKg"),n(f,"dragAreaSquareMetres"),n(f,"rollingResistanceCoefficient"),n(f,"serviceBrakeTorqueNm"),n(f,"parkingHoldingTorqueNm"),mounts));
            });
            var bindings=new TreeMap<String,String>();object(root.get("frameBindings")).forEach((id,value)->bindings.put(id,string(value)));
            var wheels=new TreeMap<String,PhysicalDefinitions.Wheel>();object(root.get("wheels")).forEach((id,raw)->{
                var w=object(raw);keys(w,"radiusMetres","massKg","inertiaKgMetresSquared","dryGripCoefficient","wetGripMultiplier");
                wheels.put(id,new PhysicalDefinitions.Wheel(n(w,"radiusMetres"),n(w,"massKg"),n(w,"inertiaKgMetresSquared"),n(w,"dryGripCoefficient"),n(w,"wetGripMultiplier")));
            });
            var engines=new TreeMap<String,PhysicalDefinitions.Engine>();object(root.get("engines")).forEach((id,raw)->{
                var e=object(raw);String kind=string(e.get("kind"));
                if(kind.equals("combustion")){
                    keys(e,"kind","installedMassKg","torque","inertiaKgMetresSquared","idleRpm","redlineRpm","forwardRatios","reverseRatio","finalDrive","clutchCapacityNm");
                    var ratios=array(e.get("forwardRatios")).stream().map(ProfileJson::number).toList();
                    for(double ratio:ratios)PhysicalDefinitions.bounded(ratio,.05,20,"forward ratio");
                    PhysicalDefinitions.bounded(n(e,"reverseRatio"),.05,20,"reverse ratio");PhysicalDefinitions.bounded(n(e,"finalDrive"),.1,20,"final drive");
                    PhysicalDefinitions.bounded(n(e,"inertiaKgMetresSquared"),.0001,100,"crank inertia");
                    engines.put(id,new PhysicalDefinitions.Engine(n(e,"installedMassKg"),new EngineProfile(curve(e.get("torque")),n(e,"inertiaKgMetresSquared"),n(e,"idleRpm"),n(e,"redlineRpm")),new TransmissionProfile(ratios,n(e,"reverseRatio"),n(e,"finalDrive")),n(e,"clutchCapacityNm"),null,0,0,0));
                }else if(kind.equals("electric")){
                    keys(e,"kind","installedMassKg","motoring","regeneration","rotorInertiaKgMetresSquared","maximumRpm","fixedReductionRatio","parkAxleHoldingTorqueNm","coastCurveFraction");
                    PhysicalDefinitions.bounded(n(e,"rotorInertiaKgMetresSquared"),.0001,100,"rotor inertia");
                    engines.put(id,new PhysicalDefinitions.Engine(n(e,"installedMassKg"),null,null,0,new EvMotorProfile(curve(e.get("motoring")),curve(e.get("regeneration")),n(e,"rotorInertiaKgMetresSquared"),n(e,"maximumRpm")),n(e,"fixedReductionRatio"),n(e,"parkAxleHoldingTorqueNm"),n(e,"coastCurveFraction")));
                }else throw new IllegalArgumentException("unknown engine kind");
            });
            var load=object(root.get("loadRules"));keys(load,"passengerKg","itemKg","strictUnmodelled","maximumKnownPayloadKg");
            if(!(load.get("strictUnmodelled") instanceof Boolean strict))throw new IllegalArgumentException("strict policy boolean");
            var definitions=new PhysicalDefinitions(frames,bindings,wheels,engines,masses(root.get("frontMassKg")),masses(root.get("rearMassKg")),new PhysicalDefinitions.LoadRules(masses(load.get("passengerKg")),masses(load.get("itemKg")),strict,n(load,"maximumKnownPayloadKg")));
            return new Read(original,definitions,"");
        }catch(CharacterCodingException|IllegalArgumentException|NullPointerException failure){return new Read(original,null,"invalid_or_unsupported_profile_definition");}
    }
    public static byte[] write(PhysicalDefinitions definitions){
        var root=map();root.put("schema",1);var frames=map();definitions.frames().forEach((id,f)->{
            var value=map();value.put("chassisMassKg",f.chassisMassKg());value.put("maxPayloadKg",f.maxPayloadKg());value.put("dragAreaSquareMetres",f.dragAreaSquareMetres());value.put("rollingResistanceCoefficient",f.rollingResistanceCoefficient());value.put("serviceBrakeTorqueNm",f.serviceBrakeTorqueNm());value.put("parkingHoldingTorqueNm",f.parkingHoldingTorqueNm());
            value.put("mounts",f.mounts().stream().map(m->List.of(m.radiusScale(),m.massScale(),m.driven())).toList());frames.put(id,value);
        });root.put("frames",frames);root.put("frameBindings",definitions.frameBindings());
        var wheels=map();definitions.wheels().forEach((id,w)->{var value=map();value.put("radiusMetres",w.radiusMetres());value.put("massKg",w.massKg());value.put("inertiaKgMetresSquared",w.inertiaKgMetresSquared());value.put("dryGripCoefficient",w.dryGripCoefficient());value.put("wetGripMultiplier",w.wetGripMultiplier());wheels.put(id,value);});root.put("wheels",wheels);
        var engines=map();definitions.engines().forEach((id,e)->{var value=map();value.put("installedMassKg",e.installedMassKg());
            if(e.combustion()!=null){var c=e.combustion();value.put("kind","combustion");value.put("torque",points(c.torqueCurve()));value.put("inertiaKgMetresSquared",c.inertiaKgMetresSquared());value.put("idleRpm",c.idleRpm());value.put("redlineRpm",c.redlineRpm());value.put("forwardRatios",e.transmission().forwardRatios());value.put("reverseRatio",e.transmission().reverseRatio());value.put("finalDrive",e.transmission().finalDrive());value.put("clutchCapacityNm",e.clutchCapacityNm());}
            else{var ev=e.electric();value.put("kind","electric");value.put("motoring",points(ev.motoringNewtonMetres()));value.put("regeneration",points(ev.regenerationNewtonMetres()));value.put("rotorInertiaKgMetresSquared",ev.rotorInertiaKgMetresSquared());value.put("maximumRpm",ev.maximumRpm());value.put("fixedReductionRatio",e.fixedReductionRatio());value.put("parkAxleHoldingTorqueNm",e.parkAxleHoldingTorqueNm());value.put("coastCurveFraction",e.coastCurveFraction());}engines.put(id,value);
        });root.put("engines",engines);root.put("frontMassKg",definitions.frontMassKg());root.put("rearMassKg",definitions.rearMassKg());var load=map();load.put("passengerKg",definitions.loadRules().passengerKg());load.put("itemKg",definitions.loadRules().itemKg());load.put("strictUnmodelled",definitions.loadRules().strictUnmodelled());load.put("maximumKnownPayloadKg",definitions.loadRules().maximumKnownPayloadKg());root.put("loadRules",load);
        var text=new StringBuilder();emit(text,root);byte[] bytes=text.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>PhysicalDefinitions.MAX_DEFINITION_BYTES)throw new IllegalArgumentException("encoded profile byte bound");return bytes;
    }
    private static Map<String,Object>map(){return new TreeMap<>();}
    private static List<List<Double>>points(TorqueCurve curve){return curve.points().stream().map(p->List.of(p.rpm(),p.torqueNewtonMetres())).toList();}
    private static TorqueCurve curve(Object raw){var points=new ArrayList<TorqueCurve.Point>();var list=array(raw);if(list.size()>PhysicalDefinitions.MAX_CURVE_POINTS)throw new IllegalArgumentException("curve bound");for(var pair:list){var p=array(pair);if(p.size()!=2)throw new IllegalArgumentException("torque pair");points.add(new TorqueCurve.Point(number(p.get(0)),number(p.get(1))));}return new TorqueCurve(points);}
    private static Map<String,Double>masses(Object raw){var out=new TreeMap<String,Double>();object(raw).forEach((id,kg)->out.put(id,number(kg)));return out;}
    @SuppressWarnings("unchecked")private static Map<String,Object>object(Object raw){if(!(raw instanceof Map<?,?>))throw new IllegalArgumentException("object required");return(Map<String,Object>)raw;}
    @SuppressWarnings("unchecked")private static List<Object>array(Object raw){if(!(raw instanceof List<?>))throw new IllegalArgumentException("array required");return(List<Object>)raw;}
    private static String string(Object raw){if(!(raw instanceof String value))throw new IllegalArgumentException("string required");return value;}
    private static double number(Object raw){if(!(raw instanceof Number value)||!Double.isFinite(value.doubleValue()))throw new IllegalArgumentException("finite number required");return value.doubleValue();}
    private static double n(Map<String,Object>raw,String key){return number(raw.get(key));}
    private static void keys(Map<String,Object>raw,String...keys){if(!raw.keySet().equals(Set.of(keys)))throw new IllegalArgumentException("unknown/missing fields");}
    private static void emit(StringBuilder out,Object value){
        if(value instanceof Map<?,?> m){out.append('{');boolean comma=false;for(var entry:new TreeMap<>(m).entrySet()){if(comma)out.append(',');comma=true;quote(out,(String)entry.getKey());out.append(':');emit(out,entry.getValue());}out.append('}');}
        else if(value instanceof List<?> l){out.append('[');for(int i=0;i<l.size();i++){if(i>0)out.append(',');emit(out,l.get(i));}out.append(']');}
        else if(value instanceof String s)quote(out,s);else if(value instanceof Boolean||value instanceof Number)out.append(value);else throw new IllegalArgumentException("JSON value");
    }
    private static void quote(StringBuilder out,String s){out.append('"');for(char c:s.toCharArray()){if(c=='"'||c=='\\')out.append('\\').append(c);else if(c<32)out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else out.append(c);}out.append('"');}
    private static final class Parser{
        final String text;int at,nodes;Parser(String text){this.text=text;}
        Object parse(){Object value=value(0);space();if(at!=text.length())throw bad();return value;}
        Object value(int depth){space();if(depth>32||++nodes>30000||at>=text.length())throw bad();char c=text.charAt(at);
            if(c=='{'){at++;var map=new TreeMap<String,Object>();space();if(take('}'))return map;do{space();String key=quoted();space();need(':');Object v=value(depth+1);if(map.putIfAbsent(key,v)!=null||map.size()>PhysicalDefinitions.MAX_TABLE_ENTRIES)throw bad();space();}while(take(','));need('}');return map;}
            if(c=='['){at++;var list=new ArrayList<>();space();if(take(']'))return list;do{list.add(value(depth+1));if(list.size()>1024)throw bad();space();}while(take(','));need(']');return list;}
            if(c=='"')return quoted();if(text.startsWith("true",at)){at+=4;return true;}if(text.startsWith("false",at)){at+=5;return false;}
            int start=at;if(take('-')&&at>=text.length())throw bad();if(take('0')){if(at<text.length()&&Character.isDigit(text.charAt(at)))throw bad();}else{if(at>=text.length()||text.charAt(at)<'1'||text.charAt(at)>'9')throw bad();while(at<text.length()&&text.charAt(at)>='0'&&text.charAt(at)<='9')at++;}
            if(take('.'))digits();if(take('e')||take('E')){if(!take('+'))take('-');digits();}String token=text.substring(start,at);if(token.length()>64)throw bad();double number=Double.parseDouble(token);if(!Double.isFinite(number))throw bad();return number;
        }
        String quoted(){need('"');var s=new StringBuilder();while(at<text.length()){char c=text.charAt(at++);if(c=='"'){if(s.length()>256)throw bad();return s.toString();}if(c<32)throw bad();if(c=='\\'){if(at>=text.length())throw bad();char e=text.charAt(at++);switch(e){case '"','\\','/'->s.append(e);case 'b'->s.append('\b');case 'f'->s.append('\f');case 'n'->s.append('\n');case 'r'->s.append('\r');case 't'->s.append('\t');case 'u'->{if(at+4>text.length())throw bad();s.append((char)Integer.parseInt(text.substring(at,at+4),16));at+=4;}default->throw bad();}}else s.append(c);if(s.length()>256)throw bad();}throw bad();}
        void digits(){int start=at;while(at<text.length()&&text.charAt(at)>='0'&&text.charAt(at)<='9')at++;if(start==at)throw bad();}
        void space(){while(at<text.length()&&" \t\r\n".indexOf(text.charAt(at))>=0)at++;}
        boolean take(char c){if(at<text.length()&&text.charAt(at)==c){at++;return true;}return false;}
        void need(char c){if(!take(c))throw bad();}IllegalArgumentException bad(){return new IllegalArgumentException("strict bounded JSON");}
    }
}
