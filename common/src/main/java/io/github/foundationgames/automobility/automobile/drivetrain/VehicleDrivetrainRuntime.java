package io.github.foundationgames.automobility.automobile.drivetrain;

import io.github.foundationgames.automobility.api.drivetrain.VehicleDrivetrainProfiles;
import io.github.foundationgames.automobility.api.drivetrain.profile.*;
import io.github.foundationgames.automobility.automobile.drivetrain.profile.*;
import io.github.foundationgames.automobility.automobile.drivetrain.metadata.*;
import io.github.foundationgames.automobility.api.drivetrain.energy.*;
import io.github.foundationgames.automobility.automobile.garage.GarageTransferAccess;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import net.minecraft.world.phys.Vec3;

/** Per-car mechanical authority. Storage receipts precede publication of every powered server step. */
public final class VehicleDrivetrainRuntime {
    static { CoreDrivetrainBridge.installRuntimeReset(AutomobileEntity::drivetrainCommittedGarage); }
    private static final double DT = 0.05;
    private static final DrivetrainSettingsEnvelope.Settings DEFAULTS = DrivetrainSettingsPolicy.defaultSettings();
    private final AutomobileEntity car;
    private boolean selected, ignitionOn, automaticCranking, previousDriveDemand, blocked;
    private long lastTick = Long.MIN_VALUE, lastSnapshotSequence, lastReceivedSequence;
    private DrivetrainProtocol.Identity receivedIdentity;
    private DrivetrainProtocol.Identity identity;
    private String engineId = "", profileHash = "";
    private PhysicalProfileCatalog.EngineDefinition combustion;
    private VehicleDrivetrainProfiles.ElectricProfile electric;
    private PhysicalDefinitions.Frame chassis;
    private PhysicalDefinitions.Wheel physicalWheel;
    private PhysicalDefinitions.Selection physicalSelection;
    private ProfileViews.Reference profileReference;
    private PayloadMassModel.Result payload;
    private double vehicleMassKg, wheelInertiaKgM2;
    private boolean profileAvailable, insidePaidStep, transportBudgetReady;
    private Optional<UUID> stepMotor = Optional.empty(), clientMotor = Optional.empty();
    private boolean clientMotorBound;
    private UUID clientMotorIncarnation, clientMotorWorld;
    private RuntimeMetadata018.Measured measured = RuntimeMetadata018.Measured.unavailable(RuntimeMetadata018.Kind.COMBUSTION);
    private PowertrainSimulation.State state;
    private EvDrivetrainStep.State electricState;
    private FuelPowertrainCoordinator fuelCoordinator;
    private ElectricPowertrainCoordinator electricCoordinator;
    private VehicleEnergyProviders.Kind ownerKind = VehicleEnergyProviders.Kind.BLOCKED;
    private DrivetrainProtocol.Snapshot snapshot;
    private float appliedClutch, load;
    private boolean cruiseRawPresent;
    private CruiseSpeedController.State cruiseControl = CruiseSpeedController.State.idle();
    private float cruiseRawClutch = 1;
    private double radius, lastWheelOmega;
    private long idleSince = Long.MIN_VALUE;
    private UUID lastGarageTransaction;
    private UUID lastDriver;
    private boolean legacyHandoff;
    private boolean profileCompatible = true;
    private EngineController.Reason reportedEngineReason=EngineController.Reason.NONE;
    private UUID clientMechanicalIncarnation;
    private boolean predictionPowerPermitted, regenerationSupported, coastRegenerationSupported;
    private double acceptedCoastRegenerationTorqueNm;
    private long lastSnapshotClientTick = Long.MIN_VALUE, predictionContextChangedAtTick = Long.MIN_VALUE;
    private AutomaticShiftController.Mode lastPredictionMode;
    private PowertrainSimulation.ControlMode lastPredictionControlMode;
    private DrivetrainProtocol.Identity lastPredictionIdentity;

    public VehicleDrivetrainRuntime(AutomobileEntity car) { this.car = Objects.requireNonNull(car); RuntimeProfileRepository018.observe(car, this); }

    /** Read-only except first selection; resource denial never returns the vehicle to legacy propulsion. */
    public boolean selected() {
        if (selected) return true;
        if (car.level().isClientSide() && car.isLocalInstanceAuthoritative() && VehicleDrivetrainPreferences.resolve(car).isEmpty()) return false;
        if (GarageTransferAccess.blocked(car) || !CoreDrivetrainBridge.maySimulateMechanics(car)
                && !(car.level().isClientSide() && CoreDrivetrainBridge.clientSnapshot(car).isPresent())) return false;
        var actual = car.garageComponents();
        if (actual == null || !actual.complete()) return false;
        engineId = actual.engine().unwrapKey().orElseThrow().identifier().toString();
        if (!car.level().isClientSide()) refreshServerProfile();
        // Missing definitions still select an unpowered authority; never fall through to legacy propulsion/debit.
        radius = profileAvailable ? radius : Math.max(.05, car.getWheels().model().radius() / 16.0);
        selected = true;
        lastDriver = car.getControllingPassenger() == null ? null : car.getControllingPassenger().getUUID();
        ignitionOn = false;
        state = PowertrainSimulation.State.stopped(controlMode(settings().orElse(DEFAULTS)));
        double body = car.drivetrainInitialBodyVelocity().dot(forward(car.getYRot())) * 20;
        if (!Double.isFinite(body)) body = 0;
        double wheel = car.automobileOnGround() ? body / radius : 0;
        state = withMechanics(state, new DrivetrainState(0, wheel, body, 0));
        electricState = new EvDrivetrainStep.State(wheel, body, EvDrivetrainStep.Range.N);
        return true;
    }

