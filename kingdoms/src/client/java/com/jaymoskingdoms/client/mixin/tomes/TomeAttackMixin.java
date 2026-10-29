package com.jaymoskingdoms.client.mixin.tomes;

import com.jaymoskingdoms.Tomes;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A spell tome in the main hand takes the attack key ({@code TomeClient} sends its press and release): the click doesn't
 * swing, hit or dig, and holding it repeats nothing. Ahead of the default priority so Better Combat doesn't take the click first.
 */
@Mixin(value = Minecraft.class, priority = 900)
abstract class TomeAttackMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$tomeClick(CallbackInfoReturnable<Boolean> cir) {
		Minecraft minecraft = (Minecraft)(Object)this;
		if (minecraft.player != null && Tomes.isTome(minecraft.player.getMainHandItem())) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$tomeHold(boolean down, CallbackInfo ci) {
		Minecraft minecraft = (Minecraft)(Object)this;
		if (minecraft.player != null && Tomes.isTome(minecraft.player.getMainHandItem())) {
			ci.cancel();
		}
	}
}
