package io.github.milkucha.momentum.network;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.util.function.ToDoubleFunction;
/** Installed by the client only; common initialization remains safe on dedicated servers. */
public final class CruiseMotorPrediction {
    private static ToDoubleFunction<AutomobileEntity> target=car->Double.NaN;
    private CruiseMotorPrediction(){}
    public static void install(ToDoubleFunction<AutomobileEntity> value){target=value;}
    public static double targetFor(AutomobileEntity car){return target.applyAsDouble(car);}
}
