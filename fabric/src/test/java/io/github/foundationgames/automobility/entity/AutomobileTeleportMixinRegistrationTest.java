package io.github.foundationgames.automobility.entity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutomobileTeleportMixinRegistrationTest {
    @Test
    void clientTeleportMixinIsPackagedAndRegistered() throws IOException {
        var loader = AutomobileTeleportMixinRegistrationTest.class.getClassLoader();
        try (var mixinClass = loader.getResourceAsStream(
                "io/github/foundationgames/automobility/mixin/ClientPacketListenerMixin.class"
        )) {
            assertNotNull(mixinClass, "the compiled client teleport hook must be packaged");
        }

        try (var resource = loader.getResourceAsStream("automobility.mixins.json")) {
            assertNotNull(resource, "the common mixin configuration must be present in the Fabric artifact");
            var config = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(config.contains("\"ClientPacketListenerMixin\""),
                    "the teleport packet hook must remain registered as a client mixin");
        }
    }
}
