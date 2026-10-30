package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.client.BioForms;
import com.jaymoskingdoms.client.TinkererOutfit;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.render.VillagerVisuals;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.client.render.layer.VillagerLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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
		if ((layer instanceof ClothingLayer<?, ?> || layer instanceof HairLayer<?, ?> || layer instanceof FaceLayer<?, ?>) && jaymoskingdoms$bio(state)) {
			ci.cancel();
		}
		// (Andy, 2026-10-29) the Mad Tinkerer's hat takes his hair's place ({@link TinkererOutfit})
		if (layer instanceof HairLayer<?, ?> && state.getData(TinkererOutfit.KEY) != null) {
			ci.cancel();
		}
	}

	/**
	 * Whether the body being drawn is a Bio-built player's: the form on the state ({@link BioForms#extract}), or (0.7.363,
	 * Andy's screenshot showed MCA's clothes and hair still on one) the player MCA's visuals were taken from.
	 */
	private static boolean jaymoskingdoms$bio(HumanoidRenderState state) {
		if (state.getData(BioForms.KEY) != null) {
			return true;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return false;
		}
		try {
			Entity entity = minecraft.level.getEntity(VillagerVisuals.require(state).entityId());
			return entity instanceof Player && BioForms.bio(entity);
		} catch (RuntimeException e) {
			// (no visuals on this state: not MCA's to draw)
			return false;
		}
	}
}