    public boolean active() { return selected(); }
    public Optional<DrivetrainProtocol.Snapshot> snapshot() {
        return car.level().isClientSide() ? CoreDrivetrainBridge.clientSnapshot(car).or(() -> Optional.ofNullable(snapshot))
                : Optional.ofNullable(snapshot);
    }
    /** Derived from this car's actual mechanics and accepted raw pedal; no second cruise state. */
    public boolean cruiseAvailable() {
        if (!selected) return true;
        if (!profileAvailable) return false;
        long tick = car.level().getGameTime();
        return !blocked && running() && gear() > 0 && cruiseRawPresent && cruiseRawClutch <= .05
                && (electric != null || state.manual().targetGear() > 0)
                && tick >= lastTick && tick - lastTick <= 1;
    }
    private void cancelCruise(io.github.foundationgames.automobility.automobile.driving.VehicleCruiseMotorLimit.MechanicalReason reason) {
        io.github.foundationgames.automobility.automobile.driving.VehicleCruiseMotorLimit.cancelForMechanics(car, reason);
    }
    public boolean running() {
        if (!selected) return false;
        return electric != null ? ignitionOn : state.engine().phase() == EngineController.Phase.RUNNING;
    }
    /** Server-validated existing Y action delegates to this one lifecycle owner. */
    public void requestIgnition(boolean start) {
        ignitionOn = start;
        automaticCranking = start;
        if (!start) { automaticCranking = false; cruiseControl = CruiseSpeedController.State.idle(); }
    }
    public void committedGarage(DrivetrainSettingsEnvelope.GarageReceipt receipt) {
        receipt.requireCommitFor(car.getUUID());
        if (receipt.transaction().equals(lastGarageTransaction)) return;
        lastGarageTransaction = receipt.transaction();
        // Called only after the real journal COMMIT. Quiet load/setComponents never enters here.
        profileAvailable = false; physicalSelection = null; profileReference = null; payload = null;
        clientMotorBound = false; clientMotorIncarnation = clientMotorWorld = null;
        selected = false; state = null; electricState = null; fuelCoordinator = null; electricCoordinator = null;
        cruiseControl = CruiseSpeedController.State.idle();
        identity = null; snapshot = null; ownerKind = VehicleEnergyProviders.Kind.BLOCKED;
        cruiseRawPresent = false; cruiseRawClutch = 1;
        lastTick = Long.MIN_VALUE; lastSnapshotSequence = lastReceivedSequence = 0;
        ignitionOn = automaticCranking = previousDriveDemand = false;
        predictionPowerPermitted = regenerationSupported = coastRegenerationSupported = false;
        acceptedCoastRegenerationTorqueNm = 0; lastSnapshotClientTick = Long.MIN_VALUE;
        predictionContextChangedAtTick = Long.MIN_VALUE; lastPredictionMode = null;
        lastPredictionControlMode = null; lastPredictionIdentity = null; receivedIdentity = null;
        clientMechanicalIncarnation = null; profileCompatible = true; legacyHandoff = false;
    }

