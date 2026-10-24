package io.github.foundationgames.automobility.automobile.drivetrain;

/** Loaded-brake/starter reproductions for the explicit stall switch across assistance strengths. */
public final class StallDisabledRegressionTest137 {
    private static int checks;
    public static void main(String[] args){
        var definition=PhysicalProfileCatalog.engine("automobility:iron").orElseThrow();var engine=definition.engine();
        var parameters=DrivetrainParameters.forEngine(engine,2,1200,.32,definition.clutchCapacityNewtonMetres(),definition.transmission());
        var environment=new PowertrainSimulation.Environment(true,0,1200*9.81,1,120,0,4000);
        for(var mode:new PowertrainSimulation.ControlMode[]{PowertrainSimulation.ControlMode.MANUAL_CLUTCH,PowertrainSimulation.ControlMode.MANUAL_ASSISTED}){
            for(double assist:new double[]{0,.25,.5,1}){
                var setup=new PowertrainSimulation.Setup(engine,parameters,EngineController.Config.defaults().withStallBehavior(false,assist>0),
                        new ClutchController.Config(.8,0),AutomaticShiftController.Config.defaults(),ManualShiftController.Config.defaults(),
                        ThrottleController.Config.defaults(),LaunchController.Config.defaults(),.005,5000).withManualClutchAssistance(assist);
                var initial=PowertrainSimulation.State.stopped(mode);
                var state=new PowertrainSimulation.State(new DrivetrainState(Units.rpmToRadiansPerSecond(800),0,0,1),EngineController.State.running(),
                        new ClutchController.State(1),initial.throttle(),initial.launch(),AutomaticShiftController.State.drive(1,AutomaticShiftController.Mode.BALANCED),ManualShiftController.State.drive(1),mode,false,false,0);
                var input=new PowertrainSimulation.DriverInput(mode,AutomaticShiftController.Mode.BALANCED,new ThrottleController.Input(false,0),
                        ClutchController.Input.digital(false),ManualShiftController.Request.NONE,PowertrainSimulation.RangeRequest.KEEP,1,true,false,true,true,true);
                for(int tick=0;tick<80;tick++){
                    state=PowertrainSimulation.advance(setup,state,input,environment,.05).state();
                    check(state.engine().phase()==EngineController.Phase.RUNNING&&state.mechanics().engineRadiansPerSecond()>0,"stall-disabled brake hold stopped the engine: "+mode+" "+assist);
                    check(Math.abs(state.mechanics().speedMetresPerSecond())<.01,"clutch safety defeated the held brake");
                }
                // An ordinary paid starter must still catch with first selected and the pedal released.
                state=new PowertrainSimulation.State(new DrivetrainState(0,0,0,1),EngineController.State.off(),state.pedal(),initial.throttle(),initial.launch(),
                        AutomaticShiftController.State.drive(1,AutomaticShiftController.Mode.BALANCED),ManualShiftController.State.drive(1),mode,false,false,0);
                var start=new PowertrainSimulation.DriverInput(mode,input.drivingMode(),input.throttle(),input.clutch(),input.shift(),input.range(),1,true,true,true,true,true);
                for(int tick=0;tick<35;tick++)state=PowertrainSimulation.advance(setup,state,start,environment,.05).state();
                check(state.engine().phase()==EngineController.Phase.RUNNING,"stall-disabled restart could not catch under clutch load");
            }
        }
        System.out.println("PASS StallDisabledRegressionTest137 checks="+checks);
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
