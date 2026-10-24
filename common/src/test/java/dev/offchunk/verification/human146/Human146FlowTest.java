package dev.offchunk.verification.human146;

import io.github.foundationgames.automobility.automobile.drivetrain.*;
import io.github.foundationgames.automobility.entity.VehicleBodyInterpolation;

/** New stop/rollback and pose-retargeting counterexamples, without Minecraft or storage execution. */
public final class Human146FlowTest {
    private static int checks;
    public static void main(String[] args) {
        stoppedWheelResidue();
        brakeTrajectory("automobility:iron");
        brakeTrajectory("automobility:diamond");
        poses();
        System.out.println("PASS_HUMAN146_FLOWS checks="+checks+" boundary=PURE_NOT_NATIVE");
    }
    private static void stoppedWheelResidue() {
        var config=AutomaticShiftController.Config.defaults();
        for (var definition:PhysicalProfileCatalog.engines().values()) {
            var engine=definition.engine();var box=definition.transmission();
            for (int gear=2;gear<=box.forwardRatios().size();gear++) {
                for (double wheel:new double[]{-1e-12,-1e-8,-.01,0,.01}) {
                    var in=new AutomaticShiftController.Input(engine.idleRpm(),wheel,0,0,AutomaticShiftController.Mode.BALANCED);
                    var start=AutomaticShiftController.advance(config,engine,box,AutomaticShiftController.State.drive(gear,in.mode()),in,0);
                    check(start.currentGear()==gear && start.targetGear()==1 && start.phase()==AutomaticShiftController.Phase.TORQUE_CUT,"signed stopped-wheel residue blocked launch ratio");
                    var done=AutomaticShiftController.advance(config,engine,box,start,in,config.torqueCutSeconds()+config.ratioSwitchSeconds()+config.reengageSeconds()+.001);
                    check(done.currentGear()==1 && done.phase()==AutomaticShiftController.Phase.DRIVE,"launch ratio did not complete");
                }
                double reverse=-Units.rpmToRadiansPerSecond(engine.redlineRpm()*1.1)/box.totalRatio(1);
                var in=new AutomaticShiftController.Input(engine.idleRpm(),reverse,0,0,AutomaticShiftController.Mode.BALANCED);
                check(AutomaticShiftController.advance(config,engine,box,AutomaticShiftController.State.drive(gear,in.mode()),in,0).targetGear()==gear,"fast reverse-wheel guard lost");
                var stopped=new AutomaticShiftController.Input(engine.idleRpm(),-1e-8,0,0,in.mode());
                var pending=AutomaticShiftController.advance(config,engine,box,AutomaticShiftController.State.drive(gear,in.mode()),stopped,0);
                var rejected=AutomaticShiftController.advance(config,engine,box,pending,in,config.torqueCutSeconds());
                check(rejected.currentGear()==gear,"reverse wheel acceleration during shift bypassed overspeed guard");
            }
        }
    }
    private static void brakeTrajectory(String id) {
        var definition=PhysicalProfileCatalog.engine(id).orElseThrow();var engine=definition.engine();
        var chassis=PhysicalProfileCatalog.chassis("automobility:vw_t3").orElseThrow();
        var mechanics=PhysicalProfileCatalog.mechanics(definition,chassis,.30,75);
        var manual=new ManualShiftController.Config(.05,.04,.20,.05,.35,.75,.98,true,true,1).withDownshiftProtection(true);
        var setup=new PowertrainSimulation.Setup(engine,mechanics,EngineController.Config.defaults().withStallBehavior(false,true),
                new ClutchController.Config(.8,0),AutomaticShiftController.Config.defaults(),manual,ThrottleController.Config.defaults(),
                new LaunchController.Config(500,2,.425,true,.4,.2),.005,chassis.parkingHoldingTorqueNewtonMetres()).withManualClutchAssistance(.5);
        int gear=3;double speed=8,wheel=speed/.30,omega=wheel*definition.transmission().totalRatio(gear);
        var state=new PowertrainSimulation.State(new DrivetrainState(omega,wheel,speed,gear),EngineController.State.running(),
                new ClutchController.State(1),new ThrottleController.State(0),new LaunchController.State(1,0),
                AutomaticShiftController.State.drive(gear,AutomaticShiftController.Mode.BALANCED),ManualShiftController.State.drive(gear),
                PowertrainSimulation.ControlMode.AUTOMATIC,false,false,0);
        for (int tick=0;tick<140;tick++) {
            double brake=tick<70?1:0;
            var driver=new PowertrainSimulation.DriverInput(PowertrainSimulation.ControlMode.AUTOMATIC,AutomaticShiftController.Mode.BALANCED,
                    ThrottleController.Input.digital(false),ClutchController.Input.digital(false),ManualShiftController.Request.NONE,
                    PowertrainSimulation.RangeRequest.KEEP,brake,brake,true,false,true,true,true);
            double load=mechanics.vehicleMassKg()*9.81;
            var environment=new PowertrainSimulation.Environment(true,0,load,.8,chassis.rollingResistanceCoefficient()*load,
                    chassis.airResistanceForceNewtons(state.mechanics().speedMetresPerSecond(),1.225),chassis.serviceBrakeTorqueNewtonMetres());
            state=PowertrainSimulation.advance(setup,state,driver,environment,.05).state();
        }
        check(Math.abs(state.mechanics().speedMetresPerSecond())<.01,"no-throttle car crept after braking: "+id);
        check(state.mechanics().gear()==1,"coupled brake-to-stop retained high gear: "+id);
        check(state.engine().phase()==EngineController.Phase.RUNNING,"stall-disabled brake trajectory stopped engine: "+id);
    }
    private static void poses() {
        var current=new VehicleBodyInterpolation.Pose(0,0,0,179);
        double previous=0;
        for (int tick=1;tick<=200;tick++) {
            var target=new VehicleBodyInterpolation.Pose(tick*.25,0,0,tick%2==0?-179:179);
            check(VehicleBodyInterpolation.mayInterpolate(current,target),"normal server step rejected");
            current=VehicleBodyInterpolation.advance(current,target,VehicleBodyInterpolation.TICKS);
            check(current.x()>previous && current.x()<=target.x(),"retarget moved backwards or overshot");
            check(current.x()-previous<=.250001,"constant-speed authoritative stream jumped");
            check(Math.abs(current.yaw()-180)<2,"yaw crossed the long arc");
            previous=current.x();
        }
        var target=new VehicleBodyInterpolation.Pose(current.x(),3,current.z(),-179);
        current=VehicleBodyInterpolation.advance(current,target,2);
        check(current.y()==1.5,"vertical interpolation skipped its midpoint");
        current=VehicleBodyInterpolation.advance(current,target,1);
        check(current.y()==3,"vertical interpolation did not finish");
        check(!VehicleBodyInterpolation.mayInterpolate(current,new VehicleBodyInterpolation.Pose(current.x()+9,3,0,0)),"large teleport softened instead of using vanilla");
    }
    private static void check(boolean value,String message) { checks++;if(!value)throw new AssertionError(message); }
}
