package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.bb.BbModel;
import com.jaymospowers.power.xenoform.forms.Asterion;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Asterion's body: Kingdoms' minotaur ("Minotaur" by eanncrod, Blockbench Workshop, CC BY 4.0; credit in
 * {@code art/xenoform_asterion-CREDITS.md}), the same cubes and its own eight animations, baked by
 * {@code tools/gen_xenoform_asterion.py} into {@code bbmodels/xenoform_asterion.json} and drawn in its alien skin.
 * <p>
 * The minotaur hangs on the root beside the man's parts, which stay (hidden) so the pose code, the band's tap and the
 * first-person hand find them. Each frame it plays the minotaur's idle and walk by the player's own walk, its hammer swings
 * on an attack (the two in turn), its run through a Charge and its slam through a Slam, all blended; the Roar and the Gore
 * are posed on top in code. The head follows the look. Its great hammer is hidden while it holds a big weapon, which is
 * drawn along the hammer's haft instead ({@link #submitHeld}). The first-person arm is a second copy of its right arm,
 * hanging straight, without the hammer, shrunk to a man's arm.
 */
public final class AsterionModel extends XenoFormModels.FormModel {
	// ---- tuning ----
	/** The minotaur stands about 65 pixels; drawn at this share it stands in a man's frame (the form's scale grows it from there). */
	static final float RENDER_SCALE = 0.48F;
	/** A man's walk cycle in walk units (2 pi / 0.6662), and the minotaur's walk and run cycles (seconds of animation). */
	static final float MAN_STRIDE = 9.43F, STRIDE_SECONDS = 2.0F;
	/** How much the walk's speed blends idle into walk. */
	static final float WALK_BLEND = 1.6F;
	/** A swing eases in and out over this share of it at each end. */
	static final float SWING_EDGE = 0.15F;
	/** Where the slam's hammer lands in its animation (seconds), timed to the server's {@link Asterion#SLAM_WINDUP}. */
	static final float SLAM_HIT_SECONDS = 1.1F;
	/** An act's pose eases in over this many ticks. */
	static final float ACT_BLEND = 3.0F;
	/** A charge drops the head this far (radians). */
	static final float CHARGE_HEAD = 0.55F;
	/** The roar: held, the head thrown back, the chest up, the arms spread and a shudder; let go, the head thrust out and the arms flung wide. */
	static final float ROAR_HEAD = -0.6F, ROAR_CHEST = -0.2F, ROAR_ARMS = 0.7F, ROAR_SHAKE = 0.04F, BELLOW_HEAD = 0.35F, BELLOW_ARMS = 1.0F, BELLOW_LEAN = 0.15F;
	/** The gore: the head's dip and toss, and the lunge of the body (radians). */
	static final float GORE_DIP = 0.45F, GORE_TOSS = 0.9F, GORE_LUNGE = 0.15F;
	/** How far the head turns with the look (radians), and how much of the look's pitch it takes. */
	static final float LOOK_YAW = 1.0F, LOOK_PITCH = 0.6F;
	/** The first-person arm: its size against the minotaur's, and where its shoulder sits on a man's arm (pixels). */
	static final float ARM_SCALE = 0.4F, ARM_X = 1.72F, ARM_Y = 0.4F, ARM_Z = 1.2F;
	/** A big weapon in its hand: how big it's drawn (the minotaur's own units; its hammer is about 77 pixels long). */
	static final float ITEM_SCALE = 2.6F;
	/** The insignia on its chest plate (the torso's small front cube): its middle, and its size against the band's 5-pixel face. */
	static final float PLATE_Y = 8.2F, PLATE_Z = -4.6F, PLATE_SIZE = 0.8F;

	static final String NAME = "xenoform_asterion";

	private final BbModel bb;
	private final ModelPart bbBody, torso, bbHead, bbRightArm, bbLeftArm, armour, anchor, hammer;

	AsterionModel() {
		this(BbModel.load(JaymosPowers.MOD_ID, NAME), BbModel.load(JaymosPowers.MOD_ID, NAME));
	}

	private AsterionModel(BbModel body, BbModel arm) {
		super(root(body, arm), "asterion");
		this.bb = body;
		this.renderScale = RENDER_SCALE;
		this.insigniaY = PLATE_Y;
		this.insigniaZ = PLATE_Z;
		this.insigniaSize = PLATE_SIZE;
		this.bbBody = body.part("body");
		this.torso = body.part("torso");
		this.bbHead = body.part("head");
		this.bbRightArm = body.part("rightArm");
		this.bbLeftArm = body.part("leftArm");
		this.armour = body.part("armorRightArm");
		this.anchor = body.part("itemAnchor");
		this.hammer = body.part("itemBone");
	}

	/** The man's parts (empty), the minotaur, and the first-person arm in the man's right arm. */
	private static ModelPart root(BbModel body, BbModel arm) {
		ModelPart limb = arm.part("rightArm");
		limb.setInitialPose(PartPose.ZERO);
		ModelPart upper = arm.part("armorRightArm");
		PartPose rest = upper.getInitialPose();
		upper.setInitialPose(PartPose.offset(rest.x(), rest.y(), rest.z()));
		arm.part("itemBone").visible = false;
		ModelPart firstPerson = new ModelPart(List.of(), Map.of("arm", limb));
		firstPerson.setInitialPose(PartPose.offset(ARM_X, ARM_Y, ARM_Z).withScale(ARM_SCALE));
		Map<String, ModelPart> parts = new LinkedHashMap<>();
		parts.put("head", new ModelPart(List.of(), Map.of("hat", empty())));
		parts.put("body", empty());
		parts.put("right_arm", new ModelPart(List.of(), Map.of("first_person", firstPerson)));
		parts.put("left_arm", empty());
		parts.put("right_leg", empty());
		parts.put("left_leg", empty());
		parts.put("minotaur", body.root);
		ModelPart root = new ModelPart(List.of(), parts);
		root.getAllParts().forEach(ModelPart::resetPose);
		return root;
	}

