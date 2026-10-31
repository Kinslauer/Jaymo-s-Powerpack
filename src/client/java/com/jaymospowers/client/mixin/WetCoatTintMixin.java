package com.jaymospowers.client.mixin;

import com.jaymospowers.client.pets.WetCoats;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (Motion patch 6) A wet furry animal's coat drawn a shade darker (WetCoats). Wolves keep vanilla's own wet shade. */
@Mixin(LivingEntityRenderer.class)
abstract class WetCoatTintMixin {
	@Inject(method = "getModelTint", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$wetCoat(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
		int tinted = WetCoats.tint(state, cir.getReturnValueI());
		if (tinted != cir.getReturnValueI()) {
			cir.setReturnValue(tinted);
		}
	}
}
