package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.JaymosPowers;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * The Xeno-Band on a wearer's left wrist: a 5x3x5 box round the forearm ({@code textures/entity/xeno_band.png}, drawn
 * by {@code tools/gen_xenoform.py}) whose dial glows ({@code xeno_band_glow.png}, an emissive pass). {@link Layer}
 * draws it on everyone whose power is Xenoform while they're themselves (the alien has the insignia instead);
 * {@link #firstPerson} is the same band after the first-person arm, for the slam.
 */
public final class XenoBand {
	/** Whether this player wears the band (and is in their own shape), on the render state. */
	public static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "jaymospowers:xenoform_band");
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/xeno_band.png");
	static final Identifier GLOW = JaymosPowers.id("textures/entity/xeno_band_glow.png");

	private static ModelPart worn, firstPerson;

	private XenoBand() {
	}

	/** The band in the left arm's own space (the arm's box is -1..3 x, -2..10 y, -2..2 z; the wrist is near y 6-9). */
	private static ModelPart bake(PartPose pose) {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("band", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, 6.0F, -2.5F, 5.0F, 3.0F, 5.0F), pose);
		return LayerDefinition.create(mesh, 32, 32).bakeRoot().getChild("band");
	}

	/** For a layer: placed after the arm's own translateAndRotate. */
	static ModelPart worn() {
		if (worn == null) {
			worn = bake(PartPose.ZERO);
		}
		return worn;
	}

	/** For first person: carries the left arm's own offset and the slight outward lean the hand renderer gives it. */
	public static ModelPart firstPerson() {
		if (firstPerson == null) {
			firstPerson = bake(PartPose.offsetAndRotation(5.0F, 2.0F, 0.0F, 0.0F, 0.0F, -0.1F));
		}
		return firstPerson;
	}

	public static void submit(ModelPart part, PoseStack poseStack, SubmitNodeCollector collector, int light) {
		collector.submitModelPart(part, poseStack, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY, null);
		collector.submitModelPart(part, poseStack, RenderTypes.eyes(GLOW), light, OverlayTexture.NO_OVERLAY, null);
	}

	/** The band on a wearer's wrist, in third person. */
	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			if (!Boolean.TRUE.equals(state.getData(KEY)) || state.isInvisible) {
				return;
			}
			PlayerModel model = this.getParentModel();
			// the slam pose is laid over the model after it animates; the arm here is before that, so lay it over again
			XenoformSlap.applyModel(model, state);
			poseStack.pushPose();
			model.leftArm.translateAndRotate(poseStack);
			XenoBand.submit(worn(), poseStack, collector, lightCoords);
			poseStack.popPose();
		}
	}
}
