package io.github.foundationgames.automobility.automobile.drivetrain;

/**
 * Applied net crank torque has already been approved by the propulsion/energy owner.
 * Grade is sin(ground tangent inclination), positive uphill; external force excludes that gravity.
 */
public record DrivetrainInput(double appliedCrankTorqueNewtonMetres, double clutchEngagement,
                              double serviceBrakeTorqueNewtonMetres, double rollingResistanceNewtons,
                              boolean grounded, double gradeSine, double normalLoadNewtons,
                              double tyreFrictionCoefficient, double externalLongitudinalForceNewtons,
                              double engineDragTorqueNewtonMetres) {
    public DrivetrainInput {
        Units.finite(appliedCrankTorqueNewtonMetres, "crank torque");
        Units.between(clutchEngagement, 0.0, 1.0, "clutch engagement");
        Units.nonnegative(serviceBrakeTorqueNewtonMetres, "service brake torque");
        Units.nonnegative(rollingResistanceNewtons, "rolling resistance");
        Units.between(gradeSine, -1.0, 1.0, "grade sine");
        Units.nonnegative(normalLoadNewtons, "normal load");
        Units.nonnegative(tyreFrictionCoefficient, "tyre friction");
        Units.finite(externalLongitudinalForceNewtons, "external force");
        Units.nonnegative(engineDragTorqueNewtonMetres, "passive engine drag capacity");
    }

    public DrivetrainInput(double appliedCrankTorqueNewtonMetres, double clutchEngagement,
                            double serviceBrakeTorqueNewtonMetres, double rollingResistanceNewtons,
                            boolean grounded, double gradeSine, double normalLoadNewtons,
                            double tyreFrictionCoefficient, double externalLongitudinalForceNewtons) {
        this(appliedCrankTorqueNewtonMetres, clutchEngagement, serviceBrakeTorqueNewtonMetres,
                rollingResistanceNewtons, grounded, gradeSine, normalLoadNewtons,
                tyreFrictionCoefficient, externalLongitudinalForceNewtons, 0.0);
    }
}
