package io.github.foundationgames.automobility.automobile.drivetrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable server-owned input lease. Context and echo issuance must come from the trusted adapter. */
public final class DrivetrainServerSession {
    public static final int MAX_ECHO_TICKETS = 64;

    public record Policy(long inputTtlTicks, long echoMaxAgeTicks) {
        public Policy {
            if (inputTtlTicks < 1 || inputTtlTicks > 200 || echoMaxAgeTicks < 1 || echoMaxAgeTicks > inputTtlTicks)
                throw new IllegalArgumentException("bounded input TTL and echo age required");
        }
    }
    public record Binding(DrivetrainProtocol.Identity identity, UUID authenticatedDriver) {
        public Binding { Objects.requireNonNull(identity, "identity"); DrivetrainProtocol.requireId(authenticatedDriver, "driver"); }
    }
    public record Context(UUID authenticatedDriver, UUID controlledVehicle, UUID vehicleIncarnation,
                          UUID worldInstance, long serverTick, boolean mayControl) {
        public Context {
            DrivetrainProtocol.requireId(authenticatedDriver, "driver"); DrivetrainProtocol.requireId(controlledVehicle, "vehicle");
            DrivetrainProtocol.requireId(vehicleIncarnation, "incarnation"); DrivetrainProtocol.requireId(worldInstance, "world");
            DrivetrainProtocol.nonnegativeTick(serverTick);
        }
    }
    /** Fail-safe means no throttle and an open clutch. It is not an ignition or selector operation. */
    public record HeldInput(float throttle, float clutchDepression, ClutchController.PedalMode clutchMode, boolean startHeld) {
        public HeldInput(float throttle, float clutchDepression, ClutchController.PedalMode clutchMode) {
            this(throttle, clutchDepression, clutchMode, false);
        }
        public HeldInput {
            Units.between(throttle, 0.0, 1.0, "throttle"); Units.between(clutchDepression, 0.0, 1.0, "clutch depression");
            Objects.requireNonNull(clutchMode, "clutch mode");
            if (clutchMode == ClutchController.PedalMode.DIGITAL && clutchDepression != 0f && clutchDepression != 1f)
                throw new IllegalArgumentException("digital clutch must be held or released");
        }
        public static HeldInput neutral() { return new HeldInput(0f, 1f, ClutchController.PedalMode.DIGITAL); }
    }
    public enum Rejection { NONE, MALFORMED, WRONG_CONTEXT, CLOCK_REVERSED, WRONG_IDENTITY, REPLAY_OR_REORDERED, STALE_ECHO, ECHO_NOT_ISSUED }
    /** keyEdges and rangeRequest are delivered only once here, never retained as held state. */
    public record Receipt(boolean accepted, Rejection rejection, DrivetrainServerSession after,
                          DrivetrainProtocol.Intent acceptedIntent) { }

    private final Binding binding;
    private final Policy policy;
    private final long lastSequence, acceptedAtTick, observedAtTick;
    private final List<Long> issuedEchoTicks;
    private final HeldInput held;

    private DrivetrainServerSession(Binding binding, Policy policy, long sequence, long acceptedAt,
                                    long observedAt, List<Long> echoes, HeldInput held) {
        this.binding = binding; this.policy = policy; this.lastSequence = sequence;
        this.acceptedAtTick = acceptedAt; this.observedAtTick = observedAt;
        this.issuedEchoTicks = List.copyOf(echoes); this.held = held;
    }

    public static DrivetrainServerSession open(Binding binding, Policy policy, long serverTick) {
        Objects.requireNonNull(binding, "binding"); Objects.requireNonNull(policy, "policy"); DrivetrainProtocol.nonnegativeTick(serverTick);
        return new DrivetrainServerSession(binding, policy, 0, -1, serverTick, List.of(), HeldInput.neutral());
    }
    public Binding binding() { return binding; }
    public long lastSequence() { return lastSequence; }
    public long acceptedAtTick() { return acceptedAtTick; }
    public HeldInput held() { return held; }
    public List<Long> issuedEchoTicks() { return issuedEchoTicks; }

