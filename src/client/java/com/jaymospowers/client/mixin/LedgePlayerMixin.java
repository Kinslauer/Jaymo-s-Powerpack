package com.jaymospowers.client.mixin;

import com.jaymospowers.client.LedgeClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The local player on a ledge: the hold moves them instead of their own travel (no gravity, no steering), and a Jump
 * press there climbs instead of opening an elytra.
 */
@Mixin(Player.class)
abstract class LedgePlayerMixin {
	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$ledgeTravel(Vec3 input, CallbackInfo ci) {
		if (LedgeClient.active() && (Object)this == Minecraft.getInstance().player) {
			LedgeClient.travel(Minecraft.getInstance().player);
			ci.cancel();
		}
	}

	@Inject(method = "tryToStartFallFlying", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$ledgeNoGlide(CallbackInfoReturnable<Boolean> cir) {
		if (LedgeClient.active() && (Object)this == Minecraft.getInstance().player) {
			cir.setReturnValue(false);
		}
	}
}
