package io.github.foundationgames.automobility.item;

import io.github.foundationgames.automobility.automobile.AutomobileData;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AutomobileItemSafetyTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void missingAutomobileComponentUsesAPlayableDefault() {
        assertEquals(AutomobileData.DEFAULT, AutomobileItem.resolveData(null));
    }

    @Test
    void existingAutomobileComponentIsPreserved() {
        var custom = AutomobileData.prefab(
                io.github.foundationgames.automobility.Automobility.rl("test"),
                io.github.foundationgames.automobility.automobile.AutomobileFrame.RED_TRACTOR,
                io.github.foundationgames.automobility.automobile.AutomobileWheel.TRACTOR,
                io.github.foundationgames.automobility.automobile.AutomobileEngine.COPPER
        );

        assertEquals(custom, AutomobileItem.resolveData(custom));
    }
}
