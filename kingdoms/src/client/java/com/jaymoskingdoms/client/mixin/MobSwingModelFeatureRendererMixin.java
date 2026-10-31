package com.jaymoskingdoms.client.mixin;

import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Right after a model is posed to be drawn (the body, and each armour piece): a mob's Better Combat swing (MobAnimations),
 * and (0.7.321) last, a goblin's body reshaped into the goblin's (GoblinBody).
 */
@Mixin(ModelFeatureRenderer.class)
abstract class MobSwingModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymoskingdoms$swing(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		// (0.7.451) the list lives in ModelPoses, so a model can be posed this way ahead of its draw too
		com.jaymoskingdoms.client.ModelPoses.after(submit.model(), submit.state());
	}
}