    /** Consumes one authenticated raw sample at most once per actual game tick. */
    public double advance(float movementYaw, boolean grounded, Vec3 contactNormal, double grip, double boostFraction) {
        if (!selected()) { cruiseControl = CruiseSpeedController.State.idle(); return 0; }
        long tick = car.level().getGameTime();
        if (tick == lastTick) return speed();
        lastTick = tick;
        if (GarageTransferAccess.blocked(car)) { cruiseControl = CruiseSpeedController.State.idle(); return speed(); }
        boolean client = car.level().isClientSide();
        if (client && !car.isLocalInstanceAuthoritative()) { cruiseControl = CruiseSpeedController.State.idle(); reconcileSnapshot(); return speed(); }
        if (!client) {
            var driver = car.getControllingPassenger();
            if (driver != null && !driver.getUUID().equals(lastDriver)) {
                lastDriver = driver.getUUID(); previousDriveDemand = false; automaticCranking = false;
                cruiseControl = CruiseSpeedController.State.idle();
                legacyHandoff = !CoreDrivetrainBridge.maySimulateMechanics(car);
            }
            if (CoreDrivetrainBridge.maySimulateMechanics(car)) legacyHandoff = false;
            if (legacyHandoff) requestIgnition(false);
            if (legacyHandoff && Math.abs(speed()) < .02 && Math.abs(wheelOmega()) < .1
                    && (electric != null || Math.abs(state.mechanics().engineRadiansPerSecond()) < .1)) {
                // A different incompatible driver gets legacy authority only at a quiescent boundary.
                selected = false; fuelCoordinator = null; electricCoordinator = null; snapshot = null;
                ownerKind = VehicleEnergyProviders.Kind.BLOCKED;
                predictionPowerPermitted = regenerationSupported = coastRegenerationSupported = false;
                acceptedCoastRegenerationTorqueNm = 0;
                return 0;
            }
        }
        if (!client) refreshServerProfile();
        Raw raw = readRaw(client);
        cruiseRawPresent = raw.present; cruiseRawClutch = raw.clutch;
        if (client) reconcileSnapshot();
        if (!profileAvailable) {
            cruiseControl = CruiseSpeedController.State.idle();
            blocked = true; cruiseRawPresent = false; predictionPowerPermitted = false; acceptedCoastRegenerationTorqueNm = 0;
            measured = RuntimeMetadata018.Measured.unavailable(electric != null ? RuntimeMetadata018.Kind.ELECTRIC : RuntimeMetadata018.Kind.COMBUSTION, RuntimeMetadata018.Availability.UNKNOWN_PROFILE);
            return speed();
        }
        if (!client) {
            var currentIdentity = CoreDrivetrainBridge.serverIdentity(car).orElse(null);
            if (identity != null && currentIdentity != null && !identity.equals(currentIdentity)) {
                automaticCranking = false;
                cruiseControl = CruiseSpeedController.State.idle();
                predictionPowerPermitted = false; acceptedCoastRegenerationTorqueNm = 0;
                if (!identity.worldInstance().equals(currentIdentity.worldInstance()) || !identity.incarnation().equals(currentIdentity.incarnation())) {
                    fuelCoordinator = null; electricCoordinator = null; ownerKind = VehicleEnergyProviders.Kind.BLOCKED;
                    regenerationSupported = coastRegenerationSupported = false;
                }
            }
            identity = currentIdentity;
        }
        var chosen = settings();
        blocked = chosen.isEmpty() || !payload.poweredPredictionPermitted() || client && !profileCompatible;
        var preferences = chosen.orElse(DEFAULTS);
        var policy = VehicleDrivetrainPolicy.resolve(car);
        if (client && (lastPredictionMode != policy.mode() || lastPredictionControlMode != controlMode(preferences)
                || !Objects.equals(lastPredictionIdentity, identity))) {
            predictionContextChangedAtTick = tick; lastPredictionMode = policy.mode();
            lastPredictionControlMode = controlMode(preferences); lastPredictionIdentity = identity;
        }
        boolean controls = raw.present && CoreDrivetrainBridge.maySimulateMechanics(car)
                || legacyHandoff && car.hasFreshAcceptedClientInput();
        double physical = controls && car.rawBrakePedalHeld() ? 1 : 0;
        boolean automaticRange = !legacyHandoff && (electric != null || preferences.transmissionMode() == DrivetrainSettingsEnvelope.TransmissionMode.AUTOMATIC);
        var pedals = AutomaticPedalControls.resolve(controls, blocked, automaticRange, physical,
                raw.throttle, car.waitingForReverseRelease(), gear(), speed(), wheelOmega(),
                PowertrainSimulation.RangeRequest.valueOf(raw.range.name()), policy.handbrakeHeld());
        boolean reverse = pedals.reversePropulsion();
        double throttle = pedals.throttle();
        double physicalService = pedals.footBrake();
        double service = pedals.serviceBrake();
        var range = DrivetrainProtocol.RangeRequest.valueOf(pedals.range().name());
        if (controls && (raw.edges & DrivetrainProtocol.IGNITION_TOGGLE_EDGE) != 0) requestIgnition(!ignitionOn);
        if (controls && !blocked && raw.start) ignitionOn = true;
        boolean driveDemand = controls && (throttle > .01 || reverse);
        if (!ignitionOn && driveDemand && !previousDriveDemand && car.getVehicleEngineSettings().automaticStart()) requestIgnition(true);
        previousDriveDemand = driveDemand;
        if (car.getPassengers().isEmpty() && running()) {
            if (idleSince == Long.MIN_VALUE) idleSince = tick;
            int limit = car.getVehicleEngineSettings().idleShutdownSeconds();
            if (limit >= 0 && tick - idleSince >= limit * 20L) requestIgnition(false);
        } else idleSince = Long.MIN_VALUE;
        boolean clutchIntent = controls && !blocked && raw.clutch > .05;
        if (clutchIntent) cancelCruise(io.github.foundationgames.automobility.automobile.driving.VehicleCruiseMotorLimit.MechanicalReason.CLUTCH);
        if (!ignitionOn) cancelCruise(io.github.foundationgames.automobility.automobile.driving.VehicleCruiseMotorLimit.MechanicalReason.ENGINE_STOPPED);
        Vec3 normal = grounded && contactNormal.lengthSqr() > .1 ? contactNormal.normalize() : new Vec3(0, 1, 0);
        if (normal.y < 0) normal = normal.reverse();
        Vec3 direction = forward(movementYaw);
        double normalAlong = normal.dot(direction);
        double grade = grounded ? clamp(-normalAlong / Math.sqrt(normal.y * normal.y + normalAlong * normalAlong), -.95, .95) : 0;
        double normalLoad = grounded ? vehicleMassKg * 9.81 * Math.max(0, normal.y) : 0;
        double rolling = chassis.rollingResistanceCoefficient() * normalLoad;
        double drag = (-.5 * 1.225 * chassis.dragAreaSquareMetres() * speed() * Math.abs(speed()));
        double wet = car.level().isRainingAt(car.blockPosition()) || car.isInWater() ? physicalWheel.wetGripMultiplier() : 1;
        double friction = clamp(grip * physicalWheel.dryGripCoefficient() * wet, .01, 2);
        double boost = 1 + .18 * clamp(boostFraction, 0, 1);
        transportBudgetReady = RuntimeTransportBudget018.permits(physicalSelection, payload.knownSubtotalKg(),
                electric == null ? state.mechanics().engineRadiansPerSecond() : wheelOmega() * electric.fixedReductionRatio(),
                wheelOmega(), speed(), normalLoad, friction, grade, drag, boost,
                EngineController.Config.defaults().starterTorqueFractionOfPeak(), DT);
        if (!transportBudgetReady) predictionPowerPermitted = false;
        if (!client) bindOwner();
        if (!client && (legacyHandoff || blocked || ownerKind == VehicleEnergyProviders.Kind.BLOCKED)) predictionPowerPermitted = false;
        boolean permitted = transportBudgetReady && !legacyHandoff && !blocked && (client
                ? CoreDrivetrainBridge.maySimulateMechanics(car)
                    && DrivetrainPredictionGuard.power(snapshot, identity, engineId, profileHash, lastSnapshotClientTick, tick, 5)
                : ownerKind != VehicleEnergyProviders.Kind.BLOCKED);
        boolean cruiseEnabled = Double.isFinite(policy.approvedCruiseTargetMetresPerSecond()) && controls && !clutchIntent
                && range == DrivetrainProtocol.RangeRequest.KEEP && ignitionOn && running() && permitted && !reverse && physical == 0
                && !policy.handbrakeHeld() && !policy.unsafeForRecovery() && gear() > 0;
        var cruiseCommand = CruiseSpeedController.advance(CruiseSpeedController.Config.defaults(), cruiseControl, cruiseEnabled,
                cruiseEnabled ? Math.max(0, policy.approvedCruiseTargetMetresPerSecond()) : 0, speed(), DT);
        cruiseControl = cruiseCommand.state();
        if (cruiseEnabled) throttle = cruiseCommand.throttle();
        if (electric != null) advanceElectric(raw, range, throttle, physical, physicalService, service, controls, permitted,
                grounded, grade, normalLoad, friction, rolling, drag, boost, policy.unsafeForRecovery() || car.burningOut());
        else advanceCombustion(raw, preferences, policy, range, throttle, physical, service, controls, permitted,
                grounded, grade, normalLoad, friction, rolling, drag, boost);
        lastWheelOmega = wheelOmega();
        return speed();
    }

