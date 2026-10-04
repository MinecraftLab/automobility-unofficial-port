package io.github.foundationgames.automobility.entity;

import io.github.foundationgames.automobility.automobile.AutomobileEngine;
import io.github.foundationgames.automobility.automobile.AutomobileFrame;
import io.github.foundationgames.automobility.automobile.AutomobileWheel;
import io.github.foundationgames.automobility.item.AutomobilityItems;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;

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
        MISSING_PARTS, INTEGRATION, STALE, PROTECTED
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

    public static boolean parked(AutomobileEntity car) {
        return stationary(car.isVehicle(), car.isPassenger(), car.engineRunning(), car.automobileOnGround(),
                car.getHSpeed(), car.getVSpeed(), car.getDeltaMovement().lengthSqr());
    }

    static boolean stationary(boolean passengers, boolean riding, boolean running, boolean grounded,
                              double horizontal, double vertical, double velocitySquared) {
        return !passengers && !riding && !running && grounded
                && Double.isFinite(horizontal) && Double.isFinite(vertical) && Double.isFinite(velocitySquared)
                && Math.abs(horizontal) <= 0.01 && Math.abs(vertical) <= 0.035
                && velocitySquared >= 0 && velocitySquared <= 0.0025;
    }

    public static Plan plan(AutomobileEntity car, Components expected, ItemStack input, boolean outputEmpty) {
        var current = car.garageComponents();
        if (current == null || !current.complete()) return Plan.denied(Status.INVALID_PART, 0);
        if (!current.equals(expected)) return Plan.denied(Status.STALE, 0);
        if (!parked(car)) return Plan.denied(Status.PARK, 0);
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
