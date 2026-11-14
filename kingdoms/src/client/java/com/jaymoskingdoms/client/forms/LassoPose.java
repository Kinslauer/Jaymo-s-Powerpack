package com.jaymoskingdoms.client.forms;

import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.client.MobAnimations;
import com.jaymoskingdoms.client.ShackleTether;
import com.jaymoskingdoms.forms.Lasso;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * How the lasso looks on bodies (Lasso): the holder's arm whirling the loop overhead as it builds speed, both hands at work
 * tying someone, and the hogtied lying on their side, wrists and ankles roped behind them, squirming harder the nearer
 * they are to working free. Laid over the model right after it's posed, like the other poses here; the lying is turned on
 * the pose stack ({@code LassoLyingMixin}), and the cords on the wrists and shins are a layer on every living renderer.
 */
public final class LassoPose {
	/** The whirl: how fast the loop goes round at first and at full speed (radians a tick), and how wide (blocks). */
	static final float TURN_START = 0.3F, TURN_FULL = 0.95F, RADIUS_START = 0.35F, RADIUS_FULL = 0.8F;
	/** The whirling arm, straight up (radians), and how far it circles with the loop. */
	static final float ARM_UP = -2.85F, ARM_CIRCLE = 0.18F;
	/** Tied: the arms back and in behind the body, the legs drawn back to meet them, the head raised (radians). */
	static final float TIED_ARMS_BACK = 0.65F, TIED_ARMS_IN = 0.28F, TIED_LEGS_BACK = 0.95F, TIED_HEAD = -0.45F;
	/** Tied, squirming: the kick of the legs and the rock of the body (radians, degrees) at the least and the most struggle. */
	static final float KICK_LEAST = 0.08F, KICK_MOST = 0.35F, ROCK_LEAST = 1.5F, ROCK_MOST = 7.0F;
	/** Lying on its side: lifted this share of its width off the ground, so it doesn't sink in. */
	static final float LIE_LIFT = 0.45F;

	/** This frame's lasso pose: whirling (its angle and speed, which arm), tying, or tied (how far their struggle has got). */
	record Pose(int kind, float angle, float speed, boolean rightArm, float share, float age, int id) {
		static final int SPIN = 0, TYING = 1, TIED = 2;
	}

	static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:lasso");

