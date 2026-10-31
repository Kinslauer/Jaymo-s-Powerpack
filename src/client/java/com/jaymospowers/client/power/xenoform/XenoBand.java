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
import org.joml.Vector3f;

/**
 * The Xeno-Band on a wearer's left wrist: a 5x3x5 box round the forearm with a 5x5 face plate on its outer side
 * ({@code textures/entity/xeno_band.png}, drawn by {@code tools/gen_xenoform.py}) whose face glows
 * ({@code xeno_band_glow.png}, an emissive pass). {@link Layer}
 * draws it on everyone whose power is Xenoform while they're themselves (the alien has the insignia instead), over
 * any costume, suit or chestplate sleeve;
 * {@link #submitFirstPerson} is the same band on your first-person left arm. While you hold Z the face plate lifts
 * and grows, the dialled alien's silhouette shows dark on its green face, and the alien stands over it as a
 * {@link XenoHologram}.
 */
public final class XenoBand {
	/** Whether this player wears the band (and is in their own shape), on the render state. */
	public static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "jaymospowers:xenoform_band");
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/xeno_band.png");
	static final Identifier GLOW = JaymosPowers.id("textures/entity/xeno_band_glow.png");

	private static ModelPart worn;
	private static ModelPart plate;
	/** A slim (3-pixel) arm: the band, made for the 4-pixel one, is narrowed to it, about the arm's middle. */
	static final float SLIM_WIDTH = 0.8F;
	/**
	 * The band stands proud of whatever covers the arm, so it always shows: grown this much round the arm (5 to 5.5
	 * pixels, over a costume's or the symbiote's sleeve), and this much more over a chestplate's sleeve (6 pixels).
	 */
	static final float GROW = 1.1F, ARMOUR_GROW = 1.25F;
	/**
	 * (0.18.88) The face plate (Andy: the band should show like a watch, its face big and lit): how far it lifts out of the
	 * band while raised (model pixels) and how much it grows then, about its middle; the silhouette's share of the green
	 * face's height, how far off the face it stands (blocks) and its colour.
	 */
	static final float PLATE_LIFT_PX = 1.0F, PLATE_GROW = 0.5F, SILHOUETTE_FILL = 0.9F, SILHOUETTE_OFF = 0.002F;
	static final int SILHOUETTE = 0xFF0C1410;
	/** (0.18.125, patch 8) The rival band: its body darkened red, its glow red. */
	static final int RIVAL_BODY = 0xFF8A3A3A, RIVAL_GLOW = 0xFF3030;
	/** Set by whoever draws the band next (the wearer's, from {@link XenoformClient#DARK}): draw it as the rival band. */
	static boolean rival;
	/** The plate's outer face, in the arm's pixels: its x, and its middle (y, z); the green face is 3 pixels square. */
	static final float FACE_X = 4.5F, FACE_Y = 7.5F, FACE_Z = 0.0F, FACE_PX = 3.0F;

	private XenoBand() {
	}

	/** The band in the left arm's own space (the arm's box is -1..3 x, -2..10 y, -2..2 z; the wrist is near y 6-9). */
	private static ModelPart bake(PartPose pose) {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("band", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, 6.0F, -2.5F, 5.0F, 3.0F, 5.0F), pose);
		return LayerDefinition.create(mesh, 32, 32).bakeRoot().getChild("band");
	}

	/** The face plate: a 1x5x5 slab on the band's outer face (x 3.5-4.5), round the dial's middle. */
	private static ModelPart plate() {
		if (plate == null) {
			MeshDefinition mesh = new MeshDefinition();
			mesh.getRoot().addOrReplaceChild("plate", CubeListBuilder.create().texOffs(0, 10).addBox(3.5F, 5.0F, -2.5F, 1.0F, 5.0F, 5.0F), PartPose.ZERO);
			plate = LayerDefinition.create(mesh, 32, 32).bakeRoot().getChild("plate");
		}
		return plate;
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
		submitOnArm(poseStack, collector, light, slim, false);
	}

	/** As above; {@code armoured}: the arm wears a chestplate's sleeve, so the band is grown out over it. */
	public static void submitOnArm(PoseStack poseStack, SubmitNodeCollector collector, int light, boolean slim, boolean armoured) {
		poseStack.pushPose();
		// grown round the arm's middle (x 1, z 0, at the band's height) to sit over any sleeve
		float grow = armoured ? GROW * ARMOUR_GROW : GROW;
		poseStack.translate(1.0F / 16.0F, 0.0F, 0.0F);
		poseStack.scale(grow, 1.0F, grow);
		poseStack.translate(-1.0F / 16.0F, 0.0F, 0.0F);
		if (slim) {
			// a slim arm spans -1..2 (model pixels), not -1..3: narrow the band about the slim arm's middle
			poseStack.translate(0.5F / 16.0F, 0.0F, 0.0F);
			poseStack.scale(SLIM_WIDTH, 1.0F, 1.0F);
			poseStack.translate(-1.0F / 16.0F, 0.0F, 0.0F);
		}
		ModelPart part = worn();
		collector.submitModelPart(part, poseStack, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY, null, rival ? RIVAL_BODY : -1);
		collector.submitModelPart(part, poseStack, RenderTypes.eyes(GLOW), light, OverlayTexture.NO_OVERLAY, null, glow(255));
		float pop = XenoformSlamView.pop;
		if (pop > 0.02F) {
			// the dialled alien, a hologram over the dial
			XenoHologram.submit(poseStack, collector, pop);
			// raised: the plate lifts out of the band and grows about its middle
			poseStack.translate(PLATE_LIFT_PX * pop / 16.0F, FACE_Y / 16.0F, FACE_Z / 16.0F);
			float g = 1.0F + PLATE_GROW * pop;
			poseStack.scale(1.0F, g, g);
			poseStack.translate(0.0F, -FACE_Y / 16.0F, -FACE_Z / 16.0F);
		}
		ModelPart plate = plate();
		collector.submitModelPart(plate, poseStack, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY, null, rival ? RIVAL_BODY : -1);
		int alpha = pop > 0.02F ? Mth.clamp((int)(XenoformSlamView.glow * 255.0F), 0, 255) : 255;
		collector.submitModelPart(plate, poseStack, RenderTypes.eyes(GLOW), light, OverlayTexture.NO_OVERLAY, null, glow(alpha));
		if (pop > 0.02F) {
			silhouette(poseStack, collector, light);
		}
		poseStack.popPose();
	}

	/**
	 * The dialled alien, flat and dark on the plate's green face: its own model, squashed onto the face and stood upright
	 * as you see it (its head toward the world's up, or away from you when the face looks straight up). The pose stack is
	 * in the arm's space, the plate placed.
	 */
	private static void silhouette(PoseStack poseStack, SubmitNodeCollector collector, int light) {
		net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		com.jaymospowers.power.xenoform.XenoForm dialled = com.jaymospowers.power.xenoform.XenoForms.get(
			com.jaymospowers.power.xenoform.XenoformState.band(player).dialled());
		XenoFormModels.FormModel model = dialled == null ? null : XenoFormModels.of(dialled.id);
		if (model == null) {
			return;
		}
		org.joml.Matrix4f pose = poseStack.last().pose();
		Vector3f at = pose.transformPosition(FACE_X / 16.0F, FACE_Y / 16.0F, FACE_Z / 16.0F, new Vector3f());
		Vector3f normal = pose.transformDirection(1.0F, 0.0F, 0.0F, new Vector3f());
		// how big the face is now (the plate may be grown), in blocks
		float face = pose.transformDirection(0.0F, FACE_PX / 16.0F, 0.0F, new Vector3f()).length();
		if (normal.lengthSquared() < 1.0E-8F || face < 1.0E-5F) {
			return;
		}
		normal.normalize();
		// up on the face: the world's up laid onto it; a face looking straight up takes "away from you" instead
		Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
		up.sub(new Vector3f(normal).mul(up.dot(normal)));
		if (up.lengthSquared() < 0.04F) {
			up.set(at).sub(new Vector3f(normal).mul(at.dot(normal)));
		}
		if (up.lengthSquared() < 1.0E-8F) {
			return;
		}
		up.normalize();
		// the model's x to the face's right, its y (down) to -up, its z (its back) into the face
		Vector3f right = new Vector3f(up).cross(normal);
		Vector3f down = new Vector3f(up).negate();
		Vector3f back = new Vector3f(normal).negate();
		poseStack.pushPose();
		poseStack.setIdentity();
		poseStack.translate(at.x + normal.x * SILHOUETTE_OFF, at.y + normal.y * SILHOUETTE_OFF, at.z + normal.z * SILHOUETTE_OFF);
		poseStack.mulPose(new org.joml.Matrix4f(right.x, right.y, right.z, 0.0F, down.x, down.y, down.z, 0.0F, back.x, back.y, back.z, 0.0F,
			0.0F, 0.0F, 0.0F, 1.0F));
		// a humanoid model stands 2 blocks (head top -8 to feet 24 pixels) over its render scale; fit it to the face
		float scale = face * SILHOUETTE_FILL * model.renderScale / 2.0F;
		poseStack.scale(scale, scale, 0.001F);
		poseStack.translate(0.0F, -0.5F, 0.0F);
		collector.submitModelPart(model.root(), poseStack, RenderTypes.entityCutout(model.texture), light, OverlayTexture.NO_OVERLAY, null, SILHOUETTE);
		poseStack.popPose();
	}

	/**
	 * (0.18.90) The insignia on an alien's chest, lit as it taps it to change back (Andy: "the insignia ... should move
	 * from the watch to their chest"): the band's face plate laid flat on the chest's front, only its glow drawn, over the
	 * insignia the skin already wears. The pose stack is the layer's, before the body's own placement.
	 */
	static void submitInsignia(PoseStack poseStack, SubmitNodeCollector collector, int light, XenoFormModels.FormModel model, float lit) {
		int alpha = Mth.clamp((int)(lit * 255.0F), 0, 255);
		if (alpha < 4) {
			return;
		}
		poseStack.pushPose();
		model.body.translateAndRotate(poseStack);
		// a hair proud of the chest, its face (the plate's +x) turned to the front (-z)
		poseStack.translate(0.0F, model.insigniaY / 16.0F, (model.insigniaZ - 0.05F) / 16.0F);
		poseStack.rotateDegrees(com.mojang.math.Axis.YP, 90.0F);
		poseStack.scale(1.0F, model.insigniaSize, model.insigniaSize);
		poseStack.translate(-FACE_X / 16.0F, -FACE_Y / 16.0F, -FACE_Z / 16.0F);
		collector.submitModelPart(plate(), poseStack, RenderTypes.eyes(GLOW), light, OverlayTexture.NO_OVERLAY, null, glow(alpha));
		poseStack.popPose();
	}

	/** The glow's colour at {@code alpha}: white (the green texture as it is), or red on the rival band. */
	private static int glow(int alpha) {
		return alpha << 24 | (rival ? RIVAL_GLOW : 0xFFFFFF);
	}

	/**
	 * In first person, after the hand renderer has drawn your left arm (AvatarRenderer.renderHand: the arm is reset to
	 * its rest pose with its slight outward lean): the band on its wrist, whenever that arm is in view, the slam too.
	 */
	public static void submitFirstPerson(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart arm, PlayerModel model) {
		net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
		if (arm != model.leftArm || player == null || !com.jaymospowers.power.xenoform.XenoformPower.has(player)
			|| com.jaymospowers.power.xenoform.XenoformState.transformed(player) || player.isInvisible()) {
			return;
		}
		poseStack.pushPose();
		arm.translateAndRotate(poseStack);
		rival = com.jaymospowers.power.xenoform.XenoformState.rival(player);
		submitOnArm(poseStack, collector, light, player.getSkin().model() == net.minecraft.world.entity.player.PlayerModelType.SLIM);
		rival = false;
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
			// them and carry their own band) wears no band floating where it would be. A costume or suit doesn't hide it:
			// the band is drawn over its sleeve
			if (!Boolean.TRUE.equals(state.getData(KEY)) || state.isInvisible || !model.leftArm.visible) {
				return;
			}
			// the slam pose is laid over the model after it animates; the arm here is before that, so lay it over again
			XenoformSlap.applyModel(model, state);
			// your own body in first person: through the slam its arms are hidden and the slam's own hands carry the band
			XenoformSlap.Pose pose = state.getData(XenoformSlap.KEY);
			boolean own = pose != null && XenoformSlap.ownBody(state);
			if (own && !XenoformSlap.FP_BODY_ARMS) {
				return;
			}
			boolean dial = own;
			if (dial) {
				XenoformSlamView.dial(pose, state.ageInTicks);
			}
			poseStack.pushPose();
			model.leftArm.translateAndRotate(poseStack);
			rival = Boolean.TRUE.equals(state.getData(XenoformClient.DARK));
			XenoBand.submitOnArm(poseStack, collector, lightCoords, state.skin != null && state.skin.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM,
				state.chestEquipment != null && !state.chestEquipment.isEmpty());
			rival = false;
			poseStack.popPose();
			if (dial) {
				XenoformSlamView.dialDone();
			}
		}
	}
}
