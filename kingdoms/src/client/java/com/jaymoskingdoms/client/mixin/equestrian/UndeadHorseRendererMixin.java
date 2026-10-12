package com.jaymoskingdoms.client.mixin.equestrian;

import com.jaymoskingdoms.client.equestrian.HorseLooks;
import net.minecraft.client.renderer.entity.UndeadHorseRenderer;
import net.minecraft.client.renderer.entity.state.EquineRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.5.33) Skeleton and zombie horses in their new coats. */
@Mixin(UndeadHorseRenderer.class)
abstract class UndeadHorseRendererMixin {
	@Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/EquineRenderState;)Lnet/minecraft/resources/Identifier;", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$coat(EquineRenderState state, CallbackInfoReturnable<Identifier> cir) {
		Identifier coat = HorseLooks.texture(state);
		if (coat != null) {
			cir.setReturnValue(coat);
		}
	}
}
