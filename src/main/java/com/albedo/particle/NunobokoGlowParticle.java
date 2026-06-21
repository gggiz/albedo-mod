package com.albedo.particle;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;

public class NunobokoGlowParticle extends SingleQuadParticle {
    private final float maxAlpha;

    protected NunobokoGlowParticle(ClientLevel level, double x, double y, double z,
                                   double vx, double vy, double vz,
                                   float scale, int lifetime, float alpha,
                                   TextureAtlasSprite sprite) {
        super(level, x, y, z, vx, vy, vz, sprite);
        this.maxAlpha = alpha;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = lifetime;
        this.age = 0;
        this.alpha = alpha;
        this.quadSize = scale;
        this.gravity = 0.0f;
        this.hasPhysics = false;
    }

    @Override
    public ParticleRenderType getGroup() {
        return ParticleRenderType.SINGLE_QUADS;
    }

    @Override
    public Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        this.alpha = (1.0f - (float) this.age / this.lifetime) * maxAlpha;
        this.xd *= 0.96;
        this.yd *= 0.96;
        this.zd *= 0.96;
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return 15728880; // Full bright
    }

    public static class Factory implements net.minecraft.client.particle.ParticleProvider<NunobokoParticleOptions> {
        private final FabricSpriteSet sprites;

        public Factory(FabricSpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public net.minecraft.client.particle.Particle createParticle(
                NunobokoParticleOptions options,
                ClientLevel level,
                double x, double y, double z,
                double vx, double vy, double vz,
                RandomSource random) {
            TextureAtlasSprite sprite = sprites.get(level.getRandom());
            return new NunobokoGlowParticle(
                    level, x, y, z, vx, vy, vz,
                    options.scale(), options.lifetime(), options.alpha(), sprite);
        }
    }
}
