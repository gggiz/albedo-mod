package com.albedo.entity.ai;

import com.albedo.AlbedoConfig;
import com.albedo.entity.AlbedoBoss;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class ShadowCloneGoal extends Goal {
    private final AlbedoBoss boss;
    private int tick = 0;
    private boolean clonesSpawned = false;
    private static final int WARMUP_TICKS = 20;
    private static final int MAX_CLONES = 2;

    public ShadowCloneGoal(AlbedoBoss boss) {
        this.boss = boss;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return boss.getTarget() != null
                && boss.getTarget().isAlive()
                && boss.getAttackState() == 0
                && !boss.isSitting()
                && !boss.isClone()
                && boss.getPhase() >= 3
                && !boss.isOnCooldown("clone");
    }

    @Override
    public void start() {
        tick = 0;
        clonesSpawned = false;
        boss.setAttackState(4);
        boss.setCooldown("clone", AlbedoConfig.CLONE_COOLDOWN);
        boss.playSound(SoundEvents.ENDERMAN_TELEPORT);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void tick() {
        tick++;

        if (tick == WARMUP_TICKS && !clonesSpawned && boss.level() instanceof ServerLevel level) {
            clonesSpawned = true;
            LivingEntity target = boss.getTarget();
            if (target == null) return;

            // Count existing clones (max 2 total)
            long existing = level.getEntities(boss.getType(),
                    boss.getBoundingBox().inflate(50),
                    e -> e.entityTags().contains("albedo_clone")
            ).size();
            int toSpawn = (int) Math.max(0, MAX_CLONES - existing);

            for (int i = 0; i < toSpawn; i++) {
                double angle = i * Math.PI;
                Vec3 offset = new Vec3(
                        Math.cos(angle) * 3,
                        0,
                        Math.sin(angle) * 3
                );

                AlbedoBoss clone = new AlbedoBoss(
                        (net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.monster.Monster>) boss.getType(),
                        level);
                clone.setPos(boss.position().add(offset));
                clone.setTarget(target);
                clone.setHealth(40f);
                clone.addTag("albedo_clone");
                clone.setSilent(true);
                clone.setNoAi(false);

                level.addFreshEntity(clone);

                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        clone.getX(), clone.getY() + 1, clone.getZ(),
                        10, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return tick < WARMUP_TICKS + 10;
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
