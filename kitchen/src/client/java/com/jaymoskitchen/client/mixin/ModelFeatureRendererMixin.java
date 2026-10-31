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
		com.jaymoskitchen.client.corpse.ZombiePlayerRenderer.armsOut(model, submit.state());
		Limp.apply(model, submit.state());
		CarryPose.apply(model, submit.state());
		com.jaymoskitchen.client.corpse.DragPose.apply(model, submit.state());
		com.jaymoskitchen.client.corpse.CrawlPose.apply(model, submit.state());
		com.jaymoskitchen.client.corpse.Severed.hide(model, submit.state());
	}

	/** (0.4.37) Whatever a body's lost parts hid, shown again once it's drawn (see Severed). */
	@Inject(method = "prepareModel", at = @At("RETURN"))
	private void jaymoskitchen$showSevered(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		com.jaymoskitchen.client.corpse.Severed.restore();
	}
}
