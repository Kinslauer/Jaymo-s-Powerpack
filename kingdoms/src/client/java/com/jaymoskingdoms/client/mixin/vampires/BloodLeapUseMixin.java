package com.jaymoskingdoms.client.mixin.vampires;

import com.jaymoskingdoms.BloodGrab;
import com.jaymoskingdoms.BloodLeap;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.Vampires;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A vampire's right-click with an empty main hand ({@link BloodLeap}): locked on, it's the leap at the target, ahead of
 * anything else the click would do; not locked, the strong jump, only when nothing else took the click (the method's
 * fall-through end). Once a press: holding use (which repeats the click every few ticks) leaps once.
 */
@Mixin(Minecraft.class)
abstract class BloodLeapUseMixin {
	@Shadow
	public @Nullable LocalPlayer player;
	@Shadow
	public @Nullable MultiPlayerGameMode gameMode;

	/** A leap was asked for during this press of use. */
	@Unique
	private static boolean jaymoskingdoms$leapt;

	@Inject(method = "tick", at = @At("HEAD"))
	private void jaymoskingdoms$releaseLeap(CallbackInfo ci) {
		if (!((Minecraft)(Object)this).options.keyUse.isDown()) {
			jaymoskingdoms$leapt = false;
		}
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$lockedLeap(CallbackInfo ci) {
		if (this.jaymoskingdoms$can() && PowersCompat.lockedClient()) {
			ci.cancel();
			this.jaymoskingdoms$leap();
		}
	}

	@Inject(method = "startUseItem", at = @At("TAIL"))
	private void jaymoskingdoms$jump(CallbackInfo ci) {
		if (this.jaymoskingdoms$can() && this.gameMode != null && !this.gameMode.isDestroying()) {
			this.jaymoskingdoms$leap();
		}
	}

	@Unique
	private boolean jaymoskingdoms$can() {
		LocalPlayer p = this.player;
		return p != null && !p.isSpectator() && !p.isHandsBusy() && p.getMainHandItem().isEmpty() && Vampires.isVampire(p)
			&& !p.hasAttached(BloodGrab.GRABBING);
	}

	@Unique
	private void jaymoskingdoms$leap() {
		if (!jaymoskingdoms$leapt) {
			jaymoskingdoms$leapt = true;
			ClientPlayNetworking.send(new BloodLeap.Leap());
		}
	}
}
