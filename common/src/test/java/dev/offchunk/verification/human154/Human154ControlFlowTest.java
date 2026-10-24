package dev.offchunk.verification.human154;

import io.github.foundationgames.automobility.automobile.drivetrain.*;
import io.github.foundationgames.automobility.client.dashboard.CabinDisplayLayout;

/** New closed-loop regressions from Human153; no Minecraft, stores, native rendering or old-suite replay. */
public final class Human154ControlFlowTest {
    private static int checks;
    private static final double DT=.05;
    private static final PhysicalProfileCatalog.EngineDefinition MOTOR=PhysicalProfileCatalog.engine("automobility:diamond").orElseThrow();
    private static final PhysicalProfileCatalog.ChassisDefinition BODY=PhysicalProfileCatalog.chassis("automobility:vw_t3").orElseThrow();
    private static final double RADIUS=.381;
    private static final PowertrainSimulation.Setup SETUP=setup();
    public static void main(String[] args) {
        frontFacingText();
        proportionalCounterexampleAndReset();
        for(double target:new double[]{32,35,42}) vehicleCruise(target);
        firstShiftCharacter();
        System.out.println("PASS_HUMAN154_CONTROL_FLOWS checks="+checks+" boundary=PURE_NOT_NATIVE");
    }
    private static void frontFacingText() {
        for(float[] panel:new float[][]{{.28f,.11f},{.35f,.16f},{.28f,.125f},{.35f,.175f}}) {
            for(boolean tape:new boolean[]{false,true}) for(boolean progress:new boolean[]{false,true}) {
                var layout=CabinDisplayLayout.forPanel(panel[0],panel[1],tape,progress);
                // Exact native glyph winding: top-left, bottom-left, bottom-right, top-right.
                double ax=layout.glyphX(0),ay=layout.glyphY(0),bx=layout.glyphX(0),by=layout.glyphY(8),cx=layout.glyphX(8),cy=layout.glyphY(8);
                check((bx-ax)*(cy-ay)-(by-ay)*(cx-ax)>0,"font faces away from visible +Z logo/panel face");
                check(layout.glyphX(104)>=layout.contentX0()-.000001f,"text escapes panel width");
                check(layout.glyphX(84)>layout.contentX0()+layout.logoSize(),"reserved logo area intersects text");
                check(layout.glyphY(38)<panel[1]-.027f,"last text row intersects buttons/edge");
                check(layout.glyphZ(.031f)-layout.glyphZ(0)<.0002f,"font pixel depth becomes centimetres in world space");
                check(layout.glyphZ(0)>.0026f,"text placed behind logo/progress layers");
                check(layout.progressX0(0)==layout.contentX1() && Math.abs(layout.progressX0(1)-layout.contentX0())<.000001,"progress endpoints drift");
                check(layout.progressX0(.75f)<layout.progressX0(.25f),"front-reader progress moves backwards");
            }
        }
    }
    private static void proportionalCounterexampleAndReset() {
        var config=CruiseSpeedController.Config.defaults();var state=CruiseSpeedController.State.idle();
        double target=32/3.6,oldSpeed=0,speed=0;
        for(int i=0;i<1000;i++) {
            double oldDemand=Math.clamp((target-oldSpeed)*.35,0,1);
            oldSpeed=Math.max(0,oldSpeed+(4*oldDemand-.5)*DT);
            var command=CruiseSpeedController.advance(config,state,true,target,speed,DT);state=command.state();
            speed=Math.max(0,speed+(4*command.throttle()-.5)*DT);
        }
        check((target-oldSpeed)*3.6>.75,"old proportional-only steady deficit counterexample missing");
        check(Math.abs(target-speed)*3.6<.05,"remembered load did not remove steady speed deficit");
        check(state.integralThrottle()>0,"zero-error cruise has no load compensation");
        var reset=CruiseSpeedController.advance(config,state,false,target,speed,DT);
        check(!reset.state().active() && reset.throttle()==0 && reset.state().integralThrottle()==0,"ineligible cruise sample retains remembered demand");
        var saturated=CruiseSpeedController.State.idle();
        for(int i=0;i<1000;i++) saturated=CruiseSpeedController.advance(config,saturated,true,30,0,DT).state();
        check(saturated.integralThrottle()==0,"unavailable speed headroom winds integral to full throttle");
        var resume=CruiseSpeedController.advance(config,saturated,true,target,target,DT);
        check(resume.throttle()==0,"large target change reuses stale integral");
        var zero=CruiseSpeedController.advance(config,state,true,0,0,DT);
        check(zero.throttle()==0 && !zero.state().active(),"zero target creates idle creep demand");
    }
    private static PowertrainSimulation.Setup setup() {
        var mechanics=PhysicalProfileCatalog.mechanics(MOTOR,BODY,RADIUS,75);
        var manual=new ManualShiftController.Config(.05,.04,.20,.05,.35,.75,.98,true,true,1).withDownshiftProtection(true);
        return new PowertrainSimulation.Setup(MOTOR.engine(),mechanics,EngineController.Config.defaults().withStallBehavior(false,true),
                new ClutchController.Config(.8,0),AutomaticShiftController.Config.defaults(),manual,ThrottleController.Config.defaults(),
                new LaunchController.Config(500,2,.425,true,.4,.2),.005,BODY.parkingHoldingTorqueNewtonMetres()).withManualClutchAssistance(.5);
    }
    private static PowertrainSimulation.State initial(AutomaticShiftController.Mode mode) {
        return new PowertrainSimulation.State(new DrivetrainState(Units.rpmToRadiansPerSecond(MOTOR.engine().idleRpm()),0,0,1),
                EngineController.State.running(),new ClutchController.State(1),new ThrottleController.State(0),new LaunchController.State(1,0),
                AutomaticShiftController.State.drive(1,mode),ManualShiftController.State.drive(1),PowertrainSimulation.ControlMode.AUTOMATIC,false,false,0);
    }
    private static PowertrainSimulation.State step(PowertrainSimulation.State state,double throttle,double brake,double grade,AutomaticShiftController.Mode mode) {
        var input=new PowertrainSimulation.DriverInput(PowertrainSimulation.ControlMode.AUTOMATIC,mode,
                new ThrottleController.Input(throttle!=0&&throttle!=1,throttle),ClutchController.Input.digital(false),ManualShiftController.Request.NONE,
                PowertrainSimulation.RangeRequest.KEEP,brake,brake,true,false,true,true,true);
        double normal=SETUP.mechanics().vehicleMassKg()*9.81*Math.sqrt(1-grade*grade);
        var environment=new PowertrainSimulation.Environment(true,grade,normal,.9,BODY.rollingResistanceCoefficient()*normal,
                BODY.airResistanceForceNewtons(state.mechanics().speedMetresPerSecond(),1.225),BODY.serviceBrakeTorqueNewtonMetres());
        return PowertrainSimulation.advance(SETUP,state,input,environment,DT).state();
    }
    private static void vehicleCruise(double targetKmh) {
        var state=initial(AutomaticShiftController.Mode.BALANCED);var cruise=CruiseSpeedController.State.idle();int changes=0,last=1;
        double maximumFinalError=0;
        for(int tick=0;tick<1200;tick++) {
            var command=CruiseSpeedController.advance(CruiseSpeedController.Config.defaults(),cruise,true,targetKmh/3.6,state.mechanics().speedMetresPerSecond(),DT);
            cruise=command.state();state=step(state,command.throttle(),0,tick<400?0:.03,AutomaticShiftController.Mode.BALANCED);
            check(Double.isFinite(state.mechanics().speedMetresPerSecond()),"nonfinite coupled cruise speed");
            if(tick>=800) {
                maximumFinalError=Math.max(maximumFinalError,Math.abs(state.mechanics().speedMetresPerSecond()*3.6-targetKmh));
                if(state.mechanics().gear()!=last)changes++;
            }
            last=state.mechanics().gear();
        }
        check(maximumFinalError<.25,"loaded W4 cruise steady error at"+targetKmh+" was"+maximumFinalError);
        check(state.mechanics().gear()>=2,"urban cruise kept first at"+targetKmh);
        check(changes<=2,"urban cruise hunted between gears at"+targetKmh);
        check(state.engine().phase()==EngineController.Phase.RUNNING,"loaded cruise stopped engine");
        System.out.println("FLOW154_W4_CRUISE target="+targetKmh+" actual="+state.mechanics().speedMetresPerSecond()*3.6+" gear="+state.mechanics().gear()+" maxFinalError="+maximumFinalError);
    }
    private static void firstShiftCharacter() {
        double[] shifts=new double[3];int index=0;
        for(var mode:new AutomaticShiftController.Mode[]{AutomaticShiftController.Mode.TOURING,AutomaticShiftController.Mode.BALANCED,AutomaticShiftController.Mode.SPORT}) {
            var state=initial(mode);boolean shifted=false;
            for(int tick=0;tick<1200;tick++) {
                state=step(state,1,0,0,mode);
                if(state.mechanics().gear()>=2) { shifts[index++]=state.mechanics().speedMetresPerSecond()*3.6;shifted=true;break; }
            }
            check(shifted,"full keyboard throttle never upshifted: "+mode);
        }
        check(shifts[0]<shifts[1] && shifts[1]<shifts[2],"driving modes lost distinct shift character");
        check(shifts[0]<36 && shifts[1]<42,"normal keyboard acceleration held first beyond urban speeds");
        System.out.println("FLOW154_FIRST_SHIFT touring="+shifts[0]+" balanced="+shifts[1]+" sport="+shifts[2]);
    }
    private static void check(boolean value,String message) { checks++;if(!value)throw new AssertionError(message); }
}
