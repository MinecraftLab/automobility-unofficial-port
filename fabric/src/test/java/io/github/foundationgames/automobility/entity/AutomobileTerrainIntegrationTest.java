package io.github.foundationgames.automobility.entity;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Compiled wiring checks complement the revision/collision behavior tests; not a live-client test. */
final class AutomobileTerrainIntegrationTest {
    private static final String PREFIX = "io/github/foundationgames/automobility/";

    @Test
    void terrainHooksArePackagedOnlyAsRequiredClientMixins() throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream("automobility.mixins.json")) {
            assertNotNull(stream);
            var config = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(config.get("required").getAsBoolean());
            assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
            var client = config.getAsJsonArray("client").asList().stream().map(value -> value.getAsString()).toList();
            var common = config.getAsJsonArray("mixins").asList().stream().map(value -> value.getAsString()).toList();
            for (String mixin : List.of("ClientChunkCacheTerrainMixin", "ClientLevelTerrainMixin")) {
                assertEquals(1, client.stream().filter(mixin::equals).count());
                assertFalse(common.contains(mixin), "terrain hooks must not load client classes on a server");
                assertNotNull(compiled(PREFIX + "mixin/" + mixin));
            }
            assertTrue(client.contains("ClientPacketListenerMixin"));
            assertTrue(client.contains("LocalPlayerMixin"), "retain accepted 26.3 camera/interpolation hooks");
        }
    }

    @Test
    void actualMinecraft263MethodsRetainTheInjectionSignatures() throws IOException {
        var level = compiled("net/minecraft/client/multiplayer/ClientLevel");
        assertMethod(level, "setBlocksDirty", "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V");
        assertMethod(level, "onChunkLoaded", "(Lnet/minecraft/world/level/ChunkPos;)V");
        assertMethod(level, "unload", "(Lnet/minecraft/world/level/chunk/LevelChunk;)V");
        var cache = compiled("net/minecraft/client/multiplayer/ClientChunkCache");
        assertMethod(cache, "updateViewCenter", "(II)V");
        assertMethod(cache, "updateViewRadius", "(I)V");
        assertMethod(cache, "getLevel", "()Lnet/minecraft/world/level/BlockGetter;");
    }

    @Test
    void compiledTerrainEventsReachTheirRevisionTracker() throws IOException {
        var level = PREFIX + "mixin/ClientLevelTerrainMixin";
        assertTrue(calls(level, "automobility$terrainBlockChanged").contains("changed"));
        assertTrue(calls(level, "automobility$terrainChunkLoaded").contains("changed"));
        assertTrue(calls(level, "automobility$terrainChunkUnloaded").contains("unloaded"));
        var cache = PREFIX + "mixin/ClientChunkCacheTerrainMixin";
        assertTrue(calls(cache, "automobility$terrainViewCenterChanged").contains("automobility$terrainStorageChanged"));
        assertTrue(calls(cache, "automobility$terrainViewRadiusChanged").contains("automobility$terrainStorageChanged"));
        assertTrue(calls(cache, "automobility$terrainStorageChanged").contains("storageChanged"));
        assertTrue(calls(PREFIX + "entity/AutomobileEntity", "onSyncedDataUpdated").contains("invalidateTerrain"));
    }

    @Test
    void terrainRefreshCannotReplaceARemoteDriversDisplacement() throws IOException {
        String source = Files.readString(findSource("common/src/main/java/" + PREFIX + "entity/AutomobileEntity.java"));
        int start = source.indexOf("public void displacementTick(boolean tick)");
        int end = source.indexOf("private boolean usesLocalDisplacement()", start);
        assertTrue(start >= 0 && end > start);
        String method = source.substring(start, end);
        int localStart = method.indexOf("if (this.usesLocalDisplacement()) {");
        int remoteStart = method.indexOf("} else {", localStart);
        assertTrue(localStart >= 0 && remoteStart > localStart);
        String local = method.substring(localStart, remoteStart);
        assertTrue(local.contains("this.displacement.shouldRefreshTerrain("));
        assertTrue(local.contains("if (tick || terrainChanged)"));
        assertTrue(local.contains("this.displacement.tick("));
        String remote = method.substring(remoteStart, method.indexOf("this.displacement.postTick()", remoteStart));
        assertTrue(remote.contains("this.displacement.invalidateTerrain()"));
        assertTrue(remote.contains("if (!this.displacement.hasSyncedData())"));
        assertTrue(remote.contains("this.displacement.snapTo("));
        assertFalse(remote.contains("this.displacement.tick("));
        assertFalse(remote.contains("this.displacement.shouldRefreshTerrain("));
    }

    private static ClassModel compiled(String name) throws IOException {
        try (var stream = AutomobileTerrainIntegrationTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(stream, "missing compiled class " + name);
            return ClassFile.of().parse(stream.readAllBytes());
        }
    }

    private static void assertMethod(ClassModel owner, String name, String descriptor) {
        assertTrue(owner.methods().stream().anyMatch(method -> method.methodName().equalsString(name)
                && method.methodType().equalsString(descriptor)), "missing 26.3 injection target " + name + descriptor);
    }

    private static List<String> calls(String owner, String name) throws IOException {
        return compiled(owner).methods().stream().filter(method -> method.methodName().equalsString(name))
                .flatMap(method -> method.code().stream()).flatMap(code -> code.elementStream())
                .filter(InvokeInstruction.class::isInstance).map(InvokeInstruction.class::cast)
                .map(call -> call.name().stringValue()).toList();
    }

    private static Path findSource(String relative) {
        for (Path directory = Path.of("").toAbsolutePath(); directory != null; directory = directory.getParent()) {
            Path source = directory.resolve(relative);
            if (Files.isRegularFile(source)) return source;
        }
        throw new AssertionError("Missing source " + relative);
    }
}
