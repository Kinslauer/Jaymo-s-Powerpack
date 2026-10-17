package com.jaymospowers.mixin;

import com.jaymospowers.core.Ledge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player hanging from a ledge (or pulling up over it, or just off it) is never kicked for floating or moving too fast. */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class LedgePacketListenerMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "getMaximumFlyingTicks", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$ledgeFloating(Entity entity, CallbackInfoReturnable<Integer> cir) {
		if (entity == this.player && Ledge.relaxesMovement(this.player)) {
			cir.setReturnValue(Integer.MAX_VALUE);
		}
	}

	@Inject(method = "shouldCheckPlayerMovement", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$ledgeMovement(boolean isFallFlying, CallbackInfoReturnable<Boolean> cir) {
		if (Ledge.relaxesMovement(this.player)) {
			cir.setReturnValue(false);
		}
	}
}
