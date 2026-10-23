package io.github.milkucha.momentum;

/** Identity binding for both active assistance and its saved Resume target. */
public final class CruiseVehicleContext {
    private Object car, world, driver;

    public void bind(Object car, Object world, Object driver) {
        if (car == null || world == null || driver == null)
            throw new IllegalArgumentException("Cruise requires a car, world and driver");
        this.car = car;
        this.world = world;
        this.driver = driver;
    }

    public boolean matches(Object car, Object world, Object driver) {
        return this.car != null && this.car == car && this.world == world && this.driver == driver;
    }

    public boolean differs(Object car, Object world, Object driver) {
        return this.car != null && !matches(car, world, driver);
    }

    public void clear() {
        car = world = driver = null;
    }
}
