package com.albedo.entity.ai;

import com.albedo.AlbedoConfig;
import com.albedo.entity.AlbedoBoss;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class HellThrustGoal extends Goal {
    private final AlbedoBoss boss;
    private int tick = 0;
    private Vec3 chargeDir = Vec3.ZERO;
    private final Set<UUID> hitEntities = new HashSet<>();
    private static final int WARMUP_TICKS = 10;
    private static final int CHARGE_TICKS = 5;
    private static final int RECOVERY_TICKS = 10;
    private static final double CHARGE_SPEED = 1.2;

    public HellThrustGoal(AlbedoBoss boss) {
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
                && boss.distanceTo(target) >= 3.0f
                && boss.distanceTo(target) <= 10.0f
                && boss.getPhase() >= 2
                && !boss.isOnCooldown("thrust");
    }

    @Override
    public void start() {
        tick = 0;
        hitEntities.clear();
        boss.setAttackState(3);
        boss.setCooldown("thrust", AlbedoConfig.THRUST_COOLDOWN);
        boss.playSound(SoundEvents.PLAYER_ATTACK_STRONG);
        LivingEntity target = boss.getTarget();
        if (target != null) {
            chargeDir = target.position().subtract(boss.position()).normalize();
        }
    }

    @Override
    public void tick() {
        tick++;

        if (tick <= WARMUP_TICKS) {
            LivingEntity target = boss.getTarget();
            if (target != null) {
                chargeDir = target.position().subtract(boss.position()).normalize();
                boss.getLookControl().setLookAt(target, 30f, 30f);
            }
            // Warmup dark energy gathering
            if (boss.level() instanceof ServerLevel level) {
                Vec3 p = boss.position().add(0, 1.2, 0);
                level.sendParticles(ParticleTypes.PORTAL,
                        p.x, p.y, p.z,
                        3, 0.3, 0.3, 0.3, 0.02);
            }
        } else if (tick <= WARMUP_TICKS + CHARGE_TICKS) {
            boss.setDeltaMovement(chargeDir.scale(CHARGE_SPEED));
            boss.hurtMarked = true;

            // Charge trail
            if (boss.level() instanceof ServerLevel level) {
                Vec3 trail = boss.position().add(0, 1.0, 0);
                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        trail.x, trail.y, trail.z,
                        4, 0.3, 0.2, 0.3, 0.02);
                level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                        trail.x, trail.y + 0.5, trail.z,
                        2, 0.2, 0.2, 0.2, 0.1);
            }

            AABB hitbox = boss.getBoundingBox().inflate(1.5, 0.5, 1.5);
            boss.level().getEntities(boss, hitbox,
                    e -> e instanceof LivingEntity && e != boss && !(e instanceof AlbedoBoss)
            ).forEach(entity -> {
                if (hitEntities.add(entity.getUUID())) {
                    boss.swing(InteractionHand.MAIN_HAND);
                    entity.hurt(
                            boss.damageSources().mobAttack(boss),
                            AlbedoConfig.THRUST_DAMAGE
                    );
                    // Impact burst
                    if (boss.level() instanceof ServerLevel level) {
                        level.sendParticles(ParticleTypes.EXPLOSION,
                                entity.getX(), entity.getY() + 1, entity.getZ(),
                                3, 0.3, 0.3, 0.3, 0.1);
                    }
                    Vec3 kb = chargeDir.scale(2.0);
                    entity.push(kb.x, 0.5, kb.z);
                }
            });
        } else {
            boss.setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return tick < WARMUP_TICKS + CHARGE_TICKS + RECOVERY_TICKS;
    }

    @Override
    public void stop() {
        boss.setAttackState(0);
        boss.setDeltaMovement(Vec3.ZERO);
        tick = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
