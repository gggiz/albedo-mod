package com.albedo.mixin;

import com.albedo.entity.AxolotlMage;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public class MonsterTargetMixin {

    @Shadow @Final protected GoalSelector targetSelector;

    @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V", at = @At("TAIL"))
    private void onInit(EntityType<? extends Mob> type, Level level, CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (self instanceof Monster && !(self instanceof AxolotlMage)) {
            targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(
                    self, AxolotlMage.class, true));
        }
    }
}
