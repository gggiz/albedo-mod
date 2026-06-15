package com.albedo;

import com.albedo.entity.AlbedoBoss;
import com.albedo.item.AlbedoItems;
import com.albedo.sound.AlbedoSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlbedoMod implements ModInitializer {
    public static final String MOD_ID = "albedo";
    public static final Logger LOGGER = LoggerFactory.getLogger("Albedo");

    public static final EntityType<AlbedoBoss> ALBEDO = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceKey.create(Registries.ENTITY_TYPE, id("albedo")).identifier(),
            EntityType.Builder.of(AlbedoBoss::new, MobCategory.MONSTER)
                    .sized(0.7f, 2.3f)
                    .eyeHeight(2.05f)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("albedo")))
    );

    @Override
    public void onInitialize() {
        LOGGER.info("Albedo - Guardian Overseer initializing...");
        AlbedoItems.init();
        AlbedoSounds.init();
        FabricDefaultAttributeRegistry.register(ALBEDO, AlbedoBoss.createAttributes());
        LOGGER.info("Albedo boss ready.");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
