package com.albedo.item;

import com.albedo.AlbedoMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;

public class AlbedoItems {
    public static final Item HELL_ABYSS = Registry.register(
            BuiltInRegistries.ITEM,
            AlbedoMod.id("hell_abyss"),
            new HellAbyssItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, AlbedoMod.id("hell_abyss")))
                    .stacksTo(1)
                    .fireResistant())
    );

    public static final Item SUCCUBUS_HORN = Registry.register(
            BuiltInRegistries.ITEM,
            AlbedoMod.id("succubus_horn"),
            new AlbedoHornItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, AlbedoMod.id("succubus_horn")))
                    .stacksTo(1)
                    .fireResistant()
                    .equippable(EquipmentSlot.HEAD))
    );

    public static final Item GUARDIAN_SHARD = Registry.register(
            BuiltInRegistries.ITEM,
            AlbedoMod.id("guardian_shard"),
            new Item(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, AlbedoMod.id("guardian_shard"))))
    );

    public static void init() {
        // Static initializer triggers registration
    }
}
