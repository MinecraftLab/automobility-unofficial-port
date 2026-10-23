package io.github.foundationgames.automobility.util.network.content;

/** One connection-bound configuration task. Stale/duplicate replies never complete another task. */
public final class PendingCoreContentGate {
    public enum Result { ACCEPTED, INCOMPATIBLE, STALE, EXPIRED, CLOSED }
    private final long nonce;
    private final int required;
    private final long started;
    private boolean closed;

    public PendingCoreContentGate(long nonce, int required, long nowNanos) {
        if (nonce == 0 || required < 0) throw new IllegalArgumentException("Valid content gate required");
        this.nonce = nonce;
        this.required = required;
        this.started = nowNanos;
    }

    public Result reply(int revision, long replyNonce, int supported, long nowNanos) {
        if (closed) return Result.CLOSED;
        if (expired(nowNanos)) { closed = true; return Result.EXPIRED; }
        if (replyNonce != nonce) return Result.STALE;
        closed = true;
        return CoreContentProtocol.accepts(revision, replyNonce, supported, required)
                ? Result.ACCEPTED : Result.INCOMPATIBLE;
    }

    public boolean expired(long nowNanos) {
        // Difference arithmetic also handles a nanoTime wrap during this bounded interval.
        return nowNanos - started >= CoreContentProtocol.TIMEOUT_NANOS;
    }

    public CoreContentChallenge challenge() { return new CoreContentChallenge(CoreContentProtocol.REVISION, nonce, required); }
}
