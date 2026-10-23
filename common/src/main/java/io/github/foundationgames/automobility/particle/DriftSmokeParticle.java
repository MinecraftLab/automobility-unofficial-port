package io.github.foundationgames.automobility.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

public class DriftSmokeParticle extends SingleQuadParticle {
    private static final float SCALE_FACTOR = 0.83f;
    private static final int MAX_AGE = (int)(Math.log(0.1) / Math.log(SCALE_FACTOR));

    protected DriftSmokeParticle(ClientLevel world, double x, double y, double z, SpriteSet sprites) {
        super(world, x, y, z, sprites.first());
        this.quadSize = 0.6f - (this.random.nextFloat() * 0.1f);
        this.alpha = this.quadSize;
        this.setLifetime(MAX_AGE);
    }

    @Override
    public void tick() {
        super.tick();
        this.scale(SCALE_FACTOR);
        this.alpha = this.quadSize;
    }

    @Override
    public Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private long cosmeticSample;

        public Factory(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel world, double x, double y, double z, double vx, double vy, double vz, RandomSource random) {
            int percent = io.github.foundationgames.automobility.client.lighting.VehicleLightingView.graphics(
                    net.minecraft.client.Minecraft.getInstance()).driftParticlesPercent();
            if (percent != 100 && !io.github.foundationgames.automobility.client.vehicle.VehicleCosmeticPolicy
                    .driftSmoke(percent, cosmeticSample++, world.getGameTime())) return null;
            return new DriftSmokeParticle(world, x, y, z, this.sprites);
        }
    }
}