	private static ModelPart empty() {
		return new ModelPart(List.of(), Map.of());
	}

	@Override
	public void setupAnim(AvatarRenderState state) {
		// resets every part (the minotaur's too) and poses the hidden man
		super.setupAnim(state);
		this.head.visible = this.body.visible = this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
		this.hammer.visible = state.getData(XenoformClient.HELD) == null;
		String act = state.getData(XenoformClient.ACT);
		Float since = state.getData(XenoformClient.ACT_TICKS);
		float t = since != null ? since : 0.0F;
		float in = act != null ? Math.min(1.0F, t / ACT_BLEND) : 0.0F;
		boolean charge = "charge".equals(act), slam = "slam".equals(act);
		float actWeight = charge || slam ? in : 0.0F;
		// the stance under it: idle into walk by the walk's speed
		float speed = Mth.clamp(state.walkAnimationSpeed * WALK_BLEND, 0.0F, 1.0F);
		float walk = state.walkAnimationPos / MAN_STRIDE * STRIDE_SECONDS;
		float swing = Mth.clamp(state.swingAnimation, 0.0F, 1.0F);
		float swingWeight = swing <= 0.0F ? 0.0F : Math.min(1.0F, Math.min(swing, 1.0F - swing) / SWING_EDGE) * (1.0F - actWeight);
		float stance = (1.0F - actWeight) * (1.0F - swingWeight);
		this.bb.play("idle", state.ageInTicks / 20.0F, stance * (1.0F - speed));
		this.bb.play("walk", walk, stance * speed);
		if (swingWeight > 0.0F) {
			// the hammer swings, the two in turn
			Integer swings = state.getData(XenoformClient.SWING_COUNT);
			String anim = swings != null && (swings & 1) == 1 ? "swing_2" : "swing_1";
			this.bb.play(anim, swing * this.bb.length(anim), swingWeight);
		}
		if (charge) {
			this.bb.play("run", walk, actWeight);
			this.bbHead.xRot += CHARGE_HEAD * in;
		} else if (slam) {
			this.bb.play("slam", t / Asterion.SLAM_WINDUP * SLAM_HIT_SECONDS, actWeight);
		} else if ("roar_hold".equals(act)) {
			this.bbHead.xRot += ROAR_HEAD * in;
			this.bbBody.xRot += ROAR_CHEST * in;
			this.bbBody.zRot += Mth.sin(state.ageInTicks * 2.5F) * ROAR_SHAKE * in;
			this.bbRightArm.zRot += ROAR_ARMS * in;
			this.bbLeftArm.zRot -= ROAR_ARMS * in;
		} else if ("roar".equals(act)) {
			float k = 1.0F - Mth.clamp(t / Asterion.ROAR_POSE, 0.0F, 1.0F);
			this.bbHead.xRot += BELLOW_HEAD * k;
			this.bbBody.xRot += BELLOW_LEAN * k;
			this.bbRightArm.zRot += BELLOW_ARMS * k;
			this.bbLeftArm.zRot -= BELLOW_ARMS * k;
		} else if ("gore".equals(act)) {
			float p = Mth.clamp(t / Asterion.GORE_POSE, 0.0F, 1.0F);
			float a = Mth.sin(p * 2.0F * (float)Math.PI);
			this.bbHead.xRot += a > 0.0F ? a * GORE_DIP : a * GORE_TOSS;
			this.bbBody.xRot += Mth.sin(p * (float)Math.PI) * GORE_LUNGE;
		}
		// the head follows the look
		this.bbHead.yRot += Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -LOOK_YAW, LOOK_YAW);
		this.bbHead.xRot += state.xRot * Mth.DEG_TO_RAD * LOOK_PITCH;
		// the man's body where the torso is, so the band's tap lights the chest plate
		Vector3f at = new Matrix4f().translate(this.bbBody.x, this.bbBody.y, this.bbBody.z).rotateZYX(this.bbBody.zRot, this.bbBody.yRot, this.bbBody.xRot)
			.transformPosition(this.torso.x, this.torso.y, this.torso.z, new Vector3f());
		this.body.setPos(at.x, at.y, at.z);
		this.body.setRotation(this.bbBody.xRot + this.torso.xRot, this.bbBody.yRot + this.torso.yRot, this.bbBody.zRot + this.torso.zRot);
	}

	/**
	 * A big weapon (Asterion wields it, {@code Asterion#wields}) drawn in its hand in place of the hammer, along the hammer's
	 * haft so the minotaur's swings swing it. Called inside {@link XenoformLayer}'s pose, after the body.
	 */
	void submitHeld(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int light) {
		ItemStackRenderState held = state.getData(XenoformClient.HELD);
		if (held == null || held.isEmpty()) {
			return;
		}
		// posed now, as it will be drawn
		this.setupAnim(state);
		poseStack.pushPose();
		this.root().translateAndRotate(poseStack);
		for (ModelPart part : new ModelPart[] {this.bb.root, this.bbBody, this.bbRightArm, this.armour, this.anchor, this.hammer}) {
			part.translateAndRotate(poseStack);
		}
		// the haft runs along +x: turned so a held item, which points ahead of a hanging arm, points along it
		poseStack.rotateDegrees(Axis.YP, -90.0F);
		poseStack.rotateDegrees(Axis.XP, -90.0F);
		poseStack.rotateDegrees(Axis.YP, 180.0F);
		poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
		held.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
	}
}
