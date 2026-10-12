package com.jaymoskingdoms.client.mixin.equestrian;

import com.jaymoskingdoms.client.equestrian.HorseLooks;
import net.minecraft.client.renderer.entity.HorseRenderer;
import net.minecraft.client.renderer.entity.state.HorseRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Markings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.5.33) A horse wears its breed's coat (its markings are painted in, so vanilla's are left off). */
@Mixin(HorseRenderer.class)
abstract class HorseRendererMixin {
	@Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/HorseRenderState;)Lnet/minecraft/resources/Identifier;", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$coat(HorseRenderState state, CallbackInfoReturnable<Identifier> cir) {
		Identifier coat = HorseLooks.texture(state);
		if (coat != null) {
			cir.setReturnValue(coat);
		}
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/animal/equine/Horse;Lnet/minecraft/client/renderer/entity/state/HorseRenderState;F)V", at = @At("TAIL"))
	private void jaymoskingdoms$noMarkings(Horse horse, HorseRenderState state, float partialTicks, CallbackInfo ci) {
		if (HorseLooks.texture(state) != null) {
			state.markings = Markings.NONE;
		}
	}
}
