package io.github.foundationgames.automobility.util.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonPacketsTest {
    @Test
    void acceptsAWellFormedAutomobileState() {
        var buffer = validStateBuffer();

        assertTrue(CommonPackets.isValidAutomobileSyncState(buffer));
        assertTrue(buffer.readerIndex() == 0, "Validation must not consume the packet");
    }

    @Test
    void rejectsNonFinitePhysicsValues() {
        int[] floatOffsets = {
                Integer.BYTES,
                2 * Integer.BYTES,
                4 * Integer.BYTES,
                5 * Integer.BYTES,
                48,
                54,
                70,
                74,
                78,
                82
        };
        for (int offset : floatOffsets) {
            var buffer = validStateBuffer();
            buffer.setFloat(offset, Float.NaN);

            assertFalse(CommonPackets.isValidAutomobileSyncState(buffer), "NaN at offset " + offset);
        }

        int[] doubleOffsets = {24, 32, 40, 62};
        for (int offset : doubleOffsets) {
            var buffer = validStateBuffer();
            buffer.setDouble(offset, Double.POSITIVE_INFINITY);

            assertFalse(CommonPackets.isValidAutomobileSyncState(buffer), "infinity at offset " + offset);
        }
    }

    @Test
    void rejectsExtremeClientPhysicsValues() {
        var engineSpeed = validStateBuffer();
        engineSpeed.setFloat(16, 1_000.01f);
        assertFalse(CommonPackets.isValidAutomobileSyncState(engineSpeed));

        var boostSpeed = validStateBuffer();
        boostSpeed.setFloat(20, -1_000.01f);
        assertFalse(CommonPackets.isValidAutomobileSyncState(boostSpeed));

        var position = validStateBuffer();
        position.setDouble(24, 32_000_001d);
        assertFalse(CommonPackets.isValidAutomobileSyncState(position));
    }

    @Test
    void rejectsTruncatedStates() {
        var buffer = validStateBuffer();
        buffer.writerIndex(buffer.writerIndex() - 1);

        assertFalse(CommonPackets.isValidAutomobileSyncState(buffer));
    }

    @Test
    void componentSyncRequestRequiresExactlyOneEntityId() {
        var exact = new FriendlyByteBuf(Unpooled.buffer());
        exact.writeInt(42);
        assertTrue(CommonPackets.isExactEntityIdPayload(exact));

        var truncated = new FriendlyByteBuf(Unpooled.buffer());
        truncated.writeMedium(42);
        assertFalse(CommonPackets.isExactEntityIdPayload(truncated));

        var trailing = new FriendlyByteBuf(Unpooled.buffer());
        trailing.writeInt(42);
        trailing.writeByte(0);
        assertFalse(CommonPackets.isExactEntityIdPayload(trailing));
    }

    @Test
    void extractsOnlyInputIntentFromLegacyStateLayout() {
        var buffer = validStateBuffer();
        buffer.setBoolean(52, true);
        buffer.setBoolean(53, true);
        buffer.setFloat(54, -0.75f);
        buffer.setBoolean(58, true);
        buffer.setBoolean(59, true);

        var state = CommonPackets.readClientAutomobileInput(buffer);

        assertTrue(state.accelerating());
        assertTrue(state.braking());
        assertEquals(-0.75f, state.inputSteering());
        assertTrue(state.holdingDrift());
        assertTrue(state.holdingHorn());
        assertEquals(0, buffer.readerIndex(), "Reading client intent must not consume the validated packet");
    }

    private static FriendlyByteBuf validStateBuffer() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer(CommonPackets.AUTOMOBILE_SYNC_STATE_SIZE));
        buffer.writeInt(0);
        buffer.writeFloat(0f);
        buffer.writeFloat(0f);
        buffer.writeInt(0);
        buffer.writeFloat(0f);
        buffer.writeFloat(0f);
        buffer.writeDouble(0d);
        buffer.writeDouble(0d);
        buffer.writeDouble(0d);
        buffer.writeFloat(0f);
        buffer.writeBoolean(false);
        buffer.writeBoolean(false);
        buffer.writeFloat(0f);
        buffer.writeBoolean(false);
        buffer.writeBoolean(false);
        buffer.writeBoolean(false);
        buffer.writeBoolean(false);
        buffer.writeDouble(0d);
        buffer.writeFloat(0f);
        buffer.writeFloat(0f);
        buffer.writeFloat(0f);
        buffer.writeFloat(1f);
        return buffer;
    }
}
