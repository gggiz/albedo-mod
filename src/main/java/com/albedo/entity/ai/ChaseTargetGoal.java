package com.albedo.entity.ai;

import com.albedo.entity.AxolotlMage;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class ChaseTargetGoal extends Goal {
    private final AxolotlMage mage;

    public ChaseTargetGoal(AxolotlMage mage) {
        this.mage = mage;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mage.getTarget();
        return target != null && target.isAlive()
                && !mage.isCasting()
                && mage.distanceTo(target) > 10.0;
    }

    @Override
    public void tick() {
        LivingEntity target = mage.getTarget();
        if (target != null) {
            mage.getNavigation().moveTo(target, 1.0);
            mage.getLookControl().setLookAt(target, 30f, 30f);
        }
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mage.getTarget();
        return target != null && target.isAlive()
                && !mage.isCasting()
                && mage.distanceTo(target) > 6.0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
