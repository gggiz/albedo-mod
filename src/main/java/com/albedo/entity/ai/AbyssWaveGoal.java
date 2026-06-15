package com.albedo.entity.ai;

import com.albedo.AlbedoConfig;
import com.albedo.entity.AlbedoBoss;
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

public class AbyssWaveGoal extends Goal {
    private final AlbedoBoss boss;
    private int tick = 0;
    private Vec3 waveDir = Vec3.ZERO;
    private final Set<UUID> hitEntities = new HashSet<>();
    private static final int WARMUP_TICKS = 12;
    private static final int WAVE_TRAVEL_TICKS = 14;
    private static final double WAVE_SPEED = 1.1;

    public AbyssWaveGoal(AlbedoBoss boss) {
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
                && boss.distanceTo(target) >= 4.0f
                && boss.distanceTo(target) <= 15.0f
                && boss.getPhase() >= 2
                && !boss.isOnCooldown("wave");
    }

    @Override
    public void start() {
        tick = 0;
        hitEntities.clear();
        boss.setAttackState(4);
        boss.setCooldown("wave", AlbedoConfig.WAVE_COOLDOWN);
        boss.playSound(SoundEvents.EVOKER_CAST_SPELL);
        LivingEntity target = boss.getTarget();
        if (target != null) {
            waveDir = target.position().subtract(boss.position()).normalize();
        }
    }

    @Override
    public void tick() {
        tick++;

        if (tick <= WARMUP_TICKS) {
            LivingEntity target = boss.getTarget();
            if (target != null) {
                waveDir = target.position().subtract(boss.position()).normalize();
                boss.getLookControl().setLookAt(target, 30f, 30f);
            }
            // Warmup gathering energy
            if (boss.level() instanceof ServerLevel level) {
                Vec3 p = boss.position().add(0, 1.2, 0);
                level.sendParticles(ParticleTypes.PORTAL,
                        p.x, p.y, p.z,
                        4, 0.5, 0.3, 0.5, 0.03);
            }
        } else {
            int waveTick = tick - WARMUP_TICKS;
            if (waveTick <= WAVE_TRAVEL_TICKS && boss.level() instanceof ServerLevel level) {
                double dist = waveTick * WAVE_SPEED;

                for (int i = -2; i <= 2; i++) {
                    double angle = i * Math.toRadians(18);
                    double cos = Math.cos(angle);
                    double sin = Math.sin(angle);
                    Vec3 dir = new Vec3(
                            waveDir.x * cos - waveDir.z * sin,
                            0,
                            waveDir.x * sin + waveDir.z * cos
                    );

                    Vec3 wavePos = boss.position().add(0, 1.5, 0).add(dir.scale(dist));

                    // Main wave particle
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                            wavePos.x, wavePos.y, wavePos.z,
                            1, 0.3, 0.1, 0.3, 0);
                    // Dark trail
                    level.sendParticles(ParticleTypes.LARGE_SMOKE,
                            wavePos.x, wavePos.y + 0.3, wavePos.z,
                            2, 0.4, 0.1, 0.4, 0.01);
                    // Ground effect
                    level.sendParticles(ParticleTypes.PORTAL,
                            wavePos.x, wavePos.y - 0.8, wavePos.z,
                            1, 0.2, 0, 0.2, 0);

                    level.getEntities(boss,
                            AABB.ofSize(wavePos, 2.5, 2.0, 2.5),
                            e -> e instanceof LivingEntity && e != boss && !(e instanceof AlbedoBoss)
                    ).forEach(entity -> {
                        if (hitEntities.add(entity.getUUID())) {
                            entity.hurt(
                                    boss.damageSources().magic(),
                                    AlbedoConfig.WAVE_DAMAGE
                            );
                            // Hit flash
                            level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                                    entity.getX(), entity.getY() + 1, entity.getZ(),
                                    3, 0.2, 0.2, 0.2, 0.1);
                        }
                    });
                }
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return tick < WARMUP_TICKS + WAVE_TRAVEL_TICKS;
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
