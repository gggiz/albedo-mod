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

    public static void init() {
        // Static initializer
    }
}
