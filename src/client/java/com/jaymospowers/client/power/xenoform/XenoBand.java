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
import net.minecraft.util.Mth;

/**
 * The Xeno-Band on a wearer's left wrist: a 5x3x5 box round the forearm ({@code textures/entity/xeno_band.png}, drawn
 * by {@code tools/gen_xenoform.py}) whose dial glows ({@code xeno_band_glow.png}, an emissive pass). {@link Layer}
 * draws it on everyone whose power is Xenoform while they're themselves (the alien has the insignia instead);
 * {@link #submitFirstPerson} is the same band on your first-person left arm.
 */
public final class XenoBand {
	/** Whether this player wears the band (and is in their own shape), on the render state. */
	public static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "jaymospowers:xenoform_band");
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/xeno_band.png");
	static final Identifier GLOW = JaymosPowers.id("textures/entity/xeno_band_glow.png");

	private static ModelPart worn;
	private static ModelPart cap;
	/** A slim (3-pixel) arm: the band, made for the 4-pixel one, is narrowed to it, about the arm's middle. */
	static final float SLIM_WIDTH = 0.8F;

	private XenoBand() {
	}

	/** The band in the left arm's own space (the arm's box is -1..3 x, -2..10 y, -2..2 z; the wrist is near y 6-9). */
	private static ModelPart bake(PartPose pose) {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("band", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, 6.0F, -2.5F, 5.0F, 3.0F, 5.0F), pose);
		return LayerDefinition.create(mesh, 32, 32).bakeRoot().getChild("band");
	}

	/**
	 * The dial's cap: the dial's 3x3 face lifted out of the band (a 1-pixel slab on the band's outer face, its face sampling
	 * the dial's texels, 11-14 x 5-8), scaled out to its height as it stands.
	 */
	private static ModelPart cap() {
		if (cap == null) {
			MeshDefinition mesh = new MeshDefinition();
			mesh.getRoot().addOrReplaceChild("cap", CubeListBuilder.create().texOffs(7, 2).addBox(3.5F, 6.0F, -1.5F, 1.0F, 3.0F, 3.0F), PartPose.ZERO);
			cap = LayerDefinition.create(mesh, 32, 32).bakeRoot().getChild("cap");
		}
		return cap;
	}

	/** For a layer: placed after the arm's own translateAndRotate. */
	static ModelPart worn() {
		if (worn == null) {
			worn = bake(PartPose.ZERO);
		}
		return worn;
	}

	/** The band on a left arm, the pose stack already carrying the arm's own placement. */
	public static void submitOnArm(PoseStack poseStack, SubmitNodeCollector collector, int light, boolean slim) {
		poseStack.pushPose();
		if (slim) {
			// a slim arm spans -1..2 (model pixels), not -1..3: narrow the band about the slim arm's middle
			poseStack.translate(0.5F / 16.0F, 0.0F, 0.0F);
			poseStack.scale(SLIM_WIDTH, 1.0F, 1.0F);
			poseStack.translate(-1.0F / 16.0F, 0.0F, 0.0F);
		}
		ModelPart part = worn();
		collector.submitModelPart(part, poseStack, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY, null);
		collector.submitModelPart(part, poseStack, RenderTypes.eyes(GLOW), light, OverlayTexture.NO_OVERLAY, null);
		float pop = XenoformSlamView.pop;
		if (pop > 0.02F) {
			// the slam's raised dial: a small cap, glowing and pulsing
			poseStack.translate(3.5F / 16.0F, 0.0F, 0.0F);
			poseStack.scale(pop * XenoformSlamView.POP_PX, 1.0F, 1.0F);
			poseStack.translate(-3.5F / 16.0F, 0.0F, 0.0F);
			ModelPart cap = cap();
			collector.submitModelPart(cap, poseStack, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY, null);
			int alpha = Mth.clamp((int)(XenoformSlamView.glow * 255.0F), 0, 255);
			collector.submitModelPart(cap, poseStack, RenderTypes.eyes(GLOW), light, OverlayTexture.NO_OVERLAY, null, alpha << 24 | 0xFFFFFF);
		}
		poseStack.popPose();
	}

	/**
	 * In first person, after the hand renderer has drawn your left arm (AvatarRenderer.renderHand: the arm is reset to
	 * its rest pose with its slight outward lean): the band on its wrist, whenever that arm is in view, the slam too.
	 */
	public static void submitFirstPerson(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart arm, PlayerModel model) {
		net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
		if (arm != model.leftArm || player == null || !com.jaymospowers.power.xenoform.XenoformPower.has(player)
			|| com.jaymospowers.power.xenoform.XenoformState.transformed(player) || player.isInvisible() || com.jaymospowers.client.BodyCover.ownArms()) {
			return;
		}
		poseStack.pushPose();
		arm.translateAndRotate(poseStack);
		submitOnArm(poseStack, collector, light, player.getSkin().model() == net.minecraft.world.entity.player.PlayerModelType.SLIM);
		poseStack.popPose();
	}

	/** The band on a wearer's wrist, in third person. */
	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			PlayerModel model = this.getParentModel();
			// an arm that isn't drawn (First-person Model hides your body's arms in first person, the hands stand in for
			// them and carry their own band) wears no band floating where it would be
			if (!Boolean.TRUE.equals(state.getData(KEY)) || state.isInvisible || !model.leftArm.visible || com.jaymospowers.client.BodyCover.body(state)) {
				return;
			}
			// the slam pose is laid over the model after it animates; the arm here is before that, so lay it over again
			XenoformSlap.applyModel(model, state);
			// your own body in first person: the band's dial pops up and glows through the slam, as the drawn hands' did
			XenoformSlap.Pose pose = state.getData(XenoformSlap.KEY);
			boolean dial = pose != null && XenoformSlap.ownBody(state);
			if (dial) {
				XenoformSlamView.dial(pose, state.ageInTicks);
			}
			poseStack.pushPose();
			model.leftArm.translateAndRotate(poseStack);
			XenoBand.submitOnArm(poseStack, collector, lightCoords, state.skin != null && state.skin.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM);
			poseStack.popPose();
			if (dial) {
				XenoformSlamView.dialDone();
			}
		}
	}
}
