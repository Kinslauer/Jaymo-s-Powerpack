package com.jaymoskingdoms.client.mixin.sailing;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Whalers;
import com.jaymoskingdoms.client.bb.BbModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractBoatRenderer;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.351) A whaleboat ({@link Whalers#LAUNCH}) drawn as a launch: the Blockbench Workshop's "Launch" (CC BY 4.0, credits
 * in textures/entity/bb/CREDITS.txt; baked to bbmodels/whaleboat.json), a long open rowing boat with a keel, benches and
 * a raked stem, instead of the spruce boat she is. Turned and rocked as the boat is; its bow forward.
 */
@Mixin(AbstractBoatRenderer.class)
abstract class WhaleboatLookMixin {
	/** How big the launch is drawn (its own 0.8 by 2.9 blocks would sit small round two rowers). */
	@Unique
	private static final float SCALE = 1.2F;
	/** The launch's middle in the model, along its length (pixels): put over the boat's. */
	@Unique
	private static final float MIDDLE = 8.1F;
	@Unique
	private static final RenderStateDataKey<Boolean> LAUNCH = RenderStateDataKey.create(() -> "jaymoskingdoms:whaleboat_launch");
	@Unique
	private static BbModel bb;
	@Unique
	private static Model.Simple model;

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;Lnet/minecraft/client/renderer/entity/state/BoatRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskingdoms$launch(AbstractBoat boat, BoatRenderState state, float partialTicks, CallbackInfo ci) {
		state.setData(LAUNCH, boat.hasAttached(Whalers.LAUNCH));
	}

	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/BoatRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$drawLaunch(BoatRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		if (!Boolean.TRUE.equals(state.getData(LAUNCH))) {
			return;
		}
		if (model == null) {
			bb = BbModel.load(JaymosKingdoms.MOD_ID, "whaleboat");
			model = new Model.Simple(bb.root, RenderTypes::entityCutout);
		}
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, 180.0F - state.yRot);
		if (state.hurtTime > 0.0F) {
			poseStack.rotateDegrees(Axis.XP, Mth.sin(state.hurtTime) * state.hurtTime * state.damageTime / 10.0F * state.hurtDir);
		}
		poseStack.scale(SCALE, SCALE, SCALE);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.translate(0.5F, -1.5F, -MIDDLE / 16.0F);
		collector.submitModel(model, Unit.INSTANCE, poseStack, RenderTypes.entityCutout(bb.texture), state.lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
			state.outlineColor);
		poseStack.popPose();
		ci.cancel();
	}
}
