package io.github.foundationgames.automobility.entity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutomobileTeleportMixinRegistrationTest {
    @Test
    void clientPositionMixinIsPackagedAndRegistered() throws IOException {
        var loader = AutomobileTeleportMixinRegistrationTest.class.getClassLoader();
        try (var mixinClass = loader.getResourceAsStream(
                "io/github/foundationgames/automobility/mixin/ClientPacketListenerMixin.class"
        )) {
            assertNotNull(mixinClass, "the compiled client position hooks must be packaged");
            var classBytes = new String(mixinClass.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertTrue(classBytes.contains("handleTeleportEntity"),
                    "the rider-specific teleport hook must remain packaged");
            assertTrue(classBytes.contains("handleEntityPositionSync"),
                    "the absolute entity-position sync hook must be packaged");
            assertTrue(classBytes.contains("handleMoveEntity"),
                    "the relative movement hook for small unoccupied teleports must be packaged");
            assertTrue(classBytes.contains("clientOnAuthoritativePositionApplied"),
                    "absolute packets must use authoritative reconciliation");
            assertTrue(classBytes.contains("clientOnRelativePositionApplied"),
                    "ordinary relative movement must keep its non-authoritative reconciliation path");
        }

        try (var resource = loader.getResourceAsStream("automobility.mixins.json")) {
            assertNotNull(resource, "the common mixin configuration must be present in the Fabric artifact");
            var config = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(config.contains("\"ClientPacketListenerMixin\""),
                    "the position packet hooks must remain registered as a client mixin");
        }
    }

    @Test
    void packetHandlersKeepHardAndSoftReconciliationSeparate() throws IOException {
        String mixin = Files.readString(findSource(
                "common/src/main/java/io/github/foundationgames/automobility/mixin/ClientPacketListenerMixin.java"));
        String automobile = Files.readString(findSource(
                "common/src/main/java/io/github/foundationgames/automobility/entity/AutomobileEntity.java"));

        assertTrue(methodBody(mixin, "private void automobility$captureAutomobileBeforeTeleport(").contains(
                "automobility$capturePosition(self.getLevel().getEntity(packet.id()), true)"));
        assertTrue(methodBody(mixin, "private void automobility$captureAutomobileBeforePositionSync(").contains(
                "exceedsRelativeEncoding"));
        String relativeCapture = methodBody(mixin, "private void automobility$captureAutomobileBeforeRelativeMove(");
        assertTrue(relativeCapture.contains("packet.hasPosition()"));
        assertTrue(relativeCapture.contains(", false)"));

        String authoritative = methodBody(automobile, "public void clientOnAuthoritativePositionApplied(");
        assertTrue(authoritative.contains("this.lerpTicks = 0"));
        assertTrue(authoritative.contains("this.trackedX = this.getX()"));
        assertTrue(authoritative.contains("this.trackedY = this.getY()"));
        assertTrue(authoritative.contains("this.trackedZ = this.getZ()"));
        assertTrue(authoritative.contains("this.trackedYaw = this.getYRot()"));

        String relative = methodBody(automobile, "public void clientOnRelativePositionApplied(");
        assertTrue(!relative.contains("lerpTicks")
                        && !relative.contains("trackedX")
                        && !relative.contains("trackedY")
                        && !relative.contains("trackedZ")
                        && !relative.contains("trackedYaw"),
                "relative packets must not discard Automobility's ordinary interpolation target");
    }

    private static String methodBody(String source, String methodDeclaration) {
        int method = source.indexOf(methodDeclaration);
        assertTrue(method >= 0, "missing source method " + methodDeclaration);
        int openingBrace = source.indexOf('{', method);
        assertTrue(openingBrace >= 0, "missing method body for " + methodDeclaration);
        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') depth++;
            if (character == '}' && --depth == 0) return source.substring(openingBrace, index + 1);
        }
        throw new AssertionError("unterminated method body for " + methodDeclaration);
    }

    private static Path findSource(String repositoryRelativePath) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(repositoryRelativePath);
            if (Files.isRegularFile(candidate)) return candidate;
            current = current.getParent();
        }
        throw new AssertionError("could not locate " + repositoryRelativePath);
    }
}
