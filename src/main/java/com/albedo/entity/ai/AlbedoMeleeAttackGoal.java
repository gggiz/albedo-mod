package com.albedo.entity.ai;

import com.albedo.AlbedoConfig;
import com.albedo.entity.AlbedoBoss;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class AlbedoMeleeAttackGoal extends Goal {
    private final AlbedoBoss boss;
    private final PathNavigation navigation;
    private int swingTimer = 0;
    private int strafeDir = 1;
    private int strafeTimer = 0;
    private int backstepTimer = 0;
    private static final int SWING_COOLDOWN = 25;
    private static final double ATTACK_RANGE = 3.0;
    private static final double CHASE_SPEED = 1.4;
    private static final double STRAFE_SPEED = 1.1;
    private int pathUpdateTimer = 0;

    public AlbedoMeleeAttackGoal(AlbedoBoss boss) {
        this.boss = boss;
        this.navigation = boss.getNavigation();
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive() && !boss.isSitting();
    }

    @Override
    public boolean canContinueToUse() {
        if (boss.getTarget() == null || !boss.getTarget().isAlive() || boss.isSitting()) {
            return false;
        }
        // Yield to skills if any is ready
        if (!boss.isOnCooldown("sweep") && boss.getPhase() >= 1 && boss.distanceTo(boss.getTarget()) <= 3.5f
                || !boss.isOnCooldown("thrust") && boss.getPhase() >= 2 && !boss.isClone()
                || !boss.isOnCooldown("wave") && boss.getPhase() >= 2 && !boss.isClone()
                || !boss.isOnCooldown("judgment") && boss.getPhase() >= 3 && !boss.isClone()
                || !boss.isOnCooldown("clone") && boss.getPhase() >= 3 && !boss.isClone()) {
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        swingTimer = 10;
        pathUpdateTimer = 0;
        strafeTimer = 0;
        backstepTimer = 0;
        strafeDir = boss.getRandom().nextBoolean() ? 1 : -1;
    }

    @Override
    public void tick() {
        LivingEntity target = boss.getTarget();
        if (target == null) return;

        boss.getLookControl().setLookAt(target, 30f, 30f);

        double dist = boss.distanceTo(target);
        swingTimer--;
        strafeTimer--;

        if (dist > ATTACK_RANGE + 2.0) {
            // Rush in
            if (--pathUpdateTimer <= 0) {
                pathUpdateTimer = 8;
                navigation.moveTo(target, CHASE_SPEED);
            }
        } else if (dist > ATTACK_RANGE) {
            // Strafe sideways to circle target
            if (strafeTimer <= 0) {
                strafeTimer = 20 + boss.getRandom().nextInt(20);
                strafeDir = boss.getRandom().nextBoolean() ? 1 : -1;
            }
            Vec3 toTarget = target.position().subtract(boss.position());
            Vec3 strafe = new Vec3(-toTarget.z, 0, toTarget.x).normalize().scale(strafeDir);
            Vec3 strafeTarget = boss.position().add(strafe.scale(0.8)).add(toTarget.normalize().scale(0.3));
            if (--pathUpdateTimer <= 0) {
                pathUpdateTimer = 12;
                navigation.moveTo(strafeTarget.x, strafeTarget.y, strafeTarget.z, STRAFE_SPEED);
            }
        } else {
            navigation.stop();
            // Occasionally backstep to dodge
            if (backstepTimer <= 0 && boss.getRandom().nextFloat() < 0.15f && dist < 2.0) {
                backstepTimer = 30;
                Vec3 back = boss.position().subtract(target.position()).normalize().scale(1.5);
                boss.push(back.x, 0.1, back.z);
            }
            backstepTimer--;

            if (swingTimer <= 0) {
                swingTimer = SWING_COOLDOWN + boss.getRandom().nextInt(8);
                boss.setAttackState(1);
                boss.playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK);
                boss.swing(InteractionHand.MAIN_HAND);
                target.hurt(
                        boss.damageSources().mobAttack(boss),
                        (float) AlbedoConfig.BASE_ATTACK
                );
                Vec3 kb = target.position()
                        .subtract(boss.position())
                        .normalize()
                        .scale(0.8);
                target.push(kb.x, 0.3, kb.z);
            }
        }
    }

    @Override
    public void stop() {
        boss.setAttackState(0);
        navigation.stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