    private void advanceCombustion(Raw raw, DrivetrainSettingsEnvelope.Settings preferences, VehicleDrivetrainPolicy.Policy policy,
            DrivetrainProtocol.RangeRequest range, double throttle, double physical, double service, boolean controls, boolean permitted,
            boolean grounded, double grade, double normalLoad, double friction, double rolling, double drag, double boost) {
        double assist = preferences.assistanceStrength();
        var base = combustion.engine();
        var engine = new EngineProfile(scale(base.torqueCurve(), boost), base.inertiaKgMetresSquared(), base.idleRpm(), base.redlineRpm());
        var mechanics = DrivetrainParameters.forEngine(engine, wheelInertiaKgM2, vehicleMassKg,
                radius, combustion.clutchCapacityNewtonMetres(), combustion.transmission());
        var ec = EngineController.Config.defaults().withStallBehavior(preferences.stallEnabled(), assist > 0);
        var manual = new ManualShiftController.Config(.05, .04, .20, .05, .35, .75, .98,
                assist > 0, assist > 0, 2 * assist).withDownshiftProtection(preferences.downshiftProtection());
        var setup = new PowertrainSimulation.Setup(engine, mechanics, ec,
                new ClutchController.Config(preferences.clutchReleaseSeconds(), preferences.analogSlewSeconds()),
                AutomaticShiftController.Config.defaults(), manual, ThrottleController.Config.defaults(),
                new LaunchController.Config(1000 * assist, Math.max(.01, 4 * assist),
                        .25 + .35 * (1 - assist), assist > 0, .8 * assist, .2), .005, chassis.parkingHoldingTorqueNm())
                .withManualClutchAssistance(assist);
        var shift = blocked ? ManualShiftController.Request.NONE : (raw.edges & DrivetrainProtocol.SHIFT_UP_EDGE) != 0 ? ManualShiftController.Request.UP
                : (raw.edges & DrivetrainProtocol.SHIFT_DOWN_EDGE) != 0 ? ManualShiftController.Request.DOWN : ManualShiftController.Request.NONE;
        var input = new PowertrainSimulation.DriverInput(controlMode(preferences), policy.mode(),
                new ThrottleController.Input(throttle != 0 && throttle != 1, throttle),
                new ClutchController.Input(raw.clutchMode, controls && !blocked ? raw.clutch : 1, 1), shift,
                PowertrainSimulation.RangeRequest.valueOf(range.name()), physical, service, ignitionOn,
                controls && (raw.start || automaticCranking), controls, permitted, permitted);
        var environment = new PowertrainSimulation.Environment(grounded, grade, normalLoad, friction, rolling, drag,
                chassis.serviceBrakeTorqueNm());
        PowertrainSimulation.Result result;
        RuntimeMetadata018.Availability availability = ownerKind == VehicleEnergyProviders.Kind.BLOCKED ? RuntimeMetadata018.Availability.OWNER_UNAVAILABLE : RuntimeMetadata018.Availability.DENIED;
        if (!car.level().isClientSide() && ownerKind == VehicleEnergyProviders.Kind.MANAGED && fuelCoordinator != null && transportBudgetReady) {
            FuelPowertrainCoordinator.Result exchange;
            insidePaidStep = true;
            try { exchange = fuelCoordinator.advance(setup, state, input, environment, DT, lastTick, UUID.randomUUID(), policy.unsafeForRecovery()); }
            finally { insidePaidStep = false; }
            availability = switch (exchange.status()) { case PAID -> RuntimeMetadata018.Availability.PAID; case COASTED_OWNER_UNCERTAIN -> RuntimeMetadata018.Availability.OWNER_UNAVAILABLE; default -> RuntimeMetadata018.Availability.DENIED; };
            result = exchange.mechanics();
            predictionPowerPermitted = DrivetrainPredictionGuard.afterOwnerStep(predictionPowerPermitted,
                    exchange.status() == FuelPowertrainCoordinator.Status.PAID,
                    result.activity().meanCombustionTorqueNewtonMetres() > 0 || result.activity().meanStarterTorqueNewtonMetres() > 0,
                    !blocked && !legacyHandoff);
        } else result = PowertrainSimulation.advance(setup, state, input, environment, DT);
        if (!car.level().isClientSide() && ownerKind == VehicleEnergyProviders.Kind.UNMETERED) predictionPowerPermitted = transportBudgetReady && !blocked && !legacyHandoff;
        if (!car.level().isClientSide() && ownerKind == VehicleEnergyProviders.Kind.UNMETERED && permitted) availability = RuntimeMetadata018.Availability.UNMETERED;
        measured = availability != RuntimeMetadata018.Availability.PAID && availability != RuntimeMetadata018.Availability.UNMETERED ? RuntimeMetadata018.Measured.unavailable(RuntimeMetadata018.Kind.COMBUSTION, availability)
                : new RuntimeMetadata018.Measured(availability, RuntimeMetadata018.Kind.COMBUSTION,
                    result.activity().meanCombustionTorqueNewtonMetres(), 0, 0, result.energy().combustionWorkJoules() / DT, 0);
        state = result.state(); appliedClutch = (float) result.appliedClutchEngagement();
        if(!car.level().isClientSide()&&state.engine().reason()!=reportedEngineReason){
            reportedEngineReason=state.engine().reason();
            if(reportedEngineReason!=EngineController.Reason.NONE&&reportedEngineReason!=EngineController.Reason.IGNITION_OFF)
                org.slf4j.LoggerFactory.getLogger("AutomobilityDrivetrain").info("Engine {}: phase={}, reason={}, rpm={}, energy={}, controls={}, permitted={}",
                        car.getUUID(),state.engine().phase(),reportedEngineReason,Units.radiansPerSecondToRpm(state.mechanics().engineRadiansPerSecond()),availability,controls,permitted);
        }
        if (range == DrivetrainProtocol.RangeRequest.NEUTRAL && result.refusal() == PowertrainSimulation.Refusal.NONE
                && state.manual().targetGear() == 0)
            cancelCruise(io.github.foundationgames.automobility.automobile.driving.VehicleCruiseMotorLimit.MechanicalReason.NEUTRAL);
        if (!running()) cancelCruise(io.github.foundationgames.automobility.automobile.driving.VehicleCruiseMotorLimit.MechanicalReason.ENGINE_STOPPED);
        load = (float) clamp(result.activity().meanCombustionLoadFraction(), 0, 1);
        if (state.engine().phase() != EngineController.Phase.STARTING && state.engine().phase() != EngineController.Phase.OFF) automaticCranking = false;
        if (state.engine().reason() != EngineController.Reason.NONE) automaticCranking = false;
        if (!controls) automaticCranking = false;
    }

