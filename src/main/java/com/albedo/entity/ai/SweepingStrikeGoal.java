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

public class SweepingStrikeGoal extends Goal {
    private final AlbedoBoss boss;
    private int tick = 0;
    private boolean damageDealt = false;
    private static final int WARMUP_TICKS = 8;
    private static final int RECOVERY_TICKS = 10;

    public SweepingStrikeGoal(AlbedoBoss boss) {
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
                && boss.distanceTo(target) <= 3.5f
                && boss.getPhase() >= 1
                && !boss.isOnCooldown("sweep");
    }

    @Override
    public void start() {
        tick = 0;
        damageDealt = false;
        boss.setAttackState(2);
        boss.setCooldown("sweep", AlbedoConfig.SWEEP_COOLDOWN);
    }

    @Override
    public void tick() {
        tick++;
        LivingEntity target = boss.getTarget();
        if (target == null) return;

        boss.getLookControl().setLookAt(target, 30f, 30f);

        // Warmup particles
        if (tick < WARMUP_TICKS && boss.level() instanceof ServerLevel level) {
            Vec3 pos = boss.position().add(0, 0.8, 0);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    pos.x, pos.y, pos.z,
                    2, 0.4, 0.4, 0.4, 0.05);
        }

        if (tick == WARMUP_TICKS && !damageDealt) {
            damageDealt = true;
            boss.playSound(SoundEvents.PLAYER_ATTACK_SWEEP);
            Vec3 forward = boss.getLookAngle();
            Vec3 pos = boss.position().add(0, 0.8, 0);
            double range = 3.5;

            // Sweep arc particles
            if (boss.level() instanceof ServerLevel level) {
                for (int a = -90; a <= 90; a += 10) {
                    double rad = Math.toRadians(a);
                    double cos = Math.cos(rad);
                    double sin = Math.sin(rad);
                    Vec3 arc = new Vec3(forward.x * cos - forward.z * sin, 0, forward.x * sin + forward.z * cos);
                    for (double r = 0.5; r <= range; r += 0.6) {
                        Vec3 p = pos.add(arc.scale(r));
                        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                                p.x, p.y, p.z,
                                1, 0.05, 0.05, 0.05, 0);
                    }
                }
            }

            boss.level().getEntities(boss,
                    AABB.ofSize(pos, range * 2, 2, range * 2),
                    e -> e instanceof LivingEntity && e != boss && !(e instanceof AlbedoBoss)
                            && e.getType() != EntityType.CAT && e.getType() != EntityType.WOLF
                            && boss.hasLineOfSight(e)
                            && forward.dot(e.position().subtract(pos).normalize()) > 0.0
            ).forEach(entity -> {
                boss.swing(InteractionHand.MAIN_HAND);
                entity.hurt(
                        boss.damageSources().mobAttack(boss),
                        AlbedoConfig.SWEEP_DAMAGE
                );
                Vec3 kb = entity.position().subtract(pos).normalize().scale(1.5);
                entity.push(kb.x, 0.4, kb.z);
            });
        }
    }

    @Override
    public boolean canContinueToUse() {
        return tick < WARMUP_TICKS + RECOVERY_TICKS;
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
