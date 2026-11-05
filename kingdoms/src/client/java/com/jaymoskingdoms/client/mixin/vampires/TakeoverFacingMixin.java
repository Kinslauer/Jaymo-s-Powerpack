package com.jaymoskingdoms.client.mixin.vampires;

import com.jaymoskingdoms.TakeoverFacing;
import com.jaymoskingdoms.client.SeizureClient;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (2026-11-05, Andy) A player something has (the blood fever, the curse, the Grue: SeizureClient#drivenBody) is drawn on every
 * screen facing the way they're driven, not the way their camera looks (TakeoverFacing; the head is held to a neck's turn
 * by TakeoverFacingHeadMixin).
 */
@Mixin(LivingEntity.class)
abstract class TakeoverFacingMixin {
	@Inject(method = "tickHeadTurn", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$takeoverFacing(float yBodyRotT, CallbackInfo ci) {
		LivingEntity self = (LivingEntity)(Object)this;
		if (self.level().isClientSide() && self instanceof Player player && SeizureClient.drivenBody(player)) {
			TakeoverFacing.turn(self);
			ci.cancel();
		}
	}
}