    private void advanceElectric(Raw raw, DrivetrainProtocol.RangeRequest range, double throttle, double physical, double physicalService, double service,
            boolean controls, boolean permitted, boolean grounded, double grade, double normalLoad, double friction,
            double rolling, double drag, double boost, boolean hazard) {
        var motor = electric.motor();
        var boosted = new EvMotorProfile(scale(motor.motoringNewtonMetres(), boost), motor.regenerationNewtonMetres(),
                motor.rotorInertiaKgMetresSquared(), motor.maximumRpm());
        var parameters = new EvDrivetrainStep.Parameters(boosted, electric.fixedReductionRatio(),
                wheelInertiaKgM2, vehicleMassKg, radius,
                electric.parkAxleHoldingTorqueNm(), raw.range == DrivetrainProtocol.RangeRequest.KEEP
                        ? new EvDrivetrainStep.RangePolicy(.15, .75, .2, false, false) : EvDrivetrainStep.RangePolicy.defaults());
        var requested = switch (range) { case KEEP -> EvDrivetrainStep.RangeRequest.KEEP; case DRIVE -> EvDrivetrainStep.RangeRequest.D;
            case NEUTRAL -> EvDrivetrainStep.RangeRequest.N; case REVERSE -> EvDrivetrainStep.RangeRequest.R; case PARK -> EvDrivetrainStep.RangeRequest.P; };
        // Compatibility SÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢R is a propulsion intent, not a request for brake energy recovery.
        boolean client = car.level().isClientSide();
        double curve = motor.maximumRegenerationTorque(electricState.motorRpm(parameters));
        boolean coast = ignitionOn && throttle == 0 && grounded && physical == 0 && service == 0 && !hazard
                && electric.coastRegenerationCurveFraction() > 0;
        double regen;
        if (client) {
            // Physical foot braking predicts the exact total axle friction capacity, not friction + recovery twice.
            regen = coast && CoreDrivetrainBridge.maySimulateMechanics(car) ? DrivetrainPredictionGuard.coastTorque(snapshot, identity, engineId, profileHash,
                    lastSnapshotClientTick, lastTick, 5, predictionContextChangedAtTick, ignitionOn, throttle,
                    grounded, physical > 0, service > 0, hazard, curve * electric.coastRegenerationCurveFraction()) : 0;
            coast = regen > 0;
        } else {
            coast &= coastRegenerationSupported;
            regen = permitted && regenerationSupported && ignitionOn && grounded && !hazard
                    ? physicalService > 0 ? curve : coast ? curve * electric.coastRegenerationCurveFraction() : 0 : 0;
        }
        var control = new EvDrivetrainStep.Control(requested, controls ? throttle : 0, regen,
                boosted.statistics().torqueNewtonMetres().value(), motor.statistics().torqueNewtonMetres().value(),
                service * chassis.serviceBrakeTorqueNm(), physical, physical > 0,
                permitted && ignitionOn, client ? regen > 0 : permitted && regenerationSupported, coast, hazard);
        var environment = new EvDrivetrainStep.Environment(grounded, grade, normalLoad, friction, rolling, drag, 0);
        EvDrivetrainStep.Result result;
        RuntimeMetadata018.Availability availability = ownerKind == VehicleEnergyProviders.Kind.BLOCKED ? RuntimeMetadata018.Availability.OWNER_UNAVAILABLE : RuntimeMetadata018.Availability.DENIED;
        acceptedCoastRegenerationTorqueNm = 0;
        if (!client && ownerKind == VehicleEnergyProviders.Kind.MANAGED && electricCoordinator != null && transportBudgetReady) {
            ElectricPowertrainCoordinator.Result exchange;
            insidePaidStep = true;
            try { exchange = electricCoordinator.advance(parameters, electricState, control, environment, DT, lastTick, UUID.randomUUID()); }
            finally { insidePaidStep = false; }
            result = exchange.mechanics();
            boolean paid = exchange.status() == ElectricPowertrainCoordinator.Status.PAID || exchange.status() == ElectricPowertrainCoordinator.Status.PAID_REGEN_REDUCED;
            predictionPowerPermitted = DrivetrainPredictionGuard.afterOwnerStep(predictionPowerPermitted, paid,
                    Math.abs(result.commandedDriveMotorTorqueNewtonMetres()) > 0 || result.positiveDriveShaftWorkJoules() > 0,
                    !blocked && !legacyHandoff);
            if (!paid && exchange.status() == ElectricPowertrainCoordinator.Status.COASTED_OWNER_UNCERTAIN) availability = RuntimeMetadata018.Availability.OWNER_UNAVAILABLE;
            if (paid) {
                availability = RuntimeMetadata018.Availability.PAID;
                if (coast && !result.regenerationCutAtZero()) acceptedCoastRegenerationTorqueNm = Math.abs(result.meanRegenerationMotorTorqueNewtonMetres());
            }
        } else result = EvDrivetrainStep.advance(parameters, electricState, new EvDrivetrainStep.Input(control, environment), DT);
        if (!client && ownerKind == VehicleEnergyProviders.Kind.UNMETERED && permitted) availability = RuntimeMetadata018.Availability.UNMETERED;
        measured = availability != RuntimeMetadata018.Availability.PAID && availability != RuntimeMetadata018.Availability.UNMETERED ? RuntimeMetadata018.Measured.unavailable(RuntimeMetadata018.Kind.ELECTRIC, availability)
                : new RuntimeMetadata018.Measured(availability, RuntimeMetadata018.Kind.ELECTRIC, 0,
                    result.commandedDriveMotorTorqueNewtonMetres(), result.meanRegenerationMotorTorqueNewtonMetres(), 0, result.motorShaftWorkJoules() / DT);
        electricState = result.state(); appliedClutch = electricState.range() == EvDrivetrainStep.Range.N || electricState.range() == EvDrivetrainStep.Range.P ? 0 : 1;
        if (requested == EvDrivetrainStep.RangeRequest.N && result.refusal() == EvDrivetrainStep.Refusal.NONE
                && electricState.range() == EvDrivetrainStep.Range.N)
            cancelCruise(io.github.foundationgames.automobility.automobile.driving.VehicleCruiseMotorLimit.MechanicalReason.NEUTRAL);
        double peak = Math.max(motor.statistics().torqueNewtonMetres().value(), motor.regenerationNewtonMetres().statistics().torqueNewtonMetres().value());
        load = (float) (peak > 0 ? clamp(result.motorTorqueRmsNewtonMetres() / peak, 0, 1) : 0);
    }

