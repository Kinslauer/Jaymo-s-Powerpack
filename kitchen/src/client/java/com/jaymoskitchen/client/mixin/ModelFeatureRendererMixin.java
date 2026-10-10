package com.jaymoskitchen.client.mixin;

import com.jaymoskitchen.client.corpse.CarryPose;
import com.jaymoskitchen.client.corpse.Limp;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Right after a model animates (every override in its chain has run, so a zombie's raised arms too), a carried
 * corpse's limbs are set hanging limp instead.
 */
@Mixin(ModelFeatureRenderer.class)
abstract class ModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymoskitchen$limpCorpse(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		Model<?> model = submit.model();
		Limp.apply(model, submit.state());
		CarryPose.apply(model, submit.state());
		com.jaymoskitchen.client.corpse.CrawlPose.apply(model, submit.state());
	}
}