	private LassoPose() {
	}

	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> helper.register(layer(renderer)));
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Layer<?> layer(LivingEntityRenderer<?, ?, ?> renderer) {
		return new Layer((RenderLayerParent)renderer);
	}

	// ---- the whirl ---------------------------------------------------------------------------------------------------------

	/** How far the whirl is up to speed, {@code t} ticks in (0-1). */
	public static float speed(float t) {
		return Mth.clamp(t / Lasso.SPIN_FULL, 0.0F, 1.0F);
	}

	/** How far round the loop has gone, {@code t} ticks into the whirl (radians): the turns come quicker until full speed. */
	public static float angle(float t) {
		float full = Lasso.SPIN_FULL;
		if (t < full) {
			return TURN_START * t + (TURN_FULL - TURN_START) * t * t / (2.0F * full);
		}
		return TURN_START * full + (TURN_FULL - TURN_START) * full * 0.5F + TURN_FULL * (t - full);
	}

	/** How wide the loop is, {@code t} ticks into the whirl (blocks). */
	public static float radius(float t) {
		return Mth.lerp(speed(t), RADIUS_START, RADIUS_FULL);
	}

	/** Whether {@code player} is whirling a lasso (using it with nothing on the rope), and how long (ticks, with the partial tick); -1 if not. */
	public static float whirling(Player player, float partialTick) {
		if (!player.isUsingItem() || !player.getUseItem().is(Lasso.ITEM) || player.getAttached(Lasso.HOLDING) != null) {
			return -1.0F;
		}
		return player.getTicksUsingItem() + partialTick;
	}

	/** Whether {@code player} holds the lasso's use with someone on the rope (tying them). */
	static boolean tying(Player player) {
		return player.isUsingItem() && player.getUseItem().is(Lasso.ITEM) && player.getAttached(Lasso.HOLDING) != null;
	}

	/** Whether the local player is whirling in first person: the hand is raised up out of the way. */
	public static float firstPersonWhirl(float partialTick) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player == null ? -1.0F : whirling(minecraft.player, partialTick);
	}

	/** Your own first-person lasso hand, whirling: lifted up and circling with the loop. */
	public static void firstPersonHand(PoseStack poseStack, float t) {
		float up = Math.min(1.0F, t / 6.0F), a = angle(t), r = 0.06F + 0.05F * speed(t);
		poseStack.translate(Mth.cos(a) * r * up, 0.42F * up + Mth.sin(a) * r * 0.6F * up, -0.12F * up);
		poseStack.rotate(Axis.XP.rotationDegrees(35.0F * up));
	}

	// ---- on the bodies -----------------------------------------------------------------------------------------------------

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		Pose pose = null;
		Integer tied = entity.getAttached(Lasso.TIED);
		if (tied != null && tied >= 0 && !PowersCompat.carried(entity)) {
			pose = new Pose(Pose.TIED, 0.0F, 0.0F, false, tied / 100.0F, entity.tickCount + partialTicks, entity.getId());
		} else if (entity instanceof Player player) {
			boolean right = player.getUsedItemHand() == InteractionHand.MAIN_HAND == (player.getMainArm() == HumanoidArm.RIGHT);
			float t = whirling(player, partialTicks);
			if (t >= 0.0F) {
				pose = new Pose(Pose.SPIN, angle(t), speed(t), right, 0.0F, t, player.getId());
			} else if (tying(player)) {
				pose = new Pose(Pose.TYING, 0.0F, 0.0F, right, 0.0F, player.getTicksUsingItem() + partialTicks, player.getId());
			}
		}
		state.setData(KEY, pose);
	}

	/** Whether this body lies hogtied (its limbs are this pose's). */
	public static boolean tied(Object state) {
		return state instanceof LivingEntityRenderState living && living.getData(KEY) != null && living.getData(KEY).kind() == Pose.TIED;
	}

	/** How hard they squirm, 0-1: harder the nearer they are to working free, in fits. */
	private static float squirm(Pose pose) {
		float fit = 0.5F + 0.5F * Mth.sin(pose.age() * 0.13F + (pose.id() & 15));
		return Mth.clamp(0.25F + 0.75F * pose.share(), 0.0F, 1.0F) * (0.4F + 0.6F * fit);
	}

	/** After the body's been turned to face its way: a tied body is laid on its side, rocking as it squirms. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Pose pose = state.getData(KEY);
		if (pose == null || pose.kind() != Pose.TIED || state.deathTime > 0.0F) {
			return;
		}
		float height = state.boundingBoxHeight / Math.max(entityScale, 0.01F);
		float width = state.boundingBoxWidth / Math.max(entityScale, 0.01F);
		float side = (pose.id() & 1) == 0 ? 1.0F : -1.0F;
		float rock = Mth.lerp(squirm(pose), ROCK_LEAST, ROCK_MOST) * Mth.sin(pose.age() * 0.9F);
		poseStack.translate(side * height * 0.5F, width * LIE_LIFT, 0.0F);
		poseStack.rotate(Axis.ZP.rotationDegrees(side * 90.0F + rock));
	}

	/** Right after a model was posed for this state (the body, and each armour piece). */
	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || living.deathTime > 0.0F) {
			return;
		}
		Pose pose = living.getData(KEY);
		if (pose == null) {
			return;
		}
		if (model instanceof HumanoidModel<?> m) {
			switch (pose.kind()) {
				case Pose.SPIN -> whirl(m, pose);
				case Pose.TYING -> tie(m, pose);
				default -> tied(m.head, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg, pose);
			}
		} else if (pose.kind() == Pose.TIED && model instanceof MobAnimations.IllagerParts parts) {
			// (an illager's arms are folded across it already, as good as bound)
			tied(parts.jaymoskingdoms$head(), null, null, parts.jaymoskingdoms$rightLeg(), parts.jaymoskingdoms$leftLeg(), pose);
		}
	}

	/** One arm straight up, circling with the loop; the head turned up to it a little. */
	private static void whirl(HumanoidModel<?> m, Pose pose) {
		ModelPart arm = pose.rightArm() ? m.rightArm : m.leftArm;
		float out = pose.rightArm() ? 1.0F : -1.0F;
		float up = Math.min(1.0F, pose.age() / 6.0F), circle = ARM_CIRCLE * (0.6F + 0.4F * pose.speed());
		arm.xRot = Mth.lerp(up, arm.xRot, ARM_UP + circle * Mth.sin(pose.angle()));
		arm.yRot = Mth.lerp(up, arm.yRot, 0.0F);
		arm.zRot = Mth.lerp(up, arm.zRot, out * (0.12F + circle * Mth.cos(pose.angle())));
	}

	/** Both hands down in front at the knots, working fast. */
	private static void tie(HumanoidModel<?> m, Pose pose) {
		float work = Mth.sin(pose.age() * 1.4F) * 0.25F;
		m.rightArm.xRot = -1.15F + work;
		m.leftArm.xRot = -1.15F - work;
		m.rightArm.yRot = -0.3F;
		m.leftArm.yRot = 0.3F;
		m.rightArm.zRot = 0.0F;
		m.leftArm.zRot = 0.0F;
		m.head.xRot = 0.6F;
	}

	/** Wrists behind, ankles drawn back to them, head up, legs kicking against the rope. */
	private static void tied(ModelPart head, @Nullable ModelPart rightArm, @Nullable ModelPart leftArm, ModelPart rightLeg, ModelPart leftLeg,
		Pose pose) {
		float squirm = squirm(pose);
		float kick = Mth.lerp(squirm, KICK_LEAST, KICK_MOST) * Mth.sin(pose.age() * 0.8F);
		if (rightArm != null && leftArm != null) {
			float pull = 0.08F * squirm * Mth.sin(pose.age() * 0.8F + 1.0F);
			rightArm.xRot = TIED_ARMS_BACK + pull;
			leftArm.xRot = TIED_ARMS_BACK + pull;
			rightArm.yRot = 0.0F;
			leftArm.yRot = 0.0F;
			rightArm.zRot = -TIED_ARMS_IN;
			leftArm.zRot = TIED_ARMS_IN;
		}
		rightLeg.xRot = TIED_LEGS_BACK + kick;
		leftLeg.xRot = TIED_LEGS_BACK + kick;
		rightLeg.yRot = 0.0F;
		leftLeg.yRot = 0.0F;
		rightLeg.zRot = 0.02F;
		leftLeg.zRot = -0.02F;
		head.xRot = TIED_HEAD;
		head.yRot = 0.3F * squirm * Mth.sin(pose.age() * 0.35F);
		head.zRot = 0.0F;
	}

	// ---- the cords ---------------------------------------------------------------------------------------------------------

	private static @Nullable ModelPart shins, rightWrist, leftWrist;

	private static ModelPart part(float x, float y, float width) {
		CubeListBuilder cubes = CubeListBuilder.create();
		// two turns, standing just off the limb, and the knot
		cubes.texOffs(0, 0).addBox(x, y, -2.4F, width, 0.8F, 4.8F);
		cubes.texOffs(0, 6).addBox(x, y + 1.4F, -2.4F, width, 0.8F, 4.8F);
		cubes.texOffs(0, 12).addBox(x + width * 0.5F - 0.6F, y + 0.4F, 2.1F, 1.2F, 1.4F, 0.8F);
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("cord", cubes, PartPose.ZERO);
		return LayerDefinition.create(mesh, 16, 16).bakeRoot().getChild("cord");
	}

	/** In the right leg's space (pixels, the left leg alongside at +x): both shins at once, at the ankle. */
	private static ModelPart shins() {
		if (shins == null) {
			shins = part(-2.4F, 9.0F, 8.6F);
		}
		return shins;
	}

	/** Round one wrist, in that arm's space (pixels). */
	private static ModelPart wrist(boolean right) {
		if (right) {
			if (rightWrist == null) {
				rightWrist = part(-3.4F, 7.6F, 4.8F);
			}
			return rightWrist;
		}
		if (leftWrist == null) {
			leftWrist = part(-1.4F, 7.6F, 4.8F);
		}
		return leftWrist;
	}

	/** On every living renderer; draws only on a hogtied body built like a person. */
	static final class Layer<S extends LivingEntityRenderState> extends RenderLayer<S, EntityModel<S>> {
		Layer(RenderLayerParent<S, EntityModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S living, float yRot, float xRot) {
			Pose pose = living.getData(KEY);
			if (pose == null || pose.kind() != Pose.TIED || living.isInvisible || living.deathTime > 0.0F) {
				return;
			}
			EntityModel<S> model = this.getParentModel();
			ModelPart rightLeg, rightArm = null, leftArm = null;
			if (model instanceof HumanoidModel<?> h) {
				rightLeg = h.rightLeg;
				rightArm = h.rightArm;
				leftArm = h.leftArm;
			} else if (model instanceof MobAnimations.IllagerParts parts) {
				rightLeg = parts.jaymoskingdoms$rightLeg();
			} else {
				return;
			}
			int overlay = LivingEntityRenderer.getOverlayCoords(living, 0.0F);
			this.cord(poseStack, collector, model, rightLeg, shins(), lightCoords, overlay);
			if (rightArm != null) {
				this.cord(poseStack, collector, model, rightArm, wrist(true), lightCoords, overlay);
				this.cord(poseStack, collector, model, leftArm, wrist(false), lightCoords, overlay);
			}
		}

		private void cord(PoseStack poseStack, SubmitNodeCollector collector, EntityModel<S> model, ModelPart limb, ModelPart cord, int light, int overlay) {
			if (!limb.visible || limb.skipDraw) {
				return;
			}
			poseStack.pushPose();
			model.root().translateAndRotate(poseStack);
			limb.translateAndRotate(poseStack);
			collector.submitModelPart(cord, poseStack, RenderTypes.entityCutout(ShackleTether.ROPE), light, overlay, null);
			poseStack.popPose();
		}
	}
}
