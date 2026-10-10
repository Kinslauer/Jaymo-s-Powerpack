package com.jaymospowers.mixin;

import com.jaymospowers.core.Carry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A carried (or just let go) player goes where the carry puts them: never kicked for floating or moving too fast. */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class CarryPacketListenerMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "getMaximumFlyingTicks", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$carriedFloating(Entity entity, CallbackInfoReturnable<Integer> cir) {
		if (entity == this.player && Carry.relaxesMovement(this.player)) {
			cir.setReturnValue(Integer.MAX_VALUE);
		}
	}

	@Inject(method = "shouldCheckPlayerMovement", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$carriedMovement(boolean isFallFlying, CallbackInfoReturnable<Boolean> cir) {
		if (Carry.relaxesMovement(this.player)) {
			cir.setReturnValue(false);
		}
	}
}
