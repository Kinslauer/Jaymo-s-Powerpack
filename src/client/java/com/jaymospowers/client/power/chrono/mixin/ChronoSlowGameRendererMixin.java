package com.jaymospowers.client.power.chrono.mixin;

import com.jaymospowers.client.power.chrono.ChronoSkipClient;
import com.jaymospowers.client.power.chrono.ChronoSlowLook;
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

/**
 * A Chrono's slowed or stopped world drains to old gold (see ChronoSlowLook), and a time skip warps the runner's screen
 * (ChronoSkipClient, told its strength just before the effect is drawn).
 */
@Mixin(GameRenderer.class)
abstract class ChronoSlowGameRendererMixin {
	@Shadow
	@Final
	private List<PostChain> appliedPostEffects;

	@Shadow
	public abstract List<Identifier> getRequestedPostEffects();

	@Inject(method = "update", at = @At("TAIL"))
	private void jaymospowers$chronoSlowGold(DeltaTracker delta, CallbackInfo ci) {
		Identifier effect = ChronoSlowLook.effect();
		if (effect != null && !this.getRequestedPostEffects().contains(effect)) {
			this.getRequestedPostEffects().add(effect);
		}
		Identifier warp = ChronoSkipClient.effect();
		if (warp != null && !this.getRequestedPostEffects().contains(warp)) {
			this.getRequestedPostEffects().add(warp);
		}
	}

	@Inject(method = "applyPostEffects", at = @At("HEAD"))
	private void jaymospowers$chronoWarpWrite(CallbackInfo ci) {
		if (!this.appliedPostEffects.isEmpty()) {
			ChronoSkipClient.write(this.appliedPostEffects);
		}
	}
}
