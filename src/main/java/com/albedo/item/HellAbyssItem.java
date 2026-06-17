package com.albedo.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class HellAbyssItem extends AxeItem {
    private static final float ATTACK_DAMAGE = 14.0f;
    private static final float ATTACK_SPEED = -2.8f;

    private static final ToolMaterial HELL_ABYSS_MATERIAL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
            9999,
            10.0f,
            5.0f,
            22,
            ItemTags.NETHERITE_TOOL_MATERIALS
    );

    // 深渊斩击波
    private static final int WARMUP_TICKS = 12;
    private static final int WAVE_TRAVEL_TICKS = 14;
    private static final double WAVE_SPEED = 1.1;
    private static final float WAVE_DAMAGE = 12.0f;
    private static final int COOLDOWN_TICKS = 22;

    private static final Map<UUID, WaveState> activeWaves = new HashMap<>();

    private static class WaveState {
        int tick = 0;
        Vec3 waveDir;
        final Set<UUID> hitEntities = new HashSet<>();
        long lastTickTime = 0;
    }

    public HellAbyssItem(Properties properties) {
        super(HELL_ABYSS_MATERIAL, ATTACK_DAMAGE, ATTACK_SPEED, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.albedo.hell_abyss");
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (target.getArmorValue() > 0) {
            target.hurt(attacker.damageSources().magic(), 40.0f);
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;

        if (!level.isClientSide()) {
            ServerLevel sl = (ServerLevel) level;
            Vec3 look = player.getLookAngle();
            WaveState state = new WaveState();
            state.waveDir = new Vec3(look.x, 0, look.z).normalize();
            state.lastTickTime = sl.getGameTime();
            activeWaves.put(player.getUUID(), state);
            player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);

            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND) return;
        if (!(entity instanceof Player player)) return;

        UUID pid = player.getUUID();
        WaveState state = activeWaves.get(pid);
        if (state == null) return;

        // 如果超过3秒没更新，丢弃过期状态
        if (level.getGameTime() - state.lastTickTime > 60) {
            activeWaves.remove(pid);
            return;
        }
        state.lastTickTime = level.getGameTime();
        state.tick++;

        if (state.tick <= WARMUP_TICKS) {
            Vec3 p = player.position().add(0, 1.2, 0);
            level.sendParticles(ParticleTypes.PORTAL,
                    p.x, p.y, p.z,
                    4, 0.5, 0.3, 0.5, 0.03);
        } else {
            int waveTick = state.tick - WARMUP_TICKS;
            if (waveTick <= WAVE_TRAVEL_TICKS) {
                double dist = waveTick * WAVE_SPEED;

                for (int i = -2; i <= 2; i++) {
                    double angle = i * Math.toRadians(18);
                    double cos = Math.cos(angle);
                    double sin = Math.sin(angle);
                    Vec3 dir = new Vec3(
                            state.waveDir.x * cos - state.waveDir.z * sin,
                            0,
                            state.waveDir.x * sin + state.waveDir.z * cos
                    );

                    Vec3 wavePos = player.position().add(0, 1.5, 0).add(dir.scale(dist));

                    level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                            wavePos.x, wavePos.y, wavePos.z,
                            1, 0.3, 0.1, 0.3, 0);
                    level.sendParticles(ParticleTypes.LARGE_SMOKE,
                            wavePos.x, wavePos.y + 0.3, wavePos.z,
                            2, 0.4, 0.1, 0.4, 0.01);
                    level.sendParticles(ParticleTypes.PORTAL,
                            wavePos.x, wavePos.y - 0.8, wavePos.z,
                            1, 0.2, 0, 0.2, 0);

                    level.getEntities(player,
                            AABB.ofSize(wavePos, 2.5, 2.0, 2.5),
                            e -> e instanceof LivingEntity && e != player
                    ).forEach(target -> {
                        if (state.hitEntities.add(target.getUUID())) {
                            target.hurt(player.damageSources().magic(), WAVE_DAMAGE);
                            level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                                    target.getX(), target.getY() + 1, target.getZ(),
                                    3, 0.2, 0.2, 0.2, 0.1);
                        }
                    });
                }
            }
        }

        if (state.tick >= WARMUP_TICKS + WAVE_TRAVEL_TICKS) {
            activeWaves.remove(pid);
        }
    }
}
