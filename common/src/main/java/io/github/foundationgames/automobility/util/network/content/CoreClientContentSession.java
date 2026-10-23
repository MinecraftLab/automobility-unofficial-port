package io.github.foundationgames.automobility.util.network.content;

/** Current client configuration/play state; no Minecraft-client classes can leak onto a server. */
public final class CoreClientContentSession {
    public enum State { ABSENT, NEGOTIATING, NEGOTIATED, LEGACY }
    private static Object owner;
    private static State state = State.ABSENT;
    private static int capabilities;
    private static boolean replySent;

    private CoreClientContentSession() { }

    public static void begin(Object listener) {
        owner = listener;
        state = listener == null ? State.ABSENT : State.NEGOTIATING;
        capabilities = 0;
        replySent = false;
    }

    public static boolean accepts(Object listener, CoreContentChallenge challenge) {
        return listener != null && listener == owner && state == State.NEGOTIATING && !replySent
                && CoreContentProtocol.accepts(challenge.revision(), challenge.nonce(),
                        CoreContentProtocol.SUPPORTED, challenge.required());
    }

    public static void sentReply(Object listener, CoreContentChallenge challenge) {
        if (!accepts(listener, challenge)) return;
        // Current revision defines the same capability set on both peers. Server requirements
        // also certify availability; do not infer unrequested future animation capabilities.
        capabilities = challenge.required() & CoreContentProtocol.SUPPORTED;
        replySent = true;
    }

    public static void complete(Object listener) {
        if (listener != owner || state != State.NEGOTIATING) return;
        state = replySent ? State.NEGOTIATED : State.LEGACY;
        if (!replySent) capabilities = 0;
    }

    public static boolean isCurrent(Object listener) { return listener != null && listener == owner; }
    public static boolean supports(int required) {
        return required > 0 && state == State.NEGOTIATED && (capabilities & required) == required;
    }
    public static State state() { return state; }
    public static void clear(Object listener) { if (listener == owner) reset(); }
    public static void reset() { owner = null; state = State.ABSENT; capabilities = 0; replySent = false; }
}
