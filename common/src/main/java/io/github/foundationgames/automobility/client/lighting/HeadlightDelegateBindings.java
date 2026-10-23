package io.github.foundationgames.automobility.client.lighting;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

/** Bounded, identity-only observations of mutable collector delegates. No model callbacks. */
final class HeadlightDelegateBindings {
    static final HeadlightDelegateBindings EMPTY = new HeadlightDelegateBindings(new Binding[0]);
    private record Binding(Object owner, Field field, Object expected) {
        boolean unchanged() {
            try { return field.get(owner) == expected; }
            catch (IllegalAccessException | RuntimeException error) { return false; }
        }
    }
    private final Binding[] bindings;
    private boolean revoked;
    private HeadlightDelegateBindings(Binding[] bindings) { this.bindings = bindings; }
    boolean stillValid() {
        if (revoked) return false;
        for (var binding : bindings) if (!binding.unchanged()) { revoked = true; return false; }
        return true;
    }
    static final class Builder {
        private final ArrayList<Binding> bindings = new ArrayList<>();
        private boolean valid = true;
        boolean capture(Object owner, Field field, Object expected) {
            if (!valid) return false;
            if (owner == null || field == null || expected == null || Modifier.isStatic(field.getModifiers())
                    || field.getType().isPrimitive() || !field.getDeclaringClass().isInstance(owner)) return valid = false;
            for (var previous : bindings) if (previous.owner == owner && previous.field.equals(field)) {
                return valid = previous.expected == expected && previous.unchanged();
            }
            if (bindings.size() >= 256) return valid = false;
            var binding = new Binding(owner, field, expected);
            if (!binding.unchanged()) return valid = false;
            bindings.add(binding);
            return true;
        }
        boolean stillValid() {
            if (!valid) return false;
            for (var binding : bindings) if (!binding.unchanged()) return valid = false;
            return true;
        }
        HeadlightDelegateBindings freeze() {
            if (!stillValid()) throw new IllegalStateException("Revoked delegate observations");
            return bindings.isEmpty() ? EMPTY : new HeadlightDelegateBindings(bindings.toArray(Binding[]::new));
        }
    }
}
