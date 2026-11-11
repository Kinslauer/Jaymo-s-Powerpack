package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.McaCompat;
import com.jaymoskingdoms.ZFighterFolk;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import org.jspecify.annotations.Nullable;

/**
 * Andy: the people who build and upgrade the Xeno-Band are the Galvan: "very small with bug eyes", the same body as
 * Powers' Gremlin, and "the npc galvan wear robes like the picture, and monocles, and are a high class society". A
 * villager whose part is one of {@link ZFighterFolk#GALVAN} (the star forge's smith) is still an MCA villager, so its
 * menu and its talk are unchanged, but it is drawn as a Galvan:
 * <ul>
 * <li>every man's body drawn for it (MCA's skin, face, clothes and hair, any armour or look of ours) is shrunk to
 * nothing ({@link #apply}), and</li>
 * <li>the Galvan is drawn in its place ({@link Layer}): a slim grey body on thin legs with broad feet, a wide flat head
 * with two great eyes that shine in the dark and blink, in a robe to its ankles, a mantle, wide cuffs and a monocle on
 * a chain, its hands folded in front of it. One of {@link #ROBES} robes, by its id.</li>
 * </ul>
 * The villager itself is made small on the server ({@code ZFighterFolk#GALVAN_SCALE}), so the model here is drawn at
 * its own size inside that. The skins are {@code tools/gen_galvan.py}; the boxes are the Gremlin's
 * ({@code XenoFormModels.Gremlin} in Powers), kept in step by hand.
 */
public final class GalvanBody {
	static final RenderStateDataKey<Integer> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:galvan_robe");
	/** How many robes there are ({@code galvan_<n>.png}). */
	static final int ROBES = 3;
	private static final Identifier[] SKINS = new Identifier[ROBES];
	private static final Identifier GLOW = JaymosKingdoms.id("textures/entity/galvan/galvan_glow.png");

	static {
		for (int i = 0; i < ROBES; i++) {
			SKINS[i] = JaymosKingdoms.id("textures/entity/galvan/galvan_" + i + ".png");
		}
	}

	private static @Nullable GalvanModel model;

	private GalvanBody() {
	}

	/** From the living renderer's extractRenderState ({@link VillagerLooks#extract}): a Galvan's robe, or none. True if it is one. */
	static boolean extract(LivingEntity entity, LivingEntityRenderState state) {
		String part = entity.getAttached(ZFighterFolk.PART);
		if (part == null || !ZFighterFolk.GALVAN.contains(part)) {
			state.setData(KEY, null);
			return false;
		}
		state.setData(KEY, (int)Math.floorMod(entity.getUUID().getLeastSignificantBits() >>> 5, (long)ROBES));
		return true;
	}

	static boolean galvan(Object state) {
		return state instanceof LivingEntityRenderState living && living.getData(KEY) != null;
	}

	/**
	 * Right after a body was posed for {@code state} ({@link ModelPoses#after}): a man's body drawn for a Galvan is
	 * shrunk to nothing, bone by bone (the scale is put back the next time the body is posed).
	 */
	public static void apply(Model<?> model, Object state) {
		if (!(model instanceof HumanoidModel<?> m) || !galvan(state)) {
			return;
		}
		for (ModelPart part : new ModelPart[] {m.head, m.hat, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg}) {
			hide(part);
		}
		if (McaCompat.LOADED) {
			McaLookModels.hideChest(m);
		}
	}

	static void hide(ModelPart part) {
		part.xScale = 0.0F;
		part.yScale = 0.0F;
		part.zScale = 0.0F;
	}

