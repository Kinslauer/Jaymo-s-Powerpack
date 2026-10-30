package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.client.BioForms;
import com.jaymoskingdoms.client.TinkererOutfit;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.client.render.layer.VillagerLayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.313) A Bio-built player is their form's body ({@link BioForms}): MCA's clothing, hair and face are left off it,
 * as for a disguise ({@link McaDisguiseLayerMixin}).
 */
@Mixin(VillagerLayer.class)
abstract class McaBioFormLayerMixin {
	@Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
		at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$bioFormLayers(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidRenderState state,
		float yRot, float xRot, CallbackInfo ci) {
		Object layer = this;
		if ((layer instanceof ClothingLayer<?, ?> || layer instanceof HairLayer<?, ?> || layer instanceof FaceLayer<?, ?>)
			&& state.getData(BioForms.KEY) != null) {
			ci.cancel();
		}
		// (Andy, 2026-10-29) the Mad Tinkerer's hat takes his hair's place ({@link TinkererOutfit})
		if (layer instanceof HairLayer<?, ?> && state.getData(TinkererOutfit.KEY) != null) {
			ci.cancel();
		}
	}
}
