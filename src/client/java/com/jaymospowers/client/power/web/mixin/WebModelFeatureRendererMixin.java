package com.jaymospowers.client.power.web.mixin;

import com.jaymospowers.client.power.web.WebArms;
import com.jaymospowers.client.power.web.WebRagdoll;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right after a model animates (body and armour alike), a web slinger's arms reach along their webs (see WebArms). */
@Mixin(ModelFeatureRenderer.class)
abstract class WebModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$webArms(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		com.jaymospowers.client.BodyMotion.applyModel(submit.model(), submit.state());
		com.jaymospowers.client.FreefallPose.applyModel(submit.model(), submit.state());
		com.jaymospowers.client.freerun.BalancePose.applyModel(submit.model(), submit.state());
		com.jaymospowers.client.freerun.RunPose.applyModel(submit.model(), submit.state());
		com.jaymospowers.client.freerun.UpRunPose.applyModel(submit.model(), submit.state());
		WebArms.applyModel(submit.model(), submit.state());
		com.jaymospowers.client.power.web.WebFeral.applyModel(submit.model(), submit.state());
		com.jaymospowers.client.power.web.WebSuitGrowth.applyModel(submit.model(), submit.state());
		WebRagdoll.applyModel(submit.model(), submit.state());
		com.jaymospowers.client.power.web.WebHangPose.applyModel(submit.model(), submit.state());
		com.jaymospowers.client.power.web.WebClimbPose.applyModel(submit.model(), submit.state());
		com.jaymospowers.client.power.web.WebHeldGrabPose.applyModel(submit.model(), submit.state());
		com.jaymospowers.client.power.web.WebExecutionPose.applyModel(submit.model(), submit.state());
		com.jaymospowers.client.power.web.WebBodyPose.applyModel(submit.model(), submit.state());
		com.jaymospowers.client.power.web.WebCounterPose.applyModel(submit.model(), submit.state());
		com.jaymospowers.client.power.web.WebSuitTear.applyModel(submit.model(), submit.state());
		com.jaymospowers.client.power.web.WebMorphLayer.applyModel(submit.model(), submit.state());
		com.jaymospowers.client.power.web.WebWallRunPose.applyModel(submit.model(), submit.state());
		com.jaymospowers.client.power.web.WebParkourPose.applyModel(submit.model(), submit.state());
		com.jaymospowers.client.power.chrono.ChronoFlowPose.applyModel(submit.model(), submit.state());
	}
}
