package dev.offchunk.verification.reverse162;

import io.github.foundationgames.automobility.automobile.drivetrain.*;
import java.util.List;

/** Only the new traction governor and its physical closed-loop consequences; no161b unrelated suite replay. */
public final class ReverseGovernor162Test {
    private static int checks;
    private static final double DT=.05;
    private static AutomaticPedalControls.Commands decode(double physical,double accelerator,int gear,double speed,double wheel) {
        return AutomaticPedalControls.resolve(true,false,true,physical,accelerator,false,gear,speed,wheel,PowertrainSimulation.RangeRequest.KEEP,false);
    }
    public static void main(String[] args) {
        boolean remaining=args.length==1&&args[0].equals("--remaining");
        // Prefix cases were already observed on exact production162 and are retained, not replayed by the repair run.
        if(!remaining){envelope();for(double grade:new double[]{0,-.04})combustion(grade);}
        downhillOverspeed();
        electric();
        System.out.println("PASS_REVERSE162B checks="+checks+" boundary=CORRECTED_OVERSPEED_AND_PREVIOUSLY_UNREACHED_EV_ONLY remaining="+remaining);
    }
    private static void envelope() {
        double last=2;
        for(double speed:new double[]{.1,1,2,3,3.5,4,4.5,4.9,5,8,15}) {
            var p=decode(1,0,-1,-speed,-speed/.381);
            check(p.throttle()<=last&&p.throttle()>=0,"reverse throttle envelope must be monotonic and bounded");last=p.throttle();
            check(p.reversePropulsion()&&p.serviceBrake()==0&&p.footBrake()==0,"governor may only remove propulsion, never apply a brake");
            if(speed>=5)check(p.throttle()==0,"no propulsion above18km/h");
            if(speed<=3)check(p.throttle()==1,"preserve full initial reverse launch");
        }
        var braking=decode(0,1,-1,-8,-8/.381);
        check(braking.serviceBrake()==1&&braking.throttle()==0,"W must still brake downhill/overspeed reverse");
        var analog=decode(.35,0,-1,-1,-1/.381);
        check(analog.throttle()==.35,"analog reverse launch keeps its range");
        var manual=AutomaticPedalControls.resolve(true,false,false,0,1,false,-1,-8,-8/.381,PowertrainSimulation.RangeRequest.KEEP,false);
        check(manual.throttle()==1&&manual.serviceBrake()==0,"manual throttle remains under the driver's control");
    }
    private static PowertrainSimulation.Setup setup(PhysicalProfileCatalog.EngineDefinition motor,PhysicalProfileCatalog.ChassisDefinition body) {
        return new PowertrainSimulation.Setup(motor.engine(),PhysicalProfileCatalog.mechanics(motor,body,.381,75),
                EngineController.Config.defaults().withStallBehavior(false,true),new ClutchController.Config(.8,0),
                AutomaticShiftController.Config.defaults(),new ManualShiftController.Config(.05,.04,.20,.05,.35,.75,.98,true,true,1).withDownshiftProtection(true),
                ThrottleController.Config.defaults(),new LaunchController.Config(500,2,.425,true,.4,.2),.005,body.parkingHoldingTorqueNewtonMetres()).withManualClutchAssistance(.5);
    }
    private static void combustion(double grade) {
        var motor=PhysicalProfileCatalog.engine("automobility:diamond").orElseThrow();var body=PhysicalProfileCatalog.chassis("automobility:vw_t3").orElseThrow();var setup=setup(motor,body);
        var state=new PowertrainSimulation.State(new DrivetrainState(Units.rpmToRadiansPerSecond(motor.engine().idleRpm()),0,0,0),EngineController.State.running(),
                new ClutchController.State(1),new ThrottleController.State(0),LaunchController.State.stopped(),AutomaticShiftController.State.drive(0,AutomaticShiftController.Mode.BALANCED),
                ManualShiftController.State.drive(0),PowertrainSimulation.ControlMode.AUTOMATIC,false,false,0);
        double peak=0,minTail=Double.POSITIVE_INFINITY,maxTail=0,normal=setup.mechanics().vehicleMassKg()*9.81*Math.sqrt(1-grade*grade);int cut=0;
        for(int tick=0;tick<400;tick++) {
            var p=decode(1,0,state.mechanics().gear(),state.mechanics().speedMetresPerSecond(),state.mechanics().wheelRadiansPerSecond());
            if(state.mechanics().speedMetresPerSecond()<=-5){check(p.throttle()==0,"downhill reverse must cut propulsion above the ceiling");cut++;}
            var input=new PowertrainSimulation.DriverInput(PowertrainSimulation.ControlMode.AUTOMATIC,AutomaticShiftController.Mode.BALANCED,
                    new ThrottleController.Input(p.throttle()!=0&&p.throttle()!=1,p.throttle()),ClutchController.Input.digital(false),ManualShiftController.Request.NONE,
                    p.range(),1,p.serviceBrake(),true,false,true,true,true);
            var env=new PowertrainSimulation.Environment(true,grade,normal,.9,body.rollingResistanceCoefficient()*normal,body.airResistanceForceNewtons(state.mechanics().speedMetresPerSecond(),1.225),body.serviceBrakeTorqueNewtonMetres());
            var result=PowertrainSimulation.advance(setup,state,input,env,DT);state=result.state();double speed=-state.mechanics().speedMetresPerSecond();peak=Math.max(peak,speed);
            check(result.energy().serviceBrakeJoules()==0,"soft reverse governor cannot hide a speed brake");
            if(tick>=200){minTail=Math.min(minTail,speed);maxTail=Math.max(maxTail,speed);}
        }
        check(state.mechanics().gear()==-1&&peak>2.5,"reverse launch remains responsive through the real ratio");
        if(grade<=0){check(peak<=5.15,"powered flat/uphill reverse remains near18km/h");check(maxTail-minTail<.7,"governor must settle instead of repeatedly pulsing torque");}
        System.out.printf(java.util.Locale.ROOT,"REVERSE162 combustion grade=%.2f peakKmh=%.3f finalKmh=%.3f tailRangeKmh=%.3f overspeedCuts=%d%n",grade,peak*3.6,-state.mechanics().speedMetresPerSecond()*3.6,(maxTail-minTail)*3.6,cut);
    }
    private static void downhillOverspeed() {
        var motor=PhysicalProfileCatalog.engine("automobility:diamond").orElseThrow();
        var body=PhysicalProfileCatalog.chassis("automobility:vw_t3").orElseThrow();var setup=setup(motor,body);
        double initialSpeed=-8,wheel=initialSpeed/.381,crank=wheel*motor.transmission().totalRatio(-1),grade=.04;
        var state=new PowertrainSimulation.State(new DrivetrainState(crank,wheel,initialSpeed,-1),EngineController.State.running(),
                new ClutchController.State(1),new ThrottleController.State(0),new LaunchController.State(1,0),
                AutomaticShiftController.State.drive(-1,AutomaticShiftController.Mode.BALANCED),ManualShiftController.State.drive(-1),
                PowertrainSimulation.ControlMode.AUTOMATIC,false,false,0);
        double normal=setup.mechanics().vehicleMassKg()*9.81*Math.sqrt(1-grade*grade),first=0;int cuts=0;
        for(int tick=0;tick<400;tick++) {
            var pedals=decode(1,0,-1,state.mechanics().speedMetresPerSecond(),state.mechanics().wheelRadiansPerSecond());
            if(state.mechanics().speedMetresPerSecond()<=-5){check(pedals.throttle()==0,"pre-existing overspeed must cut reverse traction");cuts++;}
            var input=new PowertrainSimulation.DriverInput(PowertrainSimulation.ControlMode.AUTOMATIC,AutomaticShiftController.Mode.BALANCED,
                    new ThrottleController.Input(pedals.throttle()!=0&&pedals.throttle()!=1,pedals.throttle()),ClutchController.Input.digital(false),ManualShiftController.Request.NONE,
                    pedals.range(),1,0,true,false,true,true,true);
            var env=new PowertrainSimulation.Environment(true,grade,normal,.9,body.rollingResistanceCoefficient()*normal,
                    body.airResistanceForceNewtons(state.mechanics().speedMetresPerSecond(),1.225),body.serviceBrakeTorqueNewtonMetres());
            var result=PowertrainSimulation.advance(setup,state,input,env,DT);state=result.state();
            check(result.energy().serviceBrakeJoules()==0,"overspeed governor may not substitute a service brake");
            if(tick==0){first=state.mechanics().speedMetresPerSecond();check(first < -5.1,"a governor may not snap existing momentum to its propulsion ceiling");}
        }
        check(cuts>0,"coherent initial overspeed must actually exercise traction cut");
        System.out.printf(java.util.Locale.ROOT,"REVERSE162B downhill seededKmh=28.800 firstStepKmh=%.3f finalKmh=%.3f overspeedCuts=%d%n",-first*3.6,-state.mechanics().speedMetresPerSecond()*3.6,cuts);
    }
    private static void electric() {
        var motor=new EvMotorProfile(new TorqueCurve(List.of(new TorqueCurve.Point(0,220),new TorqueCurve.Point(4000,220),new TorqueCurve.Point(8000,110),new TorqueCurve.Point(12000,70),new TorqueCurve.Point(14000,0))),
                new TorqueCurve(List.of(new TorqueCurve.Point(0,110),new TorqueCurve.Point(5000,110),new TorqueCurve.Point(10000,55),new TorqueCurve.Point(14000,35))),.08,14000);
        var p=new EvDrivetrainStep.Parameters(motor,9,3.2,1885,.381,3200,new EvDrivetrainStep.RangePolicy(.15,.75,.2,false,false));
        var state=new EvDrivetrainStep.State(0,0,EvDrivetrainStep.Range.N);double normal=p.vehicleMassKg()*9.81,peak=0,minTail=Double.POSITIVE_INFINITY,maxTail=0;
        for(int tick=0;tick<400;tick++) {
            var pedals=decode(1,0,state.range()==EvDrivetrainStep.Range.R?-1:0,state.speedMetresPerSecond(),state.wheelRadiansPerSecond());
            var request=pedals.range()==PowertrainSimulation.RangeRequest.REVERSE?EvDrivetrainStep.RangeRequest.R:EvDrivetrainStep.RangeRequest.KEEP;
            var control=new EvDrivetrainStep.Control(request,pedals.throttle(),0,220,110,0,1,true,true,true,false,false);
            var env=new EvDrivetrainStep.Environment(true,0,normal,.9,.018*normal,-.5*1.225*1.15*state.speedMetresPerSecond()*Math.abs(state.speedMetresPerSecond()),0);
            var result=EvDrivetrainStep.advance(p,state,new EvDrivetrainStep.Input(control,env),DT);state=result.state();double speed=-state.speedMetresPerSecond();peak=Math.max(peak,speed);
            check(result.recoveredMechanicalJoules()==0&&result.serviceAndParkingBrakeEnergyJoules()==0,"EV traction taper cannot manufacture braking or regeneration");
            if(tick>=200){minTail=Math.min(minTail,speed);maxTail=Math.max(maxTail,speed);}
        }
        check(state.range()==EvDrivetrainStep.Range.R&&peak>2.5&&peak<=5.15,"EV reverse must remain responsive and governed through fixed reduction");
        check(maxTail-minTail<.7,"EV governor must settle smoothly");
        System.out.printf(java.util.Locale.ROOT,"REVERSE162 electric peakKmh=%.3f finalKmh=%.3f tailRangeKmh=%.3f%n",peak*3.6,-state.speedMetresPerSecond()*3.6,(maxTail-minTail)*3.6);
    }
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
}
