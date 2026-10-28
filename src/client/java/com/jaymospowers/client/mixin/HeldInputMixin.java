package com.jaymospowers.client.mixin;

import com.jaymospowers.client.HeldInput;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.17.97, was FinisherAttackMixin; 0.17.129 every charge bar's) While a hold has the hands ({@link HeldInput}), the click
 * doesn't swing and holding it neither repeats Better Combat's swings (its hold-to-attack runs in {@code continueAttack}) nor
 * digs; held use still goes to the server, which counts its repeats as the hold, but the arm stays put. Ahead of the default
 * priority so Better Combat doesn't take the click first, as with the bomb glider.
 */
@Mixin(value = Minecraft.class, priority = 900)
abstract class HeldInputMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$heldClick(CallbackInfoReturnable<Boolean> cir) {
		if (HeldInput.locksAttack((Minecraft)(Object)this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$heldAttack(boolean down, CallbackInfo ci) {
		if (HeldInput.locksAttack((Minecraft)(Object)this)) {
			ci.cancel();
		}
	}

	@WrapWithCondition(method = "startUseItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z"))
	private boolean jaymospowers$heldUse(LocalPlayer player, InteractionHand hand, SwingAnimation animation, boolean sendToServer) {
		return !HeldInput.locksUseSwing();
	}
}
