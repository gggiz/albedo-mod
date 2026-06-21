package com.albedo.particle;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public class NunobokoParticleType extends ParticleType<NunobokoParticleOptions> {
    public NunobokoParticleType(boolean overrideLimiter) {
        super(overrideLimiter);
    }

    @Override
    public MapCodec<NunobokoParticleOptions> codec() {
        return NunobokoParticleOptions.CODEC;
    }

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, NunobokoParticleOptions> streamCodec() {
        return NunobokoParticleOptions.STREAM_CODEC;
    }
}
