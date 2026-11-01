package com.jaymoskingdoms.client.mixin.tweaks;

import com.jaymoskingdoms.client.spirits.GrueClient;
import com.jaymoskingdoms.spirits.Grue;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.464) The Grue has its host ({@link GrueClient#hostLocked}): the attack key does nothing, and the use key only eats or
 * drinks what is in hand (the server holds to the same, {@link Grue}). Ahead of the default priority, like the tome's.
 */
@Mixin(value = Minecraft.class, priority = 900)
abstract class GrueHandsMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$grueAttack(CallbackInfoReturnable<Boolean> cir) {
		if (GrueClient.hostLocked()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$grueHold(boolean down, CallbackInfo ci) {
		if (GrueClient.hostLocked()) {
			ci.cancel();
		}
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$grueUse(CallbackInfo ci) {
		Minecraft minecraft = (Minecraft)(Object)this;
		if (GrueClient.hostLocked() && minecraft.player != null
			&& !Grue.keepsAlive(minecraft.player.getMainHandItem()) && !Grue.keepsAlive(minecraft.player.getOffhandItem())) {
			ci.cancel();
		}
	}
}
