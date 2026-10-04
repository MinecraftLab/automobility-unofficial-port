package io.github.foundationgames.automobility.block;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AutopilotSignBlockTest {
    @Test
    void headingNbtRoundTripPreservesEveryVector() {
        var expected = new AutopilotSignBlock.Heading(
                new Vec3(1.25, 2.5, -3.75),
                new Vec3(-0.5, 0, 0.75),
                new Vec3(8, 9, 10),
                new Vec3(0, -1, 0),
                true
        );

        var encoded = assertInstanceOf(CompoundTag.class, expected.toNbt());
        var actual = AutopilotSignBlock.Heading.fromNbt(encoded);

        assertNotNull(actual);
        assertEquals(expected, actual);
    }
}
