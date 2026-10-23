package io.github.foundationgames.automobility.util.network.content;

/** Content/schema negotiation, deliberately independent of Momentum's semantic physics revision. */
public final class CoreContentProtocol {
    public static final int REVISION = 1;
    public static final int TRIO_CONTENT = 1;
    public static final int MX5_ANIMATION = 2;
    public static final int GARAGE_PODIUM = 4;
    public static final int GARAGE_POLISH = 8;
    public static final int GARAGE_WORKSHOP = 16;
    public static final int GARAGE_PICKER = 32;
    public static final int GARAGE_TEST_DRIVE = 64;
    // Each garage generation has a distinct opening codec. Do not advertise the
    // old codec: test drives add an explicit return-to-tuning opening flag.
    public static final int SUPPORTED = TRIO_CONTENT | MX5_ANIMATION | GARAGE_TEST_DRIVE;
    public static final int REQUIRED = TRIO_CONTENT | MX5_ANIMATION;
    public static final long TIMEOUT_NANOS = 45_000_000_000L;

    private CoreContentProtocol() { }

    public static boolean accepts(int revision, long nonce, int offered, int required) {
        return revision == REVISION && nonce != 0 && offered >= 0 && required >= 0
                && (offered & required) == required;
    }
}
