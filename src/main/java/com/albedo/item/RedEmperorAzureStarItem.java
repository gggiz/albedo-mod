package com.albedo.item;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class RedEmperorAzureStarItem extends Item {
    private static final int WARMUP_TICKS = 15;
    private static final int FALL_TICKS = 200;
    private static final int EXPLODE_TICKS = 30;
    private static final int AFTERMATH_TICKS = 100;
    private static final double METEOR_START_HEIGHT = 25.0;
    private static final double FALL_SPEED = 0.125;
    private static final float DAMAGE = 1000.0f;
    private static final double DAMAGE_RADIUS = 12.0;
    private static final double BURN_RADIUS = 10.0;
    private static final float BURN_DAMAGE = 4.0f;
    private static final int COOLDOWN_TICKS = 400;
    private static final double MAX_RANGE = 40.0;

    private static final Map<UUID, MeteorState> activeMeteors = new HashMap<>();

    private static class MeteorState {
        int tick = 0;
        Vec3 targetPos;
        double meteorY;
        final Set<UUID> hitEntities = new HashSet<>();
        long lastTickTime = 0;
    }

    public RedEmperorAzureStarItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.albedo.red_emperor_azure_star");
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;

        if (!level.isClientSide()) {
            ServerLevel sl = (ServerLevel) level;
            HitResult hit = player.pick(MAX_RANGE, 0f, false);
            Vec3 target;
            if (hit.getType() == HitResult.Type.BLOCK) {
                target = hit.getLocation();
            } else {
                Vec3 look = player.getLookAngle();
                target = player.position().add(look.x * MAX_RANGE, 1.0, look.z * MAX_RANGE);
            }

            MeteorState state = new MeteorState();
            state.targetPos = target;
            state.meteorY = target.y + METEOR_START_HEIGHT;
            state.lastTickTime = sl.getGameTime();
            activeMeteors.put(player.getUUID(), state);

            player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);

            sl.playSound(null, target.x, target.y, target.z,
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0f, 0.2f);
            sl.playSound(null, target.x, target.y, target.z,
                    SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.5f, 0.5f);
            sl.playSound(null, target.x, target.y, target.z,
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.8f, 0.1f);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND) return;
        if (entity instanceof Player player) {
            tickPlayerMeteors(level, player);
        }
    }

    public static void tickPlayerMeteors(ServerLevel level, Player player) {
        UUID pid = player.getUUID();
        MeteorState state = activeMeteors.get(pid);
        if (state == null) return;

        // Avoid double-ticking within the same game tick
        if (state.lastTickTime >= level.getGameTime()) return;

        if (level.getGameTime() - state.lastTickTime > 400) {
            activeMeteors.remove(pid);
            return;
        }
        state.lastTickTime = level.getGameTime();
        state.tick++;

        Vec3 tp = state.targetPos;

        if (state.tick <= WARMUP_TICKS) {
            tickWarmup(state, level, tp);
        } else if (state.tick <= WARMUP_TICKS + FALL_TICKS) {
            tickFall(state, level, tp, state.tick - WARMUP_TICKS);
        } else if (state.tick <= WARMUP_TICKS + FALL_TICKS + EXPLODE_TICKS) {
            tickExplode(state, level, tp, state.tick - WARMUP_TICKS - FALL_TICKS, player);
        } else if (state.tick <= WARMUP_TICKS + FALL_TICKS + EXPLODE_TICKS + AFTERMATH_TICKS) {
            tickAftermath(state, level, tp, state.tick - WARMUP_TICKS - FALL_TICKS - EXPLODE_TICKS);
        } else {
            activeMeteors.remove(pid);
        }
    }

    // ===== Phase 1: Warmup — Energy Convergence =====
    private static void tickWarmup(MeteorState state, ServerLevel level, Vec3 tp) {
        double progress = (double) state.tick / WARMUP_TICKS;
        double mx = tp.x, my = state.meteorY, mz = tp.z;

        // === Ground: massive multi-ring scorch circle ===
        for (int ring = 0; ring < 5; ring++) {
            double baseR = 2.0 + ring * 1.5 + progress * 3.0;
            int pts = 24 + ring * 12;
            for (int i = 0; i < pts; i++) {
                double a = i * 2 * Math.PI / pts;
                double wobble = Math.sin(state.tick * 0.4 + i * 0.3) * 0.3;
                double r = baseR + wobble;
                // Outer rings: red-orange
                level.sendParticles(new DustParticleOptions(0xFF2200, 1.0f + ring * 0.3f),
                        tp.x + Math.cos(a) * r, tp.y + 0.05, tp.z + Math.sin(a) * r,
                        1, 0, 0, 0, 0);
                // Inner ring pulsing: gold
                if (ring < 2) {
                    level.sendParticles(new DustParticleOptions(0xFFAA00, 0.8f),
                            tp.x + Math.cos(a) * (r * 0.5), tp.y + 0.08, tp.z + Math.sin(a) * (r * 0.5),
                            1, 0, 0, 0, 0);
                }
            }
        }
        // Ground center glow intensifying
        for (int i = 0; i < 15; i++) {
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            double r = level.getRandom().nextDouble() * 2.0 * progress;
            level.sendParticles(ParticleTypes.END_ROD,
                    tp.x + Math.cos(a) * r, tp.y + 0.1, tp.z + Math.sin(a) * r,
                    1, 0, 0.02, 0, 0);
        }

        // === Sky core: massive energy convergence ===
        // Blue outer nebula
        for (int i = 0; i < 20; i++) {
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            double r = 6.0 * (1.0 - progress * 0.5) + level.getRandom().nextGaussian() * 1.5;
            double h = level.getRandom().nextGaussian() * 2.0;
            level.sendParticles(ParticleTypes.PORTAL,
                    mx + Math.cos(a) * r, my + h, mz + Math.sin(a) * r,
                    3, 0.1, 0.08, 0.1, 0.03);
            level.sendParticles(new DustParticleOptions(0x3355FF, 1.5f),
                    mx + Math.cos(a) * r, my + h, mz + Math.sin(a) * r,
                    2, 0.08, 0.05, 0.08, 0.02);
        }
        // White-hot core forming
        level.sendParticles(ParticleTypes.END_ROD,
                mx, my, mz,
                8, 0.5, 0.4, 0.5, 0.01);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                mx, my, mz,
                4, 0.4, 0.3, 0.4, 0.02);
        // Red-gold blaze growing
        level.sendParticles(new DustParticleOptions(0xFF3300, 3.0f * (float)progress),
                mx, my, mz,
                5, 0.5, 0.4, 0.5, 0.02);
        level.sendParticles(new DustParticleOptions(0xFFD700, 2.5f * (float)progress),
                mx, my, mz,
                4, 0.4, 0.3, 0.4, 0.02);
        level.sendParticles(new DustParticleOptions(0xFFFFFF, 4.0f * (float)progress),
                mx, my, mz,
                3, 0.3, 0.2, 0.3, 0.01);

        // === Triple spiral layers of energy converging inward ===
        for (int layer = 0; layer < 3; layer++) {
            double spiralR = (6.0 - layer * 1.5) * (1.0 - progress);
            double rotSpeed = 8 + layer * 3;
            double heightSpread = 4.0 - layer * 1.0;
            for (int i = 0; i < 16; i++) {
                double angle = (state.tick * rotSpeed + i * 22.5) * Math.PI / 180;
                double sy = my + Math.sin(i * 0.7 + layer) * heightSpread;
                double sx = mx + Math.cos(angle) * spiralR;
                double sz = mz + Math.sin(angle) * spiralR;
                Vec3 toCore = new Vec3(mx - sx, my - sy, mz - sz).normalize();
                double speed = 0.15 + layer * 0.05;
                level.sendParticles(layer == 0 ? ParticleTypes.PORTAL :
                                layer == 1 ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.END_ROD,
                        sx, sy, sz,
                        2, toCore.x * speed, toCore.y * speed, toCore.z * speed, 0.03);
                level.sendParticles(new DustParticleOptions(
                                layer == 0 ? 0x4D66FF : layer == 1 ? 0x8844FF : 0xFFAA44, 1.2f),
                        sx, sy, sz,
                        1, toCore.x * speed * 0.7, toCore.y * speed * 0.7, toCore.z * speed * 0.7, 0.02);
            }
        }

        // === Vertical energy columns descending from core to ground ===
        for (int col = 0; col < 8; col++) {
            double ca = col * Math.PI * 2 / 8;
            double cr = 1.5 * (1.0 - progress);
            double cx = mx + Math.cos(ca) * cr;
            double cz = mz + Math.sin(ca) * cr;
            for (int s = 0; s < 6; s++) {
                double t = s / 5.0;
                double cy = my - t * (my - tp.y);
                level.sendParticles(ParticleTypes.END_ROD,
                        cx, cy, cz,
                        1, 0.02, 0, 0.02, 0);
            }
        }

        // Sound: deepening rumble
        if (state.tick % 4 == 0) {
            level.playSound(null, tp.x, tp.y, tp.z,
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.3f, (float)(0.2 + progress * 0.3));
        }
    }

    // ===== Phase 2: Meteor Falling =====
    private static void tickFall(MeteorState state, ServerLevel level, Vec3 tp, int fallTick) {
        state.meteorY = tp.y + METEOR_START_HEIGHT - fallTick * FALL_SPEED;
        double mx = tp.x;
        double my = state.meteorY;
        double mz = tp.z;
        double fallProgress = (double) fallTick / FALL_TICKS;
        double heightRatio = (state.meteorY - tp.y) / METEOR_START_HEIGHT;

        // === Ground: expanding, intensifying warning rings ===
        double warnR = 4.0 + fallProgress * 8.0 + Math.sin(fallTick * 0.3) * 3.0;
        for (int ring = 0; ring < 6; ring++) {
            double rr = warnR + ring * 3.0;
            int pts = 36 + ring * 8;
            for (int i = 0; i < pts; i++) {
                double a = i * 2 * Math.PI / pts;
                double wobble = Math.sin(fallTick * 0.5 + i * 0.2) * 0.5;
                level.sendParticles(new DustParticleOptions(0xFF1A0A, 1.5f + ring * 0.2f),
                        tp.x + Math.cos(a) * (rr + wobble), tp.y + 0.05, tp.z + Math.sin(a) * (rr + wobble),
                        1, 0, 0, 0, 0);
            }
        }
        // Ground center getting brighter
        for (int i = 0; i < 10; i++) {
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            double r = level.getRandom().nextDouble() * warnR * 0.6;
            level.sendParticles(ParticleTypes.FLAME,
                    tp.x + Math.cos(a) * r, tp.y + 0.1, tp.z + Math.sin(a) * r,
                    1, 0.03, 0.02, 0.03, 0.01);
            level.sendParticles(ParticleTypes.END_ROD,
                    tp.x + Math.cos(a) * r, tp.y + 0.15, tp.z + Math.sin(a) * r,
                    1, 0.02, 0.01, 0.02, 0);
        }
        // Rising heat shimmer from ground
        for (int i = 0; i < 8; i++) {
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            double r = level.getRandom().nextDouble() * warnR;
            level.sendParticles(ParticleTypes.FALLING_WATER,
                    tp.x + Math.cos(a) * r, tp.y + 0.5, tp.z + Math.sin(a) * r,
                    1, 0.02, 0.06, 0.02, 0);
        }

        // === METEOR CORE — blazing red-gold-white star ===
        double coreSize = 1.0 + fallProgress * 1.5;
        level.sendParticles(ParticleTypes.END_ROD,
                mx, my, mz,
                10, coreSize, coreSize * 0.8, coreSize, 0.005);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                mx, my, mz,
                6, coreSize * 0.8, coreSize * 0.6, coreSize * 0.8, 0.01);
        level.sendParticles(ParticleTypes.FLAME,
                mx, my, mz,
                8, coreSize * 0.6, coreSize * 0.5, coreSize * 0.6, 0.015);
        // Color layers: white center → gold → orange → red
        level.sendParticles(new DustParticleOptions(0xFFFFFF, 5.0f),
                mx, my, mz,
                5, 0.3, 0.2, 0.3, 0.01);
        level.sendParticles(new DustParticleOptions(0xFFD700, 4.0f),
                mx, my, mz,
                6, 0.5, 0.4, 0.5, 0.015);
        level.sendParticles(new DustParticleOptions(0xFF6600, 3.5f),
                mx, my, mz,
                8, 0.7, 0.5, 0.7, 0.02);
        level.sendParticles(new DustParticleOptions(0xFF2200, 3.0f),
                mx, my, mz,
                8, 0.9, 0.6, 0.9, 0.025);

        // === Massive flame tail ===
        double trailLen = 8.0 + fallProgress * 4.0;
        for (int s = 0; s < 20; s++) {
            double t = s / 19.0;
            double ty = my + t * trailLen;
            double spread = t * 1.2 + 0.3;
            // Red-orange flame
            level.sendParticles(ParticleTypes.FLAME,
                    mx + level.getRandom().nextGaussian() * spread,
                    ty,
                    mz + level.getRandom().nextGaussian() * spread,
                    3, 0.08, 0.2, 0.08, 0.03);
            // Gold sparks in trail
            level.sendParticles(new DustParticleOptions(0xFFAA00, 2.0f),
                    mx + level.getRandom().nextGaussian() * spread * 0.7,
                    ty,
                    mz + level.getRandom().nextGaussian() * spread * 0.7,
                    2, 0.06, 0.12, 0.06, 0.02);
            // White-hot core of tail
            level.sendParticles(ParticleTypes.END_ROD,
                    mx + level.getRandom().nextGaussian() * spread * 0.3,
                    ty,
                    mz + level.getRandom().nextGaussian() * spread * 0.3,
                    2, 0.03, 0.06, 0.03, 0.01);
        }

        // === Blue-purple ley-line vortex spiraling around meteor ===
        for (int layer = 0; layer < 5; layer++) {
            double spiralR = 1.2 + layer * 0.8;
            double speed = 10 + layer * 2;
            double heightOff = (layer - 2) * 2.0;
            for (int i = 0; i < 3; i++) {
                double angle = (fallTick * speed + i * 120 + layer * 72) * Math.PI / 180;
                double sx = mx + Math.cos(angle) * spiralR;
                double sy = my + heightOff + Math.sin(fallTick * 0.5 + layer) * 1.5;
                double sz = mz + Math.sin(angle) * spiralR;
                level.sendParticles(ParticleTypes.PORTAL,
                        sx, sy, sz,
                        3, 0.12, 0.08, 0.12, 0.015);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        sx, sy, sz,
                        2, 0.08, 0.05, 0.08, 0.01);
                level.sendParticles(new DustParticleOptions(0x6633FF, 1.5f),
                        sx, sy, sz,
                        2, 0.06, 0.04, 0.06, 0.01);
            }
        }

        // === 24 dragon death light beams converging from all directions ===
        for (int b = 0; b < 24; b++) {
            double beamAngle = (b * 15.0 + fallTick * 0.8) * Math.PI / 180;
            double beamDist = 16.0 + Math.sin(fallTick * 0.15 + b) * 6.0;
            double srcX = tp.x + Math.cos(beamAngle) * beamDist;
            double srcY = my + 8.0 + Math.sin(b * 2.5) * 5.0;
            double srcZ = tp.z + Math.sin(beamAngle) * beamDist;
            // END_ROD beam core
            for (int s = 0; s < 12; s++) {
                double t = s / 11.0;
                level.sendParticles(ParticleTypes.END_ROD,
                        srcX + (mx - srcX) * t,
                        srcY + (my - srcY) * t,
                        srcZ + (mz - srcZ) * t,
                        2, 0.02, 0.02, 0.02, 0);
            }
            // Red energy pulsing along beams
            for (int s = 0; s < 6; s++) {
                double t = s / 5.0;
                level.sendParticles(new DustParticleOptions(0xFF3300, 1.0f),
                        srcX + (mx - srcX) * t,
                        srcY + (my - srcY) * t,
                        srcZ + (mz - srcZ) * t,
                        1, 0, 0, 0, 0);
            }
            // Blue-purple ley energy on alternating beams
            if (b % 3 == 0) {
                for (int s = 0; s < 8; s++) {
                    double t = s / 7.0;
                    level.sendParticles(ParticleTypes.PORTAL,
                            srcX + (mx - srcX) * t,
                            srcY + (my - srcY) * t,
                            srcZ + (mz - srcZ) * t,
                            1, 0, 0, 0, 0);
                }
            }
        }

        // === Vertical light pillars through meteor to ground ===
        for (int pillar = 0; pillar < 5; pillar++) {
            double px = mx + (pillar - 2) * 0.6;
            double pz = mz + (pillar - 2) * 0.3;
            double topY = my + 3;
            for (int y = 0; y < (my - tp.y + 8) * 2; y++) {
                double by = tp.y + y * 0.3;
                if (by < topY) {
                    level.sendParticles(ParticleTypes.END_ROD,
                            px + level.getRandom().nextGaussian() * 0.1,
                            by,
                            pz + level.getRandom().nextGaussian() * 0.1,
                            2, 0.02, 0, 0.02, 0);
                }
            }
        }

        // === Orbiting electric sparks ===
        for (int i = 0; i < 8; i++) {
            double orbitAngle = (fallTick * 15 + i * 45) * Math.PI / 180;
            double orbitR = 2.5;
            double oy = my + Math.sin(fallTick * 0.3 + i) * 2.0;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    mx + Math.cos(orbitAngle) * orbitR,
                    oy,
                    mz + Math.sin(orbitAngle) * orbitR,
                    1, 0, 0, 0, 0);
        }

        // === Sound design ===
        if (fallTick % 5 == 0) {
            float pitch = (float)(0.1 + fallProgress * 0.8);
            level.playSound(null, tp.x, tp.y, tp.z,
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.3f, pitch);
        }
        if (fallTick % 12 == 0) {
            level.playSound(null, tp.x, tp.y, tp.z,
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.2f, (float)(0.5 + fallProgress));
        }
        if (fallTick >= FALL_TICKS - 20 && fallTick % 2 == 0) {
            level.playSound(null, tp.x, tp.y, tp.z,
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.15f, (float)(1.5 + fallProgress * 0.5));
        }
    }

    // ===== Phase 3: Impact Explosion =====
    private static void tickExplode(MeteorState state, ServerLevel level, Vec3 tp, int explodeTick, Player player) {
        double progress = (double) explodeTick / EXPLODE_TICKS;
        double expandPhase = Math.min(progress * 3.0, 1.0);       // rapid expansion
        double sustainPhase = Math.max(0, (progress - 0.25) / 0.75); // fade after 25%
        double pulse2 = Math.sin(progress * Math.PI * 2) * 0.5 + 0.5; // secondary pulse wave

        if (explodeTick == 1) {
            // === DAMAGE ===
            level.getEntities(player,
                    AABB.ofSize(tp, DAMAGE_RADIUS * 2, DAMAGE_RADIUS * 2, DAMAGE_RADIUS * 2),
                    e -> e instanceof LivingEntity && e != player
            ).forEach(target -> {
                if (state.hitEntities.add(target.getUUID())) {
                    target.hurt(player.damageSources().magic(), DAMAGE);
                }
            });

            // Screen shake — violent
            player.setDeltaMovement(player.getDeltaMovement().add(
                    (level.getRandom().nextDouble() - 0.5) * 4.0, 1.2,
                    (level.getRandom().nextDouble() - 0.5) * 4.0));

            // === DEAFENING IMPACT ===
            level.playSound(null, tp.x, tp.y, tp.z,
                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4.0f, 0.15f);
            level.playSound(null, tp.x, tp.y, tp.z,
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0f, 0.03f);
            level.playSound(null, tp.x, tp.y, tp.z,
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0f, 0.08f);
            level.playSound(null, tp.x, tp.y, tp.z,
                    SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 2.0f, 0.2f);
            level.playSound(null, tp.x, tp.y, tp.z,
                    SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 2.0f, 0.1f);
        }

        // Secondary shake pulses
        if (explodeTick == 8 || explodeTick == 16) {
            player.setDeltaMovement(player.getDeltaMovement().add(
                    (level.getRandom().nextDouble() - 0.5) * 1.5,
                    0.3,
                    (level.getRandom().nextDouble() - 0.5) * 1.5));
        }

        // ==========================================
        // LAYER 1: BLINDING WHITE FLASH
        // ==========================================
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                tp.x, tp.y + 2, tp.z,
                (int)(15 + expandPhase * 40), 8.0 * expandPhase, 7.0 * expandPhase, 8.0 * expandPhase, 1.0);

        // ==========================================
        // LAYER 2: 3 CONCENTRIC SPHERICAL SHELLS expanding at different speeds
        // ==========================================
        for (int shell = 0; shell < 3; shell++) {
            double shellSpeed = 1.0 + shell * 0.6;
            double shellR = expandPhase * 35.0 * shellSpeed;
            if (shellR > 35.0) shellR = 35.0;
            int latCount = 14 + shell * 4;
            for (int lat = 0; lat < latCount; lat++) {
                double pitch = lat * Math.PI / (latCount - 1);
                double sp = Math.sin(pitch);
                double cp = Math.cos(pitch);
                int lc = 10 + (int)(sp * 25) + shell * 5;
                for (int lon = 0; lon < lc; lon++) {
                    double yaw = lon * Math.PI * 2 / lc;
                    double dx = sp * Math.cos(yaw);
                    double dy = cp;
                    double dz = sp * Math.sin(yaw);
                    double r = shellR * (0.8 + level.getRandom().nextDouble() * 0.4);
                    // Shell 0: pure white END_ROD
                    // Shell 1: gold-red FLAME + dust
                    // Shell 2: blue SOUL_FIRE_FLAME
                    if (shell == 0) {
                        level.sendParticles(ParticleTypes.END_ROD,
                                tp.x + dx * r, tp.y + dy * r, tp.z + dz * r,
                                3, 0.3, 0.3, 0.3, 0);
                    } else if (shell == 1) {
                        level.sendParticles(ParticleTypes.FLAME,
                                tp.x + dx * r, tp.y + dy * r, tp.z + dz * r,
                                2, 0.2, 0.2, 0.2, 0.02);
                        level.sendParticles(new DustParticleOptions(0xFFD700, 2.5f),
                                tp.x + dx * r, tp.y + dy * r, tp.z + dz * r,
                                1, 0.1, 0.1, 0.1, 0.01);
                    } else {
                        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                                tp.x + dx * r, tp.y + dy * r, tp.z + dz * r,
                                2, 0.2, 0.2, 0.2, 0.03);
                        level.sendParticles(new DustParticleOptions(0x4D66FF, 2.0f),
                                tp.x + dx * r, tp.y + dy * r, tp.z + dz * r,
                                1, 0.1, 0.1, 0.1, 0.02);
                    }
                }
            }
        }

        // Additional surface-layer sphere: ELECTRIC_SPARK covering the outer shell
        double sparkR = expandPhase * 32.0;
        for (int i = 0; i < 60; i++) {
            double lat = level.getRandom().nextDouble() * Math.PI;
            double lon = level.getRandom().nextDouble() * Math.PI * 2;
            double dx = Math.sin(lat) * Math.cos(lon);
            double dy = Math.cos(lat);
            double dz = Math.sin(lat) * Math.sin(lon);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    tp.x + dx * sparkR, tp.y + dy * sparkR, tp.z + dz * sparkR,
                    1, 0.05, 0.05, 0.05, 0);
        }

        // ==========================================
        // LAYER 3: MUSHROOM CLOUD rising
        // ==========================================
        double cloudBase = tp.y + 3.0 + expandPhase * 18.0;
        double cloudR = expandPhase * 20.0;
        // Volumetric smoke rings at multiple heights
        for (int ring = 0; ring < 5; ring++) {
            double ry = cloudBase + ring * 3.0 * expandPhase;
            double rr = cloudR * (1.0 - ring * 0.15);
            for (int i = 0; i < 40; i++) {
                double a = i * 9 * Math.PI / 180;
                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        tp.x + Math.cos(a) * rr, ry, tp.z + Math.sin(a) * rr,
                        1, 0.3, 0.15, 0.3, 0.02);
                level.sendParticles(ParticleTypes.CLOUD,
                        tp.x + Math.cos(a) * (rr * 0.7), ry, tp.z + Math.sin(a) * (rr * 0.7),
                        1, 0.2, 0.1, 0.2, 0.01);
            }
        }
        // Smoke column rising in center
        for (int i = 0; i < 30; i++) {
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            double r = level.getRandom().nextDouble() * 4.0 * expandPhase;
            double h = level.getRandom().nextDouble() * cloudBase;
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    tp.x + Math.cos(a) * r, tp.y + h, tp.z + Math.sin(a) * r,
                    2, 0.15, 0.3, 0.15, 0.04);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    tp.x + Math.cos(a) * r, tp.y + h + 0.5, tp.z + Math.sin(a) * r,
                    1, 0.05, 0.05, 0.05, 0.01);
        }
        // Rolling ash clouds at top of mushroom
        for (int i = 0; i < 20; i++) {
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            double r = cloudR * (0.5 + level.getRandom().nextDouble() * 0.5);
            level.sendParticles(ParticleTypes.CLOUD,
                    tp.x + Math.cos(a) * r, cloudBase + level.getRandom().nextGaussian() * 3.0,
                    tp.z + Math.sin(a) * r,
                    2, 0.4, 0.2, 0.4, 0.03);
        }

        // ==========================================
        // LAYER 4: FIRE TORNADO — spinning flame vortex at center
        // ==========================================
        double tornadoH = expandPhase * 20.0;
        for (int y = 0; y < tornadoH * 3; y++) {
            double ty = tp.y + y * 0.3;
            double tProgress = ty / (tp.y + tornadoH);
            double tornadoR = 1.5 + tProgress * 4.0 * expandPhase;
            double spinAngle = (y * 25 + explodeTick * 20) * Math.PI / 180;
            for (int arm = 0; arm < 4; arm++) {
                double aa = spinAngle + arm * Math.PI / 2;
                level.sendParticles(ParticleTypes.FLAME,
                        tp.x + Math.cos(aa) * tornadoR, ty, tp.z + Math.sin(aa) * tornadoR,
                        2, 0.1, 0.1, 0.1, 0.03);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        tp.x + Math.cos(aa + 0.3) * tornadoR * 0.7, ty,
                        tp.z + Math.sin(aa + 0.3) * tornadoR * 0.7,
                        1, 0.07, 0.07, 0.07, 0.02);
            }
            // Inner core: END_ROD + gold dust
            level.sendParticles(ParticleTypes.END_ROD,
                    tp.x + level.getRandom().nextGaussian() * 0.5,
                    ty,
                    tp.z + level.getRandom().nextGaussian() * 0.5,
                    2, 0.05, 0.05, 0.05, 0.01);
            level.sendParticles(new DustParticleOptions(0xFFD700, 1.5f),
                    tp.x + level.getRandom().nextGaussian() * 0.3,
                    ty,
                    tp.z + level.getRandom().nextGaussian() * 0.3,
                    1, 0.03, 0.03, 0.03, 0.01);
        }

        // ==========================================
        // LAYER 5: GROUND SHOCKWAVE RINGS (10 layers)
        // ==========================================
        // Outer red-gold (赤金)
        for (int ring = 0; ring < 8; ring++) {
            double rr = expandPhase * 28.0 + ring * 3.5;
            for (int i = 0; i < 72; i++) {
                double a = i * 5 * Math.PI / 180;
                level.sendParticles(ParticleTypes.FLAME,
                        tp.x + Math.cos(a) * rr, tp.y + 0.5 + ring * 0.15, tp.z + Math.sin(a) * rr,
                        3, 0.25, 0.15, 0.25, 0.06);
                level.sendParticles(new DustParticleOptions(0xFF4400, 3.0f),
                        tp.x + Math.cos(a) * rr, tp.y + 0.25 + ring * 0.1, tp.z + Math.sin(a) * rr,
                        2, 0.12, 0.06, 0.12, 0.03);
                level.sendParticles(new DustParticleOptions(0xFFAA00, 2.5f),
                        tp.x + Math.cos(a) * rr, tp.y + 0.4, tp.z + Math.sin(a) * rr,
                        2, 0.08, 0.04, 0.08, 0.02);
            }
        }
        // Inner blue-white (苍青)
        for (int ring = 0; ring < 6; ring++) {
            double rr = expandPhase * 18.0 + ring * 3.0;
            for (int i = 0; i < 60; i++) {
                double a = i * 6 * Math.PI / 180;
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        tp.x + Math.cos(a) * rr, tp.y + 0.35 + ring * 0.12, tp.z + Math.sin(a) * rr,
                        4, 0.18, 0.12, 0.18, 0.05);
                level.sendParticles(new DustParticleOptions(0x4D66FF, 3.5f),
                        tp.x + Math.cos(a) * rr, tp.y + 0.18 + ring * 0.08, tp.z + Math.sin(a) * rr,
                        3, 0.1, 0.05, 0.1, 0.03);
                level.sendParticles(new DustParticleOptions(0x88AAFF, 2.5f),
                        tp.x + Math.cos(a) * rr, tp.y + 0.3, tp.z + Math.sin(a) * rr,
                        2, 0.06, 0.03, 0.06, 0.02);
                // White sparks at leading edge
                if (i % 4 == 0) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            tp.x + Math.cos(a) * rr, tp.y + 0.4, tp.z + Math.sin(a) * rr,
                            1, 0.05, 0.03, 0.05, 0.01);
                }
            }
        }
        // Secondary pulse ring (pulses and contracts)
        if (explodeTick > 5 && explodeTick < 20) {
            double pulseR = pulse2 * 12.0;
            for (int i = 0; i < 32; i++) {
                double a = i * 11.25 * Math.PI / 180;
                level.sendParticles(new DustParticleOptions(0xFFFFFF, 2.0f),
                        tp.x + Math.cos(a) * pulseR, tp.y + 0.6, tp.z + Math.sin(a) * pulseR,
                        1, 0, 0, 0, 0);
            }
        }

        // ==========================================
        // LAYER 6: GROUND RIPPLES (concentric, like water rings)
        // ==========================================
        for (int ripple = 0; ripple < 20; ripple++) {
            double rr = (ripple * 1.5 + expandPhase * 30.0) % 30.0;
            for (int i = 0; i < 6; i++) {
                double a = i * 60 * Math.PI / 180 + ripple * 0.3;
                level.sendParticles(new DustParticleOptions(0xFF6600, 1.0f),
                        tp.x + Math.cos(a) * rr, tp.y + 0.02, tp.z + Math.sin(a) * rr,
                        1, 0, 0.01, 0, 0);
            }
        }

        // ==========================================
        // LAYER 7: HEMISPHERICAL BLUE-WHITE DOME ERUPTION
        // ==========================================
        for (int i = 0; i < 60; i++) {
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            double r = level.getRandom().nextDouble() * expandPhase * 22.0;
            double h = level.getRandom().nextDouble() * 10.0 * expandPhase;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    tp.x + Math.cos(a) * r, tp.y + h, tp.z + Math.sin(a) * r,
                    4, 0.25, 0.35, 0.25, 0.06);
            level.sendParticles(ParticleTypes.END_ROD,
                    tp.x + Math.cos(a) * r, tp.y + h, tp.z + Math.sin(a) * r,
                    3, 0.2, 0.25, 0.2, 0.05);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    tp.x + Math.cos(a) * r, tp.y + h, tp.z + Math.sin(a) * r,
                    2, 0.15, 0.2, 0.15, 0.04);
        }

        // ==========================================
        // LAYER 8: PARTICLE RAIN — debris flying UP then falling DOWN
        // ==========================================
        double rainPhase = Math.min(progress * 2.0, 1.0);
        for (int i = 0; i < 50; i++) {
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            double r = level.getRandom().nextDouble() * expandPhase * 25.0;
            // Rising phase (first half): particles go up
            // Falling phase (second half): particles come down
            double h;
            double vy;
            if (progress < 0.5) {
                h = rainPhase * 15.0;
                vy = 0.5;
            } else {
                h = (1.0 - (progress - 0.5) * 2.0) * 15.0;
                vy = -0.3;
            }
            level.sendParticles(ParticleTypes.WITCH,
                    tp.x + Math.cos(a) * r, tp.y + h, tp.z + Math.sin(a) * r,
                    1, 0.2, vy, 0.2, 0.06);
            level.sendParticles(ParticleTypes.FALLING_WATER,
                    tp.x + Math.cos(a) * r, tp.y + h - 0.5, tp.z + Math.sin(a) * r,
                    1, 0.3, vy, 0.3, 0.05);
            level.sendParticles(new DustParticleOptions(0xFF6600, 1.0f),
                    tp.x + Math.cos(a) * r, tp.y + h - 0.3, tp.z + Math.sin(a) * r,
                    2, 0.15, vy, 0.15, 0.04);
        }

        // ==========================================
        // LAYER 9: 16 RADIAL GROUND CRACKS
        // ==========================================
        for (int c = 0; c < 16; c++) {
            double crackAngle = c * 22.5 * Math.PI / 180;
            double cosC = Math.cos(crackAngle);
            double sinC = Math.sin(crackAngle);
            double crackLen = expandPhase * 18.0;
            for (int s = 0; s < 20; s++) {
                double dist = s * crackLen / 19.0;
                double bx = tp.x + cosC * dist;
                double bz = tp.z + sinC * dist;
                level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                        bx, tp.y + 0.05, bz,
                        3, 0, 0, 0, 0);
                level.sendParticles(new DustParticleOptions(0x4D66FF, 2.5f),
                        bx, tp.y + 0.03, bz,
                        2, 0, 0, 0, 0);
                level.sendParticles(new DustParticleOptions(0x88BBFF, 1.5f),
                        bx, tp.y + 0.07, bz,
                        1, 0, 0, 0, 0);
                // Branching
                if (s > 4 && level.getRandom().nextFloat() < 0.35f) {
                    double br = crackAngle + (level.getRandom().nextBoolean() ? 0.6 : -0.6);
                    level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                            bx + Math.cos(br) * 0.7, tp.y + 0.05, bz + Math.sin(br) * 0.7,
                            1, 0, 0, 0, 0);
                }
                // Vertical beam shooting up from crack at wide intervals
                if (s % 4 == 0) {
                    for (int v = 0; v < 5; v++) {
                        level.sendParticles(ParticleTypes.END_ROD,
                                bx, tp.y + v * 0.5, bz,
                                1, 0.03, 0.03, 0.03, 0);
                    }
                }
            }
        }

        // ==========================================
        // LAYER 10: DEBRIS FIELD
        // ==========================================
        for (int i = 0; i < 60; i++) {
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            double r = level.getRandom().nextDouble() * expandPhase * 32.0;
            double h = 1.0 + level.getRandom().nextDouble() * 8.0 * expandPhase;
            level.sendParticles(ParticleTypes.WITCH,
                    tp.x + Math.cos(a) * r, tp.y + h, tp.z + Math.sin(a) * r,
                    2, 0.5, 0.9, 0.5, 0.1);
            level.sendParticles(ParticleTypes.FALLING_WATER,
                    tp.x + Math.cos(a) * r, tp.y + h - 0.4, tp.z + Math.sin(a) * r,
                    1, 0.6, 0.7, 0.6, 0.08);
            level.sendParticles(new DustParticleOptions(0xFF6600, 1.5f),
                    tp.x + Math.cos(a) * r, tp.y + h - 0.6, tp.z + Math.sin(a) * r,
                    2, 0.35, 0.5, 0.35, 0.06);
            level.sendParticles(new DustParticleOptions(0xFF4400, 1.0f),
                    tp.x + Math.cos(a) * r, tp.y + h + 0.6, tp.z + Math.sin(a) * r,
                    1, 0.25, -0.04, 0.25, 0.03);
        }
        // Smaller sparks scattered widely
        for (int i = 0; i < 30; i++) {
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            double r = level.getRandom().nextDouble() * expandPhase * 30.0;
            level.sendParticles(ParticleTypes.LAVA,
                    tp.x + Math.cos(a) * r, tp.y + 2.0, tp.z + Math.sin(a) * r,
                    1, 0.2, 0.3, 0.2, 0.04);
        }

        // ==========================================
        // LAYER 11: LIGHTNING STORM
        // ==========================================
        if (explodeTick <= 8) {
            for (int l = 0; l < 10; l++) {
                double la = level.getRandom().nextDouble() * Math.PI * 2;
                double lr = 4.0 + level.getRandom().nextDouble() * 20.0;
                double lx = tp.x + Math.cos(la) * lr;
                double lz = tp.z + Math.sin(la) * lr;
                for (int y = 0; y < 25; y++) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            lx + level.getRandom().nextGaussian() * 0.2,
                            tp.y + y * 0.5,
                            lz + level.getRandom().nextGaussian() * 0.2,
                            1, 0.15, 0.05, 0.15, 0);
                }
                if (l % 2 == 0) {
                    level.playSound(null, lx, tp.y, lz,
                            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS,
                            0.5f, 0.2f + l * 0.15f);
                }
            }
        }

        // ==========================================
        // LAYER 12: SPINNING VORTEX RINGS at 3 heights
        // ==========================================
        for (int vRing = 0; vRing < 3; vRing++) {
            double vh = tp.y + 1.5 + vRing * 4.0 * expandPhase;
            double vr = expandPhase * (10.0 + vRing * 4.0);
            for (int i = 0; i < 48; i++) {
                double a = (i * 7.5 + explodeTick * (20 + vRing * 8)) * Math.PI / 180;
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        tp.x + Math.cos(a) * vr, vh, tp.z + Math.sin(a) * vr,
                        2, 0.12, 0.06, 0.12, 0.03);
                level.sendParticles(new DustParticleOptions(
                        vRing == 0 ? 0xFFD700 : vRing == 1 ? 0xFF6600 : 0x4D66FF, 2.0f),
                        tp.x + Math.cos(a) * vr, vh, tp.z + Math.sin(a) * vr,
                        1, 0.06, 0.03, 0.06, 0.02);
            }
        }

        // ==========================================
        // LAYER 13: CENTER AFTERGLOW — persistent brilliant core
        // ==========================================
        double glowIntensity = 1.0 - sustainPhase;
        level.sendParticles(ParticleTypes.END_ROD,
                tp.x, tp.y + 1.5, tp.z,
                (int)(10 * glowIntensity), 1.5, 1.0, 1.5, 0.01);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                tp.x, tp.y + 1.5, tp.z,
                (int)(5 * glowIntensity), 1.0, 0.8, 1.0, 0.01);
        level.sendParticles(new DustParticleOptions(0xFFFFFF, 5.0f),
                tp.x, tp.y + 1.5, tp.z,
                (int)(4 * glowIntensity), 0.8, 0.6, 0.8, 0.01);

        // Sound: continuous thunder pulses
        if (explodeTick % 3 == 0) {
            level.playSound(null, tp.x, tp.y, tp.z,
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS,
                    1.0f * (float)(1.0 - sustainPhase * 0.7), 0.1f + explodeTick * 0.02f);
        }
        if (explodeTick % 8 == 0) {
            level.playSound(null, tp.x, tp.y, tp.z,
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS,
                    0.6f * (float)(1.0 - sustainPhase), 0.05f);
        }
    }

    // ===== Phase 4: Burning Aftermath =====
    private static void tickAftermath(MeteorState state, ServerLevel level, Vec3 tp, int aftermathTick) {
        double fade = 1.0 - (double) aftermathTick / AFTERMATH_TICKS;

        // === Massive ground fire field ===
        for (int i = 0; i < 25; i++) {
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            double r = level.getRandom().nextDouble() * BURN_RADIUS;
            double px = tp.x + Math.cos(a) * r;
            double pz = tp.z + Math.sin(a) * r;
            // Red flames
            level.sendParticles(ParticleTypes.FLAME,
                    px, tp.y + 0.1, pz,
                    2, 0.1, 0.03, 0.1, 0.03);
            // Blue soul fire patches
            if (level.getRandom().nextFloat() < 0.35f) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        px, tp.y + 0.05, pz,
                        2, 0.08, 0.03, 0.08, 0.02);
            }
            // Center is more intense
            if (r < 3.0) {
                level.sendParticles(ParticleTypes.END_ROD,
                        px, tp.y + 0.2, pz,
                        1, 0.05, 0.02, 0.05, 0.01);
            }
        }

        // === Rising ember columns ===
        for (int i = 0; i < 15; i++) {
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            double r = level.getRandom().nextDouble() * BURN_RADIUS;
            double h = 0.3 + level.getRandom().nextDouble() * 4.0 * fade;
            level.sendParticles(ParticleTypes.WITCH,
                    tp.x + Math.cos(a) * r, tp.y + h, tp.z + Math.sin(a) * r,
                    1, 0.06, 0.08, 0.06, 0.015);
            level.sendParticles(ParticleTypes.FALLING_WATER,
                    tp.x + Math.cos(a) * r, tp.y + h, tp.z + Math.sin(a) * r,
                    1, 0.04, 0.1, 0.04, 0.01);
        }

        // === Heat distortion across the field ===
        for (int i = 0; i < 12; i++) {
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            double r = level.getRandom().nextDouble() * BURN_RADIUS;
            level.sendParticles(ParticleTypes.FALLING_WATER,
                    tp.x + Math.cos(a) * r, tp.y + 0.4, tp.z + Math.sin(a) * r,
                    1, 0.03, 0.12, 0.03, 0);
        }

        // === Fading multi-ring fire circles ===
        for (int ring = 0; ring < 3; ring++) {
            double ringR = (BURN_RADIUS - ring * 2.5) * (0.5 + Math.sin(aftermathTick * 0.15 + ring) * 0.5);
            for (int i = 0; i < 40; i++) {
                double a = i * 9 * Math.PI / 180;
                level.sendParticles(new DustParticleOptions(ring == 0 ? 0xFF3300 : ring == 1 ? 0xFF6600 : 0xFF9900,
                                1.2f * (float)fade),
                        tp.x + Math.cos(a) * ringR, tp.y + 0.05, tp.z + Math.sin(a) * ringR,
                        1, 0, 0, 0, 0);
            }
        }

        // === Floating dragon molt debris ===
        for (int i = 0; i < 8; i++) {
            double px = tp.x + (level.getRandom().nextDouble() - 0.5) * BURN_RADIUS * 2;
            double py = tp.y + 1.5 + level.getRandom().nextDouble() * 5.0 * fade;
            double pz = tp.z + (level.getRandom().nextDouble() - 0.5) * BURN_RADIUS * 2;
            level.sendParticles(new DustParticleOptions(0xFF4400, 0.8f),
                    px, py, pz,
                    1, 0.03, -0.02, 0.03, 0.01);
            level.sendParticles(new DustParticleOptions(0xFFAA44, 0.5f),
                    px + 0.2, py, pz + 0.2,
                    1, 0.02, -0.01, 0.02, 0.005);
            level.sendParticles(ParticleTypes.WITCH,
                    px, py + 0.5, pz,
                    1, 0.04, -0.02, 0.04, 0.005);
        }

        // === Sparse lingering blue ley sparks ===
        for (int i = 0; i < 5; i++) {
            double px = tp.x + (level.getRandom().nextDouble() - 0.5) * BURN_RADIUS * 1.5;
            double pz = tp.z + (level.getRandom().nextDouble() - 0.5) * BURN_RADIUS * 1.5;
            level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    px, tp.y + 0.1, pz,
                    1, 0, 0, 0, 0);
            level.sendParticles(new DustParticleOptions(0x4D66FF, 0.8f),
                    px, tp.y + 0.08, pz,
                    1, 0, 0, 0, 0);
        }

        // === Burn damage ===
        if (aftermathTick % 10 == 0) {
            level.getEntities((Entity) null,
                    AABB.ofSize(tp, BURN_RADIUS * 2, 4.0, BURN_RADIUS * 2),
                    e -> e instanceof LivingEntity && !(e instanceof Player)
            ).forEach(target -> {
                target.hurt(level.damageSources().inFire(), BURN_DAMAGE);
                target.setRemainingFireTicks(80);
            });
        }

        // Fading rumble
        if (aftermathTick % 25 == 0) {
            level.playSound(null, tp.x, tp.y, tp.z,
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.3f * (float)fade, 0.08f);
        }
    }
}
