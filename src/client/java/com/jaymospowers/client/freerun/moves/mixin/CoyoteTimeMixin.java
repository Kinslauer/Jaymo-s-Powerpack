package com.jaymospowers.client.freerun.moves.mixin;

import com.jaymospowers.client.freerun.moves.CoyoteTimeClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Coyote time ({@link com.jaymospowers.freerun.moves.CoyoteTime}): your own body, just off an edge, takes a fresh Jump as if
 * still on it. Asked just before the game's own jump check, which only jumps on the ground.
 */
@Mixin(LivingEntity.class)
abstract class CoyoteTimeMixin {
	@Shadow
	protected boolean jumping;
	@Shadow
	private int noJumpDelay;

	@Inject(method = "jumpFromGround", at = @At("HEAD"))
	private void jaymospowers$jumped(CallbackInfo ci) {
		if ((Object)this instanceof LocalPlayer) {
			CoyoteTimeClient.jumped();
		}
	}

	@Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isAffectedByFluids()Z"))
	private void jaymospowers$lateJump(CallbackInfo ci) {
		if ((Object)this instanceof LocalPlayer player && CoyoteTimeClient.lateJump(player, this.jumping) && this.noJumpDelay == 0) {
			player.jumpFromGround();
			this.noJumpDelay = 10;
			CoyoteTimeClient.took(player);
		}
	}
}
