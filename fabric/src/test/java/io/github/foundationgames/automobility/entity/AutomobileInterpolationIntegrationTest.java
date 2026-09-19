package io.github.foundationgames.automobility.entity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class AutomobileInterpolationIntegrationTest {
    @Test
    void actualMinecraft263HandlerIsCreatedAndAdvancedOnlyAtTheExistingTrackingPhase() throws IOException {
        assertTrue(calls("entity/AutomobileEntity", "createInterpolationHandler").contains("<init>"),
                "26.3 calls createInterpolationHandler, not legacy lerpTo");
        assertTrue(calls("entity/AutomobileEntity", "positionTrackingTick").contains("tick"),
                "the deferred handler must advance after Automobile.tick captures prevPos");
        assertTrue(calls("entity/AutomobileEntity", "positionTrackingTick").contains("cancel"),
                "a newly local-authoritative driver must discard the remote interpolation target");
        assertFalse(calls("entity/AutomobileInterpolationHandler", "interpolate").contains("tick"),
                "vanilla commonTick must not run a second interpolation step");
        assertTrue(calls("entity/AutomobileEntity", "lerpTo").contains("isLocalInstanceAuthoritative"),
                "legacy addon calls must retain their previous local-driver ignore behavior");
    }

    @Test
    void packetHooksBracketHardCorrectionsAndPreserveExistingReconciliation() throws IOException {
        assertTrue(calls("mixin/ClientPacketListenerMixin", "automobility$capturePosition")
                .contains("clientBeginPositionUpdate"));
        var finish = calls("mixin/ClientPacketListenerMixin", "automobility$finishPosition");
        assertTrue(finish.contains("clientEndPositionUpdate"));
        assertTrue(finish.contains("clientOnAuthoritativePositionApplied"));
        assertTrue(finish.contains("clientOnRelativePositionApplied"));
        assertTrue(calls("entity/AutomobileEntity", "clientEndPositionUpdate").contains("cancel"),
                "even a hard correction to the current position must cancel an older target");
        assertFalse(calls("entity/AutomobileEntity", "clientBeginPositionUpdate").contains("cancel"),
                "vanilla must resolve relative teleports against the pending endpoint before cancellation");
        var syncCapture = calls("mixin/ClientPacketListenerMixin",
                "automobility$captureAutomobileBeforePositionSync");
        assertTrue(syncCapture.contains("exceedsRelativeEncoding"));
        assertTrue(syncCapture.contains("isTickingEntity"),
                "unticking entities use vanilla's direct snap path even for a small network delta");
        assertTrue(syncCapture.contains("distanceToSqr"),
                "a >64-block visual gap snaps even when the network endpoint delta is small");
    }

    private static List<String> calls(String className, String method) throws IOException {
        try (var stream = AutomobileInterpolationIntegrationTest.class.getClassLoader().getResourceAsStream(
                "io/github/foundationgames/automobility/" + className + ".class")) {
            assertNotNull(stream);
            return ClassFile.of().parse(stream.readAllBytes()).methods().stream()
                    .filter(candidate -> candidate.methodName().equalsString(method))
                    .flatMap(candidate -> candidate.code().stream())
                    .flatMap(code -> code.elementStream())
                    .filter(InvokeInstruction.class::isInstance)
                    .map(InvokeInstruction.class::cast)
                    .map(call -> call.name().stringValue())
                    .toList();
        }
    }
}
