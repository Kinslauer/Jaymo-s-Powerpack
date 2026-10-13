package com.jaymoskingdoms.client.mixin.equestrian;

import com.jaymoskingdoms.client.equestrian.HorseLooks;
import com.jaymoskingdoms.client.equestrian.TackLayer;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.state.EquineRenderState;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.5.34) Each horse's breed coat and build onto its render state ({@link HorseLooks}); (0.5.44) its tack ({@link TackLayer}). */
@Mixin(AbstractHorseRenderer.class)
abstract class AbstractHorseRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/animal/equine/AbstractHorse;Lnet/minecraft/client/renderer/entity/state/EquineRenderState;F)V", at = @At("TAIL"))
	private void jaymoskingdoms$looks(AbstractHorse horse, EquineRenderState state, float partialTicks, CallbackInfo ci) {
		HorseLooks.extract(horse, state);
		TackLayer.extract(horse, state, partialTicks);
	}
}
