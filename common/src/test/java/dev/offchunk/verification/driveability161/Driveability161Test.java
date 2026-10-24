package dev.offchunk.verification.driveability161;

import io.github.foundationgames.automobility.automobile.drivetrain.*;
import java.util.List;

/** Production pedal decoder through the actual solver; new reverse/hill-hold/audio cases only. */
public final class Driveability161Test {
    private static final double DT=.05;
    private static int checks;
    public static void main(String[] args) {
        pedalTransitions();
        for(String body:List.of("vw_t3","defender_90","mx5_na"))
            for(double grade:new double[]{0,-.04,.04}) combustionReverse(body,grade);
        electricReverse();
        soundFamilies();
        twilightContrast();
        System.out.println("PASS_DRIVEABILITY161 checks="+checks+" boundary=PURE_NOT_NATIVE_OR_HEARING");
    }
    private static AutomaticPedalControls.Commands pedals(boolean automatic,double physical,double accelerator,
            boolean latch,int gear,double speed,double wheel,PowertrainSimulation.RangeRequest request) {
        return AutomaticPedalControls.resolve(true,false,automatic,physical,accelerator,latch,gear,speed,wheel,request,false);
    }
    private static void pedalTransitions() {
        var keep=PowertrainSimulation.RangeRequest.KEEP;
        var reverse=pedals(true,1,0,false,-1,-1,-3,keep);
        // The old post-decoding W guard sees the newly-created reverse throttle and clamps it at ~0.54 km/h.
        boolean oldGuard=reverse.throttle()>0 && -1<0 && (-1<-.15 || -3<-.75);
        check(oldGuard,"reported reverse counterexample must exercise the previous wrong guard");
        check(reverse.reversePropulsion()&&reverse.throttle()==1&&reverse.serviceBrake()==0,"S must continue reverse propulsion beyond the old clamp");
        check(reverse.range()==keep,"steady reverse must not restart a range shift each tick");
        var w=pedals(true,0,1,false,-1,-3,-8,keep);
        check(w.throttle()==0&&w.serviceBrake()==1&&w.footBrake()==0&&w.range()==keep,"W must stop reverse travel without physical foot-brake/regen intent");
        var s=pedals(true,1,0,false,3,8,20,keep);
        check(!s.reversePropulsion()&&s.throttle()==0&&s.footBrake()==1&&s.range()==keep,"S must brake forward travel before reverse");
        var latch=pedals(true,1,0,true,1,0,0,keep);
        check(!latch.reversePropulsion()&&latch.footBrake()==1,"stop latch must require pedal release");
        var released=pedals(true,0,0,false,1,0,0,keep);
        check(!released.reversePropulsion()&&released.throttle()==0&&released.serviceBrake()==0,"released pedals coast");
        var select=pedals(true,1,0,false,1,0,0,PowertrainSimulation.RangeRequest.PARK);
        check(!select.reversePropulsion()&&select.footBrake()==1&&select.range()==PowertrainSimulation.RangeRequest.PARK,"explicit selector keeps real brake intent");
        var manual=pedals(false,1,0,false,-1,-3,-8,keep);
        check(!manual.reversePropulsion()&&manual.footBrake()==1&&manual.throttle()==0,"manual keeps conventional independent brake/throttle");
        var analog=pedals(true,.35,0,false,-1,-1,-3,keep);
        check(analog.throttle()==.35&&analog.footBrake()==0,"analog reverse demand keeps its amount");
        var blocked=AutomaticPedalControls.resolve(true,true,true,1,0,false,-1,-1,-3,keep,false);
        check(!blocked.reversePropulsion()&&blocked.throttle()==0&&blocked.serviceBrake()==1,"blocked power keeps safety braking");
    }
    private static PowertrainSimulation.Setup setup(PhysicalProfileCatalog.EngineDefinition motor,PhysicalProfileCatalog.ChassisDefinition body,double radius) {
        return new PowertrainSimulation.Setup(motor.engine(),PhysicalProfileCatalog.mechanics(motor,body,radius,75),
                EngineController.Config.defaults().withStallBehavior(false,true),new ClutchController.Config(.8,0),
                AutomaticShiftController.Config.defaults(),new ManualShiftController.Config(.05,.04,.20,.05,.35,.75,.98,true,true,1).withDownshiftProtection(true),
                ThrottleController.Config.defaults(),new LaunchController.Config(500,2,.425,true,.4,.2),.005,body.parkingHoldingTorqueNewtonMetres()).withManualClutchAssistance(.5);
    }
    private static PowertrainSimulation.State initial(EngineProfile engine) {
        return new PowertrainSimulation.State(new DrivetrainState(Units.rpmToRadiansPerSecond(engine.idleRpm()),0,0,0),
                EngineController.State.running(),new ClutchController.State(1),new ThrottleController.State(0),LaunchController.State.stopped(),
                AutomaticShiftController.State.drive(0,AutomaticShiftController.Mode.BALANCED),ManualShiftController.State.drive(0),
                PowertrainSimulation.ControlMode.AUTOMATIC,false,false,0);
    }
    private static PowertrainSimulation.DriverInput input(AutomaticPedalControls.Commands pedals,double physical) {
        return new PowertrainSimulation.DriverInput(PowertrainSimulation.ControlMode.AUTOMATIC,AutomaticShiftController.Mode.BALANCED,
                new ThrottleController.Input(false,pedals.throttle()),ClutchController.Input.digital(false),ManualShiftController.Request.NONE,
                pedals.range(),physical,pedals.serviceBrake(),true,false,true,true,true);
    }
    private static void combustionReverse(String bodyName,double grade) {
        var motor=PhysicalProfileCatalog.engine("automobility:iron").orElseThrow();
        var body=PhysicalProfileCatalog.chassis("automobility:"+bodyName).orElseThrow();
        double radius=bodyName.equals("vw_t3")?.381:bodyName.equals("defender_90")?.4:.28125;
        var setup=setup(motor,body,radius);var state=initial(motor.engine());
        double normal=setup.mechanics().vehicleMassKg()*9.81*Math.sqrt(1-grade*grade),peak=0,maxHold=0;
        for(int tick=0;tick<160;tick++) {
            var p=pedals(true,1,0,false,state.mechanics().gear(),state.mechanics().speedMetresPerSecond(),state.mechanics().wheelRadiansPerSecond(),PowertrainSimulation.RangeRequest.KEEP);
            var env=new PowertrainSimulation.Environment(true,grade,normal,.9,body.rollingResistanceCoefficient()*normal,
                    body.airResistanceForceNewtons(state.mechanics().speedMetresPerSecond(),1.225),body.serviceBrakeTorqueNewtonMetres());
            var result=PowertrainSimulation.advance(setup,state,input(p,1),env,DT);state=result.state();
            peak=Math.max(peak,-state.mechanics().speedMetresPerSecond());maxHold=Math.max(maxHold,state.launch().hillHoldRemainingSeconds());
            check(Double.isFinite(state.mechanics().speedMetresPerSecond()),"finite reverse speed");
        }
        check(state.mechanics().gear()==-1,"actual R ratio must engage");
        check(peak>2,"reverse cannot remain clamped near walking pace: "+bodyName+" "+grade+" speed="+peak);
        check(maxHold==0,"reverse propulsion on S must not continually arm hill hold");
        System.out.printf(java.util.Locale.ROOT,"REVERSE161 body=%s grade=%.2f peakKmh=%.3f gear=%d hillHold=%.2f%n",bodyName,grade,peak*3.6,state.mechanics().gear(),maxHold);
        double start=state.mechanics().speedMetresPerSecond();
        for(int tick=0;tick<80;tick++) {
            var p=pedals(true,0,1,false,state.mechanics().gear(),state.mechanics().speedMetresPerSecond(),state.mechanics().wheelRadiansPerSecond(),PowertrainSimulation.RangeRequest.KEEP);
            var env=new PowertrainSimulation.Environment(true,grade,normal,.9,body.rollingResistanceCoefficient()*normal,
                    body.airResistanceForceNewtons(state.mechanics().speedMetresPerSecond(),1.225),body.serviceBrakeTorqueNewtonMetres());
            state=PowertrainSimulation.advance(setup,state,input(p,0),env,DT).state();
        }
        check(state.mechanics().speedMetresPerSecond()>start+1,"W must brake reverse and permit a guarded forward launch");
        // A real applied foot brake still arms assistance, independently of the physical S selector metadata.
        var stopped=initial(motor.engine());
        var brake=new AutomaticPedalControls.Commands(false,0,1,1,PowertrainSimulation.RangeRequest.KEEP);
        var env=new PowertrainSimulation.Environment(true,grade,normal,.9,body.rollingResistanceCoefficient()*normal,0,body.serviceBrakeTorqueNewtonMetres());
        var held=PowertrainSimulation.advance(setup,stopped,input(brake,1),env,DT).state();
        check(held.launch().hillHoldRemainingSeconds()>0,"real foot brake must retain hill hold");
    }
    private static void electricReverse() {
        // Exact existing Powerworks profile points are supplied by the source freezer, not an invented test motor.
        var motor=new EvMotorProfile(new TorqueCurve(List.of(new TorqueCurve.Point(0,220),new TorqueCurve.Point(4000,220),new TorqueCurve.Point(8000,110),new TorqueCurve.Point(12000,70),new TorqueCurve.Point(14000,0))),
                new TorqueCurve(List.of(new TorqueCurve.Point(0,110),new TorqueCurve.Point(5000,110),new TorqueCurve.Point(10000,55),new TorqueCurve.Point(14000,35))),.08,14000);
        var p=new EvDrivetrainStep.Parameters(motor,9,3.2,1885,.381,3200,new EvDrivetrainStep.RangePolicy(.15,.75,.2,false,false));
        var state=new EvDrivetrainStep.State(0,0,EvDrivetrainStep.Range.N);
        double normal=p.vehicleMassKg()*9.81,positive=0;
        for(int tick=0;tick<120;tick++) {
            int gear=state.range()==EvDrivetrainStep.Range.R?-1:state.range()==EvDrivetrainStep.Range.D?1:0;
            var pedals=pedals(true,1,0,false,gear,state.speedMetresPerSecond(),state.wheelRadiansPerSecond(),PowertrainSimulation.RangeRequest.KEEP);
            var range=pedals.range()==PowertrainSimulation.RangeRequest.REVERSE?EvDrivetrainStep.RangeRequest.R:EvDrivetrainStep.RangeRequest.KEEP;
            var control=new EvDrivetrainStep.Control(range,pedals.throttle(),0,220,110,pedals.serviceBrake()*6500,1,true,true,true,false,false);
            var env=new EvDrivetrainStep.Environment(true,0,normal,.9,.018*normal,-.5*1.225*1.15*state.speedMetresPerSecond()*Math.abs(state.speedMetresPerSecond()),0);
            var result=EvDrivetrainStep.advance(p,state,new EvDrivetrainStep.Input(control,env),DT);state=result.state();
            check(result.recoveredMechanicalJoules()==0&&result.serviceAndParkingBrakeEnergyJoules()==0,"reverse S must not brake or manufacture recovered energy");
            positive+=result.positiveDriveShaftWorkJoules();
        }
        check(state.range()==EvDrivetrainStep.Range.R&&state.speedMetresPerSecond() < -2&&positive>0,"EV must reverse through its real fixed reduction");
        System.out.printf(java.util.Locale.ROOT,"REVERSE161 body=vw_t3 electric peakKmh=%.3f positiveShaftJ=%.1f%n",-state.speedMetresPerSecond()*3.6,positive);
    }
    private static void soundFamilies() {
        var phase=EngineController.Phase.RUNNING;
        var petrol=DrivetrainSoundModel.sample(900,6500,.1,phase,DrivetrainSoundModel.Voice.PETROL);
        var diesel=DrivetrainSoundModel.sample(900,6500,.1,phase,DrivetrainSoundModel.Voice.DIESEL);
        check(petrol.pitch()!=diesel.pitch()&&petrol.gain()!=diesel.gain(),"fuel families need distinct RPM and load envelopes");
        for(var voice:DrivetrainSoundModel.Voice.values()) {
            var high=DrivetrainSoundModel.sample(3500,6500,.8,phase,voice);
            var low=DrivetrainSoundModel.sample(3500,6500,.1,phase,voice);
            check(high.gain()>low.gain(),"actual load must change sound at fixed shaft RPM");
            var stopped=DrivetrainSoundModel.sample(0,6500,0,EngineController.Phase.OFF,voice);
            var winding=DrivetrainSoundModel.sample(700,6500,0,EngineController.Phase.OFF,voice);
            check(!stopped.audible(),"zero shaft and off must be silent");
            check(winding.audible()==(voice!=DrivetrainSoundModel.Voice.ELECTRIC),"preserve combustion winddown and EV off silence");
        }
    }
    private static void twilightContrast() {
        for(var theme:io.github.foundationgames.automobility.client.dashboard.InfotainmentTheme.values())
            for(int step=0;step<=40;step++) {
                var palette=io.github.foundationgames.automobility.client.dashboard.InfotainmentTheme.palette(theme.accent,step/40f);
                check(contrast(palette.surface(),palette.text())>=4.5,"twilight text loses contrast: "+theme+" step="+step);
                check(contrast(palette.surface(),palette.accent())>=4.5,"twilight station/accent text loses contrast: "+theme+" step="+step);
            }
    }
    private static double contrast(int a,int b) {
        double left=luma(a),right=luma(b);return (Math.max(left,right)+.05)/(Math.min(left,right)+.05);
    }
    private static double luma(int rgb) {
        double sum=0;double[] weights={.0722,.7152,.2126};
        for(int component=0;component<3;component++) {
            double channel=((rgb>>(component*8))&255)/255.0;
            sum+=weights[component]*(channel<=.04045?channel/12.92:Math.pow((channel+.055)/1.055,2.4));
        }
        return sum;
    }
    private static void check(boolean condition,String message) { checks++;if(!condition)throw new AssertionError(message); }
}
