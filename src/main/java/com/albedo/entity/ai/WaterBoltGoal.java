package com.albedo.entity.ai;

import com.albedo.AlbedoConfig;
import com.albedo.entity.AxolotlMage;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WaterBoltGoal extends Goal {
    private final AxolotlMage mage;
    private int tick = 0;
    private Vec3 boltDir = Vec3.ZERO;
    private final Set<UUID> hitEntities = new HashSet<>();

    private static final int WARMUP_TICKS = 8;
    private static final int TRAVEL_TICKS = 16;
    private static final double BOLT_SPEED = 1.4;

    public WaterBoltGoal(AxolotlMage mage) {
        this.mage = mage;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mage.getTarget();
        return target != null
                && target.isAlive()
                && mage.getState() == 0
                && !mage.isCasting()
                && mage.distanceTo(target) >= 3.0f
                && mage.distanceTo(target) <= 18.0f
                && !mage.isOnCooldown("water_bolt");
    }

    @Override
    public void start() {
        tick = 0;
        hitEntities.clear();
        mage.setCasting(true);
        mage.setState(1);
        mage.setCooldown("water_bolt", AlbedoConfig.WATER_BOLT_COOLDOWN);
        mage.playSound(SoundEvents.AXOLOTL_ATTACK, 0.6f, 1.2f);
        mage.playSound(SoundEvents.AXOLOTL_SPLASH, 0.4f, 1.5f);

        LivingEntity target = mage.getTarget();
        if (target != null) {
            boltDir = target.position().subtract(mage.position()).normalize();
        }
    }

    @Override
    public void tick() {
        tick++;

        if (tick <= WARMUP_TICKS) {
            LivingEntity target = mage.getTarget();
            if (target != null) {
                boltDir = target.position().subtract(mage.position()).normalize();
                mage.getLookControl().setLookAt(target, 30f, 30f);
            }
            // Water + axolotl energy gathering at hand
            if (mage.level() instanceof ServerLevel level) {
                Vec3 hand = mage.position().add(0, 1.4, 0).add(boltDir.scale(0.8));
                level.sendParticles(ParticleTypes.FALLING_WATER,
                        hand.x, hand.y, hand.z,
                        2, 0.15, 0.15, 0.15, 0.05);
                level.sendParticles(ParticleTypes.BUBBLE_POP,
                        hand.x, hand.y, hand.z,
                        1, 0.1, 0.1, 0.1, 0.02);
                level.sendParticles(ParticleTypes.WITCH,
                        hand.x, hand.y, hand.z,
                        1, 0.08, 0.08, 0.08, 0.01);
            }
        } else {
            int travelTick = tick - WARMUP_TICKS;
            if (travelTick <= TRAVEL_TICKS && mage.level() instanceof ServerLevel level) {
                double dist = travelTick * BOLT_SPEED;
                Vec3 boltPos = mage.position().add(0, 1.5, 0).add(boltDir.scale(dist));

                // Axolotl water projectile — splash + gill trail
                level.sendParticles(ParticleTypes.SPLASH,
                        boltPos.x, boltPos.y, boltPos.z,
                        1, 0.2, 0.2, 0.2, 0);
                level.sendParticles(ParticleTypes.BUBBLE,
                        boltPos.x, boltPos.y + 0.2, boltPos.z,
                        3, 0.3, 0.1, 0.3, 0.02);
                level.sendParticles(ParticleTypes.WITCH,
                        boltPos.x, boltPos.y, boltPos.z,
                        1, 0.15, 0.15, 0.15, 0.01);
                level.sendParticles(ParticleTypes.FALLING_WATER,
                        boltPos.x, boltPos.y - 0.3, boltPos.z,
                        2, 0.1, 0.05, 0.1, 0);

                // Hit detection
                level.getEntities(mage,
                        AABB.ofSize(boltPos, 2.0, 1.5, 2.0),
                        e -> e instanceof LivingEntity
                                && e != mage
                                && !(e instanceof AxolotlMage)
                ).forEach(entity -> {
                    if (hitEntities.add(entity.getUUID())) {
                        entity.hurt(
                                mage.damageSources().magic(),
                                AlbedoConfig.WATER_BOLT_DAMAGE
                        );
                        // Apply slowness on hit
                        if (entity instanceof LivingEntity le) {
                            le.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                    net.minecraft.world.effect.MobEffects.SLOWNESS,
                                    60, 1, false, true, true));
                        }
                        // Hit splash
                        level.sendParticles(ParticleTypes.SPLASH,
                                entity.getX(), entity.getY() + 1, entity.getZ(),
                                8, 0.4, 0.4, 0.4, 0.1);
                        level.sendParticles(ParticleTypes.BUBBLE_POP,
                                entity.getX(), entity.getY() + 1, entity.getZ(),
                                5, 0.3, 0.3, 0.3, 0.05);
                        mage.playSound(SoundEvents.AXOLOTL_HURT, 0.4f, 1.2f);
                    }
                });
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return tick < WARMUP_TICKS + TRAVEL_TICKS;
    }

    @Override
    public void stop() {
        mage.setCasting(false);
        mage.setState(0);
        tick = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
