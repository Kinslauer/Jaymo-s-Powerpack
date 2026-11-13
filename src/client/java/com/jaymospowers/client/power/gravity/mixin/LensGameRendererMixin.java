package com.jaymospowers.client.power.gravity.mixin;

import com.jaymospowers.client.power.gravity.LensClient;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A black hole in view: the screen wears the lensing effect, told each frame where the hole is (see LensClient). */
@Mixin(GameRenderer.class)
abstract class LensGameRendererMixin {
	@Shadow
	@Final
	private List<PostChain> appliedPostEffects;

	@Shadow
	public abstract List<Identifier> getRequestedPostEffects();

	@Inject(method = "update", at = @At("TAIL"))
	private void jaymospowers$lensAsk(DeltaTracker delta, CallbackInfo ci) {
		Identifier effect = LensClient.effect();
		if (effect != null && !this.getRequestedPostEffects().contains(effect)) {
			this.getRequestedPostEffects().add(effect);
		}
	}

	@Inject(method = "applyPostEffects", at = @At("HEAD"))
	private void jaymospowers$lensWrite(CallbackInfo ci) {
		if (!this.appliedPostEffects.isEmpty()) {
			LensClient.write(this.appliedPostEffects);
		}
	}
}