    private void bindOwner() {
        if (identity == null || fuelCoordinator != null || electricCoordinator != null) return;
        var source = electric == null ? EnergyDemand.Source.FUEL : EnergyDemand.Source.ELECTRIC;
        var energyIdentity = new EnergyDemand.Identity(identity.worldInstance(), identity.incarnation(), car.getUUID(),
                electric == null ? Optional.empty() : VehicleEnergyProviders.physicalMotorId(car));
        var resolution = VehicleEnergyProviders.resolve(car, energyIdentity, source);
        ownerKind = resolution.kind();
        if (ownerKind != VehicleEnergyProviders.Kind.MANAGED) return;
        var binding = resolution.binding().orElseThrow();
        regenerationSupported = binding.capability().regenerationSupported();
        coastRegenerationSupported = binding.capability().coastRegenerationSupported();
        var ledger = new EnergyTransactionGuard.RuntimeLedger(energyIdentity.worldSessionId(), energyIdentity.runtimeId());
        var guard = ledger.bind(energyIdentity, binding);
        if (electric == null) fuelCoordinator = new FuelPowertrainCoordinator(energyIdentity, guard, binding::price);
        else electricCoordinator = new ElectricPowertrainCoordinator(energyIdentity, guard, binding::price);
    }

    private Raw readRaw(boolean client) {
        if (!CoreDrivetrainBridge.maySimulateMechanics(car)) return Raw.absent();
        if (client) {
            var local = CoreDrivetrainBridge.consumeLocalInput(car);
            if (local.isEmpty() || !local.get().controlsAvailable()) return Raw.absent();
            var i = local.get().intent(); identity = i.identity();
            return new Raw(i.throttle(), i.clutchDepression(), i.clutchMode(), i.keyEdges(), i.rangeRequest(), i.startHeld(), true);
        }
        var input = CoreDrivetrainBridge.consumeInput(car);
        if (input.isEmpty() || !input.get().fresh()) return Raw.absent();
        var i = input.get();
        return new Raw(i.held().throttle(), i.held().clutchDepression(), i.held().clutchMode(), i.keyEdges(), i.rangeRequest(), i.held().startHeld(), true);
    }

    private void reconcileSnapshot() {
        var incoming = CoreDrivetrainBridge.clientSnapshot(car);
        if (incoming.isEmpty()) { profileCompatible = false; profileAvailable = false; return; }
        var s = incoming.get();
        if (!s.identity().incarnation().equals(clientMechanicalIncarnation)) {
            state = PowertrainSimulation.State.stopped(controlMode(settings().orElse(DEFAULTS)));
            electricState = new EvDrivetrainStep.State(0, 0, EvDrivetrainStep.Range.N);
            clientMechanicalIncarnation = s.identity().incarnation();
        }
        boolean previouslyAvailable = profileAvailable;
        var previousReference = profileReference;
        refreshClientProfile(s);
        boolean justResolved = profileAvailable && (!previouslyAvailable || !Objects.equals(previousReference, profileReference));
        profileCompatible = profileAvailable && matchesProfile(s);
        if (!s.identity().equals(receivedIdentity)) { receivedIdentity = s.identity(); lastReceivedSequence = 0; }
        if (s.sequence() <= lastReceivedSequence && !justResolved) return;
        if (s.sequence() > lastReceivedSequence) lastSnapshotClientTick = car.level().getGameTime();
        lastReceivedSequence = s.sequence(); snapshot = s;
        ignitionOn = s.enginePhase() == EngineController.Phase.RUNNING || s.enginePhase() == EngineController.Phase.STARTING;
        if (electric != null) electricState = new EvDrivetrainStep.State(s.drivenWheelOmega(), s.speedMetresPerSecond(),
                s.parkingEngaged() ? EvDrivetrainStep.Range.P : s.gear() < 0 ? EvDrivetrainStep.Range.R : s.gear() == 0 ? EvDrivetrainStep.Range.N : EvDrivetrainStep.Range.D);
        else {
            var mechanical = new DrivetrainState(s.engineOmega(), s.drivenWheelOmega(), s.speedMetresPerSecond(), s.gear());
            boolean manualPhase = s.controlMode() != PowertrainSimulation.ControlMode.AUTOMATIC || s.gear() <= 0 || s.targetGear() <= 0;
            var auto = manualPhase ? AutomaticShiftController.State.drive(s.gear(), state.automatic().latchedMode())
                    : new AutomaticShiftController.State(s.gear(), s.targetGear(), s.phase(), s.phaseRemainingSeconds(), 0, state.automatic().latchedMode());
            var manual = manualPhase ? new ManualShiftController.State(s.gear(), s.targetGear(), ManualShiftController.Phase.valueOf(s.phase().name()), s.phaseRemainingSeconds(), ManualShiftController.Reason.NONE)
                    : ManualShiftController.State.drive(s.gear());
            var engine = switch (s.enginePhase()) { case OFF -> EngineController.State.off(); case STALLED -> EngineController.State.stalled();
                case RUNNING -> EngineController.State.running(); case STARTING -> new EngineController.State(EngineController.Phase.STARTING, 0, 0, false, false, EngineController.Reason.NONE); };
            state = new PowertrainSimulation.State(mechanical, engine, new ClutchController.State(s.clutchEngagement()),
                    state.throttle(), state.launch(), auto, manual, s.controlMode(),
                    manualPhase && s.controlMode() == PowertrainSimulation.ControlMode.AUTOMATIC && s.phase() != AutomaticShiftController.Phase.DRIVE,
                    s.parkingEngaged(), 0);
        }
    }

