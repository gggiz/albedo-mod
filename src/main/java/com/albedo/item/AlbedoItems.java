package com.albedo.item;

import com.albedo.AlbedoMod;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.TypedEntityData;

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

    public static final Item GRAND_ARCANA_LIGHT_BREAKER = Registry.register(
            BuiltInRegistries.ITEM,
            AlbedoMod.id("grand_arcana_light_breaker"),
            new GrandArcanaLightBreaker(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, AlbedoMod.id("grand_arcana_light_breaker")))
                    .stacksTo(1)
                    .fireResistant())
    );

    public static Item ALBEDO_SPAWN_EGG;
    public static CreativeModeTab ALBEDO_TAB;

    public static void init() {
        ALBEDO_SPAWN_EGG = Registry.register(
                BuiltInRegistries.ITEM,
                ResourceKey.create(Registries.ITEM, AlbedoMod.id("albedo_spawn_egg")),
                new SpawnEggItem(new Item.Properties()
                        .setId(ResourceKey.create(Registries.ITEM, AlbedoMod.id("albedo_spawn_egg")))
                        .component(DataComponents.ENTITY_DATA, TypedEntityData.of(AlbedoMod.ALBEDO, new CompoundTag())))
        );

        ALBEDO_TAB = Registry.register(
                BuiltInRegistries.CREATIVE_MODE_TAB,
                ResourceKey.create(Registries.CREATIVE_MODE_TAB, AlbedoMod.id("albedo_tab")),
                CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                        .title(Component.translatable("itemGroup.albedo"))
                        .icon(() -> new ItemStack(ALBEDO_SPAWN_EGG))
                        .displayItems((params, output) -> {
                            output.accept(ALBEDO_SPAWN_EGG);
                            output.accept(HELL_ABYSS);
                            output.accept(SUCCUBUS_HORN);
                            output.accept(GUARDIAN_SHARD);
                            output.accept(GRAND_ARCANA_LIGHT_BREAKER);
                        })
                        .build()
        );
    }
}
