package io.github.foundationgames.automobility.sound;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.lwjgl.openal.AL10;

import java.util.function.IntConsumer;

public abstract class AutomobileSoundInstance extends AbstractTickableSoundInstance implements AdvancedSoundInstance {
    protected final Minecraft client;
    protected final AutomobileEntity automobile;

    private double lastDistance;

    private int fade = 0;
    private boolean die = false;

    public AutomobileSoundInstance(SoundEvent sound, Minecraft client, AutomobileEntity automobile) {
        super(sound, SoundSource.AMBIENT, automobile.level().getRandom());
        this.client = client;
        this.automobile = automobile;
        this.looping = true;
        this.delay = 0;

        this.lastDistance = getPosition(automobile).subtract(client.player.position()).length();
    }

    protected abstract boolean canPlay(AutomobileEntity automobile);

    protected abstract float getPitch(AutomobileEntity automobile);

    protected abstract float getVolume(AutomobileEntity automobile);

    protected double dopplerScale() {
        return 0.16;
    }

    protected float audibleDistance() {
        return 18;
    }

    protected Vec3 getPosition(AutomobileEntity auto) {
        return auto.position();
    }

    @Override
    public void tick() {
        var player = this.client.player;
        if (automobile.isRemoved() || player == null) {
            this.stop();
            return;
        } else if (!this.canPlay(automobile)) {
            this.die = true;
        }

        if (this.die) {
            if (this.fade > 0) this.fade--;
            else if (this.fade == 0) {
                this.stop();
                return;
            }
        } else if (this.fade < 3) {
            this.fade++;
        }
        this.volume = this.getVolume(this.automobile) * (float)fade / 3;

        var pos = getPosition(this.automobile);
        this.x = pos.x();
        this.y = pos.y();
        this.z = pos.z();

        this.pitch = this.getPitch(this.automobile);

        if (player.getVehicle() != this.automobile) {
            double distance = pos.subtract(player.position()).length();
            double dDist = Math.clamp(this.lastDistance - distance, -1.5, 1.5);
            this.pitch += (float) (dopplerScale() * dDist);

            this.lastDistance -= dDist;
        } else {
            this.lastDistance = 0;
        }
    }

    @Override
    public IntConsumer setupALState() {
        float dist = audibleDistance();
        return source -> AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, dist);
    }

    public static class EngineSound extends AutomobileSoundInstance {
        private final io.github.foundationgames.automobility.automobile.AutomobileEngine installedEngine;
        private final io.github.foundationgames.automobility.automobile.drivetrain.DrivetrainSoundModel.Voice installedVoice;
        private static SoundEvent engineLoop(AutomobileEntity car) {
            var event = car.getEngine().sound().get();
            var voice = io.github.foundationgames.automobility.client.vehicle.VehiclePowertrainPresentation.voice(car);
            // Preserve custom engine samples. Only revoice known Core loops when fuel configuration changes their family.
            boolean dieselLoop = event.location().equals(AutomobilitySounds.IRON_ENGINE.require().location()) || event.location().equals(AutomobilitySounds.DIAMOND_ENGINE.require().location());
            boolean petrolLoop = event.location().equals(AutomobilitySounds.STONE_ENGINE.require().location()) || event.location().equals(AutomobilitySounds.COPPER_ENGINE.require().location()) || event.location().equals(AutomobilitySounds.GOLD_ENGINE.require().location());
            if (voice == io.github.foundationgames.automobility.automobile.drivetrain.DrivetrainSoundModel.Voice.DIESEL && petrolLoop) return AutomobilitySounds.IRON_ENGINE.require();
            if (voice == io.github.foundationgames.automobility.automobile.drivetrain.DrivetrainSoundModel.Voice.PETROL && dieselLoop) return AutomobilitySounds.COPPER_ENGINE.require();
            return event;
        }
        private float drivetrainPitch = Float.NaN;
        private float drivetrainGain = Float.NaN;
        public EngineSound(Minecraft client, AutomobileEntity automobile) {
            super(engineLoop(automobile), client, automobile);
            installedEngine = automobile.getEngine();
            installedVoice = io.github.foundationgames.automobility.client.vehicle.VehiclePowertrainPresentation.voice(automobile);
        }

        @Override
        public void tick() {
            if (!automobile.isRemoved() && client.player != null && (installedEngine != automobile.getEngine()
                    || installedVoice != io.github.foundationgames.automobility.client.vehicle.VehiclePowertrainPresentation.voice(automobile))) {
                // A physical engine swap must not leave the old engine's loop running indefinitely.
                stop();
                if (!automobile.getEngine().isEmpty() && canPlay(automobile)) client.getSoundManager().play(new EngineSound(client, automobile));
                return;
            }
            super.tick();
        }

        @Override
        protected boolean canPlay(AutomobileEntity automobile) {
            var mechanics = io.github.foundationgames.automobility.client.vehicle.VehiclePowertrainPresentation.reading(automobile);
            if (mechanics.isPresent()) return mechanics.get().tone().audible();
            return automobile.engineRunning();
        }

        @Override
        protected float getPitch(AutomobileEntity automobile) {
            var mechanics = io.github.foundationgames.automobility.client.vehicle.VehiclePowertrainPresentation.reading(automobile);
            if (mechanics.isPresent()) {
                float target = mechanics.get().tone().pitch();
                drivetrainPitch = Float.isFinite(drivetrainPitch) ? drivetrainPitch + 0.45f * (target - drivetrainPitch) : target;
                return drivetrainPitch;
            }
            drivetrainPitch = Float.NaN;
            return (float) (Math.pow(4, (automobile.getEffectiveSpeed() - 0.9)) + 0.32);
        }

        @Override
        protected float getVolume(AutomobileEntity automobile) {
            var mechanics = io.github.foundationgames.automobility.client.vehicle.VehiclePowertrainPresentation.reading(automobile);
            if (mechanics.isPresent()) {
                float target = mechanics.get().tone().gain();
                drivetrainGain = Float.isFinite(drivetrainGain) ? drivetrainGain + 0.45f * (target - drivetrainGain) : target;
                return drivetrainGain;
            }
            drivetrainGain = Float.NaN;
            return 1;
        }

        @Override
        protected Vec3 getPosition(AutomobileEntity auto) {
            var ePos = auto.getFrame().model().enginePos().scale(1d/16);
            var ePosD = new Vector3d(ePos.x(), ePos.y(), ePos.z());
            auto.localPosToWorldSpace(ePosD);
            return new Vec3(ePosD.x(), ePosD.y(), ePosD.z());
        }
    }

    public static class SkiddingSound extends AutomobileSoundInstance {
        public SkiddingSound(Minecraft client, AutomobileEntity automobile) {
            super(AutomobilitySounds.SKID.require(), client, automobile);
        }

        @Override
        protected boolean canPlay(AutomobileEntity automobile) {
            return automobile.isDrifting() || automobile.burningOut();
        }

        @Override
        protected float getPitch(AutomobileEntity automobile) {
            return automobile.burningOut() ? 0.75f :
                    1 + 0.056f * ((float)Math.min(automobile.getTurboCharge(), AutomobileEntity.LARGE_TURBO_TIME) / AutomobileEntity.LARGE_TURBO_TIME);
        }

        @Override
        protected float getVolume(AutomobileEntity automobile) {
            return automobile.automobileOnGround() ? 1 : 0;
        }

        @Override
        protected Vec3 getPosition(AutomobileEntity auto) {
            return auto.getTailPos();
        }
    }
}
