package com.albedo.particle;

import com.albedo.AlbedoMod;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

public class AlbedoParticles {
    public static final ShadowParticleType SHADOW = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE,
            AlbedoMod.id("shadow"),
            new ShadowParticleType()
    );

    public static final NunobokoParticleType NUNOBOKO_GLOW = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE,
            AlbedoMod.id("nunoboko_glow"),
            new NunobokoParticleType(false)
    );

    public static final RiftBlockParticleType RIFT_BLOCK = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE,
            AlbedoMod.id("rift_block"),
            new RiftBlockParticleType(false)
    );

    public static void init() {
        // Static initializer
    }
}
