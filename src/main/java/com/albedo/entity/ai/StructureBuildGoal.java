package com.albedo.entity.ai;

import com.albedo.AlbedoConfig;
import com.albedo.entity.AlbedoBoss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.List;

public class StructureBuildGoal extends Goal {
    private final AlbedoBoss boss;
    private int tick = 0;
    private int placedCount = 0;
    private int currentIndex = 0;
    private List<AlbedoBoss.BuildPlan> plan;

    public StructureBuildGoal(AlbedoBoss boss) {
        this.boss = boss;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!boss.isBuilding()) return false;
        if (boss.getAttackState() != 0) return false;
        if (boss.isOnCooldown("build")) return false;
        if (!boss.hasBuildPlan()) return false;
        return true;
    }

    @Override
    public void start() {
        tick = 0;
        placedCount = 0;
        currentIndex = 0;
        boss.setAttackState(6);

        plan = boss.getBuildPlan();
        boss.setBuildProgress(0);

        if (boss.level() instanceof ServerLevel sl) {
            sl.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 0.5f, 1.5f);
        }
    }

    @Override
    public void tick() {
        tick++;

        // 准备阶段：暖机粒子
        if (tick < AlbedoConfig.BUILD_WARMUP) {
            if (boss.level() instanceof ServerLevel sl && tick % 4 == 0) {
                sl.sendParticles(ParticleTypes.ENCHANTED_HIT,
                        boss.getX(), boss.getY() + 1.5, boss.getZ(),
                        8, 1.0, 1.0, 1.0, 0.05);
            }
            return;
        }

        // 建完了
        if (plan == null || currentIndex >= plan.size()) {
            finishBuild();
            return;
        }

        ServerLevel world = (ServerLevel) boss.level();
        int placedThisTick = 0;

        while (placedThisTick < AlbedoConfig.BUILD_BLOCKS_PER_TICK && currentIndex < plan.size()) {
            AlbedoBoss.BuildPlan entry = plan.get(currentIndex);
            BlockPos pos = entry.pos();
            BlockState target = entry.state();
            BlockState current = world.getBlockState(pos);

            if (!target.isAir()
                    && !target.is(Blocks.STRUCTURE_VOID)
                    && !target.equals(current)) {
                world.setBlock(pos, target, 2);
                placedThisTick++;
                placedCount++;

                if (placedCount % 5 == 0) {
                    world.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                            1, 0.2, 0.2, 0.2, 0);
                }
            }

            currentIndex++;
        }

        // 更新进度
        boss.setBuildProgress(plan.size() > 0 ? currentIndex * 100 / plan.size() : 0);

        // 粒子环绕
        if (world instanceof ServerLevel sl && tick % 10 == 0) {
            sl.sendParticles(ParticleTypes.PORTAL,
                    boss.getX(), boss.getY() + 1.0, boss.getZ(),
                    3, 0.5, 0.5, 0.5, 0.02);
        }
    }

    private void finishBuild() {
        if (boss.level() instanceof ServerLevel sl) {
            sl.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.7f, 1.0f);
            sl.sendParticles(ParticleTypes.EXPLOSION,
                    boss.getX(), boss.getY() + 1.5, boss.getZ(),
                    5, 1.0, 1.0, 1.0, 0.1);
        }
        boss.setBuildProgress(100);
        boss.clearBuildPlan();
        boss.setCooldown("build", AlbedoConfig.BUILD_COOLDOWN);
        stop();
    }

    @Override
    public boolean canContinueToUse() {
        if (!boss.isBuilding()) return false;
        if (boss.getTarget() != null) return false;
        if (plan == null || currentIndex >= plan.size()) return false;
        return tick < AlbedoConfig.BUILD_WARMUP || currentIndex < plan.size();
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
