package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.client.HiddenBladeClient;
import com.jaymoskingdoms.client.MobAnimations;
import com.jaymoskingdoms.client.PickpocketPose;
import com.jaymoskingdoms.client.PullerPose;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right after a model is posed to be drawn (the body, and each armour piece): a mob's Better Combat swing (MobAnimations). */
@Mixin(ModelFeatureRenderer.class)
abstract class MobSwingModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymoskingdoms$swing(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		PullerPose.apply(submit.model(), submit.state());
		PickpocketPose.apply(submit.model(), submit.state());
		com.jaymoskingdoms.client.BioAbsorbPose.apply(submit.model(), submit.state());
		com.jaymoskingdoms.client.RubberEngulf.apply(submit.model(), submit.state());
		com.jaymoskingdoms.client.BioForms.apply(submit.model(), submit.state());
		com.jaymoskingdoms.client.HandshakePose.apply(submit.model(), submit.state());
		MobAnimations.apply(submit.model(), submit.state());
		HiddenBladeClient.pose(submit.model(), submit.state());
	}
}
