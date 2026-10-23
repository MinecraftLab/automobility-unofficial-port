package io.github.foundationgames.automobility.entity;

import io.github.foundationgames.automobility.automobile.AutomobileEngine;
import io.github.foundationgames.automobility.automobile.AutomobileFrame;
import io.github.foundationgames.automobility.automobile.AutomobileWheel;
import io.github.foundationgames.automobility.item.AutomobilityItems;
import io.github.foundationgames.automobility.item.AutomobileItem;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Server-side service policy. Replaces parts in place, never serializes/recreates the car. */
public final class GarageService {
    private GarageService() {}

    // A platform must explicitly prove the absence of unsupported energy integrations.
    private static boolean engineIntegrationPresent = true;

    public static void setEngineIntegrationPresent(boolean present) {
        engineIntegrationPresent = present;
    }

    public enum Status {
        READY, INSERT_PART, TAKE_OUTPUT, PARK, INVALID_PART, SAME_PART,
        MISSING_PARTS, INTEGRATION, STALE, PROTECTED, NO_VEHICLE, STORED, CATALOG,
        ASSEMBLER_REQUIRED, BLOCKED, UNLOADED, DUPLICATE, STORAGE_ERROR, GARAGE_FULL,
        RECOVERY_REQUIRED, DEPLOYED, ASSEMBLED, CLIENT_REQUIRED,
        NO_CHANGES, INVENTORY_FULL, PENDING_CHANGES, APPLIED, CANCELLED,
        OCCUPIED, MOVING, OUT_OF_RANGE, ENGINE_RUNNING
    }

    public record Components(Holder<AutomobileFrame> frame, Holder<AutomobileWheel> wheel,
                             Holder<AutomobileEngine> engine) {
        public boolean complete() {
            return frame.unwrapKey().isPresent() && wheel.unwrapKey().isPresent() && engine.unwrapKey().isPresent()
                    && !frame.value().isEmpty() && !wheel.value().isEmpty() && !engine.value().isEmpty();
        }

        public ItemStack frameStack() {
            return frame.unwrapKey().map(AutomobilityItems.AUTOMOBILE_FRAME.require()::createStack).orElse(ItemStack.EMPTY);
        }

        public ItemStack wheelStack() {
            return wheel.unwrapKey().map(AutomobilityItems.AUTOMOBILE_WHEEL.require()::createStack).orElse(ItemStack.EMPTY);
        }

        public ItemStack engineStack() {
            return engine.unwrapKey().map(AutomobilityItems.AUTOMOBILE_ENGINE.require()::createStack).orElse(ItemStack.EMPTY);
        }
    }

    public record Plan(Status status, Components replacement, ItemStack returnedPart, int requiredCount) {
        static Plan denied(Status status, int required) {
            return new Plan(status, null, ItemStack.EMPTY, required);
        }
    }

    public static boolean accepts(ItemStack stack) {
        return stack.is(AutomobilityItems.AUTOMOBILE_ENGINE.require()) || stack.is(AutomobilityItems.AUTOMOBILE_WHEEL.require());
    }

    /** A presentation label only: never rename an entity or change its saved data. */
    public static Component displayName(AutomobileEntity car) {
        var custom = car.getCustomName();
        if (custom != null) return custom.copy();
        var parts = car.garageComponents();
        if (parts == null) return car.getDisplayName();
        var frame = parts.frame().unwrapKey();
        if (frame.isEmpty()) return car.getDisplayName();
        for (var prefab : AutomobileItem.PREFABS) {
            // An engine or wheel upgrade does not turn the vehicle into a different body type.
            if (prefab.frame().equals(frame.get()) && prefab.prefabName().isPresent())
                return prefab.asStack().getHoverName();
        }
        var id = frame.get().identifier();
        return Component.translatableWithFallback("frame." + id.getNamespace() + "." + id.getPath(),
                car.getDisplayName().getString());
    }

