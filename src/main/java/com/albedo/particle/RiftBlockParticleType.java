package com.albedo.particle;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public class RiftBlockParticleType extends ParticleType<RiftBlockParticleOptions> {
    public RiftBlockParticleType(boolean overrideLimiter) {
        super(overrideLimiter);
    }

    @Override
    public MapCodec<RiftBlockParticleOptions> codec() {
        return RiftBlockParticleOptions.CODEC;
    }

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, RiftBlockParticleOptions> streamCodec() {
        return RiftBlockParticleOptions.STREAM_CODEC;
    }
}
