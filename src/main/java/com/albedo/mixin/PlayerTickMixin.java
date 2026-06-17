package com.albedo.mixin;

import com.albedo.item.RedEmperorAzureStarItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class PlayerTickMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        Level level = self.level();
        if (level instanceof ServerLevel sl) {
            RedEmperorAzureStarItem.tickPlayerMeteors(sl, self);
        }
    }
}