    /** Unknown addon energy identities are not serialized or recreated by the garage. */
    public static boolean supportsStorage(Components parts) {
        return !engineIntegrationPresent && parts != null && parts.complete() && plainEngine(parts.engine());
    }

    public record Batch(Status status, Components replacement, List<ItemStack> debits, List<ItemStack> credits) {
        public Batch { debits = copy(debits); credits = copy(credits); }
        @Override public List<ItemStack> debits() { return copy(debits); }
        @Override public List<ItemStack> credits() { return copy(credits); }
        private static List<ItemStack> copy(List<ItemStack> stacks) { return stacks.stream().map(ItemStack::copy).toList(); }
    }

    /** Both virtual selections resolve together through the same canonical component registry. */
    public static Batch batch(Components current, ItemStack selectedEngine, ItemStack selectedWheel,
                              HolderLookup.Provider registries, boolean assembly) {
        if (current == null || !current.complete()) return new Batch(Status.INVALID_PART, null, List.of(), List.of());
        var engine = current.engine(); var wheel = current.wheel();
        if (!selectedEngine.isEmpty()) {
            var candidate = AutomobilityItems.AUTOMOBILE_ENGINE.require().lookupComponent(selectedEngine, registries);
            var key = candidate.unwrapKey();
            if (key.isEmpty() || candidate.value().isEmpty() || selectedEngine.getCount() != 1
                    || !ItemStack.isSameItemSameComponents(selectedEngine, AutomobilityItems.AUTOMOBILE_ENGINE.require().createStack(key.get())))
                return new Batch(Status.INVALID_PART, null, List.of(), List.of());
            engine = candidate;
        }
        int wheels = current.frame().value().model().wheelBase().wheelCount();
        if (!selectedWheel.isEmpty()) {
            var candidate = AutomobilityItems.AUTOMOBILE_WHEEL.require().lookupComponent(selectedWheel, registries);
            var key = candidate.unwrapKey();
            if (key.isEmpty() || candidate.value().isEmpty() || selectedWheel.getCount() != wheels
                    || wheels <= 0 || wheels > selectedWheel.getMaxStackSize()
                    || !ItemStack.isSameItemSameComponents(selectedWheel, AutomobilityItems.AUTOMOBILE_WHEEL.require().createStack(key.get())))
                return new Batch(Status.INVALID_PART, null, List.of(), List.of());
            wheel = candidate;
        }
        boolean engineChanged = !engine.equals(current.engine()), wheelChanged = !wheel.equals(current.wheel());
        if (!assembly && engineChanged && (engineIntegrationPresent || !plainEngine(current.engine()) || !plainEngine(engine)))
            return new Batch(Status.INTEGRATION, null, List.of(), List.of());
        var parts = new Components(current.frame(), wheel, engine);
        if (!assembly && !engineChanged && !wheelChanged) return new Batch(Status.NO_CHANGES, current, List.of(), List.of());
        var debits = new ArrayList<ItemStack>(); var credits = new ArrayList<ItemStack>();
        if (assembly) debits.add(parts.frameStack());
        if (assembly || engineChanged) debits.add(parts.engineStack());
        if (assembly || wheelChanged) { var stack = parts.wheelStack(); stack.setCount(wheels); debits.add(stack); }
        if (!assembly && engineChanged) credits.add(current.engineStack());
        if (!assembly && wheelChanged) { var stack = current.wheelStack(); stack.setCount(wheels); credits.add(stack); }
        return new Batch(Status.READY, parts, debits, credits);
    }

    public static boolean parked(AutomobileEntity car) {
        return stationary(car.isVehicle(), car.isPassenger(), car.engineRunning(), car.automobileOnGround(),
                car.getHSpeed(), car.getVSpeed(), car.getDeltaMovement().lengthSqr());
    }

