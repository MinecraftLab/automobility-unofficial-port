package io.github.milkucha.momentum;

import java.util.Objects;

/**
 * Optional controller-action bridge.
 *
 * <p>The core Momentum client never links against Controlify directly. A compatibility
 * entrypoint installs a source only when Controlify is present, keeping controller support
 * optional and keeping dedicated-server class loading safe.</p>
 */
public final class MomentumControllerInput {
    private static final ActionSnapshot NONE = new ActionSnapshot(false, false, false, false);
    private static ActionSource source = () -> NONE;

    private MomentumControllerInput() {
    }

    public static ActionSnapshot poll() {
        ActionSnapshot snapshot = source.poll();
        return snapshot == null ? NONE : snapshot;
    }

    public static ActionSnapshot none() {
        return NONE;
    }

    public static void install(ActionSource nextSource) {
        source = Objects.requireNonNull(nextSource, "nextSource");
    }

    static void resetForTests() {
        source = () -> NONE;
    }

    @FunctionalInterface
    public interface ActionSource {
        ActionSnapshot poll();
    }

    public record ActionSnapshot(boolean cruiseToggle,
                                 boolean cruiseIncrease,
                                 boolean cruiseDecrease,
                                 boolean cruiseResume) {
    }
}
