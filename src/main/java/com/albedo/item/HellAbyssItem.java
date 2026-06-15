package com.albedo.item;

import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;

public class HellAbyssItem extends AxeItem {
    private static final float ATTACK_DAMAGE = 14.0f;
    private static final float ATTACK_SPEED = -2.8f;

    private static final ToolMaterial HELL_ABYSS_MATERIAL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
            2500,
            10.0f,
            5.0f,
            22,
            ItemTags.NETHERITE_TOOL_MATERIALS
    );

    public HellAbyssItem(Properties properties) {
        super(HELL_ABYSS_MATERIAL, ATTACK_DAMAGE, ATTACK_SPEED, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.albedo.hell_abyss");
    }
}
