package io.github.milkucha.momentum.sound;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.sound.AutomobileSoundInstance;
import io.github.foundationgames.automobility.sound.AutomobilitySounds;
import io.github.milkucha.momentum.accessor.SteeringDebugAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public class VanillaDriftSkidSound extends AutomobileSoundInstance {
    private final SteeringDebugAccessor accessor;

    public VanillaDriftSkidSound(AutomobileEntity automobile) {
        super(AutomobilitySounds.SKID.require(), Minecraft.getInstance(), automobile);
        this.accessor = (SteeringDebugAccessor) automobile;
    }

    @Override
    protected boolean canPlay(AutomobileEntity automobile) {
        return accessor.momentum$isDrifting();
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
