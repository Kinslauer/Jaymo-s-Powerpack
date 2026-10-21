package com.jaymoskitchen.client.mixin;

import com.jaymoskitchen.client.ragdoll.LimpController;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.5.1) The local player lying limp doesn't steer: their travel is the ragdoll's (see LimpController), and a jump
 * does not open an elytra.
 */
@Mixin(Player.class)
abstract class LimpInputMixin {
	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$limpTravel(Vec3 input, CallbackInfo ci) {
		if (LimpController.active() && (Object)this == Minecraft.getInstance().player) {
			ci.cancel();
		}
	}

	@Inject(method = "tryToStartFallFlying", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$limpNoGlide(CallbackInfoReturnable<Boolean> cir) {
		if (LimpController.active() && (Object)this == Minecraft.getInstance().player) {
			cir.setReturnValue(false);
		}
	}
}
