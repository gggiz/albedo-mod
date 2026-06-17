package com.albedo.entity.ai;

import com.albedo.AlbedoConfig;
import com.albedo.entity.AxolotlMage;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WaterFieldGoal extends Goal {
    private final AxolotlMage mage;
    private int tick = 0;
    private final Set<UUID> affectedEntities = new HashSet<>();

    private static final int WARMUP_TICKS = 14;
    private static final int ACTIVE_TICKS = 20;
    private static final double FIELD_RADIUS = 8.0;

    public WaterFieldGoal(AxolotlMage mage) {
        this.mage = mage;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mage.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (mage.isCasting()) return false;
        if (mage.getState() != 0) return false;
        if (mage.isOnCooldown("water_field")) return false;

        return mage.distanceTo(target) <= 10.0f;
    }

    @Override
    public void start() {
        tick = 0;
        affectedEntities.clear();
        mage.setCasting(true);
        mage.setState(1);
        mage.setCooldown("water_field", AlbedoConfig.WATER_FIELD_COOLDOWN);
        mage.playSound(SoundEvents.AXOLOTL_ATTACK, 0.5f, 0.8f);
        mage.playSound(SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.4f, 0.8f);
    }

    @Override
    public void tick() {
        tick++;

        if (tick <= WARMUP_TICKS) {
            tickWarmup();
        } else {
            int activeTick = tick - WARMUP_TICKS;
            if (activeTick <= ACTIVE_TICKS && mage.level() instanceof ServerLevel level) {
                tickActive(activeTick, level);
            }
        }
    }

    private void tickWarmup() {
        if (!(mage.level() instanceof ServerLevel level)) return;

        double progress = (double) tick / WARMUP_TICKS;
        double r = 1.5 + progress * 6.0;

        // Multi-layer rotating water rings at different heights
        for (int ring = 0; ring < 3; ring++) {
            double ringY = mage.getY() + 0.05 + ring * 0.6;
            double ringR = r * (1.0 - ring * 0.2);
            int dots = 12 + ring * 6;
            double spinDir = ring % 2 == 0 ? 1 : -1;
            for (int i = 0; i < dots; i++) {
                double angle = (tick * 5 * spinDir + i * 360.0 / dots) * Math.PI / 180;
                level.sendParticles(ParticleTypes.FALLING_WATER,
                        mage.getX() + Math.cos(angle) * ringR,
                        ringY,
                        mage.getZ() + Math.sin(angle) * ringR,
                        1, 0, 0, 0, 0);
            }
        }

        // Rising water wall effect around mage
        for (int i = 0; i < 12; i++) {
            double angle = (i * 30) * Math.PI / 180;
            double wallR = r * 0.7;
            level.sendParticles(ParticleTypes.BUBBLE_POP,
                    mage.getX() + Math.cos(angle) * wallR,
                    mage.getY() + 0.2 + progress * 1.5,
                    mage.getZ() + Math.sin(angle) * wallR,
                    1, 0.05, 0.05, 0.05, 0.01);
        }

        // Center bubble column growing
        level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP,
                mage.getX(), mage.getY() + 0.5, mage.getZ(),
                5 + tick, 0.3 + progress * 0.5, 0.1, 0.3 + progress * 0.5, 0.03);

        // Pink axolotl gill wisps swirling
        for (int side = -1; side <= 1; side += 2) {
            level.sendParticles(ParticleTypes.WITCH,
                    mage.getX() + side * 0.5,
                    mage.getY() + 1.5 + Math.sin(tick * 0.4) * 0.3,
                    mage.getZ(),
                    2, 0.2, 0.15, 0.2, 0.02);
        }

        // Ground ripple expanding
        for (int i = 0; i < 8; i++) {
            double a = i * 45 * Math.PI / 180;
            level.sendParticles(ParticleTypes.SPLASH,
                    mage.getX() + Math.cos(a) * r,
                    mage.getY() + 0.05,
                    mage.getZ() + Math.sin(a) * r,
                    1, 0.05, 0, 0.05, 0);
        }
    }

    private void tickActive(int activeTick, ServerLevel level) {
        // === Rotating water dome perimeter ===
        for (int ring = 0; ring < 2; ring++) {
            double ringR = FIELD_RADIUS * (0.7 + ring * 0.3);
            int dots = 20 + ring * 12;
            double spin = ring % 2 == 0 ? 3.0 : -2.0;
            for (int i = 0; i < dots; i++) {
                double angle = (tick * spin + i * 360.0 / dots) * Math.PI / 180;
                double px = mage.getX() + Math.cos(angle) * ringR;
                double pz = mage.getZ() + Math.sin(angle) * ringR;
                level.sendParticles(ParticleTypes.FALLING_WATER,
                        px, mage.getY() + 0.05, pz,
                        1, 0, 0.01, 0, 0);
                if (i % 3 == 0) {
                    level.sendParticles(ParticleTypes.BUBBLE_POP,
                            px, mage.getY() + 0.5 + Math.sin(tick * 0.2 + i) * 0.8, pz,
                            1, 0.05, 0.03, 0.05, 0.01);
                }
            }
        }

        // === Rotating water wall rising from perimeter ===
        for (int i = 0; i < 24; i++) {
            double angle = (tick * 2.5 + i * 15) * Math.PI / 180;
            double h = 1.0 + Math.sin(tick * 0.3 + i * 0.5) * 1.5;
            level.sendParticles(ParticleTypes.FALLING_WATER,
                    mage.getX() + Math.cos(angle) * FIELD_RADIUS,
                    mage.getY() + h,
                    mage.getZ() + Math.sin(angle) * FIELD_RADIUS,
                    1, 0, 0.02, 0, 0.01);
        }

        // === Ground water surface ===
        for (int i = 0; i < 30; i++) {
            double angle = mage.getRandom().nextDouble() * Math.PI * 2;
            double dist = mage.getRandom().nextDouble() * FIELD_RADIUS;
            level.sendParticles(ParticleTypes.FALLING_WATER,
                    mage.getX() + Math.cos(angle) * dist,
                    mage.getY() + 0.02,
                    mage.getZ() + Math.sin(angle) * dist,
                    1, 0.02, 0, 0.02, 0);
        }

        // === Center whirlpool / geyser ===
        level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP,
                mage.getX(), mage.getY() + 1.0, mage.getZ(),
                6, 1.0, 0.5, 1.0, 0.06);
        // Spinning water around center
        for (int i = 0; i < 8; i++) {
            double a = (tick * 4 + i * 45) * Math.PI / 180;
            double cr = 1.2;
            level.sendParticles(ParticleTypes.BUBBLE,
                    mage.getX() + Math.cos(a) * cr,
                    mage.getY() + 0.3,
                    mage.getZ() + Math.sin(a) * cr,
                    1, 0.1, 0.05, 0.1, 0.02);
        }

        // === Spectral axolotl spirits (more, denser) ===
        for (int i = 0; i < 5; i++) {
            double spiritAngle = (tick * 8 + i * 72) * Math.PI / 180;
            double spiritDist = 1.5 + Math.sin(tick * 0.25 + i) * 5.5;
            double sx = mage.getX() + Math.cos(spiritAngle) * spiritDist;
            double sy = mage.getY() + 0.3 + Math.sin(tick * 0.35 + i) * 1.2;
            double sz = mage.getZ() + Math.sin(spiritAngle) * spiritDist;
            // Axolotl body (witch particles)
            level.sendParticles(ParticleTypes.WITCH,
                    sx, sy, sz,
                    3, 0.25, 0.2, 0.25, 0.02);
            // Axolotl trail
            level.sendParticles(ParticleTypes.BUBBLE_POP,
                    sx, sy + 0.3, sz,
                    2, 0.2, 0.1, 0.2, 0.02);
            // Water splash around spirit
            level.sendParticles(ParticleTypes.FALLING_WATER,
                    sx, sy - 0.2, sz,
                    1, 0.1, 0.02, 0.1, 0);
        }

        // === Rain falling within field ===
        for (int i = 0; i < 8; i++) {
            double rx = mage.getX() + (mage.getRandom().nextDouble() - 0.5) * FIELD_RADIUS * 2;
            double rz = mage.getZ() + (mage.getRandom().nextDouble() - 0.5) * FIELD_RADIUS * 2;
            level.sendParticles(ParticleTypes.FALLING_WATER,
                    rx, mage.getY() + 2.5, rz,
                    1, 0.05, -0.15, 0.05, 0);
        }

        // === Damage and effects on enemies ===
        AABB field = mage.getBoundingBox().inflate(FIELD_RADIUS);
        level.getEntities(mage, field,
                e -> e instanceof LivingEntity
                        && e != mage
                        && !(e instanceof AxolotlMage)
                        && !(e instanceof com.albedo.entity.AlbedoBoss)
        ).forEach(entity -> {
            if (entity instanceof LivingEntity le) {
                le.addEffect(new MobEffectInstance(
                        MobEffects.SLOWNESS, 80, 2, false, true, true));
                le.addEffect(new MobEffectInstance(
                        MobEffects.MINING_FATIGUE, 80, 1, false, true, true));

                if (!(le instanceof net.minecraft.world.entity.player.Player)
                        || mage.getLastHurtByMob() == le) {
                    if (affectedEntities.add(le.getUUID())) {
                        le.hurt(mage.damageSources().magic(),
                                AlbedoConfig.WATER_FIELD_DAMAGE);
                    }
                }

                // Water chains from mage to victim
                double dx = le.getX() - mage.getX();
                double dz = le.getZ() - mage.getZ();
                double chainDist = Math.sqrt(dx * dx + dz * dz);
                for (int j = 0; j < 5; j++) {
                    double t = j / 4.0;
                    level.sendParticles(ParticleTypes.BUBBLE_POP,
                            mage.getX() + dx * t,
                            mage.getY() + 0.8 + Math.sin(tick * 0.5 + j) * 0.5,
                            mage.getZ() + dz * t,
                            1, 0.1, 0.05, 0.1, 0.02);
                }

                // Heavy splash on victims
                level.sendParticles(ParticleTypes.SPLASH,
                        le.getX(), le.getY() + 0.3, le.getZ(),
                        5, 0.4, 0.2, 0.4, 0.08);
                level.sendParticles(ParticleTypes.FALLING_WATER,
                        le.getX(), le.getY() + 0.1, le.getZ(),
                        3, 0.3, 0.05, 0.3, 0.02);
            }
        });

        // === Pulse ring every few ticks ===
        if (activeTick % 5 == 0) {
            double pulseR = FIELD_RADIUS * (0.3 + (activeTick % 20) / 20.0 * 0.7);
            for (int i = 0; i < 20; i++) {
                double a = i * 18 * Math.PI / 180;
                level.sendParticles(ParticleTypes.BUBBLE,
                        mage.getX() + Math.cos(a) * pulseR,
                        mage.getY() + 0.3,
                        mage.getZ() + Math.sin(a) * pulseR,
                        1, 0, 0.05, 0, 0);
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return tick < WARMUP_TICKS + ACTIVE_TICKS;
    }

    @Override
    public void stop() {
        mage.setCasting(false);
        mage.setState(0);
        tick = 0;
        mage.playSound(SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 0.3f, 0.5f);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
