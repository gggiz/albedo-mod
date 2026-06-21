package com.albedo.item;

import com.albedo.particle.NunobokoParticleOptions;
import com.albedo.particle.RiftBlockParticleOptions;
import com.albedo.sound.AlbedoSounds;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SwordOfNunoboko extends Item {
    // === 空间裂缝（左键）===
    private static final int RIFT_COOLDOWN = 40;
    private static final double RIFT_RANGE = 5.0;
    private static final double RIFT_HEIGHT = 4.0;
    private static final double RIFT_WIDTH = 2.5;
    private static final float RIFT_DAMAGE = 25.0f;

    // === 十字奉火（右键）===
    private static final int CROSS_FIRE_COOLDOWN = 400;
    private static final int ORB_RISE_TICKS = 12;
    private static final int FIRE_PILLAR_TICKS = 25;
    private static final double CROSS_ARM_LENGTH = 6.0;
    private static final double PILLAR_HEIGHT = 8.0;
    private static final float CROSS_FIRE_DAMAGE = 50.0f;

    // === 上次 swingTime 追踪（静态Map，不用NBT避免持久化问题）===
    private static final Map<UUID, Integer> prevSwingMap = new HashMap<>();
    // === 右键时间追踪 ===
    private static final Map<UUID, Long> rightClickMap = new HashMap<>();

    // === 十字奉火动画状态 ===
    private static final Map<UUID, CrossFireState> activeFires = new HashMap<>();

    private static class CrossFireState {
        int tick = 0;
        Vec3 center;
        long lastTickTime;
        final Set<UUID> hitEntities = new HashSet<>();
    }

    public SwordOfNunoboko(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.albedo.sword_of_nunoboko");
    }

    // ==================== NBT helpers ====================

    private static CompoundTag getTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void updateTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static int getNbtInt(ItemStack stack, String key) {
        return getTag(stack).getInt(key).orElse(0);
    }

    private static void setNbtInt(ItemStack stack, String key, int val) {
        CompoundTag tag = getTag(stack);
        tag.putInt(key, val);
        updateTag(stack, tag);
    }

    private static long getNbtLong(ItemStack stack, String key) {
        return getTag(stack).getLong(key).orElse(0L);
    }

    private static void setNbtLong(ItemStack stack, String key, long val) {
        CompoundTag tag = getTag(stack);
        tag.putLong(key, val);
        updateTag(stack, tag);
    }

    // ==================== Left-click: 空间裂缝 ====================

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof Player player) {
            if (!player.level().isClientSide()) {
                ServerLevel sl = (ServerLevel) player.level();
                long gameTime = sl.getGameTime();
                long lastRift = getNbtLong(stack, "lastRift");
                if (gameTime - lastRift >= RIFT_COOLDOWN) {
                    setNbtLong(stack, "lastRift", gameTime);
                    Vec3 riftPos = target.position().add(0, target.getBbHeight() / 2, 0);
                    spawnSpatialRift(sl, riftPos, player.getLookAngle());
                    damageInRift(sl, player, riftPos, player.getLookAngle());
                    sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                            AlbedoSounds.NUNOBOKO_RIFT, SoundSource.PLAYERS, 1.0f, 1.0f);
                }
            }
        }
        super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;

        if (!level.isClientSide()) {
            ServerLevel sl = (ServerLevel) level;
            long gameTime = sl.getGameTime();
            long lastFire = getNbtLong(stack, "lastCrossFire");
            if (gameTime - lastFire < CROSS_FIRE_COOLDOWN) return InteractionResult.FAIL;

            setNbtLong(stack, "lastCrossFire", gameTime);
            rightClickMap.put(player.getUUID(), gameTime);
            player.getCooldowns().addCooldown(stack, CROSS_FIRE_COOLDOWN);

            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getLookAngle();
            Vec3 center = eye.add(look.scale(5.0));

            CrossFireState state = new CrossFireState();
            state.center = center;
            state.lastTickTime = gameTime;
            activeFires.put(player.getUUID(), state);

            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.8f, 0.1f);
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0f, 0.2f);
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    AlbedoSounds.NUNOBOKO_CROSS_FIRE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        return InteractionResult.SUCCESS;
    }

    // ==================== inventoryTick ====================

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (!(entity instanceof Player player)) return;

        // --- 装备语音：不在手上时重置标记，切回手上时播放 ---
        if (slot == null || (slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND)) {
            setNbtLong(stack, "equipTime", 0);
            return;
        }
        long equipTime = getNbtLong(stack, "equipTime");
        if (equipTime == 0) {
            setNbtLong(stack, "equipTime", level.getGameTime());
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    AlbedoSounds.NUNOBOKO_EQUIP, SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        // --- 检测左键（空间裂缝）---
        detectLeftClick(stack, level, player);

        // --- 处理十字奉火动画 ---
        UUID pid = player.getUUID();
        CrossFireState fireState = activeFires.get(pid);
        if (fireState == null) return;

        if (level.getGameTime() - fireState.lastTickTime > 200) {
            activeFires.remove(pid);
            return;
        }
        fireState.lastTickTime = level.getGameTime();
        fireState.tick++;

        int tick = fireState.tick;
        if (tick <= ORB_RISE_TICKS) {
            tickOrbRise(fireState, level, player);
        } else if (tick <= ORB_RISE_TICKS + FIRE_PILLAR_TICKS) {
            tickFirePillars(fireState, level, player);
        } else {
            activeFires.remove(pid);
        }
    }

    // ==================== 左键检测 ====================

    private void detectLeftClick(ItemStack stack, ServerLevel level, Player player) {
        int swingTime = player.swingTime;
        int prevSwing = prevSwingMap.getOrDefault(player.getUUID(), 0);
        prevSwingMap.put(player.getUUID(), swingTime);

        // 只在 swingTime 从 0 变为非 0 的第一 tick 触发
        if (prevSwing != 0 || swingTime <= 0) return;

        long gameTime = level.getGameTime();
        Long rcTick = rightClickMap.get(player.getUUID());
        long lastRift = getNbtLong(stack, "lastRift");

        if (rcTick != null && gameTime - rcTick <= 10) return;
        if (gameTime - lastRift < RIFT_COOLDOWN) return;

        setNbtLong(stack, "lastRift", gameTime);

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 riftPos = eye.add(look.scale(RIFT_RANGE / 2 + 1.0));

        spawnSpatialRift(level, riftPos, look);
        damageInRift(level, player, riftPos, look);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0f, 1.0f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                AlbedoSounds.NUNOBOKO_RIFT, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    // ==================== 空间裂缝视觉 ====================

    private static void spawnSpatialRift(ServerLevel level, Vec3 center, Vec3 facing) {
        Vec3 right = facing.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-10) right = new Vec3(1, 0, 0);
        right = right.normalize();

        double halfH = RIFT_HEIGHT / 2;
        double maxHalfW = RIFT_WIDTH / 2;

        // 黑色方块 — 纺锤形：两端细、中间粗
        // 纵向多层填充
        for (int layer = 0; layer < 25; layer++) {
            double yOff = -halfH + layer * RIFT_HEIGHT / 24;
            // sin曲线：两端宽度趋近0，中间最宽
            double yProgress = (yOff + halfH) / RIFT_HEIGHT;
            double layerHalfW = maxHalfW * Math.sin(Math.PI * yProgress);

            if (layerHalfW < 0.08) continue; // 两端太窄跳过

            // 每一层用方块粒子填充横向宽度
            int fillCount = (int)(layerHalfW * 8 + 2);
            for (int f = 0; f < fillCount; f++) {
                double xOff = (level.getRandom().nextDouble() - 0.5) * 2 * layerHalfW;
                double zOff = (level.getRandom().nextDouble() - 0.5) * 2 * layerHalfW * 0.4;
                Vec3 pos = center.add(0, yOff, 0)
                        .add(right.scale(xOff))
                        .add(new Vec3(0, 0, zOff));
                // 黑色方块粒子
                level.sendParticles(new RiftBlockParticleOptions(0.2f, 18, 1.0f),
                        pos.x, pos.y, pos.z, 1, 0, 0, 0, 0.01);
            }

            // 核心暗紫色发光点缀
            if (layer % 3 == 0) {
                level.sendParticles(NunobokoParticleOptions.GLOW,
                        center.x, center.y + yOff, center.z,
                        1, 0.03, 0.03, 0.03, 0.01);
            }
        }

        // 纺锤轮廓边缘 — 暗紫色描边
        for (int i = 0; i < 40; i++) {
            double yOff = -halfH + i * RIFT_HEIGHT / 39;
            double yProgress = (yOff + halfH) / RIFT_HEIGHT;
            double edgeR = maxHalfW * Math.sin(Math.PI * yProgress);
            if (edgeR < 0.08) continue;
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            Vec3 edgePos = center.add(0, yOff, 0)
                    .add(right.scale(Math.cos(angle) * edgeR))
                    .add(new Vec3(0, 0, Math.sin(angle) * edgeR * 0.4));
            level.sendParticles(NunobokoParticleOptions.SPARK,
                    edgePos.x, edgePos.y, edgePos.z, 1, 0.04, 0.04, 0.04, 0.02);
        }

        // 顶端和底端收束点
        Vec3 top = center.add(0, halfH, 0);
        Vec3 bottom = center.add(0, -halfH, 0);
        for (Vec3 p : new Vec3[]{top, bottom}) {
            level.sendParticles(new NunobokoParticleOptions(0.8f, 16, 1.0f),
                    p.x, p.y, p.z, 3, 0.1, 0.1, 0.1, 0.02);
            level.sendParticles(NunobokoParticleOptions.ORB,
                    p.x, p.y, p.z, 2, 0.08, 0.08, 0.08, 0.01);
        }

        // 吸入粒子 — 从前方拉向裂缝
        for (int i = 0; i < 12; i++) {
            double dist = 0.3 + level.getRandom().nextDouble() * 3.0;
            Vec3 pullPos = center.add(facing.scale(dist));
            pullPos = pullPos.add(right.scale((level.getRandom().nextDouble() - 0.5) * RIFT_WIDTH));
            pullPos = pullPos.add(0, (level.getRandom().nextDouble() - 0.5) * RIFT_HEIGHT, 0);
            level.sendParticles(NunobokoParticleOptions.TRAIL,
                    pullPos.x, pullPos.y, pullPos.z, 1, 0, 0, 0, 0);
        }
    }

    private static void damageInRift(ServerLevel level, Player player, Vec3 center, Vec3 facing) {
        AABB riftBox = new AABB(
                center.x - RIFT_WIDTH, center.y - RIFT_HEIGHT / 2, center.z - RIFT_WIDTH,
                center.x + RIFT_WIDTH, center.y + RIFT_HEIGHT / 2, center.z + RIFT_WIDTH
        ).expandTowards(facing.scale(RIFT_RANGE / 2))
         .expandTowards(facing.reverse().scale(RIFT_RANGE / 2));

        level.getEntities(player, riftBox,
                e -> e instanceof LivingEntity && e != player
        ).forEach(target -> {
            target.hurt(player.damageSources().magic(), RIFT_DAMAGE);
            Vec3 pull = center.subtract(target.position()).normalize().scale(1.2);
            target.setDeltaMovement(target.getDeltaMovement().add(pull.x, 0.3, pull.z));
            level.sendParticles(NunobokoParticleOptions.GLOW,
                    target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                    5, 0.3, 0.3, 0.3, 0.1);
        });
    }

    // ==================== 十字奉火 — Phase 0: 求道玉升起 ====================

    private void tickOrbRise(CrossFireState state, ServerLevel level, Player player) {
        double progress = (double) state.tick / ORB_RISE_TICKS;
        Vec3 center = state.center;
        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-10) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 forward = right.cross(new Vec3(0, 1, 0)).normalize();

        if (forward.dot(look) < 0) forward = forward.reverse();

        // 十字的4个方向：前、后、左、右
        Vec3[] directions = {forward, forward.reverse(), right, right.reverse()};

        double orbRadius = 3.0 + (1.0 - progress) * 2.0;
        double orbHeight = 1.5 + progress * 4.0;

        for (int i = 0; i < 4; i++) {
            double angle = state.tick * 20 + i * 90 * Math.PI / 180;
            Vec3 dir = directions[i];
            Vec3 orbPos = center.add(dir.scale(orbRadius)).add(0, orbHeight, 0);
            // 绕中心旋转偏移
            orbPos = orbPos.add(right.scale(Math.cos(angle) * 0.5 * (1.0 - progress)));

            float orbScale = (float)(1.0 + progress * 0.5);
            drawTruthSeekingBall(level, orbPos.x, orbPos.y, orbPos.z, orbScale);

            // 能量线从求道玉连向地面目标点
            Vec3 groundTarget = center.add(dir.scale(CROSS_ARM_LENGTH));
            drawLineParticles(level, orbPos, groundTarget, 10);
        }

        // 中心聚集点
        level.sendParticles(new NunobokoParticleOptions(1.5f * (float)progress, 22, 0.9f),
                center.x, center.y + orbHeight, center.z, 4, 0.3, 0.3, 0.3, 0.02);
        level.sendParticles(NunobokoParticleOptions.ORB,
                center.x, center.y + orbHeight, center.z, 3, 0.2, 0.2, 0.2, 0.01);

        if (state.tick % 4 == 0) {
            level.playSound(null, center.x, center.y, center.z,
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS,
                    0.5f, 0.15f + (float)progress * 0.3f);
        }

        if (state.tick == ORB_RISE_TICKS) {
            level.playSound(null, center.x, center.y, center.z,
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5f, 0.05f);
            level.playSound(null, center.x, center.y, center.z,
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0f, 0.03f);
        }
    }

    // ==================== 十字奉火 — Phase 1: 冲天火焰 ====================

    private void tickFirePillars(CrossFireState state, ServerLevel level, Player player) {
        int fireTick = state.tick - ORB_RISE_TICKS;
        double progress = (double) fireTick / FIRE_PILLAR_TICKS;
        double fade = Math.max(0, 1.0 - (progress - 0.4) / 0.6);

        Vec3 center = state.center;
        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-10) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 forward = right.cross(new Vec3(0, 1, 0)).normalize();
        if (forward.dot(look) < 0) forward = forward.reverse();

        // 十字的2条轴线
        Vec3[][] axes = {
            {center, center.add(forward.scale(CROSS_ARM_LENGTH))},           // 前-后
            {center.add(forward.reverse().scale(CROSS_ARM_LENGTH)), center}, // 后
            {center, center.add(right.scale(CROSS_ARM_LENGTH))},             // 左-右
            {center.add(right.reverse().scale(CROSS_ARM_LENGTH)), center}    // 左
        };

        // 十字火墙 — 沿两条轴线连续火焰上升
        for (Vec3[] axis : axes) {
            Vec3 start = axis[0];
            Vec3 end = axis[1];
            Vec3 delta = end.subtract(start);
            double len = delta.length();
            int wallSegments = (int)(len * 4);
            double pillarH = PILLAR_HEIGHT * (0.15 + progress * 0.85);

            for (int s = 0; s <= wallSegments; s++) {
                double t = (double) s / wallSegments;
                Vec3 base = start.add(delta.scale(t));

                // 每段火焰从地面向上延伸
                for (int h = 0; h < 18; h++) {
                    double y = h * pillarH / 17;
                    double spread = 0.15 + h * 0.04;
                    double px = base.x + (level.getRandom().nextDouble() - 0.5) * spread * 2;
                    double pz = base.z + (level.getRandom().nextDouble() - 0.5) * spread * 2;
                    double py = center.y + y;

                    level.sendParticles(ParticleTypes.FLAME,
                            px, py, pz, 1, 0.02, 0.12, 0.02, 0.02);
                }

                // 地面火焰
                if (s % 2 == 0) {
                    level.sendParticles(ParticleTypes.FLAME,
                            base.x, center.y + 0.05, base.z,
                            1, 0.01, 0.03, 0.01, 0.01);
                }
                // 暗色发光点缀
                if (s % 4 == 0) {
                    level.sendParticles(NunobokoParticleOptions.GLOW,
                            base.x, center.y + 0.08, base.z,
                            1, 0.02, 0.01, 0.02, 0.01);
                }
            }
        }

        // 中心交点 — 火焰汇聚最猛烈
        double centerPillarH = PILLAR_HEIGHT * 1.4 * (0.15 + progress * 0.85);
        for (int h = 0; h < 30; h++) {
            double y = h * centerPillarH / 29;
            double spread = 0.1 + h * 0.03;
            double px = center.x + (level.getRandom().nextDouble() - 0.5) * spread * 3;
            double pz = center.z + (level.getRandom().nextDouble() - 0.5) * spread * 3;
            double py = center.y + y;

            level.sendParticles(ParticleTypes.FLAME,
                    px, py, pz, 2, 0.03, 0.12, 0.03, 0.03);
        }
        // 中心暗色内核
        level.sendParticles(new NunobokoParticleOptions(1.8f, 8, (float)fade),
                center.x, center.y + 0.1, center.z, 3, 0.3, 0.15, 0.3, 0.04);

        // 4个端点砸地处暗色爆发
        Vec3[] tips = {
            center.add(forward.scale(CROSS_ARM_LENGTH)),
            center.add(forward.reverse().scale(CROSS_ARM_LENGTH)),
            center.add(right.scale(CROSS_ARM_LENGTH)),
            center.add(right.reverse().scale(CROSS_ARM_LENGTH))
        };
        for (Vec3 tip : tips) {
            Vec3 impactPos = new Vec3(tip.x, center.y, tip.z);
            if (fireTick <= 3) {
                level.sendParticles(new NunobokoParticleOptions(2.0f, 12, 1.0f),
                        impactPos.x, impactPos.y + 0.1, impactPos.z,
                        4, 0.4, 0.15, 0.4, 0.06);
                level.sendParticles(NunobokoParticleOptions.ORB,
                        impactPos.x, impactPos.y + 0.1, impactPos.z,
                        2, 0.2, 0.15, 0.2, 0.03);
            }
        }

        // 伤害检测
        if (fireTick <= 15) {
            AABB damageBox = new AABB(
                    center.x - CROSS_ARM_LENGTH, center.y, center.z - CROSS_ARM_LENGTH,
                    center.x + CROSS_ARM_LENGTH, center.y + PILLAR_HEIGHT, center.z + CROSS_ARM_LENGTH);
            level.getEntities(player, damageBox,
                    e -> e instanceof LivingEntity && e != player
            ).forEach(target -> {
                if (state.hitEntities.add(target.getUUID())) {
                    double dist = target.position().distanceTo(
                            new Vec3(center.x, target.getY(), center.z));
                    double dmgMult = 1.0 - Math.min(dist / CROSS_ARM_LENGTH, 1.0);
                    target.hurt(player.damageSources().magic(),
                            (float)(CROSS_FIRE_DAMAGE * dmgMult));
                    target.setDeltaMovement(target.getDeltaMovement().add(0, 1.2, 0));
                    target.setRemainingFireTicks(80);
                }
            });
        }

        if (fireTick == 1) {
            level.playSound(null, center.x, center.y, center.z,
                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 3.0f, 0.05f);
            level.playSound(null, center.x, center.y, center.z,
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.5f, 0.02f);
        }
        if (fireTick % 5 == 0) {
            level.playSound(null, center.x, center.y, center.z,
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS,
                    0.5f * (float)fade, 0.05f + fireTick * 0.02f);
        }
    }

    // ==================== 粒子辅助方法 ====================

    private void drawTruthSeekingBall(ServerLevel level, double x, double y, double z, float scale) {
        level.sendParticles(new NunobokoParticleOptions(1.5f * scale, 25, 1.0f),
                x, y, z, 3, 0.12 * scale, 0.12 * scale, 0.12 * scale, 0.01);
        level.sendParticles(new NunobokoParticleOptions(1.0f * scale, 20, 0.85f),
                x, y, z, 2, 0.15 * scale, 0.15 * scale, 0.15 * scale, 0.01);
        level.sendParticles(NunobokoParticleOptions.ORB,
                x, y, z, 2, 0.08 * scale, 0.08 * scale, 0.08 * scale, 0);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI * 2 / 6;
            double r = 0.5 * scale;
            level.sendParticles(new NunobokoParticleOptions(0.3f * scale, 12, 0.65f),
                    x + Math.cos(a) * r, y + Math.sin(a) * r, z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    private void drawLineParticles(ServerLevel level, Vec3 from, Vec3 to, int segments) {
        Vec3 delta = to.subtract(from);
        for (int i = 0; i <= segments; i++) {
            Vec3 p = from.add(delta.scale((double) i / segments));
            level.sendParticles(NunobokoParticleOptions.TRAIL,
                    p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }
}
