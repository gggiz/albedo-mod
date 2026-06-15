package com.albedo.mixin;

import com.albedo.AlbedoMod;
import com.albedo.entity.AlbedoBoss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public class ItemEntityMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (!(self.level() instanceof ServerLevel serverLevel)) return;
        if (self.tickCount < 60) return;
        if (!self.getItem().is(Items.NETHER_STAR)) return;

        BlockPos pos = self.blockPosition();
        BlockPos below = pos.below();
        if (!serverLevel.getBlockState(below).is(Blocks.NETHERITE_BLOCK)) return;
        // 物品必须在合金块表面附近（Y距离不超过1格）
        if (self.getY() - below.getY() > 1.5) return;

        for (int i = 0; i < 60; i++) {
            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    below.getX() + 0.5, below.getY() + 1.5, below.getZ() + 0.5,
                    5, 1.5, 2.0, 1.5, 0.2);
        }
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                below.getX() + 0.5, below.getY() + 1.0, below.getZ() + 0.5,
                3, 2.0, 2.0, 2.0, 0.5);
        serverLevel.playSound(null, below, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.0f, 1.0f);

        AlbedoBoss boss = AlbedoMod.ALBEDO.create(serverLevel, EntitySpawnReason.EVENT);
        if (boss != null) {
            boss.teleportTo(below.getX() + 0.5, below.getY() + 1.0, below.getZ() + 0.5);
            boss.setYRot(serverLevel.getRandom().nextFloat() * 360f);
            boss.setXRot(0f);
            serverLevel.addFreshEntity(boss);
        }

        serverLevel.destroyBlock(below, false);
        self.discard();
    }
}
