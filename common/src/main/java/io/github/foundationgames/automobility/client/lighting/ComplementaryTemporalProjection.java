package io.github.foundationgames.automobility.client.lighting;

import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntSupplier;

/** Optional, fail-closed adapter. No Iris class is referenced by the compiled production ABI. */
public final class ComplementaryTemporalProjection {
    private static final boolean EUPHORIA_ENABLED = Boolean.parseBoolean(System.getProperty("automobility.experimental.euphoriaTemporal", "true"));
    private static final String EUPHORIA_NAME = "ComplementaryUnbound_r5.9 + EuphoriaPatches_1.10.0";
    private static final Map<String, String> PACK_PINS = Map.of(
            "ComplementaryUnbound_r5.9.zip", "8b7a55b4303ca5ef47a42948ca27435857a70bb0d4f40d4f81096deb1afe1344",
            "ComplementaryUnbound_r5.9.3.zip", "98ac741dee294d2a3c82d153349148c3bb7fb688c30eb3b49c3b9ac37e968c5a");
    private static final IrisAccess IRIS = IrisAccess.resolve();
    private static final ShadowAccess SHADOW = ShadowAccess.resolve();
    private static WeakReference<Object> checkedPack = new WeakReference<>(null);
    private static String checkedName = "";
    private static boolean verifiedPack;
    private static volatile Status status = new Status(false, "not_checked", -1, 0, 0, 0, 0);
    private static int correctedPhaseMask;
    private static long correctedBatches;
    private static final java.util.concurrent.atomic.AtomicBoolean SHADOW_WARNING = new java.util.concurrent.atomic.AtomicBoolean();

    private ComplementaryTemporalProjection() { }

    /** Last non-shadow emission diagnostics; QA must match frameCounter to the captured Iris frame. */
    public record Status(boolean active, String reason, int frameCounter, int width, int height,
                         float ndcX, float ndcY) { }

    public static Status status() {
        return status;
    }

    public static String diagnosticSnapshot() {
        return "euphoriaEnabled=" + EUPHORIA_ENABLED + " phaseMask=" + correctedPhaseMask
                + " correctedBatches=" + correctedBatches + " status=" + status;
    }

    /** Admission and fade history belong only to the primary view. No optional Iris ABI linkage. */
    public static boolean isShadowPass() {
        return passState() != VehicleRenderPassPolicy.Pass.MAIN;
    }

    public static VehicleRenderPassPolicy.Pass passState() {
        if (!SHADOW.present) return VehicleRenderPassPolicy.Pass.MAIN;
        Boolean shadow = null;
        if (SHADOW.pass != null) try { shadow = (boolean) SHADOW.pass.invoke(SHADOW.api); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
        var result = VehicleRenderPassPolicy.evaluate(true, SHADOW.pass != null, shadow);
        if (result == VehicleRenderPassPolicy.Pass.API_UNAVAILABLE && SHADOW_WARNING.compareAndSet(false,true))
            io.github.foundationgames.automobility.Automobility.LOG.warn("Iris public shadow-pass API unavailable; headlight surface projections and cones are suppressed. See Automobility renderer details.");
        return result;
    }

    /** Information only; never admits an internal shader adapter or changes its phase history. */
    public static String shaderPackState() {
        if (!SHADOW.present) return "absent";
        if (SHADOW.api == null) return "unavailable";
        try {
            var type = Class.forName("net.irisshaders.iris.api.v0.IrisApi",false,ComplementaryTemporalProjection.class.getClassLoader());
            return (boolean) type.getMethod("isShaderPackInUse").invoke(SHADOW.api) ? "active" : "inactive";
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return "unavailable"; }
    }

    public static String shaderPackName() {
        if (IRIS == null || !"active".equals(shaderPackState())) return "";
        try { var name=IRIS.currentPackName.invoke(null); return name instanceof String text ? text : ""; }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return ""; }
    }

