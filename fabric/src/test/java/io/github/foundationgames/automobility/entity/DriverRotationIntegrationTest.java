package io.github.foundationgames.automobility.entity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Instruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Locks the compiled integration to the actual 26.3 passenger tick order. */
final class DriverRotationIntegrationTest {
    @Test
    void automobileQueuesLocalDriverRotationBeforePassengerSnapshotsOldYaw() throws IOException {
        var calls = calls("entity/AutomobileEntity", "postMovementTick");
        assertTrue(calls.contains("queue"),
                "local driver yaw must be queued until after passenger.commonTick snapshots yRotO");
        assertTrue(calls.contains("isLocalPlayer"), "only the local driver should be deferred");
        assertTrue(calls.contains("whenRotated"), "server/non-local rotation must retain its current path");
    }

    @Test
    void registeredLocalPlayerMixinConsumesRotationAtRideTickHead() throws IOException {
        var calls = calls("mixin/LocalPlayerMixin", "automobility$applyDriverRotation");
        assertTrue(calls.contains("applyDeferredDriverRotation"),
                "compiled local-player hook must consume the automobile's pending rotation");

        String source = Files.readString(findSource(
                "common/src/main/java/io/github/foundationgames/automobility/mixin/LocalPlayerMixin.java"));
        int method = source.indexOf("void automobility$applyDriverRotation(");
        assertTrue(method >= 0);
        String annotation = source.substring(source.lastIndexOf("@Inject", method), method);
        assertTrue(annotation.contains("method = \"rideTick\""));
        assertTrue(annotation.contains("@At(\"HEAD\")"),
                "rideTick HEAD is after commonTick but before LivingEntity's head animation tick");
        assertFalse(source.contains("getViewYRot"), "keep vanilla per-frame mouse/view interpolation");
        assertFalse(source.contains("yRotO"), "do not erase or synthesize vanilla mouse history");
        try (var resource = getClass().getClassLoader().getResourceAsStream("automobility.mixins.json")) {
            assertNotNull(resource);
            assertTrue(new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .contains("\"LocalPlayerMixin\""));
        }
    }

    @Test
    void deferredApplicationKeepsRiderSafetyChecksAndBothOriginalRotationModes() throws IOException {
        var calls = calls("entity/AutomobileEntity", "applyDeferredDriverRotation");
        for (String required : List.of("consume", "isClientSide", "isLocalPlayer", "isAlive",
                "isRemoved", "getVehicle", "isDriving", "whenRotated", "whenRotatedSmooth")) {
            assertTrue(calls.contains(required), "missing deferred-rotation contract: " + required);
        }
        assertEquals(1, calls.stream().filter("consume"::equals).count(), "one-shot consumption");
        assertTrue(calls("entity/AutomobileEntity", "removePassenger").contains("clear"),
                "dismount must cancel a pending turn before the same player can remount");
        assertTrue(calls("entity/AutomobileEntity", "postMovementTick").contains("clear"),
                "a new automobile tick must discard any stale pending rotation");
    }

    private static List<String> calls(String className, String method) throws IOException {
        String resource = "io/github/foundationgames/automobility/" + className + ".class";
        try (var stream = DriverRotationIntegrationTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, "compiled production class must be present");
            return ClassFile.of().parse(stream.readAllBytes()).methods().stream()
                    .filter(candidate -> candidate.methodName().equalsString(method))
                    .flatMap(candidate -> candidate.code().stream())
                    .flatMap(code -> code.elementStream())
                    .filter(Instruction.class::isInstance)
                    .filter(InvokeInstruction.class::isInstance)
                    .map(InvokeInstruction.class::cast)
                    .map(call -> call.name().stringValue())
                    .toList();
        }
    }

    private static Path findSource(String relativePath) {
        for (Path directory = Path.of("").toAbsolutePath(); directory != null; directory = directory.getParent()) {
            Path source = directory.resolve(relativePath);
            if (Files.isRegularFile(source)) return source;
        }
        throw new AssertionError("Missing source " + relativePath);
    }
}