    private void publish(long tick) {
        if (identity == null || !profileAvailable || profileReference == null || payload == null) return;
        var actual = RuntimeProfileRepository018.server(car).orElse(null);
        if (actual == null || !actual.authority().sha256().equals(profileReference.sha256())
                || !actual.parts().frameId().equals(physicalSelection.frameId()) || !actual.parts().wheelId().equals(physicalSelection.wheelId())
                || !actual.parts().engineId().equals(engineId) || !actual.payload().equals(payload)
                || !stepMotor.equals(electric == null ? Optional.empty() : VehicleEnergyProviders.physicalMotorId(car))) return;
        if (!RuntimeTransportBudget018.representable(electric == null ? state.mechanics().engineRadiansPerSecond() : wheelOmega() * electric.fixedReductionRatio(), wheelOmega(), speed())) return;
        int current = gear(), target = current;
        var phase = AutomaticShiftController.Phase.DRIVE; double remaining = 0;
        if (electric == null) {
            if (state.mode() == PowertrainSimulation.ControlMode.AUTOMATIC && !state.rangeShiftActive()) {
                target = state.automatic().targetGear(); phase = state.automatic().phase(); remaining = state.automatic().phaseRemainingSeconds();
            } else { target = state.manual().targetGear(); phase = AutomaticShiftController.Phase.valueOf(state.manual().phase().name()); remaining = state.manual().phaseRemainingSeconds(); }
        }
        snapshot = new DrivetrainProtocol.Snapshot(identity, ++lastSnapshotSequence, tick, electric == null ? state.mechanics().engineRadiansPerSecond()
                : wheelOmega() * electric.fixedReductionRatio(), wheelOmega(), speed(), current, target, phase, remaining,
                appliedClutch, load, engineId, profileHash, electric == null ? state.engine().phase() : running() ? EngineController.Phase.RUNNING : EngineController.Phase.OFF,
                electric == null ? state.mode() : PowertrainSimulation.ControlMode.AUTOMATIC,
                electric == null ? state.parkingLocked() : electricState.range() == EvDrivetrainStep.Range.P,
                predictionPowerPermitted, electric == null ? 0 : acceptedCoastRegenerationTorqueNm);
        var approval = new ProfilePayloadApproval(identity, snapshot.sequence(), profileReference, physicalSelection.frameId(), physicalSelection.wheelId(), engineId, stepMotor, payload);
        var metadata = new RuntimeMetadata018(approval, tick, measured);
        if (!metadata.pairs(snapshot)) throw new IllegalStateException("mechanics/metadata publication pair");
        CoreDrivetrainBridge.publishServerSnapshot(car, snapshot, metadata);
    }

