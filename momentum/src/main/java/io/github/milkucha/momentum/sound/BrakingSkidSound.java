package io.github.milkucha.momentum.sound;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.sound.AutomobileSoundInstance;
import io.github.foundationgames.automobility.sound.AutomobilitySounds;
import io.github.milkucha.momentum.MomentumBrakeState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public class BrakingSkidSound extends AutomobileSoundInstance {
    public BrakingSkidSound(AutomobileEntity automobile) {
        super(AutomobilitySounds.SKID.require(), Minecraft.getInstance(), automobile);
    }

    @Override
    protected boolean canPlay(AutomobileEntity automobile) {
        return MomentumBrakeState.brakeHeld && automobile.getHSpeed() > 0f;
    }

    @Override
    protected float getPitch(AutomobileEntity automobile) {
        return 0.7f + 0.5f * Math.min(automobile.getHSpeed() / 0.5f, 1.0f);
    }

    @Override
    protected float getVolume(AutomobileEntity automobile) {
        return automobile.automobileOnGround() ? 1.0f : 0.0f;
    }

    @Override
    protected Vec3 getPosition(AutomobileEntity auto) {
        return auto.getTailPos();
    }
}