    public static Status operatingStatus(AutomobileEntity car) {
        return operatingStatus(car.isVehicle(), car.isPassenger(), car.engineRunning(), car.automobileOnGround(),
                car.getHSpeed(), car.getVSpeed(), car.getDeltaMovement().lengthSqr());
    }

    /** Same admission predicate as stationary(), with a useful reason for each ordinary blocker. */
    static Status operatingStatus(boolean passengers, boolean riding, boolean running, boolean grounded,
                                  double horizontal, double vertical, double velocitySquared) {
        if (stationary(passengers, riding, running, grounded, horizontal, vertical, velocitySquared)) return Status.READY;
        if (passengers || riding) return Status.OCCUPIED;
        if (!Double.isFinite(horizontal) || !Double.isFinite(vertical) || !Double.isFinite(velocitySquared)
                || velocitySquared < 0) return Status.PARK;
        if (Math.abs(horizontal) > 0.01 || Math.abs(vertical) > 0.035 || velocitySquared > 0.0025)
            return Status.MOVING;
        if (running) return Status.ENGINE_RUNNING;
        return Status.PARK;
    }

    /** Permission and presence remain separate from operational state and mutable parts. */
    public static Status accessStatus(boolean present, boolean authorized, boolean loaded, boolean inRange, boolean areaAccessible) {
        if (!present) return Status.NO_VEHICLE;
        if (!authorized) return Status.PROTECTED;
        if (!loaded) return Status.UNLOADED;
        if (!inRange) return Status.OUT_OF_RANGE;
        return areaAccessible ? Status.READY : Status.PROTECTED;
    }

    static boolean stationary(boolean passengers, boolean riding, boolean running, boolean grounded,
                              double horizontal, double vertical, double velocitySquared) {
        return !passengers && !riding && !running && grounded
                && Double.isFinite(horizontal) && Double.isFinite(vertical) && Double.isFinite(velocitySquared)
                && Math.abs(horizontal) <= 0.01 && Math.abs(vertical) <= 0.035
                && velocitySquared >= 0 && velocitySquared <= 0.0025;
    }

    public static Plan plan(AutomobileEntity car, Components expected, ItemStack input, boolean outputEmpty) {
        return plan(car, expected, input, outputEmpty, false);
    }

    public static Plan planStored(AutomobileEntity car, Components expected, ItemStack input, boolean outputEmpty) {
        if (car == null || !supportsStorage(car.garageComponents()) || car.isVehicle() || car.isPassenger())
            return Plan.denied(Status.INTEGRATION, 0);
        return plan(car, expected, input, outputEmpty, true);
    }

    /** New assembly consumes these exact canonical parts, including a real staged input stack. */
    public static Plan planCatalog(Components base, ItemStack input, HolderLookup.Provider registries) {
        if (base == null || !base.complete()) return Plan.denied(Status.INVALID_PART, 0);
        if (input.isEmpty()) return new Plan(Status.READY, base, ItemStack.EMPTY, 0);
        if (input.is(AutomobilityItems.AUTOMOBILE_WHEEL.require())) {
            int count = base.frame().value().model().wheelBase().wheelCount();
            var wheel = AutomobilityItems.AUTOMOBILE_WHEEL.require().lookupComponent(input, registries);
            var key = wheel.unwrapKey();
            if (key.isEmpty() || wheel.value().isEmpty() || !ItemStack.isSameItemSameComponents(input,
                    AutomobilityItems.AUTOMOBILE_WHEEL.require().createStack(key.get()))) return Plan.denied(Status.INVALID_PART, count);
            if (!validCount(count, input.getCount(), input.getMaxStackSize())) return Plan.denied(Status.MISSING_PARTS, count);
            return new Plan(Status.READY, new Components(base.frame(), wheel, base.engine()), ItemStack.EMPTY, count);
        }
        if (input.is(AutomobilityItems.AUTOMOBILE_ENGINE.require())) {
            var engine = AutomobilityItems.AUTOMOBILE_ENGINE.require().lookupComponent(input, registries);
            var key = engine.unwrapKey();
            if (key.isEmpty() || engine.value().isEmpty() || !ItemStack.isSameItemSameComponents(input,
                    AutomobilityItems.AUTOMOBILE_ENGINE.require().createStack(key.get()))) return Plan.denied(Status.INVALID_PART, 1);
            return new Plan(Status.READY, new Components(base.frame(), base.wheel(), engine), ItemStack.EMPTY, 1);
        }
        return Plan.denied(Status.INVALID_PART, 0);
    }

