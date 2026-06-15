package com.albedo.sound;

import com.albedo.AlbedoMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public class AlbedoSounds {
    public static final SoundEvent SWEEP = register("entity.albedo.sweep");
    public static final SoundEvent THRUST = register("entity.albedo.thrust");
    public static final SoundEvent WAVE = register("entity.albedo.wave");
    public static final SoundEvent JUDGMENT = register("entity.albedo.judgment");
    public static final SoundEvent PHASE_CHANGE = register("entity.albedo.phase_change");
    public static final SoundEvent DEATH = register("entity.albedo.death");
    public static final SoundEvent HURT = register("entity.albedo.hurt");
    public static final SoundEvent AMBIENT = register("entity.albedo.ambient");

    private static SoundEvent register(String name) {
        Identifier id = AlbedoMod.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    public static void init() {
        // Static initializer
    }
}
