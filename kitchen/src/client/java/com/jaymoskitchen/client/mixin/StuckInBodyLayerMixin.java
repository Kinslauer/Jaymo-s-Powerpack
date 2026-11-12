package com.jaymoskitchen.client.mixin;

import com.jaymoskitchen.client.archery.HeadWounds;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ArrowLayer;
import net.minecraft.client.renderer.entity.layers.StuckInBodyLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player's arrow stuck in the head (the game's own arrow layer) leaves a wound where it went in, as a creature's does (HeadWounds). */
@Mixin(StuckInBodyLayer.class)
abstract class StuckInBodyLayerMixin {
	@Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/entity/layers/StuckInBodyLayer;submitStuckItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IFFFI)V"))
	private void jaymoskitchen$headWound(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state,
		float yRot, float xRot, CallbackInfo ci, @Local ModelPart part, @Local ModelPart.Cube cube, @Local(index = 12) float midX,
		@Local(index = 13) float midY, @Local(index = 14) float midZ) {
		if (!((Object) this instanceof ArrowLayer<?> layer)) {
			// a bee's sting leaves none
			return;
		}
		PlayerModel model = layer.getParentModel();
		if (part == model.head) {
			// out past the hat layer: hair painted there would cover it
			HeadWounds.submit(poseStack, collector, lightCoords, cube, midX, midY, midZ, HeadWounds.OVER_HAT);
		}
	}
}
