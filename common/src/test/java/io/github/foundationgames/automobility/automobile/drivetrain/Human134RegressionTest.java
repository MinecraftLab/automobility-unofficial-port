package io.github.foundationgames.automobility.automobile.drivetrain;

import io.github.foundationgames.automobility.api.dashboard.CabinPreferences022;
import io.github.foundationgames.automobility.client.dashboard.InfotainmentTheme;
import io.github.foundationgames.automobility.entity.DriverViewRotation;
import io.github.foundationgames.automobility.screen.GarageUiLayout;
import java.util.Arrays;
import java.util.HexFormat;

/** Reproductions of reported boundary conditions; no game, sound-hearing or visual acceptance. */
public final class Human134RegressionTest {
    private static int checks;
    public static void main(String[] args){
        for(var definition:PhysicalProfileCatalog.engines().values()){
            var engine=definition.engine();var box=definition.transmission();var config=AutomaticShiftController.Config.defaults();
            for(int gear=2;gear<=box.forwardRatios().size();gear++){
                var input=new AutomaticShiftController.Input(engine.idleRpm(),0,0,.05,AutomaticShiftController.Mode.BALANCED);
                var start=AutomaticShiftController.advance(config,engine,box,AutomaticShiftController.State.drive(gear,input.mode()),input,0);
                check(start.currentGear()==gear&&start.targetGear()==1&&start.phase()==AutomaticShiftController.Phase.TORQUE_CUT,"hard stop did not request a timed launch ratio");
                var done=AutomaticShiftController.advance(config,engine,box,start,input,config.torqueCutSeconds()+config.ratioSwitchSeconds()+config.reengageSeconds()+.001);
                check(done.currentGear()==1&&done.phase()==AutomaticShiftController.Phase.DRIVE,"stopped automatic stayed in a high gear");
                double wheel=Units.rpmToRadiansPerSecond(engine.redlineRpm()*1.1)/box.totalRatio(1);
                var unsafe=new AutomaticShiftController.Input(engine.idleRpm(),wheel,0,.05,input.mode());
                var rejected=AutomaticShiftController.advance(config,engine,box,start,unsafe,config.torqueCutSeconds());
                check(rejected.currentGear()==gear,"landing reengaged an overspeed first gear");
            }
            var mechanics=DrivetrainParameters.forEngine(engine,2,1200,.32,definition.clutchCapacityNewtonMetres(),box);
            double idleOmega=Units.rpmToRadiansPerSecond(engine.idleRpm());
            var creeping=new LaunchController.Input(true,true,idleOmega,idleOmega*.98/box.totalRatio(1),2.2,0,0,box.totalRatio(1),20,5,10,0,8000,3000);
            var coast=LaunchController.advance(LaunchController.Config.defaults(),engine,mechanics,new LaunchController.State(1,0),creeping,.05);
            check(coast.clutchCeiling()==0,"idle governor kept a no-throttle car moving");
            var braking=new LaunchController.Input(true,true,idleOmega*2,idleOmega*2/box.totalRatio(1),8,0,0,box.totalRatio(1),5,10,-10,0,8000,3000);
            check(LaunchController.advance(LaunchController.Config.defaults(),engine,mechanics,new LaunchController.State(1,0),braking,.05).clutchCeiling()==1,"coasting above idle lost engine braking");
        }
        var stopped=DrivetrainSoundModel.sample(0,6500,0,EngineController.Phase.OFF,false);
        var rundown=DrivetrainSoundModel.sample(700,6500,0,EngineController.Phase.OFF,false);
        var slower=DrivetrainSoundModel.sample(200,6500,0,EngineController.Phase.OFF,false);
        check(!stopped.audible()&&rundown.audible()&&slower.gain()<rundown.gain(),"shutdown sound ignored the spinning shaft");
        check(DrivetrainSoundModel.sample(700,6500,1,EngineController.Phase.OFF,false).equals(rundown),"shutdown invented combustion load");
        check(DriverViewRotation.turn(179.9f,.2f)>180,"forward view crossing wrapped the hand angle");
        check(Math.abs(DriverViewRotation.nearest(359.9f,.1f)-360.1f)<.001,"locked view took the long rotation path");
        float fit=GarageUiLayout.viewportScale(427,240);
        var layout=GarageUiLayout.compute((int)Math.ceil(427/fit),(int)Math.ceil(240/fit),GarageUiLayout.Mode.TUNING);
        check(!layout.small()&&layout.panel().right()*fit<=428&&layout.panel().bottom()*fit<=241,"GUI 3 showroom did not fit");
        var legacy=HexFormat.of().parseHex("4341423101000000000000000001003f59999a3eb3333300ffb449");
        check(Arrays.equals(legacy,CabinPreferences022.encode(CabinPreferences022.Settings.defaults())),"default cabin encoding changed historical AUTO data");
        var night=new CabinPreferences022.Settings(3,true,CabinPreferences022.Page.RADIO,.85f,.35f,InfotainmentTheme.FORMULA.accent,CabinPreferences022.Lighting.NIGHT);
        check(CabinPreferences022.read(CabinPreferences022.encode(night)).settings().orElseThrow().equals(night),"manual lighting did not round-trip");
        byte[] future=legacy.clone();future[4]=3;
        var opaque=CabinPreferences022.read(future);
        check(opaque.status()==CabinPreferences022.Status.OPAQUE&&Arrays.equals(future,opaque.raw()),"future cabin data was rewritten");
        for(var theme:InfotainmentTheme.values())check(InfotainmentTheme.palette(theme.accent,0).surface()!=InfotainmentTheme.palette(theme.accent,1).surface(),"theme has no day/night palette");
        System.out.println("PASS Human134RegressionTest checks="+checks);
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
