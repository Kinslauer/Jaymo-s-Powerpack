package com.jaymospowers.client.mixin;

import com.jaymospowers.client.wounds.TendPose;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.15.99) Right after a model animates: a healer on one knee, hands at the wound (see TendPose). */
@Mixin(ModelFeatureRenderer.class)
abstract class TendModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$tendLimbs(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		TendPose.applyModel(submit.model(), submit.state());
		com.jaymospowers.client.CrouchPose.applyModel(submit.model(), submit.state());
		com.jaymospowers.client.wounds.WoundNursePose.applyModel(submit.model(), submit.state());
	}

	/** (0.17.18) Last thing before the model is drawn, every pose laid: on one knee, the front knee bent (see KneeBend). */
	@Inject(method = "prepareModel", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
	private void jaymospowers$bendKnee(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		// (0.19.45) your own body in first person: the top of the torso off, before anything bends what's left
		com.jaymospowers.client.FirstPersonTrim.apply(submit.model(), submit.state());
		com.jaymospowers.client.CrouchPose.bendKnee(submit.model(), submit.state());
		// (0.21.28) and every other kneel's knees (tending, drinking, finishers, executions, Gravity's crush)
		com.jaymospowers.client.KneelLegs.bend(submit.model(), submit.state());
		// (0.19.30) and the crawl's elbows and knees
		com.jaymospowers.client.CrawlPose.bendLimbs(submit.model(), submit.state());
		// (0.20.38) and a sitter's knees
		com.jaymospowers.client.MeditatePose.bendKnees(submit.model(), submit.state());
		// (0.21.38) and a climber's elbows and knees
		com.jaymospowers.client.power.web.WebParkourPose.bendLimbs(submit.model(), submit.state());
		// and a boxer's elbows
		com.jaymospowers.client.boxing.BoxingPoses.bendLimbs(submit.model(), submit.state());
	}

	/** (0.17.18) And the leg's own straight boxes back once it's drawn. */
	@Inject(method = "prepareModel", at = @At("RETURN"))
	private void jaymospowers$straightenKnee(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		com.jaymospowers.client.KneeBend.restore();
		// (0.19.45) after the bends, which may have wrapped the cut boxes
		com.jaymospowers.client.FirstPersonTrim.restore();
	}
}