    /** Collision changes body momentum only; a collision never invents crank or wheel RPM. */
    public void reconcileBody(Vec3 actualBlocksPerTick, float yaw) {
        if (!selected || lastTick == Long.MIN_VALUE) return;
        double actual = actualBlocksPerTick.dot(forward(yaw)) * 20;
        if (!Double.isFinite(actual)) return;
        if (electric != null) electricState = new EvDrivetrainStep.State(electricState.wheelRadiansPerSecond(), actual, electricState.range());
        else state = withMechanics(state, new DrivetrainState(state.mechanics().engineRadiansPerSecond(), state.mechanics().wheelRadiansPerSecond(), actual, state.mechanics().gear()));
    }
    public void finishTick(Vec3 actualBlocksPerTick, float yaw) {
        if (!car.level().isClientSide() && selected && lastTick == car.level().getGameTime()) publish(lastTick);
    }
    public void multiplyBodySpeed(double factor) {
        if (!selected) return;
        if (electric != null) electricState = new EvDrivetrainStep.State(electricState.wheelRadiansPerSecond(), electricState.speedMetresPerSecond() * factor, electricState.range());
        else state = withMechanics(state, new DrivetrainState(state.mechanics().engineRadiansPerSecond(), state.mechanics().wheelRadiansPerSecond(), state.mechanics().speedMetresPerSecond() * factor, state.mechanics().gear()));
    }
    public void addBodyImpulse(Vec3 impulseBlocksPerTick, float yaw) {
        if (!selected) return;
        double next = speed() + impulseBlocksPerTick.dot(forward(yaw)) * 20;
        if (!Double.isFinite(next)) return;
        if (electric != null) electricState = new EvDrivetrainStep.State(electricState.wheelRadiansPerSecond(), next, electricState.range());
        else state = withMechanics(state, new DrivetrainState(state.mechanics().engineRadiansPerSecond(), state.mechanics().wheelRadiansPerSecond(), next, state.mechanics().gear()));
    }
    public int ignitionState() {
        if (electric != null) return ignitionOn ? 2 : 0;
        return state == null ? 0 : switch (state.engine().phase()) { case OFF, STALLED -> 0; case STARTING -> 1; case RUNNING -> 2; };
    }
    /** HUD, controls and compatibility callers observe one lifecycle owner. No lazy selection here. */
    public java.util.OptionalInt synchronizedIgnitionState() {
        if(!selected)return java.util.OptionalInt.empty();
        if(!car.level().isClientSide())return java.util.OptionalInt.of(ignitionState());
        var accepted=CoreDrivetrainBridge.clientSnapshot(car);
        return accepted.isEmpty()?java.util.OptionalInt.empty():java.util.OptionalInt.of(switch(accepted.get().enginePhase()){
            case RUNNING->2;case STARTING->1;default->0;
        });
    }
    public double speed() { return electric != null ? electricState.speedMetresPerSecond() : state == null ? 0 : state.mechanics().speedMetresPerSecond(); }
    public double wheelOmega() { return electric != null ? electricState.wheelRadiansPerSecond() : state == null ? 0 : state.mechanics().wheelRadiansPerSecond(); }
    public double wheelDegreesThisTick() { return lastWheelOmega * DT * 180 / Math.PI; }
    private int gear() { return electric != null ? electricState.range() == EvDrivetrainStep.Range.R ? -1
            : electricState.range() == EvDrivetrainStep.Range.D ? 1 : 0 : state.mechanics().gear(); }
    private Optional<DrivetrainSettingsEnvelope.Settings> settings() {
        return VehicleDrivetrainPreferences.resolve(car);
    }
    private static PowertrainSimulation.ControlMode controlMode(DrivetrainSettingsEnvelope.Settings s) {
        return s.transmissionMode() == DrivetrainSettingsEnvelope.TransmissionMode.AUTOMATIC ? PowertrainSimulation.ControlMode.AUTOMATIC
                : s.assistanceStrength() > 0 ? PowertrainSimulation.ControlMode.MANUAL_ASSISTED : PowertrainSimulation.ControlMode.MANUAL_CLUTCH;
    }
    private static PowertrainSimulation.State withMechanics(PowertrainSimulation.State s, DrivetrainState m) {
        return new PowertrainSimulation.State(m, s.engine(), s.pedal(), s.throttle(), s.launch(), s.automatic(), s.manual(), s.mode(), s.rangeShiftActive(), s.parkingLocked(), s.lastTransmittedCrankTorqueNewtonMetres());
    }
    private boolean matchesProfile(DrivetrainProtocol.Snapshot snapshot) {
        return profileAvailable && snapshot.identity().vehicle().equals(car.getUUID()) && snapshot.profileId().equals(engineId) && snapshot.profileSha256().equals(profileHash);
    }
    private void applyProfile(PhysicalDefinitions.Selection selection, ProfileViews.Reference reference, PayloadMassModel.Result mass) {
        physicalSelection = selection; profileReference = reference; payload = mass; chassis = selection.frame(); physicalWheel = selection.wheel();
        engineId = selection.engineId(); var definition = selection.engine();
        combustion = definition.combustion() == null ? null : new PhysicalProfileCatalog.EngineDefinition(engineId, definition.combustion(), definition.transmission(), definition.clutchCapacityNm());
        electric = definition.electric() == null ? null : new VehicleDrivetrainProfiles.ElectricProfile(definition.electric(), definition.fixedReductionRatio(), definition.parkAxleHoldingTorqueNm(), definition.coastCurveFraction());
        vehicleMassKg = selection.emptyMassKg() + mass.knownSubtotalKg(); wheelInertiaKgM2 = selection.equivalentWheelInertiaKgMetresSquared();
        radius = selection.drivenRadiusMetres(); profileHash = reference.sha256(); profileAvailable = true;
    }
    private void refreshServerProfile() {
        var bound = RuntimeProfileRepository018.server(car).orElse(null);
        if (bound == null) { profileAvailable = false; return; }
        var next = bound.parts();
        var nextMotor = next.engine().electric() == null ? Optional.<UUID>empty() : VehicleEnergyProviders.physicalMotorId(car);
        if (selected && physicalSelection != null && (!physicalSelection.frameId().equals(next.frameId())
                || !physicalSelection.wheelId().equals(next.wheelId()) || !physicalSelection.engineId().equals(next.engineId()) || !stepMotor.equals(nextMotor))) {
            profileAvailable = false; return; // only the trusted garage commit may rotate actual mechanical parts/motor identity
        }
        var a = bound.authority(); applyProfile(next, new ProfileViews.Reference(a.worldSession(), a.epoch(), a.sha256()), bound.payload());
        stepMotor = nextMotor;
    }
    private void refreshClientProfile(DrivetrainProtocol.Snapshot core) {
        profileAvailable = false;
        var sidecar = CoreDrivetrainBridge.clientMetadata(car).orElse(null); var actual = car.garageComponents();
        if (sidecar == null || !sidecar.pairs(core) || actual == null || !actual.complete()) return;
        var approval = sidecar.approval();
        if (!core.identity().incarnation().equals(clientMotorIncarnation) || !core.identity().worldInstance().equals(clientMotorWorld)) { clientMotorIncarnation = core.identity().incarnation(); clientMotorWorld = core.identity().worldInstance(); clientMotorBound = false; }
        if (!clientMotorBound) { clientMotor = approval.physicalMotor(); clientMotorBound = true; }
        if (!clientMotor.equals(approval.physicalMotor())) return;
        String frame = actual.frame().unwrapKey().orElseThrow().identifier().toString(), wheel = actual.wheel().unwrapKey().orElseThrow().identifier().toString(), engine = actual.engine().unwrapKey().orElseThrow().identifier().toString();
        var selection = CoreDrivetrainBridge.clientProfile(car, approval, frame, wheel, engine, clientMotor).orElse(null);
        if (selection == null) return; applyProfile(selection, approval.definition(), approval.payload()); measured = sidecar.measured();
    }
    public ProfileReloadGate.Boundary profileReloadBoundary() {
        boolean stationary = !selected || speed() == 0;
        boolean quiescent = !selected || !running() && !ignitionOn && wheelOmega() == 0 && gear() == 0
                && state.mechanics().engineRadiansPerSecond() == 0 && state.manual().phase() == ManualShiftController.Phase.DRIVE
                && state.automatic().phase() == AutomaticShiftController.Phase.DRIVE;
        return new ProfileReloadGate.Boundary(car.getUUID(), stepMotor.orElse(null), stationary, quiescent, GarageTransferAccess.blocked(car), insidePaidStep);
    }
    private static TorqueCurve scale(TorqueCurve curve, double factor) { return factor == 1 ? curve : new TorqueCurve(curve.points().stream().map(p -> new TorqueCurve.Point(p.rpm(), p.torqueNewtonMetres() * factor)).toList()); }
    private static Vec3 forward(float yaw) { double angle = Math.toRadians(-yaw); return new Vec3(Math.sin(angle), 0, Math.cos(angle)); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private record Raw(float throttle, float clutch, ClutchController.PedalMode clutchMode, int edges, DrivetrainProtocol.RangeRequest range, boolean start, boolean present) {
        static Raw absent() { return new Raw(0, 1, ClutchController.PedalMode.DIGITAL, 0, DrivetrainProtocol.RangeRequest.KEEP, false, false); }
    }
}