	static final class Layer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		Layer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			Integer robe = state.getData(KEY);
			if (robe == null || state.isInvisible) {
				return;
			}
			if (model == null) {
				model = new GalvanModel(GalvanModel.mesh().bakeRoot());
			}
			collector.submitModel(model, state, poseStack, RenderTypes.entityCutout(SKINS[Mth.clamp(robe, 0, ROBES - 1)]), lightCoords,
				OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
			// the eyes shine in the dark: their lit pixels again, fullbright
			collector.order(1).submitModel(model, state, poseStack, RenderTypes.eyes(GLOW), lightCoords, OverlayTexture.NO_OVERLAY, -1, null, 0);
		}
	}

	/** The Galvan: the Gremlin's boxes, and over them a robe's skirt, a mantle, cuffs and a monocle on its chain. */
	static final class GalvanModel extends EntityModel<HumanoidRenderState> {
		// ---- tuning ----
		/** The hips' height and half width (model pixels), the leg's length to the foot, and the body's height. */
		static final float HIP_Y = 17.0F, HIP_X = 1.5F, LEG = 6.0F, BODY = 7.0F;
		/** Where a shoulder sits on the body (out from the middle, down from its top). */
		static final float SHOULDER_X = 3.5F, SHOULDER_Y = 1.0F;
		/** The body's lean (radians) at rest and walking, and its bob a step (pixels). */
		static final float LEAN = 0.05F, WALK_LEAN = 0.12F, BOB = 0.3F;
		/** How fast its little legs go against the walk, and how far they swing under the robe (radians). */
		static final float GAIT = 0.6662F * 1.6F, STEP = 0.45F;
		/** The arms, hands folded in front: how far forward they are held and how far in (radians), and their sway a step. */
		static final float ARM_FORWARD = 0.55F, ARM_IN = 0.32F, ARM_SWAY = 0.3F;
		/** The head: its resting pitch (negative: held up), its wobble a step, and its slow idle tilt. */
		static final float HEAD_PITCH = -0.08F, WOBBLE = 0.05F, TILT = 0.05F;
		/** The eyes: ticks between blinks, how long one lasts, how flat a shut eye is. */
		static final float BLINK_EVERY = 70.0F, BLINK_TICKS = 3.0F, BLINK_FLAT = 0.15F;
		/** The monocle's chain: how far it swings a step (radians). */
		static final float CHAIN_SWING = 0.25F;

		private final ModelPart head, body, skirt, rightArm, leftArm, rightLeg, leftLeg, rightFoot, leftFoot, rightEye, leftEye, chain;

		GalvanModel(ModelPart root) {
			super(root);
			this.head = root.getChild("head");
			this.body = root.getChild("body");
			this.skirt = root.getChild("skirt");
			this.rightArm = root.getChild("right_arm");
			this.leftArm = root.getChild("left_arm");
			this.rightLeg = root.getChild("right_leg");
			this.leftLeg = root.getChild("left_leg");
			this.rightFoot = this.rightLeg.getChild("foot");
			this.leftFoot = this.leftLeg.getChild("foot");
			this.rightEye = this.head.getChild("right_eye");
			this.leftEye = this.head.getChild("left_eye");
			this.chain = this.head.getChild("chain");
		}

		static LayerDefinition mesh() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -7.0F, -3.5F, 8.0F, 7.0F, 6.0F),
				PartPose.offset(0.0F, HIP_Y - BODY, 0.0F));
			head.addOrReplaceChild("right_eye", CubeListBuilder.create().texOffs(32, 0).addBox(-2.0F, -2.0F, -1.5F, 4.0F, 4.0F, 3.0F),
				PartPose.offset(-4.5F, -5.0F, -3.0F));
			head.addOrReplaceChild("left_eye", CubeListBuilder.create().texOffs(32, 0).mirror().addBox(-2.0F, -2.0F, -1.5F, 4.0F, 4.0F, 3.0F),
				PartPose.offset(4.5F, -5.0F, -3.0F));
			// the monocle, a thin ring just in front of the right eye, and its chain down from the ring's outer edge
			head.addOrReplaceChild("monocle", CubeListBuilder.create().texOffs(48, 0)
				.addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F, new CubeDeformation(0.0F, 0.0F, -0.35F)), PartPose.offset(-4.5F, -5.0F, -4.8F));
			head.addOrReplaceChild("chain", CubeListBuilder.create().texOffs(60, 0)
				.addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(-0.3F, 0.0F, -0.3F)), PartPose.offset(-6.5F, -2.5F, -4.8F));
			PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-2.5F, 0.0F, -1.5F, 5.0F, 7.0F, 3.0F),
				PartPose.offset(0.0F, HIP_Y - BODY, 0.0F));
			body.addOrReplaceChild("mantle", CubeListBuilder.create().texOffs(0, 44).addBox(-3.5F, -0.2F, -2.5F, 7.0F, 2.0F, 5.0F), PartPose.ZERO);
			root.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(16, 32).addBox(-3.5F, 0.0F, -2.5F, 7.0F, 6.0F, 5.0F),
				PartPose.offset(0.0F, HIP_Y - 0.5F, 0.0F));
			for (int side = 0; side < 2; side++) {
				boolean left = side == 1;
				String s = left ? "left" : "right";
				PartDefinition arm = root.addOrReplaceChild(s + "_arm", limb(40, 16, left).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 7.0F, 2.0F),
					PartPose.offset(left ? SHOULDER_X : -SHOULDER_X, HIP_Y - BODY + SHOULDER_Y, 0.0F));
				arm.addOrReplaceChild("cuff", limb(48, 16, left).addBox(-1.5F, 3.0F, -1.5F, 3.0F, 2.0F, 3.0F), PartPose.ZERO);
				PartDefinition leg = root.addOrReplaceChild(s + "_leg", limb(0, 16, left).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F),
					PartPose.offset(left ? HIP_X : -HIP_X, HIP_Y, 0.0F));
				leg.addOrReplaceChild("foot", limb(0, 32, left).addBox(-1.5F, 0.0F, -2.5F, 3.0F, 1.0F, 4.0F), PartPose.offset(0.0F, LEG, 0.0F));
			}
			return LayerDefinition.create(mesh, 64, 64);
		}

		private static CubeListBuilder limb(int u, int v, boolean left) {
			CubeListBuilder b = CubeListBuilder.create().texOffs(u, v);
			return left ? b.mirror() : b;
		}

		@Override
		public void setupAnim(HumanoidRenderState state) {
			super.setupAnim(state);
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			float walk = state.walkAnimationPos * GAIT;
			float age = state.ageInTicks;
			// the body: a stately lean, a little more walking, the hips bobbing a touch with each step
			float lean = LEAN + WALK_LEAN * speed + Mth.sin(age * 0.09F) * 0.012F;
			float hip = HIP_Y - BOB * Mth.abs(Mth.sin(walk)) * speed;
			float topY = hip - BODY * Mth.cos(lean);
			float topZ = -BODY * Mth.sin(lean);
			this.body.y = topY;
			this.body.z = topZ;
			this.body.xRot = lean;
			this.skirt.y = hip - 0.5F;
			this.skirt.zRot = Mth.sin(walk) * 0.04F * speed;
			for (int side = 0; side < 2; side++) {
				boolean left = side == 1;
				float out = left ? 1.0F : -1.0F;
				ModelPart leg = left ? this.leftLeg : this.rightLeg;
				ModelPart foot = left ? this.leftFoot : this.rightFoot;
				ModelPart arm = left ? this.leftArm : this.rightArm;
				// small steps under the robe, the feet kept level
				float step = Mth.cos(walk + (left ? (float)Math.PI : 0.0F)) * STEP * speed;
				leg.y = hip;
				leg.xRot = step;
				foot.xRot = -step;
				// hands folded in front, swaying a little as it walks
				arm.y = topY + SHOULDER_Y * Mth.cos(lean);
				arm.z = topZ + SHOULDER_Y * Mth.sin(lean);
				arm.xRot = -ARM_FORWARD - lean + step * ARM_SWAY;
				arm.zRot = out * ARM_IN;
			}
			// the head looks where the villager looks, wobbling a little as it walks and tilting, slowly, when it stands
			this.head.y = topY;
			this.head.z = topZ;
			this.head.xRot = HEAD_PITCH + state.xRot * Mth.DEG_TO_RAD;
			this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
			this.head.zRot = Mth.sin(walk) * WOBBLE * speed + Mth.sin(age * 0.045F) * TILT * (1.0F - speed);
			this.chain.xRot = Mth.sin(walk * 2.0F) * CHAIN_SWING * speed + Mth.sin(age * 0.11F) * 0.04F;
			this.chain.zRot = -this.head.zRot;
			// the eyes blink, one a tick behind the other
			this.rightEye.yScale = age % BLINK_EVERY < BLINK_TICKS ? BLINK_FLAT : 1.0F;
			this.leftEye.yScale = (age + 1.0F) % BLINK_EVERY < BLINK_TICKS ? BLINK_FLAT : 1.0F;
		}
	}
}