    /** Public pass API is independent of every internal TAA/pack/options method. */
    private record ShadowAccess(boolean present, Object api, Method pass) {
        private static ShadowAccess resolve() {
            boolean installed = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris");
            if (!installed) return new ShadowAccess(false, null, null);
            try {
                Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi", false,
                        ComplementaryTemporalProjection.class.getClassLoader());
                return new ShadowAccess(true, type.getMethod("getInstance").invoke(null), type.getMethod("isRenderingShadowPass"));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError rejected) { return new ShadowAccess(true, null, null); }
        }
    }

    /** Invoke once per surface-emission batch, after the existing view-layering bias. */
    public static Matrix4f apply(Matrix4fc submittedPose) {
        if (IRIS == null) {
            return unchanged(submittedPose, "iris_unavailable");
        }
        try {
            if (!(boolean) IRIS.packInUse.invoke(IRIS.api)) {
                checkedPack.clear();
                verifiedPack = false;
                return unchanged(submittedPose, "shaders_inactive");
            }
            if ((boolean) IRIS.shadowPass.invoke(IRIS.api)) {
                // A late shadow batch must neither move shadow geometry nor overwrite
                // the diagnostic for this frame's main-view emission.
                return new Matrix4f(submittedPose);
            }
            Object pack = ((Optional<?>) IRIS.currentPack.invoke(null)).orElse(null);
            String name = (String) IRIS.currentPackName.invoke(null);
            if (pack == null) {
                return unchanged(submittedPose, "pack_unavailable");
            }
            if (checkedPack.get() != pack || !checkedName.equals(name)) {
                checkedPack = new WeakReference<>(pack);
                checkedName = name == null ? "" : name;
                verifiedPack = verifyPack(name, (Path) IRIS.shaderpacksDirectory.invoke(null));
            }
            if (!verifiedPack) {
                return unchanged(submittedPose, "pack_not_pinned");
            }
            Object options = IRIS.packOptions.invoke(pack);
            Object values = IRIS.optionValues.invoke(options);
            boolean euphoria = EUPHORIA_NAME.equals(name);
            if (euphoria && (IRIS.booleanValue == null
                    || (boolean) IRIS.booleanValue.invoke(values, "MIRROR_DIMENSION")
                    || (boolean) IRIS.booleanValue.invoke(values, "WORLD_CURVATURE")
                    || (boolean) IRIS.booleanValue.invoke(values, "WAVE_EVERYTHING"))) {
                return unchanged(submittedPose, "euphoria_distortion_or_options_rejected");
            }
            int taa = option(values, "TAA_DEFINE");
            int detail = option(values, "DETAIL_QUALITY");
            int jitter = option(values, "TAA_JITTER");
            if (!(taa == 1 || (taa == -1 && detail >= 2)) || jitter == 0) {
                return unchanged(submittedPose, "taa_disabled");
            }
            var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            int frame = IRIS.counter.getAsInt();
            var offset = HeadlightProjectionJitter.offset(frame, jitter, target.width, target.height);
            Matrix4f projection = new Matrix4f((Matrix4fc) IRIS.projection.invoke(IRIS.captured));
            Matrix4f view = new Matrix4f((Matrix4fc) IRIS.modelView.invoke(IRIS.captured));
            Matrix4f corrected = HeadlightProjectionJitter.apply(submittedPose, projection, view, offset, true);
            correctedPhaseMask |= 1 << Math.floorMod(frame, 8);
            correctedBatches++;
            status = new Status(true, euphoria ? "pinned_euphoria_taa" : "pinned_complementary_taa", frame, target.width, target.height,
                    offset.x(), offset.y());
            return corrected;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return unchanged(submittedPose, "iris_state_rejected");
        }
    }

    private static int option(Object values, String name) throws ReflectiveOperationException {
        // This public default method resolves the active parsed option value or its actual pack default.
        return Integer.parseInt((String) IRIS.stringValue.invoke(values, name));
    }

    private static Matrix4f unchanged(Matrix4fc pose, String reason) {
        if (status.active() || !status.reason().equals(reason)) {
            status = new Status(false, reason, -1, 0, 0, 0, 0);
        }
        return new Matrix4f(pose);
    }

    private static boolean verifyPack(String name, Path directory) {
        // The exact source was verified for this ZIP. A renamed or locally edited pack must fail closed.
        String expectedHash = name == null ? null : PACK_PINS.get(name);
        if (directory == null) {
            return false;
        }
        if (expectedHash == null) {
            if (!EUPHORIA_ENABLED || !EUPHORIA_NAME.equals(name)) return false;
            Path root = directory.toAbsolutePath().normalize();
            Path pack = root.resolve(name).normalize();
            return pack.startsWith(root) && PinnedShaderTree.matches(pack.resolve("shaders"), 620, 9584012,
                    "798e126db05b8b1f81ce78b453efcf5247b46a7cc2f410aacec61c6ee543f3f4");
        }
        try {
            Path shaderDirectory = directory.toAbsolutePath().normalize();
            Path zip = shaderDirectory.resolve(name).normalize();
            if (!zip.startsWith(shaderDirectory) || !Files.isRegularFile(zip)) {
                return false;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream stream = Files.newInputStream(zip)) {
                byte[] buffer = new byte[32768];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    digest.update(buffer, 0, count);
                }
            }
            return expectedHash.equals(HexFormat.of().formatHex(digest.digest()));
        } catch (Exception ex) {
            return false;
        }
    }

    private record IrisAccess(Object api, Method packInUse, Method shadowPass,
                              Method currentPack, Method currentPackName, Method shaderpacksDirectory,
                              Method packOptions, Method optionValues, Method stringValue, Method booleanValue,
                              Object captured, Method projection, Method modelView, IntSupplier counter) {
        private static IrisAccess resolve() {
            try {
                ClassLoader loader = ComplementaryTemporalProjection.class.getClassLoader();
                Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi", false, loader);
                Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris", false, loader);
                Class<?> packClass = Class.forName("net.irisshaders.iris.shaderpack.ShaderPack", false, loader);
                Class<?> optionsClass = Class.forName("net.irisshaders.iris.shaderpack.option.ShaderPackOptions", false, loader);
                Class<?> valuesClass = Class.forName("net.irisshaders.iris.shaderpack.option.values.OptionValues", false, loader);
                Class<?> stateClass = Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState", false, loader);
                Class<?> timeClass = Class.forName("net.irisshaders.iris.uniforms.SystemTimeUniforms", false, loader);
                return new IrisAccess(apiClass.getMethod("getInstance").invoke(null),
                        apiClass.getMethod("isShaderPackInUse"), apiClass.getMethod("isRenderingShadowPass"),
                        irisClass.getMethod("getCurrentPack"), irisClass.getMethod("getCurrentPackName"),
                        irisClass.getMethod("getShaderpacksDirectory"), packClass.getMethod("getShaderPackOptions"),
                        optionsClass.getMethod("getOptionValues"), valuesClass.getMethod("getStringValueOrDefault", String.class),
                        optionalBooleanValue(valuesClass),
                        stateClass.getField("INSTANCE").get(null), stateClass.getMethod("getGbufferProjection"),
                        stateClass.getMethod("getGbufferModelView"), (IntSupplier) timeClass.getField("COUNTER").get(null));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                return null;
            }
        }

        private static Method optionalBooleanValue(Class<?> valuesClass) {
            try { return valuesClass.getMethod("getBooleanValueOrDefault", String.class); }
            catch (NoSuchMethodException unavailable) { return null; }
        }
    }
}
