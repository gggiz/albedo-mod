package com.albedo.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record RiftBlockParticleOptions(float scale, int lifetime, float alpha) implements ParticleOptions {

    public static final MapCodec<RiftBlockParticleOptions> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Codec.FLOAT.fieldOf("scale").forGetter(RiftBlockParticleOptions::scale),
                    Codec.INT.fieldOf("lifetime").forGetter(RiftBlockParticleOptions::lifetime),
                    Codec.FLOAT.fieldOf("alpha").forGetter(RiftBlockParticleOptions::alpha)
            ).apply(instance, RiftBlockParticleOptions::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RiftBlockParticleOptions> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, RiftBlockParticleOptions::scale,
                    ByteBufCodecs.INT, RiftBlockParticleOptions::lifetime,
                    ByteBufCodecs.FLOAT, RiftBlockParticleOptions::alpha,
                    RiftBlockParticleOptions::new
            );

    @Override
    public ParticleType<?> getType() {
        return AlbedoParticles.RIFT_BLOCK;
    }
}
