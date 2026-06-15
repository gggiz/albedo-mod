package com.albedo.entity.ai;

import com.albedo.AlbedoConfig;
import com.albedo.entity.AlbedoBoss;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class FinalJudgmentGoal extends Goal {
    private final AlbedoBoss boss;
    private int tick = 0;
    private boolean damageDealt = false;
    private Vec3 slamPos = Vec3.ZERO;
    private static final int JUMP_TICKS = 15;
    private static final int APEX_TICKS = 10;
    private static final int SLAM_TICKS = 5;
    private static final int RECOVERY_TICKS = 20;
    private static final double JUMP_HEIGHT = 3.0;
    private static final double AOE_RADIUS = 5.0;

    public FinalJudgmentGoal(AlbedoBoss boss) {
        this.boss = boss;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = boss.getTarget();
        return target != null
                && target.isAlive()
                && boss.getAttackState() == 0
                && !boss.isSitting()
                && !boss.isClone()
                && boss.distanceTo(target) <= 10.0f
                && boss.getPhase() >= 3
                && !boss.isOnCooldown("judgment");
    }

    @Override
    public void start() {
        tick = 0;
        damageDealt = false;
        boss.setAttackState(5);
        slamPos = boss.position();
        boss.setCooldown("judgment", AlbedoConfig.JUDGMENT_COOLDOWN);
        boss.playSound(SoundEvents.LIGHTNING_BOLT_THUNDER);
    }

    @Override
    public void tick() {
        tick++;

        if (tick <= JUMP_TICKS) {
            double progress = (double) tick / JUMP_TICKS;
            boss.setDeltaMovement(0, 0.3 * (1 - progress), 0);
            boss.hurtMarked = true;

            if (boss.level() instanceof ServerLevel level) {
                // Dark energy spiraling up
                for (int a = 0; a < 360; a += 30) {
                    double rad = Math.toRadians(a + tick * 20);
                    double r = 1.0 + progress * 2.0;
                    level.sendParticles(ParticleTypes.PORTAL,
                            boss.getX() + Math.cos(rad) * r,
                            boss.getY() + progress * JUMP_HEIGHT,
                            boss.getZ() + Math.sin(rad) * r,
                            1, 0, 0.02, 0, 0);
                }
                // Smoke at feet
                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        slamPos.x, slamPos.y + 0.1, slamPos.z,
                        8, 1.2, 0.05, 1.2, 0.01);
                // Energy trails rising
                level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                        boss.getX(), boss.getY() + 1.5, boss.getZ(),
                        3, 0.5, 0.3, 0.5, 0.1);
            }
        } else if (tick <= JUMP_TICKS + APEX_TICKS) {
            boss.setDeltaMovement(Vec3.ZERO);

            if (boss.level() instanceof ServerLevel level) {
                double apexY = slamPos.y + JUMP_HEIGHT;
                // Boss glowing at apex
                level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                        boss.getX(), boss.getY() + 1.5, boss.getZ(),
                        5, 0.3, 0.3, 0.3, 0.2);
                // Expanding warning rings on ground
                for (int ring = 0; ring < 3; ring++) {
                    double ringR = AOE_RADIUS * (0.3 + ring * 0.35);
                    for (int a = 0; a < 360; a += 10) {
                        double rad = Math.toRadians(a);
                        level.sendParticles(ParticleTypes.PORTAL,
                                slamPos.x + Math.cos(rad) * ringR,
                                slamPos.y + 0.15,
                                slamPos.z + Math.sin(rad) * ringR,
                                1, 0, 0, 0, 0);
                    }
                }
                // Dark energy falling from apex
                for (int i = 0; i < 5; i++) {
                    level.sendParticles(ParticleTypes.LARGE_SMOKE,
                            boss.getX() + (Math.random() - 0.5) * AOE_RADIUS,
                            boss.getY() - Math.random() * 2,
                            boss.getZ() + (Math.random() - 0.5) * AOE_RADIUS,
                            1, 0.1, 0.05, 0.1, 0);
                }
            }
        } else if (tick <= JUMP_TICKS + APEX_TICKS + SLAM_TICKS) {
            boss.setDeltaMovement(0, -1.5, 0);
            boss.hurtMarked = true;

            // Descending trail
            if (boss.level() instanceof ServerLevel level) {
                for (int i = 0; i < 3; i++) {
                    level.sendParticles(ParticleTypes.LARGE_SMOKE,
                            boss.getX() + (Math.random() - 0.5) * 0.5,
                            boss.getY() + 1.0,
                            boss.getZ() + (Math.random() - 0.5) * 0.5,
                            3, 0.4, 0.2, 0.4, 0.03);
                }
                level.sendParticles(ParticleTypes.PORTAL,
                        boss.getX(), boss.getY() + 1, boss.getZ(),
                        3, 0.4, 0.2, 0.4, 0.1);
            }

            if ((boss.onGround() || tick >= JUMP_TICKS + APEX_TICKS + SLAM_TICKS) && !damageDealt) {
                damageDealt = true;
                boss.swing(InteractionHand.MAIN_HAND);
                boss.setPos(slamPos.x, slamPos.y, slamPos.z);
                boss.playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0f, 0.5f);

                if (boss.level() instanceof ServerLevel level) {
                    // Massive central explosion
                    level.sendParticles(ParticleTypes.EXPLOSION,
                            slamPos.x, slamPos.y + 1, slamPos.z,
                            30, AOE_RADIUS * 0.8, 0.5, AOE_RADIUS * 0.8, 0.4);
                    level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                            slamPos.x, slamPos.y + 0.5, slamPos.z,
                            3, 2.0, 0.5, 2.0, 0.3);
                    // Expanding shockwave rings (3 waves)
                    for (int ring = 0; ring < 3; ring++) {
                        double ringR = AOE_RADIUS * (0.3 + ring * 0.35);
                        for (int a = 0; a < 360; a += 8) {
                            double rad = Math.toRadians(a);
                            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                                    slamPos.x + Math.cos(rad) * ringR,
                                    slamPos.y + 0.3 + ring * 0.4,
                                    slamPos.z + Math.sin(rad) * ringR,
                                    2, 0.15, 0.2, 0.15, 0.08);
                        }
                    }
                    // Ground cracks radiating outward
                    for (int a = 0; a < 360; a += 20) {
                        double rad = Math.toRadians(a);
                        for (double r = 0.5; r <= AOE_RADIUS; r += 0.5) {
                            level.sendParticles(ParticleTypes.PORTAL,
                                    slamPos.x + Math.cos(rad) * r,
                                    slamPos.y + 0.05,
                                    slamPos.z + Math.sin(rad) * r,
                                    1, 0.02, 0, 0.02, 0);
                        }
                    }
                    // Flying debris
                    for (int i = 0; i < 15; i++) {
                        double dx = (Math.random() - 0.5) * AOE_RADIUS * 1.5;
                        double dz = (Math.random() - 0.5) * AOE_RADIUS * 1.5;
                        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                                slamPos.x + dx, slamPos.y + 0.5, slamPos.z + dz,
                                2, 0.1, 0.3, 0.1, 0.1);
                    }
                    // Lingering dark cloud
                    level.sendParticles(ParticleTypes.LARGE_SMOKE,
                            slamPos.x, slamPos.y + 0.3, slamPos.z,
                            20, AOE_RADIUS * 0.7, 0.2, AOE_RADIUS * 0.7, 0.02);

                    level.getEntities(boss,
                            AABB.ofSize(slamPos.add(0, 1, 0), AOE_RADIUS * 2, 4, AOE_RADIUS * 2),
                            e -> e instanceof LivingEntity && e != boss && !(e instanceof AlbedoBoss)
                                    && e.getType() != EntityType.CAT && e.getType() != EntityType.WOLF
                    ).forEach(entity -> {
                        entity.hurt(
                                boss.damageSources().explosion(boss, boss),
                                AlbedoConfig.JUDGMENT_DAMAGE
                        );
                        Vec3 kb = entity.position()
                                .subtract(slamPos.add(0, 1, 0))
                                .normalize()
                                .scale(3.0);
                        entity.push(kb.x, 0.8, kb.z);
                    });
                }
            }
        } else {
            // Recovery: lingering aftermath particles
            if (boss.level() instanceof ServerLevel level && tick % 4 == 0) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        slamPos.x + (Math.random() - 0.5) * AOE_RADIUS,
                        slamPos.y + 0.2,
                        slamPos.z + (Math.random() - 0.5) * AOE_RADIUS,
                        2, 0.3, 0.1, 0.3, 0.01);
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return tick < JUMP_TICKS + APEX_TICKS + SLAM_TICKS + RECOVERY_TICKS;
    }

    @Override
    public void stop() {
        boss.setAttackState(0);
        tick = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
