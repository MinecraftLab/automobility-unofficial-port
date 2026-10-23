package io.github.milkucha.momentum;

/** Input arbitration only. No brake strength, drift ownership or network semantics live here. */
public final class MomentumInputPolicy {
    private MomentumInputPolicy() {}

    public record Pedals(boolean brake, boolean drift) {}

    /** A shared keyboard binding must not turn the ordinary brake into a handbrake. */
    public static Pedals pedals(boolean keyboardBrake, boolean keyboardDrift, boolean sharedBinding,
                                boolean controllerBrake, boolean controllerDrift) {
        return new Pedals(keyboardBrake || controllerBrake,
                (keyboardDrift && !(keyboardBrake && sharedBinding)) || controllerDrift);
    }

    public static boolean allowCruiseResume(boolean garageReturnContext, boolean sharedReturnBinding) {
        return !garageReturnContext || !sharedReturnBinding;
    }

    public static boolean allowReload(boolean playerPresent, boolean screenOpen) {
        return playerPresent && !screenOpen;
    }
}
