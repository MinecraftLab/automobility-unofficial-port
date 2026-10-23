package io.github.foundationgames.automobility.automobile.drivetrain.profile;

import io.github.foundationgames.automobility.automobile.drivetrain.*;
import java.util.*;

/** Immutable SI game calibrations. These own no energy, identity, inventory or mutable mechanical state. */
public record PhysicalDefinitions(Map<String, Frame> frames, Map<String, String> frameBindings,
        Map<String, Wheel> wheels, Map<String, Engine> engines, Map<String, Double> frontMassKg,
        Map<String, Double> rearMassKg, LoadRules loadRules) {
    public static final int MAX_TABLE_ENTRIES=512, MAX_CURVE_POINTS=64, MAX_DEFINITION_BYTES=262144;
    public record Mount(double radiusScale,double massScale,boolean driven){
        public Mount{bounded(radiusScale,.1,4,"mount radius");bounded(massScale,.1,8,"mount mass");}
    }
    /** chassisMass excludes motor, wheels, attachments, people and cargo; maxPayload is advisory, never a mass clamp. */
    public record Frame(double chassisMassKg,double maxPayloadKg,double dragAreaSquareMetres,
            double rollingResistanceCoefficient,double serviceBrakeTorqueNm,double parkingHoldingTorqueNm,List<Mount> mounts){
        public Frame{
            bounded(chassisMassKg,10,20000,"chassis mass");bounded(maxPayloadKg,0,20000,"payload rating");
            bounded(dragAreaSquareMetres,0,10,"drag area");bounded(rollingResistanceCoefficient,0,.2,"rolling resistance");
            bounded(serviceBrakeTorqueNm,1,100000,"service brake");bounded(parkingHoldingTorqueNm,1,100000,"parking brake");
            mounts=List.copyOf(mounts);if(mounts.isEmpty()||mounts.size()>12)throw new IllegalArgumentException("wheel count");
            double driven=0;for(var m:mounts)if(m.driven()){
                if(driven!=0&&driven!=m.radiusScale())throw new IllegalArgumentException("single driven axle requires equal effective radii");driven=m.radiusScale();
            }if(driven==0)throw new IllegalArgumentException("driven axle required");
        }
        public double drivenRadiusScale(){return mounts.stream().filter(Mount::driven).findFirst().orElseThrow().radiusScale();}
    }
    /** Radius is explicit metre calibration, never the legacy wheel size/grip index. Inertia is for one base wheel. */
    public record Wheel(double radiusMetres,double massKg,double inertiaKgMetresSquared,double dryGripCoefficient,double wetGripMultiplier){
        public Wheel{bounded(radiusMetres,.03,2,"wheel radius");bounded(massKg,.1,250,"wheel mass");bounded(inertiaKgMetresSquared,.00001,200,"wheel inertia");bounded(dryGripCoefficient,.05,2,"grip");bounded(wetGripMultiplier,.05,1,"wet grip");}
    }
    /** The same immutable curve objects feed garage statistics and mechanics. Installed mass includes the EV battery assembly. */
    public record Engine(double installedMassKg,EngineProfile combustion,TransmissionProfile transmission,double clutchCapacityNm,
            EvMotorProfile electric,double fixedReductionRatio,double parkAxleHoldingTorqueNm,double coastCurveFraction){
        public Engine{
            bounded(installedMassKg,1,2000,"motor assembly mass");
            if((combustion==null)==(electric==null))throw new IllegalArgumentException("one engine kind required");
            if(combustion!=null){Objects.requireNonNull(transmission);bounded(clutchCapacityNm,1,10000,"clutch");if(fixedReductionRatio!=0||parkAxleHoldingTorqueNm!=0||coastCurveFraction!=0)throw new IllegalArgumentException("ICE electric fields");curve(combustion.torqueCurve());
                bounded(combustion.inertiaKgMetresSquared(),.0001,100,"crank inertia");bounded(combustion.idleRpm(),1,100000,"idle RPM");bounded(combustion.redlineRpm(),1,100000,"redline RPM");
                transmission.forwardRatios().forEach(r->bounded(r,.05,20,"forward ratio"));bounded(transmission.reverseRatio(),.05,20,"reverse ratio");bounded(transmission.finalDrive(),.1,20,"final drive");}
            else{if(transmission!=null||clutchCapacityNm!=0)throw new IllegalArgumentException("EV combustion fields");bounded(fixedReductionRatio,.1,100,"reduction");bounded(parkAxleHoldingTorqueNm,0,100000,"EV park");bounded(coastCurveFraction,0,1,"coast");curve(electric.motoringNewtonMetres());curve(electric.regenerationNewtonMetres());bounded(electric.rotorInertiaKgMetresSquared(),.0001,100,"rotor inertia");bounded(electric.maximumRpm(),1,100000,"motor RPM");}
        }
        public TorqueCurve.Statistics statistics(){return combustion!=null?combustion.statistics():electric.statistics();}
    }
    public record LoadRules(Map<String,Double> passengerKg,Map<String,Double> itemKg,boolean strictUnmodelled,
            double maximumKnownPayloadKg){
        public LoadRules{passengerKg=massMap(passengerKg,10000);itemKg=massMap(itemKg,1000);bounded(maximumKnownPayloadKg,1,100000,"payload arithmetic bound");}
    }
    public record Selection(String frameId,String wheelId,String engineId,Frame frame,Wheel wheel,Engine engine){
        public double emptyMassKg(){return frame.chassisMassKg()+engine.installedMassKg()+wheel.massKg()*frame.mounts().stream().mapToDouble(Mount::massScale).sum();}
        public double drivenRadiusMetres(){return wheel.radiusMetres()*frame.drivenRadiusScale();}
        /** Equivalent all-wheel KE at the driven-axis omega, assuming their calibrated no-slip radii. */
        public double equivalentWheelInertiaKgMetresSquared(){double s=frame.drivenRadiusScale();return wheel.inertiaKgMetresSquared()*s*s*frame.mounts().stream().mapToDouble(Mount::massScale).sum();}
        public DrivetrainParameters combustionParameters(double actualPayloadKg){
            bounded(actualPayloadKg,0,100000,"payload");if(engine.combustion()==null)throw new IllegalStateException("electric selection");
            return DrivetrainParameters.forEngine(engine.combustion(),equivalentWheelInertiaKgMetresSquared(),emptyMassKg()+actualPayloadKg,
                    drivenRadiusMetres(),engine.clutchCapacityNm(),engine.transmission());
        }
    }
    public PhysicalDefinitions{
        frames=table(frames);wheels=table(wheels);engines=table(engines);frameBindings=table(frameBindings);
        if(frames.isEmpty()||wheels.isEmpty()||engines.isEmpty()||frameBindings.isEmpty())throw new IllegalArgumentException("empty physical table");
        for(var binding:frameBindings.values()){id(binding);if(!frames.containsKey(binding))throw new IllegalArgumentException("unresolved frame binding");}
        frontMassKg=massMap(frontMassKg,5000);rearMassKg=massMap(rearMassKg,10000);Objects.requireNonNull(loadRules);
    }
    public Optional<Selection> resolve(String frameId,String wheelId,String engineId){
        id(frameId);id(wheelId);id(engineId);
        var binding=frameBindings.get(frameId);if(binding==null)return Optional.empty();
        var frame=frames.get(binding);var wheel=wheels.get(wheelId);var engine=engines.get(engineId);
        return frame==null||wheel==null||engine==null?Optional.empty():Optional.of(new Selection(frameId,wheelId,engineId,frame,wheel,engine));
    }
    private static <V>Map<String,V> table(Map<String,V> input){
        Objects.requireNonNull(input);if(input.size()>MAX_TABLE_ENTRIES)throw new IllegalArgumentException("table bound");
        var out=new TreeMap<String,V>();input.forEach((k,v)->{id(k);out.put(k,Objects.requireNonNull(v));});return Collections.unmodifiableMap(out);
    }
    private static Map<String,Double> massMap(Map<String,Double> input,double max){var out=table(input);out.values().forEach(v->bounded(v,0,max,"mass rule"));return out;}
    private static void curve(TorqueCurve curve){if(curve.points().size()>MAX_CURVE_POINTS)throw new IllegalArgumentException("curve point bound");for(var p:curve.points()){bounded(p.rpm(),0,100000,"RPM");bounded(p.torqueNewtonMetres(),0,10000,"torque");}}
    static String id(String id){if(id==null||id.length()>128||!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("registry/profile ID");return id;}
    static double bounded(double value,double min,double max,String name){if(!Double.isFinite(value)||value<min||value>max)throw new IllegalArgumentException(name);return value;}
}