    private static Plan plan(AutomobileEntity car, Components expected, ItemStack input, boolean outputEmpty, boolean stored) {
        var current = car.garageComponents();
        if (current == null || !current.complete()) return Plan.denied(Status.INVALID_PART, 0);
        if (!current.equals(expected)) return Plan.denied(Status.STALE, 0);
        if (!stored && !parked(car)) return Plan.denied(Status.PARK, 0);
        if (!outputEmpty) return Plan.denied(Status.TAKE_OUTPUT, 0);
        if (input.isEmpty()) return Plan.denied(Status.INSERT_PART, 0);

        if (input.is(AutomobilityItems.AUTOMOBILE_WHEEL.require())) {
            int count = current.frame().value().model().wheelBase().wheelCount();
            var wheel = AutomobilityItems.AUTOMOBILE_WHEEL.require().lookupComponent(input, car.registryAccess());
            var key = wheel.unwrapKey();
            if (key.isEmpty() || wheel.value().isEmpty()) return Plan.denied(Status.INVALID_PART, count);
            var canonical = AutomobilityItems.AUTOMOBILE_WHEEL.require().createStack(key.get());
            if (!ItemStack.isSameItemSameComponents(input, canonical)) return Plan.denied(Status.INVALID_PART, count);
            if (wheel.equals(current.wheel())) return Plan.denied(Status.SAME_PART, count);
            var returned = current.wheelStack();
            if (!validCount(count, input.getCount(), returned.getMaxStackSize())) return Plan.denied(Status.MISSING_PARTS, count);
            returned.setCount(count);
            return new Plan(Status.READY, new Components(current.frame(), wheel, current.engine()), returned, count);
        }
        if (input.is(AutomobilityItems.AUTOMOBILE_ENGINE.require())) {
            var engine = AutomobilityItems.AUTOMOBILE_ENGINE.require().lookupComponent(input, car.registryAccess());
            var key = engine.unwrapKey();
            if (key.isEmpty() || engine.value().isEmpty()) return Plan.denied(Status.INVALID_PART, 1);
            var canonical = AutomobilityItems.AUTOMOBILE_ENGINE.require().createStack(key.get());
            if (!ItemStack.isSameItemSameComponents(input, canonical)) return Plan.denied(Status.INVALID_PART, 1);
            if (engine.equals(current.engine())) return Plan.denied(Status.SAME_PART, 1);
            // Fuel/charge and battery identity belong to their addons. A generic part swap cannot transfer them.
            if (engineIntegrationPresent || !plainEngine(current.engine()) || !plainEngine(engine)) {
                return Plan.denied(Status.INTEGRATION, 1);
            }
            return new Plan(Status.READY, new Components(current.frame(), current.wheel(), engine), current.engineStack(), 1);
        }
        return Plan.denied(Status.INVALID_PART, 0);
    }

    static boolean validCount(int required, int available, int capacity) {
        return required > 0 && required <= capacity && available >= required;
    }

    private static boolean plainEngine(Holder<AutomobileEngine> engine) {
        return engine.unwrapKey().map(key -> key.equals(AutomobileEngine.STONE) || key.equals(AutomobileEngine.IRON)
                || key.equals(AutomobileEngine.COPPER) || key.equals(AutomobileEngine.GOLD)
                || key.equals(AutomobileEngine.DIAMOND) || key.equals(AutomobileEngine.CREATIVE)).orElse(false);
    }
}
