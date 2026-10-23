package io.github.foundationgames.automobility.client.vehicle;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.sound.AutomobilitySounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** Quiet listener-relative relay audio, exclusively for the alive local driver. */
public final class VehicleSignalAudio {
    private static final VehicleSignalAudioObserver OBSERVER = new VehicleSignalAudioObserver();
    private static long ticksEnqueued, tocksEnqueued;

    private VehicleSignalAudio() {}

    public static void tick(Minecraft client, int volumePercent) {
        if (client.player == null || !client.player.isAlive()
                || !(client.player.getVehicle() instanceof AutomobileEntity automobile)
                || !VehicleClientControls.isDriver(client, automobile)) {
            reset();
            return;
        }
        var sounds = client.getSoundManager();
        var tickSound = AutomobilitySounds.VEHICLE_SIGNAL_TICK.require();
        var tockSound = AutomobilitySounds.VEHICLE_SIGNAL_TOCK.require();
        // SoundManager installs new WeighedSoundEvents on every real resource reload.
        var soundEpoch = sounds.getSoundEvent(tickSound.location());
        if (soundEpoch == null || sounds.getSoundEvent(tockSound.location()) == null) {
            reset();
            return;
        }
        boolean paused = client.isPaused() || !client.isGameLoadFinished() || client.gui.overlay() != null;
        boolean mixerMuted = client.options.getSoundSourceVolume(SoundSource.MASTER) <= 0
                || client.options.getSoundSourceVolume(SoundSource.PLAYERS) <= 0;
        var edge = OBSERVER.observe(client.level, automobile, client.player, soundEpoch,
                automobile.getVehicleSignalMode(), automobile.vehicleSignalLit(), volumePercent,
                paused, mixerMuted, System.nanoTime());
        if (edge == VehicleSignalAudioObserver.Edge.NONE) return;
        var event = edge == VehicleSignalAudioObserver.Edge.TICK ? tickSound : tockSound;
        float volume = 0.35f * Math.clamp(volumePercent, 0, 100) / 100.0f;
        // Global master/category muting stays in SoundEngine. A mute transition itself is silent.
        sounds.play(new SimpleSoundInstance(event.location(), SoundSource.PLAYERS, volume, 1.0f,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE,
                0, 0, 0, true));
        if (edge == VehicleSignalAudioObserver.Edge.TICK) ticksEnqueued++;
        else tocksEnqueued++;
    }

    /** Silent rebaseline; lifetime diagnostic enqueue counters deliberately remain monotonic. */
    public static void reset() { OBSERVER.reset(); }

    /** Requests submitted to SoundManager, not evidence of hardware playback. */
    public static long tickEnqueued() { return ticksEnqueued; }
    public static long tockEnqueued() { return tocksEnqueued; }
}
