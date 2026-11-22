package com.jaymospowers.client.freerun.moves.mixin;

import com.jaymospowers.client.freerun.moves.RunMovesClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While Jump fills the charge jump's bar, it doesn't jump: the leap comes when it's let go (see RunMovesClient#tickCharge). */
@Mixin(LivingEntity.class)
abstract class ChargeJumpMixin {
	@Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$holdChargeJump(CallbackInfo ci) {
		if ((Object)this instanceof LocalPlayer player && RunMovesClient.holdingCharge(player)) {
			ci.cancel();
		}
	}
}
