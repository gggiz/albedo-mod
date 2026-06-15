package com.albedo.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public class HellAbyssItem extends AxeItem {
    private static final float ATTACK_DAMAGE = 10.0f;
    private static final float ATTACK_SPEED = -3.0f;

    public HellAbyssItem(Properties properties) {
        super(ToolMaterial.NETHERITE, ATTACK_DAMAGE, ATTACK_SPEED, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.albedo.hell_abyss");
    }
}
