package com.jaymospowers.mixin;

import com.jaymospowers.api.Power;
import com.jaymospowers.core.Powers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets powers keep players airborne / moving fast on dedicated servers without being kicked or rubber-banded. */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {
	/** Below this gravity (vanilla 0.08), a player isn't kicked for floating. */
	private static final double LOW_GRAVITY = 0.02;

	@Shadow
	public ServerPlayer player;

	@Inject(method = "getMaximumFlyingTicks", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$powerFloating(Entity entity, CallbackInfoReturnable<Integer> cir) {
		Power power = Powers.of(this.player);
		if (entity == this.player && power != null && power.allowsFloating(this.player)) {
			cir.setReturnValue(Integer.MAX_VALUE);
		} else if (entity == this.player && this.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY) < LOW_GRAVITY) {
			// someone else took their weight (Gravity's Lift): floating isn't their doing
			cir.setReturnValue(Integer.MAX_VALUE);
		}
	}

	@Inject(method = "shouldCheckPlayerMovement", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$powerMovement(boolean isFallFlying, CallbackInfoReturnable<Boolean> cir) {
		Power power = Powers.of(this.player);
		if (power != null && power.skipsMovementCheck(this.player)) {
			cir.setReturnValue(false);
		}
	}
}
