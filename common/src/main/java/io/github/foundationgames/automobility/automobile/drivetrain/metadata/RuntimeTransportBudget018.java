package io.github.foundationgames.automobility.automobile.drivetrain.metadata;

import io.github.foundationgames.automobility.automobile.drivetrain.*;
import io.github.foundationgames.automobility.automobile.drivetrain.profile.PhysicalDefinitions;
import java.util.Objects;

/** Conservative finite-force envelope before ANY owner quote/authorization. It rejects, never clamps.
 * Passive wheel/crank/rolling friction opposes its own coordinate and cannot increase its absolute speed;
 * tyre and clutch reactions are included at their full finite capacities. External collisions remain separate work.
 * This checks representation safety, not whether an engine/cargo configuration is a good game calibration. */
public final class RuntimeTransportBudget018 {
    private RuntimeTransportBudget018(){}
    public static boolean permits(PhysicalDefinitions.Selection s, double payloadKg, double engineOmega, double wheelOmega,
            double bodySpeed, double normalLoadN, double friction, double gradeSine, double externalForceN,
            double boost, double starterFractionOfPeak, double seconds) {
        Objects.requireNonNull(s);
        if(!finite(payloadKg,engineOmega,wheelOmega,bodySpeed,normalLoadN,friction,gradeSine,externalForceN,boost,starterFractionOfPeak,seconds)
                ||payloadKg<0||payloadKg>100000||normalLoadN<0||friction<0||Math.abs(gradeSine)>1||boost<1||boost>1.18
                ||starterFractionOfPeak<0||seconds<=0||seconds>.05)return false;
        double mass=s.emptyMassKg()+payloadKg, tyre=normalLoadN*friction, radius=s.drivenRadiusMetres(), axleI=s.equivalentWheelInertiaKgMetresSquared();
        double bodyEnvelope=Math.abs(bodySpeed)+seconds*(9.81*Math.abs(gradeSine)+(Math.abs(externalForceN)+tyre)/mass);
        double motorEnvelope,wheelEnvelope,maximumShaftTorque;
        var e=s.engine();
        if(e.combustion()!=null){
            double peak=e.statistics().torqueNewtonMetres().value()*boost, ratio=e.transmission().reverseRatio()*e.transmission().finalDrive();
            for(double r:e.transmission().forwardRatios())ratio=Math.max(ratio,r*e.transmission().finalDrive());
            motorEnvelope=Math.abs(engineOmega)+seconds*(peak*(1+starterFractionOfPeak)+e.clutchCapacityNm())/e.combustion().inertiaKgMetresSquared();
            wheelEnvelope=Math.abs(wheelOmega)+seconds*(e.clutchCapacityNm()*ratio+tyre*radius)/axleI;
            maximumShaftTorque=peak;
        }else{
            double drive=e.statistics().torqueNewtonMetres().value()*boost, regen=e.electric().regenerationNewtonMetres().statistics().torqueNewtonMetres().value();
            double combinedI=axleI+e.electric().rotorInertiaKgMetresSquared()*e.fixedReductionRatio()*e.fixedReductionRatio();
            wheelEnvelope=Math.abs(wheelOmega)+seconds*(drive*e.fixedReductionRatio()+tyre*radius)/combinedI;
            motorEnvelope=wheelEnvelope*e.fixedReductionRatio();maximumShaftTorque=Math.max(drive,regen);
        }
        return finite(mass,tyre,radius,axleI,bodyEnvelope,motorEnvelope,wheelEnvelope,maximumShaftTorque)
                &&bodyEnvelope<=DrivetrainProtocol.MAX_ABSOLUTE_SPEED&&motorEnvelope<=DrivetrainProtocol.MAX_ABSOLUTE_OMEGA
                &&wheelEnvelope<=DrivetrainProtocol.MAX_ABSOLUTE_OMEGA&&maximumShaftTorque<=20000&&motorEnvelope*maximumShaftTorque<=1e9;
    }
    /** No owner transaction may be started from a state whose unpowered observation is already outside the wire domain. */
    public static boolean representable(double engineOmega,double wheelOmega,double bodySpeed){return finite(engineOmega,wheelOmega,bodySpeed)
            &&Math.abs(engineOmega)<=DrivetrainProtocol.MAX_ABSOLUTE_OMEGA&&Math.abs(wheelOmega)<=DrivetrainProtocol.MAX_ABSOLUTE_OMEGA&&Math.abs(bodySpeed)<=DrivetrainProtocol.MAX_ABSOLUTE_SPEED;}
    private static boolean finite(double...values){for(double value:values)if(!Double.isFinite(value))return false;return true;}
}