    /** Call when the server actually sends an echo/challenge; merely receiving a guessed tick grants nothing. */
    public DrivetrainServerSession issueEcho(long serverTick) {
        DrivetrainServerSession now = atTick(serverTick);
        var echoes = new ArrayList<>(now.issuedEchoTicks);
        if (!echoes.contains(serverTick)) echoes.add(serverTick);
        while (echoes.size() > MAX_ECHO_TICKETS) echoes.removeFirst();
        return new DrivetrainServerSession(binding, policy, now.lastSequence, now.acceptedAtTick, serverTick, echoes, now.held);
    }

    /**
     * Call every server tick, including ticks without packets. Expiry clears held controls and keeps
     * accepted sequence/resource/ignition untouched. The clock is supplied only by the trusted adapter.
     */
    public DrivetrainServerSession atTick(long serverTick) {
        DrivetrainProtocol.nonnegativeTick(serverTick);
        if (serverTick < observedAtTick) throw new IllegalArgumentException("server tick moved backwards; open a fresh session");
        var echoes = issuedEchoTicks.stream().filter(tick -> serverTick - tick <= policy.echoMaxAgeTicks()).toList();
        HeldInput controls = acceptedAtTick < 0 || serverTick - acceptedAtTick >= policy.inputTtlTicks() ? HeldInput.neutral() : held;
        return new DrivetrainServerSession(binding, policy, lastSequence, acceptedAtTick, serverTick, echoes, controls);
    }

    public Receipt receiveBytes(Context context, byte[] packet) {
        Objects.requireNonNull(context, "context");
        final DrivetrainProtocol.Intent intent;
        try { intent = DrivetrainProtocol.decodeIntent(packet); }
        catch (IllegalArgumentException | NullPointerException malformed) { return reject(Rejection.MALFORMED, this); }
        return receive(context, intent);
    }

    public Receipt receive(Context context, DrivetrainProtocol.Intent intent) {
        Objects.requireNonNull(context, "context"); Objects.requireNonNull(intent, "intent");
        var identity = binding.identity();
        if (!context.mayControl() || !binding.authenticatedDriver().equals(context.authenticatedDriver())
                || !identity.vehicle().equals(context.controlledVehicle()) || !identity.incarnation().equals(context.vehicleIncarnation())
                || !identity.worldInstance().equals(context.worldInstance())) return reject(Rejection.WRONG_CONTEXT, this);
        if (context.serverTick() < observedAtTick) return reject(Rejection.CLOCK_REVERSED, this);
        DrivetrainServerSession now = atTick(context.serverTick());
        if (!identity.equals(intent.identity())) return reject(Rejection.WRONG_IDENTITY, now);
        if (intent.sequence() <= lastSequence) return reject(Rejection.REPLAY_OR_REORDERED, now);
        if (intent.echoedServerTick() > context.serverTick()
                || context.serverTick() - intent.echoedServerTick() > policy.echoMaxAgeTicks()) return reject(Rejection.STALE_ECHO, now);
        if (!now.issuedEchoTicks.contains(intent.echoedServerTick())) return reject(Rejection.ECHO_NOT_ISSUED, now);
        var next = new DrivetrainServerSession(binding, policy, intent.sequence(), context.serverTick(), context.serverTick(),
                now.issuedEchoTicks, new HeldInput(intent.throttle(), intent.clutchDepression(), intent.clutchMode(), intent.startHeld()));
        return new Receipt(true, Rejection.NONE, next, intent);
    }

    /** Fresh mechanical/input generation after a trusted durable COMMIT; no physics or persistent bytes are changed. */
    public DrivetrainServerSession rebindAfterGarageCommit(DrivetrainSettingsEnvelope.GarageReceipt receipt,
                                                          Binding replacement, long serverTick) {
        receipt.requireCommitFor(binding.identity().vehicle());
        Objects.requireNonNull(replacement, "replacement");
        if (!replacement.identity().vehicle().equals(binding.identity().vehicle())
                || replacement.identity().sessionNonce() == binding.identity().sessionNonce()
                || replacement.identity().incarnation().equals(binding.identity().incarnation()))
            throw new IllegalArgumentException("same vehicle and fresh nonce/incarnation required");
        return open(replacement, policy, serverTick);
    }

    private static Receipt reject(Rejection reason, DrivetrainServerSession after) { return new Receipt(false, reason, after, null); }
}
