package io.github.foundationgames.automobility.client.lighting;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Exact optional ABI of the legacy collectParts path, NOT CTM emitQuads geometry. */
final class HeadlightPinnedCollectorWrappers {
    static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("automobility.experimental.continuityCollectorCertificate", "true"));
    private static final String CTM = "me.pepperbell.continuity.client.model.CtmBlockStateModel";
    private static final String BASE = "net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel";
    private static final String CTM_SHA = "18e7b9837215c9e8365d676c00b0c685fff369a0f048ebcbcb3659b52a5e0f16";
    private static final String BASE_SHA = "a44ce1c6cbb37b8ab6b7c685d72b1786112ded3d2caa2da9feae06bb7a4e1511";
    private record Contract(Class<?> exactType, Field delegate) {}
    private static final class Lazy { static final Contract CONTRACT = resolve(); }
    private HeadlightPinnedCollectorWrappers() {}
    static boolean supported(Object value) {
        return ENABLED && value != null && Lazy.CONTRACT != null && value.getClass() == Lazy.CONTRACT.exactType;
    }
    static Object delegate(Object value, HeadlightDelegateBindings.Builder bindings) throws IllegalAccessException {
        if (!supported(value)) return null;
        var field = Lazy.CONTRACT.delegate;
        var delegate = field.get(value);
        return bindings.capture(value, field, delegate) ? delegate : null;
    }
    private static Contract resolve() {
        try {
            var loader = HeadlightPinnedCollectorWrappers.class.getClassLoader();
            var type = Class.forName(CTM, false, loader);
            var base = Class.forName(BASE, false, loader);
            var model = Class.forName("net.minecraft.client.renderer.block.dispatch.BlockStateModel", false, loader);
            var random = Class.forName("net.minecraft.util.RandomSource", false, loader);
            if (type.getSuperclass() != base || !fingerprint(type, CTM_SHA) || !fingerprint(base, BASE_SHA)
                    || type.getMethod("collectParts", random, List.class).getDeclaringClass() != base) return null;
            var field = base.getDeclaredField("wrapped");
            if (field.getType() != model || Modifier.isStatic(field.getModifiers())
                    || !Modifier.isProtected(field.getModifiers())) return null;
            field.setAccessible(true);
            return new Contract(type, field);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) { return null; }
    }
    static boolean fingerprint(Class<?> type, String expected) {
        if (type == null || expected == null) return false;
        try (var input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (input == null) return false;
            var bytes = input.readNBytes(262145);
            return bytes.length <= 262144 && HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(expected);
        } catch (IOException | NoSuchAlgorithmException | RuntimeException error) { return false; }
    }
}
