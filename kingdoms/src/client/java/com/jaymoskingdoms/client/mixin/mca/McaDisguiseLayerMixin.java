package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.dragons.Disguises;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.render.VillagerVisuals;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.client.render.layer.VillagerLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.139) A disguised Eurus or Aeolus ({@link Disguises}) is its skin alone: MCA's clothing, hair and face layers are
 * left out, since the skin already has all of those painted on.
 */
@Mixin(VillagerLayer.class)
abstract class McaDisguiseLayerMixin {
	@Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
		at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$disguiseLayers(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidRenderState state,
		float yRot, float xRot, CallbackInfo ci) {
		Object layer = this;
		if (!(layer instanceof ClothingLayer<?, ?> || layer instanceof HairLayer<?, ?> || layer instanceof FaceLayer<?, ?>)) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		try {
			if (Disguises.skin(minecraft.level.getEntity(VillagerVisuals.require(state).entityId())) != null) {
				ci.cancel();
			}
		} catch (RuntimeException e) {
			// (no visuals on this state: not an MCA villager's, so not ours to hide)
		}
	}
}
