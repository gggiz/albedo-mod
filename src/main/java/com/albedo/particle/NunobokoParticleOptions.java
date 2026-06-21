package com.albedo.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 天沼矛自定义粒子参数：可指定粒子大小、生命、透明度。
 */
public record NunobokoParticleOptions(float scale, int lifetime, float alpha) implements ParticleOptions {

    public static final MapCodec<NunobokoParticleOptions> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Codec.FLOAT.fieldOf("scale").forGetter(NunobokoParticleOptions::scale),
                    Codec.INT.fieldOf("lifetime").forGetter(NunobokoParticleOptions::lifetime),
                    Codec.FLOAT.fieldOf("alpha").forGetter(NunobokoParticleOptions::alpha)
            ).apply(instance, NunobokoParticleOptions::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, NunobokoParticleOptions> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, NunobokoParticleOptions::scale,
                    ByteBufCodecs.INT, NunobokoParticleOptions::lifetime,
                    ByteBufCodecs.FLOAT, NunobokoParticleOptions::alpha,
                    NunobokoParticleOptions::new
            );

    public static final NunobokoParticleOptions ORB = new NunobokoParticleOptions(1.2f, 30, 1.0f);
    public static final NunobokoParticleOptions GLOW = new NunobokoParticleOptions(0.5f, 20, 0.9f);
    public static final NunobokoParticleOptions TRAIL = new NunobokoParticleOptions(0.3f, 15, 0.7f);
    public static final NunobokoParticleOptions SPARK = new NunobokoParticleOptions(0.15f, 10, 0.6f);

    @Override
    public ParticleType<?> getType() {
        return AlbedoParticles.NUNOBOKO_GLOW;
    }
}
