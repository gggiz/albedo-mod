package com.albedo.item;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class AlbedoHornItem extends Item {

    private static final Identifier ARMOR_ID = Identifier.withDefaultNamespace("albedo_horn_armor");
    private static final AttributeModifier ARMOR_MODIFIER =
            new AttributeModifier(ARMOR_ID, 40.0, AttributeModifier.Operation.ADD_VALUE);

    public AlbedoHornItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, net.minecraft.world.entity.Entity entity, EquipmentSlot slot) {
        if (!(entity instanceof Player player)) return;

        AttributeInstance armorAttr = player.getAttribute(Attributes.ARMOR);
        if (armorAttr == null) return;

        if (slot == EquipmentSlot.HEAD) {
            // Apply armor while worn
            if (armorAttr.getModifier(ARMOR_ID) == null) {
                armorAttr.addTransientModifier(ARMOR_MODIFIER);
            }
            // Refresh effects
            player.addEffect(new MobEffectInstance(
                    MobEffects.NIGHT_VISION, 220, 0, false, false, true));
            player.addEffect(new MobEffectInstance(
                    MobEffects.STRENGTH, 220, 0, false, false, true));
            if (!player.hasEffect(MobEffects.ABSORPTION) ||
                    player.getEffect(MobEffects.ABSORPTION).getDuration() < 200) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.ABSORPTION, 400, 2, false, false, true));
            }
        } else if (armorAttr.getModifier(ARMOR_ID) != null) {
            // Remove armor when horn is taken off
            armorAttr.removeModifier(ARMOR_ID);
        }
    }
}
