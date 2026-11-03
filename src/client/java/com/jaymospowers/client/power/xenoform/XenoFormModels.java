package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.JaymosPowers;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The aliens' bodies: each a humanoid model (so it takes the player's own pose, swing, crouch and swim) with the
 * player's proportions pushed about and extra parts hung on, and its own 64x64 skin
 * ({@code textures/entity/xenoform_<id>.png}, 64x128, drawn by {@code tools/gen_xenoform.py}). The base limbs use the
 * vanilla skin layout in the top 64 rows; the extras use the rows below it, so nothing shares texels with a limb. {@link #of} finds a form's model by its id.
 */
public final class XenoFormModels {
	/** One alien's model: the texture to draw it with, and the model itself. */
	public abstract static class FormModel extends HumanoidModel<AvatarRenderState> {
		public final Identifier texture;
		/** (0.18.93) The Ultimate's skin (armoured, plated) and its glowing seams, drawn fullbright over it. */
		public final Identifier ultimateTexture, ultimateGlow;
		/** The model's id (its form's). */
		public final String id;
		/** How much bigger than the player's body it's drawn, from the feet (a small form's model built small). */
		float renderScale = 1.0F;
		/**
		 * (0.18.90) The insignia on its chest, which it taps to change back (it wears no band): the middle of it (model
		 * pixels in the body's space, y down) on the front of the chest (z), and its size against the band's 5-pixel face.
		 */
		float insigniaY = 7.5F, insigniaZ = -2.0F, insigniaSize = 1.0F;

		FormModel(ModelPart root, String id) {
			super(root);
			this.texture = JaymosPowers.id("textures/entity/xenoform_" + id + ".png");
			this.ultimateTexture = JaymosPowers.id("textures/entity/xenoform_" + id + "_ultimate.png");
			this.ultimateGlow = JaymosPowers.id("textures/entity/xenoform_" + id + "_ultimate_glow.png");
			this.id = id;
		}
	}

	private static final Map<String, FormModel> MODELS = new LinkedHashMap<>();

	private XenoFormModels() {
	}

	public static @Nullable FormModel of(@Nullable String form) {
		if (form == null) {
			return null;
		}
		if (MODELS.isEmpty()) {
			MODELS.put("bulwark", new Bulwark());
			MODELS.put("pyre", new Pyre());
			MODELS.put("blur", new Velox());
			MODELS.put("prism", new Prism());
			MODELS.put("wisp", new Wisp());
			MODELS.put("gremlin", new Gremlin());
			MODELS.put("knell", new Knell());
			MODELS.put("wyrm", new Wyrm());
			MODELS.put("nadir", new Nadir());
			MODELS.put("duplex", new Duplex());
			MODELS.put("rivet", new Rivet());
			MODELS.put("gorger", new Gorger());
			MODELS.put("slipfin", new Slipfin());
			MODELS.put("polestar", new Polestar());
			MODELS.put("lurk", new Lurk());
			MODELS.put("gyre", new Gyre());
			MODELS.put("cortex", new Cortex());
			MODELS.put("locust", new Locust());
			MODELS.put("croaker", new Croaker());
			MODELS.put("dud", new Dud());
			MODELS.put("rime", new Rime());
			MODELS.put("marrow", new Marrow());
			MODELS.put("swathe", new Swathe());
			MODELS.put("blight", new Blight());
			MODELS.put("howl", new Howl());
			MODELS.put("prowl", new Prowl());
			MODELS.put("tusk", new Tusk());
			MODELS.put("talon", new Talon());
			MODELS.put("murk", new Murk());
			MODELS.put("gnasher", new Gnasher());
			MODELS.put("lumen", new Lumen());
			MODELS.put("keen", new Keen());
			MODELS.put("hive", new Hive());
			MODELS.put("skitter", new Skitter());
			MODELS.put("shade", new Shade());
			MODELS.put("mirage", new Mirage());
			MODELS.put("ooze", new Ooze("ooze"));
			MODELS.put("magma_ooze", new Ooze("magma_ooze"));
			MODELS.put("fuse", new Fuse());
			MODELS.put("thornwild", new Thornwild());
			MODELS.put("billow", new Billow());
			MODELS.put("towerback", new Towerback());
		}
		return MODELS.get(form);
	}

	/** How far a tail rises: up as it falls, whipping down on landing, low when crouched (radians, + is up). */
	static float tailLift(XenoMotion.Read m) {
		return Math.max(0.0F, m.fall()) * 0.5F * m.air() - m.land() * 0.7F - (m.crouching() ? 0.35F : 0.0F);
	}

	private static ModelPart bake(MeshDefinition mesh) {
		return LayerDefinition.create(mesh, 64, 128).bakeRoot();
	}

	/**
	 * (0.18.95) Rivet: small and smooth, living metal: slim limbs, a fin of a crest down its head, drips hanging off its
	 * forearms that sway with it. Melded into a shell it's drawn as that block instead ({@code XenoformLayer}).
	 */
	static final class Rivet extends FormModel {
		private final ModelPart rightDrip, leftDrip;

		Rivet() {
			super(bake(mesh()), "rivet");
			this.insigniaZ = -1.8F;
			this.rightDrip = this.rightArm.getChild("drip");
			this.leftDrip = this.leftArm.getChild("drip");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(-0.2F), 0.0F);
			PartDefinition root = mesh.getRoot();
			root.getChild("head").addOrReplaceChild("crest", CubeListBuilder.create().texOffs(0, 64).addBox(-0.5F, -10.0F, -3.0F, 1.0F, 2.0F, 6.0F), PartPose.ZERO);
			root.getChild("right_arm").addOrReplaceChild("drip", CubeListBuilder.create().texOffs(16, 64).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F),
				PartPose.offset(-1.0F, 9.5F, 0.0F));
			root.getChild("left_arm").addOrReplaceChild("drip", CubeListBuilder.create().texOffs(16, 64).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F),
				PartPose.offset(1.0F, 9.5F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			// the drips hang straight down whatever the arm does, swaying a little
			float sway = Mth.sin(state.ageInTicks * 0.2F) * 0.08F;
			this.rightDrip.xRot = -this.rightArm.xRot + sway;
			this.leftDrip.xRot = -this.leftArm.xRot - sway;
		}
	}

	/**
	 * (0.18.96; 0.19.60 a real armadillo) Gorger: a squat armadillo on four short legs: a pointed snout with a bone horn,
	 * little ears, a domed shell of six bands over its back (the bands bob one after another as it scurries, diagonal legs
	 * together in quick little steps) and a tail dragging behind. Its head nods a beat behind the step and butts down
	 * on an attack; charging, it drops its head with the horn levelled and scrabbles faster; rolled up (B) it tucks head,
	 * legs and tail inside and the six bands close into a ball that rolls with the way it moves. The humanoid parts stay,
	 * hidden or reused: the body (hidden) carries the insignia on the back of the rump, the legs are its legs.
	 */
	static final class Gorger extends FormModel {
		// ---- tuning ----
		/** How fast its legs scurry against the walk, how far they swing and how high a foot lifts (pixels). */
		static final float GAIT = 0.6662F * 2.4F, STEP = 0.6F, LIFT = 1.0F;
		/** (charge) the legs scrabble this much faster, the head drops and the tail rises. */
		static final float CHARGE_GAIT = 1.4F, CHARGE_HEAD_DROP = 1.2F, CHARGE_HEAD_PITCH = 0.55F, CHARGE_TAIL = 0.35F;
		/** The body's dip at the widest stride (pixels), its side sway, a band's bob (pixels) and the bob's wave along the bands. */
		static final float BOB = 0.7F, SWAY = 0.4F, BAND_BOB = 0.5F, BAND_WAVE = 0.9F;
		/** How far it drops when sneaking (pixels). */
		static final float CROUCH_DROP = 1.5F;
		/** The attack: the head butts down (radians) with a little push forward (pixels; it stays on its neck). */
		static final float LUNGE = 0.4F, LUNGE_PITCH = 0.7F;
		/** Airborne, the legs reach out this far (radians); landing squashes it down this far (pixels). */
		static final float AIR_REACH = 0.5F, LAND_SQUASH = 1.0F;
		/** How far the head can look up and down (radians); its snout stops at the ground, however far it looks down. */
		static final float HEAD_UP = 0.9F, HEAD_DOWN = 1.2F;
		/** The tail at rest (radians, + is up). */
		static final float TAIL_REST = 0.15F;
		/** Rolled up: how far the ball turns for each unit of walk, and its size (the flat sides' distance from the middle). */
		static final float ROLL_PER_POS = 0.53F, BALL_APOTHEM = 7.0F, BALL_WIDTH = 1.2F;
		/** Each band of the ball a hair narrower than the last, so their flat sides don't flicker through each other. */
		static final float BALL_STEP = 0.02F;
		/** The ball is a hexagon of the six bands: 60 degrees a band, a corner this far (apothem / cos 30) from the middle. */
		private static final float SEGMENT = (float)Math.PI / 3.0F;
		private static final float BALL_CORNER = BALL_APOTHEM / 0.8660254F;
		private static final float BALL_SIDE = BALL_APOTHEM * 1.1547005F;
		/** The shell, front to back: where each band sits (z, and y of its bottom), how tall and thick it is. */
		private static final float[] SHELL_Z = {-4.5F, -2.0F, 0.0F, 2.0F, 4.0F, 6.5F};
		private static final float[] SHELL_Y = {20.0F, 18.0F, 18.0F, 18.0F, 18.0F, 20.0F};
		private static final float[] SHELL_H = {8.0F, 7.0F, 8.0F, 8.0F, 7.0F, 8.0F};
		private static final float[] SHELL_D = {3.0F, 2.0F, 2.0F, 2.0F, 2.0F, 3.0F};
		/** Front and hind legs rest here (x either side, z, y of the hip), are this long and their feet this deep (half). */
		private static final float LEG_X = 2.5F, FRONT_Z = -3.5F, HIND_Z = 4.5F, HIP_Y = 19.0F, LEG_LEN = 5.0F, FOOT_HALF = 2.0F;
		/** The head's pivot, and how far below it the snout's tip reaches at most (its lowest corner: reach * sin(pitch + angle)). */
		private static final float HEAD_Y = 17.0F, HEAD_Z = -5.5F, SNOUT_REACH = 6.32F, SNOUT_ANGLE = 0.32F;
		/** The tail's root (in the rump shield), how far below the root its tip reaches as it hangs down, and its half-thickness. */
		private static final float TAIL_Y = 20.0F, TAIL_Z = 7.0F, TAIL_REACH = 8.5F, TAIL_HALF = 1.5F;
		/** The hidden body stands here (y), at the back of the rump shield (z), facing back. */
		private static final float PLATE_Y = 15.5F, PLATE_Z = 6.0F;

		private final ModelPart torso, snout, tail, tail2, rightEar, leftEar, hindRight, hindLeft;
		private final ModelPart[] shell = new ModelPart[6];

		Gorger() {
			super(bake(mesh()), "gorger");
			ModelPart root = this.root();
			this.torso = root.getChild("torso");
			this.snout = this.head.getChild("snout");
			this.rightEar = this.head.getChild("right_ear");
			this.leftEar = this.head.getChild("left_ear");
			this.tail = root.getChild("tail");
			this.tail2 = this.tail.getChild("tail2");
			this.hindRight = root.getChild("hind_right_leg");
			this.hindLeft = root.getChild("hind_left_leg");
			for (int i = 0; i < 6; i++) {
				this.shell[i] = root.getChild("shell" + i);
			}
			// on the back of the rump (the body, hidden, stands there facing back), the plate flat on its face, above the tail
			this.insigniaY = 0.0F;
			this.insigniaZ = -2.0F;
			this.insigniaSize = 0.9F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the head: a small wedge, a pointed snout with the horn on its tip, ears at the back
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -2.5F, -3.0F, 5.0F, 5.0F, 3.0F),
				PartPose.offset(0.0F, HEAD_Y, HEAD_Z));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			PartDefinition snout = head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(16, 0).addBox(-1.5F, -1.5F, -3.0F, 3.0F, 3.0F, 3.0F),
				PartPose.offset(0.0F, 0.5F, -3.0F));
			snout.addOrReplaceChild("horn", CubeListBuilder.create().texOffs(18, 8).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F),
				PartPose.offsetAndRotation(0.0F, -1.2F, -2.0F, 0.9F, 0.0F, 0.0F));
			head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(24, 8).addBox(-1.0F, -2.0F, -0.5F, 2.0F, 2.0F, 1.0F),
				PartPose.offsetAndRotation(-2.0F, -2.3F, -0.5F, 0.0F, 0.0F, -0.35F));
			head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(24, 12).addBox(-1.0F, -2.0F, -0.5F, 2.0F, 2.0F, 1.0F),
				PartPose.offsetAndRotation(2.0F, -2.3F, -0.5F, 0.0F, 0.0F, 0.35F));
			// the soft belly and flanks under the shell
			root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 64).addBox(-4.0F, -2.5F, -6.0F, 8.0F, 5.0F, 12.0F),
				PartPose.offset(0.0F, 17.5F, 1.0F));
			// the shell: shoulder shield, four bands, rump shield (centred in z so it can close into a ball)
			int[][] uv = {{0, 82}, {0, 94}, {0, 104}, {26, 104}, {26, 94}, {28, 82}};
			for (int i = 0; i < 6; i++) {
				root.addOrReplaceChild("shell" + i, CubeListBuilder.create().texOffs(uv[i][0], uv[i][1])
					.addBox(-5.0F, -SHELL_H[i], -SHELL_D[i] / 2.0F, 10.0F, SHELL_H[i], SHELL_D[i]), PartPose.offset(0.0F, SHELL_Y[i], SHELL_Z[i]));
			}
			// the legs: the man's legs are its front ones, the hind pair new
			root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F),
				PartPose.offset(-LEG_X, HIP_Y, FRONT_Z));
			root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F),
				PartPose.offset(LEG_X, HIP_Y, FRONT_Z));
			root.addOrReplaceChild("hind_right_leg", CubeListBuilder.create().texOffs(32, 116).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F),
				PartPose.offset(-LEG_X, HIP_Y, HIND_Z));
			root.addOrReplaceChild("hind_left_leg", CubeListBuilder.create().texOffs(48, 116).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F),
				PartPose.offset(LEG_X, HIP_Y, HIND_Z));
			// the tail: two tapering joints dragging behind, low, its root set into the rump shield
			PartDefinition tail = root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 116).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 5.0F),
				PartPose.offset(0.0F, TAIL_Y, TAIL_Z));
			tail.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(18, 116).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 4.0F),
				PartPose.offset(0.0F, 0.0F, 4.5F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.hat.visible = this.rightArm.visible = this.leftArm.visible = this.body.visible = false;
			for (ModelPart part : new ModelPart[] {this.head, this.body, this.rightLeg, this.leftLeg, this.hindRight, this.hindLeft}) {
				part.resetPose();
			}
			String act = state.getData(XenoformClient.ACT);
			if ("roll".equals(act)) {
				this.ball(state);
			} else {
				this.walk(state, "charge".equals(act));
			}
		}

		private void show(boolean out) {
			this.head.visible = this.torso.visible = this.tail.visible = out;
			this.rightLeg.visible = this.leftLeg.visible = this.hindRight.visible = this.hindLeft.visible = out;
		}

		/** Walking, standing, charging: the quadruped. */
		private void walk(AvatarRenderState state, boolean charge) {
			this.show(true);
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			float p = state.walkAnimationPos * GAIT * (charge ? CHARGE_GAIT : 1.0F);
			float crouch = state.isCrouching ? CROUCH_DROP : 0.0F;
			float air = Mth.clamp(m.air(), 0.0F, 1.0F);
			// the body dips at the widest stride, and squashes on landing
			float drop = crouch + Mth.abs(Mth.cos(p)) * BOB * speed * (1.0F - air) + Math.max(0.0F, m.land()) * LAND_SQUASH;
			float sway = Mth.sin(p) * SWAY * speed;
			// the shell: the bands bob in a wave along its back; it breathes a little
			float breath = Mth.sin(age * 0.08F) * 0.12F;
			for (int i = 0; i < 6; i++) {
				ModelPart band = this.shell[i];
				float lift = (i == 0 || i == 5) ? 0.0F : Math.max(0.0F, Mth.sin(p * 2.0F - i * BAND_WAVE)) * BAND_BOB * speed + breath * 0.5F + 0.12F;
				band.x = sway;
				band.y = SHELL_Y[i] + drop - lift;
				band.z = SHELL_Z[i];
				band.xRot = band.yRot = band.zRot = 0.0F;
				band.xScale = band.yScale = band.zScale = 1.0F;
			}
			this.torso.x = sway;
			this.torso.y = 17.5F + drop;
			this.torso.z = 1.0F;
			this.torso.xRot = this.torso.yRot = this.torso.zRot = 0.0F;
			this.torso.xScale = this.torso.yScale = this.torso.zScale = 1.0F;
			// the insignia: the hidden body stands at the rump facing back, its plate on the shield's back, above the tail
			this.body.x = sway;
			this.body.y = PLATE_Y + drop;
			this.body.z = PLATE_Z;
			this.body.yRot = (float)Math.PI;
			// the head: where it looks, nodding a beat behind the step, a sniff when idle, a snap on the attack; however far
			// it looks down, its snout stops at the ground
			float swing = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
			float sniff = Mth.sin(age * 0.35F) * 0.05F * (1.0F - speed);
			this.head.x = sway;
			this.head.y = HEAD_Y + drop + (charge ? CHARGE_HEAD_DROP : 0.0F);
			this.head.z = HEAD_Z - swing * LUNGE;
			this.head.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -1.3F, 1.3F);
			float room = (24.0F - this.head.y) / SNOUT_REACH;
			float down = room >= 1.0F ? HEAD_DOWN : Math.min(HEAD_DOWN, (float)Math.asin(Math.max(0.0F, room)) - SNOUT_ANGLE);
			float pitch = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -HEAD_UP, HEAD_UP) + Mth.sin(p * 2.0F - 0.8F) * 0.06F * speed + sniff
				+ swing * LUNGE_PITCH + (charge ? CHARGE_HEAD_PITCH : 0.0F);
			this.head.xRot = Math.max(-HEAD_UP, Math.min(down, pitch));
			this.snout.xRot = sniff * 0.8F;
			// the ears prick up and flick now and then
			float flick = Math.max(0.0F, Mth.sin(age * 0.11F + 1.0F) - 0.85F) * 2.0F;
			this.rightEar.zRot = -0.35F - flick - m.trail() * 0.1F;
			this.leftEar.zRot = 0.35F + flick + m.trail() * 0.1F;
			// the tail: drags low, sways with the walk and the turn, lifts when it falls or charges; it never dips through
			// the ground (landing or sneaking would whip it down)
			this.tail.x = sway;
			this.tail.y = TAIL_Y + drop;
			float floor = -(float)Math.asin(Mth.clamp((24.0F - this.tail.y - TAIL_HALF) / TAIL_REACH, 0.0F, 1.0F));
			this.tail.xRot = Math.max(floor, TAIL_REST + tailLift(m) + (charge ? CHARGE_TAIL : 0.0F));
			this.tail.yRot = Mth.sin(age * 0.07F) * 0.12F - Mth.sin(p) * 0.25F * speed - m.turn() * 0.5F;
			this.tail2.xRot = 0.1F;
			this.tail2.yRot = Mth.sin(age * 0.07F - 0.8F) * 0.15F - m.turn() * 0.3F;
			// the legs: diagonal pairs together; each pivots at the hip, which sinks or rises as the leg swings so the
			// lowest corner of its foot meets the ground, and rises as a foot lifts through its forward swing; in the air
			// the hips stay put (in the body) and the legs reach out
			float leg = (LEG_LEN - crouch) / LEG_LEN;
			float len = LEG_LEN * leg;
			ModelPart[] legs = {this.rightLeg, this.leftLeg, this.hindRight, this.hindLeft};
			float[] phase = {0.0F, (float)Math.PI, (float)Math.PI, 0.0F};
			for (int i = 0; i < 4; i++) {
				ModelPart foot = legs[i];
				float theta = Mth.cos(p + phase[i]) * STEP * speed + (i < 2 ? -AIR_REACH : AIR_REACH) * air;
				float up = Math.max(0.0F, Mth.sin(p + phase[i])) * LIFT * speed * (1.0F - air);
				foot.xRot = theta;
				foot.yScale = leg;
				foot.y = HIP_Y + crouch + (len * (1.0F - Mth.cos(theta)) - FOOT_HALF * Mth.abs(Mth.sin(theta))) * (1.0F - air) - up;
			}
		}

		/** Rolled up: head, legs and tail tucked away, the six bands closed into a hexagonal ball that turns as it moves. */
		private void ball(AvatarRenderState state) {
			this.show(false);
			float roll = state.walkAnimationPos * ROLL_PER_POS;
			float rem = roll - (float)Math.floor(roll / SEGMENT) * SEGMENT;
			// resting on a flat side, a corner lifts it as it turns over
			float cy = 24.0F - BALL_CORNER * Mth.cos(rem - SEGMENT * 0.5F);
			for (int i = 0; i < 6; i++) {
				ModelPart band = this.shell[i];
				band.x = 0.0F;
				band.y = cy;
				band.z = 1.0F;
				band.xRot = roll + i * SEGMENT;
				band.yRot = band.zRot = 0.0F;
				band.xScale = BALL_WIDTH - i * BALL_STEP;
				band.yScale = BALL_APOTHEM / SHELL_H[i];
				band.zScale = BALL_SIDE / SHELL_D[i];
			}
			// the plate sits inside the ball, out of sight
			this.body.x = 0.0F;
			this.body.y = cy;
			this.body.z = 1.0F;
			this.body.yRot = (float)Math.PI;
		}
	}

	/**
	 * (0.18.96) Slipfin: slim and smooth, three feathery gills each side of its head that wave as it breathes and stream
	 * back as it swims, a fin down its back and a flat tail that sways.
	 */
	static final class Slipfin extends FormModel {
		private final ModelPart[] gills = new ModelPart[6];
		private final ModelPart tail;

		Slipfin() {
			super(bake(mesh()), "slipfin");
			this.insigniaZ = -1.9F;
			for (int i = 0; i < 6; i++) {
				this.gills[i] = this.head.getChild("gill_" + i);
			}
			this.tail = this.body.getChild("tail");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(-0.1F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			for (int i = 0; i < 6; i++) {
				boolean left = i >= 3;
				int row = i % 3;
				head.addOrReplaceChild("gill_" + i, (left ? CubeListBuilder.create().texOffs(0, 64).mirror() : CubeListBuilder.create().texOffs(0, 64))
					.addBox(left ? 0.0F : -4.0F, -0.5F, 0.0F, 4.0F, 1.0F, 1.0F),
					PartPose.offsetAndRotation(left ? 4.0F : -4.0F, -7.0F + row * 2.0F, 0.5F, 0.0F, 0.0F, (left ? -1.0F : 1.0F) * (0.5F - row * 0.4F)));
			}
			PartDefinition body = root.getChild("body");
			body.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(12, 64).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 10.0F, 3.0F), PartPose.offset(0.0F, 1.0F, 2.0F));
			body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(24, 64).addBox(-1.5F, -0.5F, 0.0F, 3.0F, 1.0F, 7.0F),
				PartPose.offsetAndRotation(0.0F, 11.0F, 1.5F, 0.4F, 0.0F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			XenoMotion.Read m = XenoMotion.read(state);
			float wave = Mth.sin(state.ageInTicks * 0.25F) * 0.15F;
			for (int i = 0; i < 6; i++) {
				// the gills flutter, and stream back as it swims or falls
				this.gills[i].yRot = (i >= 3 ? -1.0F : 1.0F) * (wave * (1 + i % 3) * 0.6F + state.swimAmount * 0.9F + m.speed() * 0.3F);
			}
			this.tail.yRot = Mth.sin(state.ageInTicks * 0.2F + state.walkAnimationPos * 0.5F) * (0.3F + 0.5F * state.swimAmount) - m.trail() * 0.6F;
			this.tail.xRot = 0.4F * (1.0F - state.swimAmount) + tailLift(m);
		}
	}

	/** (0.18.96) Polestar: broad, banded like a magnet, a horseshoe of two prongs rising off its head, plates on its shoulders. */
	static final class Polestar extends FormModel {
		Polestar() {
			super(bake(mesh()), "polestar");
			this.insigniaZ = -2.7F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.7F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			head.addOrReplaceChild("right_prong", CubeListBuilder.create().texOffs(0, 64).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F),
				PartPose.offsetAndRotation(-3.5F, -8.5F, 0.0F, 0.0F, 0.0F, -0.12F));
			head.addOrReplaceChild("left_prong", CubeListBuilder.create().texOffs(0, 64).mirror().addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F),
				PartPose.offsetAndRotation(3.5F, -8.5F, 0.0F, 0.0F, 0.0F, 0.12F));
			root.getChild("body").addOrReplaceChild("plates", CubeListBuilder.create().texOffs(0, 74).addBox(-6.0F, -1.0F, -3.0F, 12.0F, 3.0F, 6.0F, new CubeDeformation(0.7F)),
				PartPose.ZERO);
			return mesh;
		}
	}

	/**
	 * (0.18.96) Lurk, the stalker: tall and thin, arms that hang past its knees ending in long fingers, a face that's
	 * mostly eyes. It holds its head a little forward and tilted, as if listening.
	 */
	static final class Lurk extends FormModel {
		private final ModelPart rightHand, leftHand;

		Lurk() {
			super(bake(mesh()), "lurk");
			this.insigniaZ = -1.7F;
			this.rightHand = this.rightArm.getChild("hand");
			this.leftHand = this.leftArm.getChild("hand");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(-0.3F), 0.0F);
			PartDefinition root = mesh.getRoot();
			root.getChild("right_arm").addOrReplaceChild("hand", CubeListBuilder.create().texOffs(0, 64).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F),
				PartPose.offset(-1.0F, 9.5F, 0.0F));
			root.getChild("left_arm").addOrReplaceChild("hand", CubeListBuilder.create().texOffs(0, 64).mirror().addBox(-1.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F),
				PartPose.offset(1.0F, 9.5F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			// the head tips forward and to one side, slowly, as if it's listening
			this.head.zRot += 0.18F + Mth.sin(state.ageInTicks * 0.03F) * 0.06F;
			this.head.z -= 1.0F;
			// the long fingers trail a little behind the arm's swing
			this.rightHand.xRot = -this.rightArm.xRot * 0.2F;
			this.leftHand.xRot = -this.leftArm.xRot * 0.2F;
		}
	}

	/**
	 * (0.18.103, patch 5; 0.19.60 a turtle on four flippers) Gyre: a broad domed shell over a pale plastron, four
	 * flippers that paddle in diagonal pairs and rock the shell as it waddles, a neck that pokes out and turns with the
	 * head and a short tail. Swimming it flaps its front flippers like wings. Rolled up (holding B) the head peeks from
	 * the shell and the limbs are drawn in; in Shell Spin the shell spins fast about its middle. In the turtle the man's
	 * body is hidden and laid face-up on the shell, so the insignia sits on its crown.
	 */
	static final class Gyre extends FormModel {
		// ---- tuning ----
		/**
		 * The shell's pivot height (model y), the ground, the foot's length below the flipper's pivot, and how high a
		 * flipper's foot comes up off the ground as it swings forward (pixels).
		 */
		static final float SHELL_Y = 17.0F, GROUND = 24.0F, FOOT = 5.0F, STEP_UP = 1.0F;
		/** How far the shell drops when crouched, rolled up and spinning (pixels); a landing squats it 1.5 more. */
		static final float CROUCH_DROP = 1.5F, ROLL_DROP = 3.0F, SPIN_DROP = 2.5F, LAND_SQUAT = 1.5F;
		/** The paddle: its rate against the walk, how far the front and hind flippers swing, how far they splay and lift. */
		static final float GAIT = 0.6662F * 0.85F, STRIDE_FRONT = 0.75F, STRIDE_HIND = 0.5F, SPLAY = 0.14F, LIFT = 0.45F;
		/** The waddle: the shell's roll and yaw against the walk. */
		static final float ROCK = 0.07F, SWAY = 0.05F;
		/** Swimming: the front flippers' stroke rate (radians a tick), and how far out the stroke sweeps. */
		static final float STROKE_RATE = 0.25F, STROKE_OUT = 0.8F, STROKE_SWEEP = 0.6F;
		/** The neck: its base (model y, z), its length, how far the head overlaps its tip, and how long it is at rest. */
		static final float NECK_Y = 16.5F, NECK_Z = -6.0F, NECK_LEN = 4.0F, NECK_OVERLAP = 1.5F, REST_EXT = 0.9F;
		/** The neck's reach: when sneaking it draws in, when biting it darts out, and in the water it stretches. */
		static final float SNEAK_PULL = 0.3F, BITE_REACH = 0.6F, SWIM_REACH = 0.35F;
		/** Shell Spin: how fast the shell turns (radians a tick). */
		static final float SPIN_RATE = 0.9F;

		private final ModelPart shell, shellTop, plastron, neck, tail, hindRight, hindLeft;

		Gyre() {
			super(bake(mesh()), "gyre");
			// the man's body lies face-up on the shell's crown (an 8x10x2 box): the plate sits on its top face
			this.insigniaY = 0.0F;
			this.insigniaZ = -1.0F;
			this.insigniaSize = 1.2F;
			ModelPart root = this.root();
			this.shell = root.getChild("shell");
			this.shellTop = root.getChild("shell_top");
			this.plastron = root.getChild("plastron");
			this.neck = root.getChild("neck");
			this.tail = root.getChild("tail");
			this.hindRight = root.getChild("hind_right_leg");
			this.hindLeft = root.getChild("hind_left_leg");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the head: a small turtle's, a beak under its snout; it hangs from the neck's tip (placed in setupAnim)
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -2.5F, -6.0F, 5.0F, 5.0F, 6.0F),
				PartPose.offset(0.0F, NECK_Y, NECK_Z - (NECK_LEN * REST_EXT - NECK_OVERLAP)));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			head.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(40, 112).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 2.0F, 2.0F),
				PartPose.offset(0.0F, 0.3F, -6.0F));
			// the shell: a broad carapace, a smaller crown on it and the plastron under it, all hung from one pivot
			// so they roll and spin as one
			root.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(0, 64).addBox(-6.0F, -2.0F, -7.0F, 12.0F, 4.0F, 14.0F),
				PartPose.offset(0.0F, SHELL_Y, 0.0F));
			root.addOrReplaceChild("shell_top", CubeListBuilder.create().texOffs(0, 82).addBox(-4.0F, -4.0F, -5.0F, 8.0F, 2.0F, 10.0F),
				PartPose.offset(0.0F, SHELL_Y, 0.0F));
			root.addOrReplaceChild("plastron", CubeListBuilder.create().texOffs(0, 94).addBox(-5.0F, 2.0F, -6.0F, 10.0F, 2.0F, 12.0F),
				PartPose.offset(0.0F, SHELL_Y, 0.0F));
			root.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 108).addBox(-1.5F, -1.5F, -NECK_LEN, 3.0F, 3.0F, NECK_LEN),
				PartPose.offset(0.0F, NECK_Y, NECK_Z));
			root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(40, 108).addBox(-1.0F, -0.5F, 0.0F, 2.0F, 1.0F, 3.0F),
				PartPose.offset(0.0F, 17.5F, 6.0F));
			// the front flippers are the man's legs (so the pose code still finds them), the hind pair new; each
			// reaches 1 pixel up into the shell and 5 down to the ground
			root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-1.5F, -1.0F, -2.0F, 3.0F, 6.0F, 4.0F),
				PartPose.offset(-5.5F, 19.0F, -4.0F));
			root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-1.5F, -1.0F, -2.0F, 3.0F, 6.0F, 4.0F),
				PartPose.offset(5.5F, 19.0F, -4.0F));
			root.addOrReplaceChild("hind_right_leg", CubeListBuilder.create().texOffs(16, 108).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 6.0F, 3.0F),
				PartPose.offset(-5.0F, 19.0F, 4.5F));
			root.addOrReplaceChild("hind_left_leg", CubeListBuilder.create().texOffs(28, 108).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 6.0F, 3.0F),
				PartPose.offset(5.0F, 19.0F, 4.5F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			XenoMotion.Read m = XenoMotion.read(state);
			String act = state.getData(XenoformClient.ACT);
			boolean spin = "spin".equals(act);
			boolean roll = "roll".equals(act);
			boolean tucked = spin || roll;
			this.hat.visible = this.rightArm.visible = this.leftArm.visible = this.body.visible = false;
			for (ModelPart part : new ModelPart[] {this.head, this.body, this.rightLeg, this.leftLeg, this.hindRight, this.hindLeft,
				this.shell, this.shellTop, this.plastron, this.neck, this.tail}) {
				part.resetPose();
			}
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			float walk = state.walkAnimationPos * GAIT;
			// swimming (or paddling through water): the level body stays, the flippers row
			float swim = Math.max(state.swimAmount, state.isInWater ? Math.min(1.0F, speed * 2.0F) : 0.0F);
			float age = state.ageInTicks;

			// ---- the shell: one rigid group, dropped when it sits down, rocking as it waddles ----
			float drop = state.isCrouching ? CROUCH_DROP : 0.0F;
			if (roll) {
				drop = ROLL_DROP;
			} else if (spin) {
				drop = SPIN_DROP;
			} else {
				drop += m.land() * LAND_SQUAT;
			}
			float rock;
			float yaw;
			if (spin) {
				rock = Mth.sin(age * 0.35F) * 0.1F;
				yaw = age * SPIN_RATE;
			} else if (roll) {
				rock = Mth.sin(age * 0.08F) * 0.02F;
				yaw = 0.0F;
			} else {
				rock = Mth.sin(walk) * ROCK * speed * (1.0F - swim);
				yaw = Mth.cos(walk) * SWAY * speed * (1.0F - swim);
			}
			for (ModelPart part : new ModelPart[] {this.shell, this.shellTop, this.plastron}) {
				part.y = SHELL_Y + drop;
				part.yRot = yaw;
				part.zRot = rock;
			}
			// the (hidden) body lies face-up at the crown's middle, 3 above the pivot, turning with the shell
			this.body.x = Mth.sin(rock) * 3.0F;
			this.body.y = SHELL_Y + drop - Mth.cos(rock) * 3.0F;
			this.body.z = 0.0F;
			this.body.xRot = -Mth.HALF_PI;
			this.body.yRot = yaw;
			this.body.zRot = rock;

			// ---- pulled in: the limbs are drawn into the shell, the head peeks out of it (not at all when spinning) ----
			boolean out = !tucked;
			this.neck.visible = this.tail.visible = out;
			this.rightLeg.visible = this.leftLeg.visible = this.hindRight.visible = this.hindLeft.visible = out;
			this.head.visible = !spin;
			if (tucked) {
				this.head.x = 0.0F;
				this.head.y = SHELL_Y + drop + 0.5F;
				this.head.z = 0.0F;
				return;
			}

			// ---- the flippers: diagonal pairs (front right with hind left), the feet kept on the ground ----
			float legY = SHELL_Y + 2.0F + drop;
			float stroke = Mth.sin(age * STROKE_RATE);
			float strokeX = Mth.cos(age * STROKE_RATE);
			float air = m.air();
			this.flipper(this.rightLeg, 1.0F, 0.0F, STRIDE_FRONT, legY, walk, speed, swim, stroke, strokeX, air, true);
			this.flipper(this.leftLeg, -1.0F, Mth.PI, STRIDE_FRONT, legY, walk, speed, swim, stroke, strokeX, air, true);
			this.flipper(this.hindRight, 1.0F, Mth.PI, STRIDE_HIND, legY, walk, speed, swim, stroke, strokeX, air, false);
			this.flipper(this.hindLeft, -1.0F, 0.0F, STRIDE_HIND, legY, walk, speed, swim, stroke, strokeX, air, false);

			// ---- the neck and head ----
			float yawH = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -1.4F, 1.4F);
			float pitchH = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -1.1F, 1.1F);
			float bite = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * Mth.PI);
			float ext = REST_EXT + Mth.cos(walk * 2.0F) * 0.1F * speed + m.breath() * 0.03F + bite * BITE_REACH + swim * SWIM_REACH
				- (state.isCrouching ? SNEAK_PULL : 0.0F);
			this.neck.y = NECK_Y + drop;
			this.neck.z = NECK_Z;
			this.neck.zScale = ext;
			this.neck.yRot = yawH * 0.55F;
			this.neck.xRot = pitchH * 0.5F + bite * 0.1F;
			// the head sits at the neck's tip, a little inside it, and turns on past the neck
			float dist = NECK_LEN * ext - NECK_OVERLAP;
			float flat = Mth.cos(this.neck.xRot) * dist;
			this.head.x = -flat * Mth.sin(this.neck.yRot);
			this.head.y = this.neck.y + dist * Mth.sin(this.neck.xRot);
			this.head.z = NECK_Z - flat * Mth.cos(this.neck.yRot);
			this.head.yRot = yawH;
			this.head.xRot = pitchH + bite * 0.3F;
			this.head.zRot = -m.turn() * 0.12F;

			// ---- the tail: sways, trails a turn, lifts as it falls and drops on a landing or a sneak ----
			this.tail.y = 17.5F + drop;
			this.tail.yRot = Mth.sin(age * 0.12F) * 0.12F + Mth.sin(walk) * 0.3F * speed - m.trail() * 0.5F;
			this.tail.xRot = tailLift(m);
		}

		/**
		 * One flipper: walking it swings fore and aft (its pair half a cycle out) and lifts outward as it comes forward;
		 * swimming the front pair sweeps out and down together like wings and the hind pair trails and steers. Its length
		 * is set so the lowest corner of its foot (tilted by the swing and splay) is on the ground while it pushes back,
		 * STEP_UP above it at the top of its forward swing; in the water it keeps its own length.
		 */
		private void flipper(ModelPart leg, float side, float phase, float stride, float legY, float walk, float speed, float swim,
			float stroke, float strokeX, float air, boolean front) {
			float a = walk + phase;
			float walkX = Mth.cos(a) * stride * speed;
			float walkZ = side * (SPLAY + LIFT * Math.max(0.0F, Mth.sin(a)) * speed);
			float swimX;
			float swimZ;
			if (front) {
				swimX = -0.35F + 0.45F * strokeX;
				swimZ = side * (STROKE_OUT + STROKE_SWEEP * stroke);
			} else {
				swimX = 0.5F + 0.2F * Mth.sin(phase + stroke);
				swimZ = side * (0.5F + 0.15F * stroke);
			}
			leg.xRot = Mth.lerp(swim, walkX, swimX);
			leg.zRot = Mth.lerp(swim, walkZ, swimZ) + side * air * 0.5F;
			// the box reaches 1.5 to each side and half its depth fore and aft of the pivot, FOOT below it (times yScale)
			float halfDepth = front ? 2.0F : 1.5F;
			float cx = Mth.cos(leg.xRot);
			float cz = Mth.cos(leg.zRot);
			float down = GROUND - legY - STEP_UP * Math.max(0.0F, Mth.sin(a)) * speed;
			float pinned = ((down - 1.5F * Mth.abs(Mth.sin(leg.zRot))) / Math.max(0.2F, cz) - halfDepth * Mth.abs(Mth.sin(leg.xRot)))
				/ (FOOT * Math.max(0.2F, cx));
			leg.y = legY;
			leg.yScale = Mth.lerp(swim, Mth.clamp(pinned, 0.5F, 1.3F), 1.0F);
		}
	}

	/**
	 * (0.18.103, patch 5) Cortex: plated, one big claw on the right hand and a small one on the left that open and shut,
	 * and an open skull with the brain showing, pulsing a little.
	 */
	static final class Cortex extends FormModel {
		private final ModelPart bigClaw, smallClaw, brain;

		Cortex() {
			super(bake(mesh()), "cortex");
			this.insigniaZ = -2.3F;
			this.bigClaw = this.rightArm.getChild("claw");
			this.smallClaw = this.leftArm.getChild("claw");
			this.brain = this.head.getChild("brain");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.3F), 0.0F);
			PartDefinition root = mesh.getRoot();
			root.getChild("right_arm").addOrReplaceChild("claw", CubeListBuilder.create().texOffs(0, 64).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 5.0F, 5.0F),
				PartPose.offset(-1.0F, 9.0F, 0.0F));
			root.getChild("left_arm").addOrReplaceChild("claw", CubeListBuilder.create().texOffs(24, 64).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 3.0F, 3.0F),
				PartPose.offset(1.0F, 9.5F, 0.0F));
			root.getChild("head").addOrReplaceChild("brain", CubeListBuilder.create().texOffs(0, 76).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 3.0F, 6.0F),
				PartPose.offset(0.0F, -7.6F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float snap = Mth.sin(state.ageInTicks * 0.15F) * 0.12F;
			this.bigClaw.zRot = snap;
			this.smallClaw.zRot = -snap;
			float pulse = 1.0F + Mth.sin(state.ageInTicks * 0.2F) * 0.04F;
			this.brain.xScale = this.brain.zScale = pulse;
		}
	}

	/** (0.18.103, patch 5) Locust: lean, wing cases folded down its back, long feelers that twitch, spurs on its legs. */
	static final class Locust extends FormModel {
		private final ModelPart rightFeeler, leftFeeler;

		Locust() {
			super(bake(mesh()), "locust");
			this.insigniaZ = -1.8F;
			this.rightFeeler = this.head.getChild("right_feeler");
			this.leftFeeler = this.head.getChild("left_feeler");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(-0.2F), 0.0F);
			PartDefinition root = mesh.getRoot();
			root.getChild("body").addOrReplaceChild("wings", CubeListBuilder.create().texOffs(0, 64).addBox(-4.0F, 0.0F, 0.0F, 8.0F, 11.0F, 1.0F),
				PartPose.offsetAndRotation(0.0F, 0.5F, 2.0F, 0.12F, 0.0F, 0.0F));
			PartDefinition head = root.getChild("head");
			head.addOrReplaceChild("right_feeler", CubeListBuilder.create().texOffs(20, 64).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 6.0F, 1.0F),
				PartPose.offsetAndRotation(-1.5F, -8.0F, -3.0F, -0.6F, 0.0F, -0.35F));
			head.addOrReplaceChild("left_feeler", CubeListBuilder.create().texOffs(20, 64).mirror().addBox(-0.5F, -6.0F, -0.5F, 1.0F, 6.0F, 1.0F),
				PartPose.offsetAndRotation(1.5F, -8.0F, -3.0F, -0.6F, 0.0F, 0.35F));
			root.getChild("right_leg").addOrReplaceChild("spur", CubeListBuilder.create().texOffs(24, 64).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 5.0F, 2.0F),
				PartPose.offsetAndRotation(0.0F, 2.0F, 1.5F, 0.6F, 0.0F, 0.0F));
			root.getChild("left_leg").addOrReplaceChild("spur", CubeListBuilder.create().texOffs(24, 64).mirror().addBox(-1.0F, 0.0F, 0.0F, 2.0F, 5.0F, 2.0F),
				PartPose.offsetAndRotation(0.0F, 2.0F, 1.5F, 0.6F, 0.0F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float twitch = Mth.sin(state.ageInTicks * 0.3F) * 0.1F;
			this.rightFeeler.xRot = -0.6F + twitch;
			this.leftFeeler.xRot = -0.6F - twitch;
		}
	}

	/** (0.18.103, patch 5) Croaker: bulky, eyes on top of its head, a throat sac that swells and sinks, webbed feet. */
	static final class Croaker extends FormModel {
		private final ModelPart sac;

		Croaker() {
			super(bake(mesh()), "croaker");
			this.insigniaZ = -2.7F;
			this.sac = this.head.getChild("sac");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.7F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			head.addOrReplaceChild("right_eye", CubeListBuilder.create().texOffs(0, 64).addBox(-1.5F, -3.0F, -1.5F, 3.0F, 3.0F, 3.0F),
				PartPose.offset(-2.5F, -8.2F, -2.0F));
			head.addOrReplaceChild("left_eye", CubeListBuilder.create().texOffs(0, 64).mirror().addBox(-1.5F, -3.0F, -1.5F, 3.0F, 3.0F, 3.0F),
				PartPose.offset(2.5F, -8.2F, -2.0F));
			head.addOrReplaceChild("sac", CubeListBuilder.create().texOffs(12, 64).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 3.0F, 3.0F),
				PartPose.offset(0.0F, -0.8F, -1.6F));
			root.getChild("right_leg").addOrReplaceChild("foot", CubeListBuilder.create().texOffs(0, 72).addBox(-2.0F, 0.0F, -3.5F, 4.0F, 1.0F, 4.0F),
				PartPose.offset(0.0F, 11.5F, 0.0F));
			root.getChild("left_leg").addOrReplaceChild("foot", CubeListBuilder.create().texOffs(0, 72).mirror().addBox(-2.0F, 0.0F, -3.5F, 4.0F, 1.0F, 4.0F),
				PartPose.offset(0.0F, 11.5F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float swell = 1.0F + Math.max(0.0F, Mth.sin(state.ageInTicks * 0.12F)) * 0.35F;
			this.sac.yScale = swell;
			this.sac.zScale = swell;
		}
	}

	/** (0.18.103, patch 5) Dud: small, soft and round, two nubs on its head; it wobbles as it goes. */
	static final class Dud extends FormModel {
		Dud() {
			super(bake(mesh()), "dud");
			this.insigniaZ = -2.9F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.9F), 0.0F);
			PartDefinition head = mesh.getRoot().getChild("head");
			head.addOrReplaceChild("right_nub", CubeListBuilder.create().texOffs(0, 64).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F),
				PartPose.offset(-2.0F, -8.8F, 0.0F));
			head.addOrReplaceChild("left_nub", CubeListBuilder.create().texOffs(0, 64).mirror().addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F),
				PartPose.offset(2.0F, -8.8F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float wobble = Mth.sin(state.ageInTicks * 0.25F) * 0.05F + Mth.sin(state.walkAnimationPos * 0.6662F) * 0.1F * state.walkAnimationSpeed;
			this.body.zRot += wobble;
			this.head.zRot += wobble * 1.5F;
		}
	}

	/** (0.18.107, Scan B) Rime: angular ice, a crown of three icicles and two icicle spines down its back that shiver. */
	static final class Rime extends FormModel {
		private final ModelPart rightSpine, leftSpine;

		Rime() {
			super(bake(mesh()), "rime");
			this.insigniaZ = -2.2F;
			this.rightSpine = this.body.getChild("right_spine");
			this.leftSpine = this.body.getChild("left_spine");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.2F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			head.addOrReplaceChild("crown_mid", CubeListBuilder.create().texOffs(0, 64).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
				PartPose.offset(0.0F, -8.2F, -1.0F));
			head.addOrReplaceChild("crown_right", CubeListBuilder.create().texOffs(0, 64).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
				PartPose.offsetAndRotation(-2.5F, -8.2F, -1.0F, 0.0F, 0.0F, -0.35F));
			head.addOrReplaceChild("crown_left", CubeListBuilder.create().texOffs(0, 64).mirror().addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
				PartPose.offsetAndRotation(2.5F, -8.2F, -1.0F, 0.0F, 0.0F, 0.35F));
			PartDefinition body = root.getChild("body");
			body.addOrReplaceChild("right_spine", CubeListBuilder.create().texOffs(8, 64).addBox(-1.0F, -8.0F, -1.0F, 2.0F, 8.0F, 2.0F),
				PartPose.offsetAndRotation(-1.8F, 4.0F, 2.4F, -0.7F, 0.0F, -0.15F));
			body.addOrReplaceChild("left_spine", CubeListBuilder.create().texOffs(8, 64).mirror().addBox(-1.0F, -8.0F, -1.0F, 2.0F, 8.0F, 2.0F),
				PartPose.offsetAndRotation(1.8F, 4.0F, 2.4F, -0.7F, 0.0F, 0.15F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float shiver = Mth.sin(state.ageInTicks * 0.9F) * 0.015F;
			this.rightSpine.zRot = -0.15F + shiver;
			this.leftSpine.zRot = 0.15F - shiver;
		}
	}

	/** (0.18.107, Scan B) Marrow: bare bone, two horns curling back off the skull, an open ribcage that breathes, a spine out its back. */
	static final class Marrow extends FormModel {
		private final ModelPart ribs;

		Marrow() {
			super(bake(mesh()), "marrow");
			this.insigniaZ = -2.6F;
			this.ribs = this.body.getChild("ribs");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(-0.1F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			head.addOrReplaceChild("right_horn", CubeListBuilder.create().texOffs(0, 64).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
				PartPose.offsetAndRotation(-3.0F, -7.6F, -1.0F, -0.6F, 0.0F, -0.4F));
			head.addOrReplaceChild("left_horn", CubeListBuilder.create().texOffs(0, 64).mirror().addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
				PartPose.offsetAndRotation(3.0F, -7.6F, -1.0F, -0.6F, 0.0F, 0.4F));
			PartDefinition body = root.getChild("body");
			body.addOrReplaceChild("spine", CubeListBuilder.create().texOffs(8, 64).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 10.0F, 2.0F),
				PartPose.offset(0.0F, 0.5F, 1.8F));
			body.addOrReplaceChild("ribs", CubeListBuilder.create().texOffs(0, 80).addBox(-4.5F, 0.0F, -2.5F, 9.0F, 7.0F, 5.0F),
				PartPose.offset(0.0F, 1.0F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float breathe = 1.0F + Mth.sin(state.ageInTicks * 0.1F) * 0.03F;
			this.ribs.xScale = this.ribs.zScale = breathe;
		}
	}

	/** (0.18.107, Scan B) Swathe: wrapped head to foot, a loose end trailing from each arm and a long one down its back, all swaying. */
	static final class Swathe extends FormModel {
		private final ModelPart rightEnd, leftEnd, tail;

		Swathe() {
			super(bake(mesh()), "swathe");
			this.insigniaZ = -2.25F;
			this.rightEnd = this.rightArm.getChild("loose_end");
			this.leftEnd = this.leftArm.getChild("loose_end");
			this.tail = this.body.getChild("trail");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.25F), 0.0F);
			PartDefinition root = mesh.getRoot();
			root.getChild("right_arm").addOrReplaceChild("loose_end", CubeListBuilder.create().texOffs(0, 64).addBox(-1.5F, 0.0F, 0.0F, 3.0F, 9.0F, 1.0F),
				PartPose.offset(-1.0F, 4.0F, 2.2F));
			root.getChild("left_arm").addOrReplaceChild("loose_end", CubeListBuilder.create().texOffs(0, 64).mirror().addBox(-1.5F, 0.0F, 0.0F, 3.0F, 9.0F, 1.0F),
				PartPose.offset(1.0F, 4.0F, 2.2F));
			root.getChild("body").addOrReplaceChild("trail", CubeListBuilder.create().texOffs(8, 64).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 12.0F, 1.0F),
				PartPose.offset(0.0F, 0.5F, 2.3F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float sway = Mth.sin(state.ageInTicks * 0.12F) * 0.08F;
			float drag = Math.min(0.8F, state.walkAnimationSpeed * 0.7F);
			this.rightEnd.xRot = drag + sway;
			this.leftEnd.xRot = drag - sway;
			this.tail.xRot = drag * 0.8F + sway * 0.5F;
			this.tail.zRot = sway;
		}
	}

	/** (0.18.107, Scan B) Blight: coal-black bone, a skull on each shoulder beside the big middle one, a ribbed tail that swings. */
	static final class Blight extends FormModel {
		private final ModelPart rightSkull, leftSkull, tail;

		Blight() {
			super(bake(mesh()), "blight");
			this.insigniaZ = -2.4F;
			this.rightSkull = this.body.getChild("right_skull");
			this.leftSkull = this.body.getChild("left_skull");
			this.tail = this.body.getChild("tail");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.4F), 0.0F);
			PartDefinition body = mesh.getRoot().getChild("body");
			body.addOrReplaceChild("right_skull", CubeListBuilder.create().texOffs(0, 64).addBox(-3.0F, -6.0F, -3.0F, 6.0F, 6.0F, 6.0F),
				PartPose.offsetAndRotation(-7.0F, -1.0F, 0.5F, 0.0F, 0.25F, 0.0F));
			body.addOrReplaceChild("left_skull", CubeListBuilder.create().texOffs(0, 64).mirror().addBox(-3.0F, -6.0F, -3.0F, 6.0F, 6.0F, 6.0F),
				PartPose.offsetAndRotation(7.0F, -1.0F, 0.5F, 0.0F, -0.25F, 0.0F));
			body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(24, 64).addBox(-1.5F, 0.0F, 0.0F, 3.0F, 10.0F, 3.0F),
				PartPose.offsetAndRotation(0.0F, 10.0F, 1.5F, 0.6F, 0.0F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float bob = Mth.sin(state.ageInTicks * 0.08F) * 0.08F;
			this.rightSkull.xRot = bob;
			this.leftSkull.xRot = -bob;
			this.rightSkull.yRot = 0.25F + this.head.yRot * 0.3F;
			this.leftSkull.yRot = -0.25F + this.head.yRot * 0.3F;
			this.tail.yRot = Mth.sin(state.ageInTicks * 0.06F) * 0.3F;
			this.tail.xRot = 0.6F + Mth.sin(state.walkAnimationPos * 0.6662F) * 0.15F * state.walkAnimationSpeed;
		}
	}

	/**
	 * (0.18.109, Scan C; 0.19.60 a four-legged wolf) Howl: a wolf's muzzle and tall ears, a bushy tail that swings as it
	 * runs. Now a wolf, not a man: a long body level with the ground (a deep chest and mane in front, a lean rump
	 * behind) on four legs in a trot, diagonal pairs together, the spine flexing a little and the body bobbing with each
	 * stride. The head, with its muzzle, jaw, ruff and tall ears, turns where it looks; the bushy two-part tail hangs
	 * low and lifts and streams when it runs, flares as it falls and whips down on landing. A leap stretches it out,
	 * front legs reaching, hind legs trailing, nose following the arc. Crouched it is a low stalk (legs splayed, head
	 * and tail down, ears flat); an attack is a snapping bite: the head lunges, the jaw gapes and snaps shut. The man's
	 * parts stay, hidden, but the front legs are the player's own legs reshaped; the hidden body lies on its back
	 * under the insignia so the plate sits flat on the rump. The neck stays level in the shoulders as the head nods.
	 */
	static final class Howl extends FormModel {
		// ---- tuning ----
		/** The torso's centre height (model y), how far the leg's foot hangs below its hip, and the foot's half depth. */
		static final float TORSO_Y = 10.5F, LEG_LENGTH = 10.0F, FOOT_HALF = 1.5F;
		/** Where the legs sit: half the gap between a pair (x) and the front and hind hips (z, torso space). */
		static final float LEG_X = 2.0F, FRONT_Z = -3.5F, HIND_Z = 5.0F;
		/** How fast the legs step against the walk, how far they swing (radians), how high a swinging foot lifts, the body bob. */
		static final float GAIT = 0.6662F * 1.25F, SWING = 0.55F, LIFT = 1.4F, BOB = 0.7F;
		/** The spine's side-to-side flex in a trot, and how far it bends into a turn (radians). */
		static final float FLEX = 0.06F, TURN_BEND = 0.12F;
		/** Crouched (the stalk): how far the body drops, how far the legs splay, how much of the stride is left, the nose-down tilt. */
		static final float CROUCH_DROP = 3.0F, SPLAY = 0.55F, CROUCH_STRIDE = 0.4F, STALK_PITCH = 0.12F, STALK_HEAD = 1.2F;
		/** The renderer draws a sneaking player this many pixels lower (AvatarRenderer.getRenderOffset): the paws are planted that much higher to stay on the ground. */
		static final float CROUCH_SINK = 2.0F;
		/** A leap: how far the front legs reach going up and coming down, how far the hind trail, and the tilt with the arc. */
		static final float LEAP_REACH = 0.9F, LAND_REACH = 0.35F, TRAIL_REACH = 0.8F, LEAP_PITCH = 0.35F;
		/** A landing squashes the body down this far (pixels). */
		static final float LAND_DROP = 1.5F;
		/** A bite: the head lunges this far (pixels), the jaw gapes this wide (radians) and has shut again by SNAP_END; the front paws reach. */
		static final float LUNGE = 2.0F, JAW_OPEN = 0.8F, OPEN_AT = 0.4F, SNAP_END = 0.6F, BITE_REACH = 0.25F;
		/** The tail: how far it hangs (radians below straight back), how far its tip curls, and how far a run lifts it. */
		static final float TAIL_HANG = 0.9F, TIP_CURL = 0.3F, TAIL_RUN = 0.6F;

		private final ModelPart hindRight, hindLeft, torso, chest, tail, tailTip, jaw, neck, rightEar, leftEar;

		Howl() {
			super(bake(mesh()), "howl");
			// the plate sits on the top of the rump (the hidden body lies on its back)
			this.insigniaY = -3.5F;
			this.insigniaZ = -3.5F;
			this.insigniaSize = 1.0F;
			ModelPart root = this.root();
			this.hindRight = root.getChild("hind_right_leg");
			this.hindLeft = root.getChild("hind_left_leg");
			this.torso = root.getChild("torso");
			this.chest = this.torso.getChild("chest");
			this.tail = this.torso.getChild("tail");
			this.tailTip = this.tail.getChild("tail_tip");
			this.jaw = this.head.getChild("jaw");
			this.neck = this.head.getChild("neck");
			this.rightEar = this.head.getChild("right_ear");
			this.leftEar = this.head.getChild("left_ear");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the head: a skull with a muzzle and a jaw, a ruff of cheek fur, a short neck, tall ears
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -3.0F, -5.0F, 6.0F, 6.0F, 5.0F),
				PartPose.offset(0.0F, 9.5F, -6.0F));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(32, 64).addBox(-1.5F, 0.0F, -4.0F, 3.0F, 3.0F, 4.0F),
				PartPose.offset(0.0F, -0.5F, -5.0F));
			head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(32, 72).addBox(-1.5F, 0.0F, -4.0F, 3.0F, 1.0F, 4.0F),
				PartPose.offset(0.0F, 2.4F, -5.0F));
			head.addOrReplaceChild("ruff", CubeListBuilder.create().texOffs(0, 96).addBox(-3.5F, -3.5F, -2.0F, 7.0F, 7.0F, 3.0F), PartPose.ZERO);
			head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(20, 96).addBox(-2.0F, -2.5F, 0.0F, 4.0F, 5.0F, 5.0F), PartPose.ZERO);
			head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(40, 96).addBox(-1.0F, -4.0F, -0.5F, 2.0F, 4.0F, 1.0F),
				PartPose.offsetAndRotation(-2.0F, -2.8F, -1.5F, 0.0F, 0.0F, -0.18F));
			head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(40, 96).mirror().addBox(-1.0F, -4.0F, -0.5F, 2.0F, 4.0F, 1.0F),
				PartPose.offsetAndRotation(2.0F, -2.8F, -1.5F, 0.0F, 0.0F, 0.18F));
			// the wolf's body: a lean rump with a deeper chest and mane in front, a bushy tail off the back
			PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 64).addBox(-3.5F, -3.5F, -1.0F, 7.0F, 7.0F, 9.0F),
				PartPose.offset(0.0F, TORSO_Y, 0.0F));
			torso.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(0, 80).addBox(-4.0F, -4.5F, -6.0F, 8.0F, 8.0F, 7.0F),
				PartPose.offset(0.0F, 0.0F, -1.0F));
			PartDefinition tail = torso.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(48, 64).addBox(-1.5F, -1.5F, -0.5F, 3.0F, 3.0F, 5.0F),
				PartPose.offsetAndRotation(0.0F, -2.0F, 7.5F, -TAIL_HANG, 0.0F, 0.0F));
			tail.addOrReplaceChild("tail_tip", CubeListBuilder.create().texOffs(36, 80).addBox(-2.0F, -2.0F, -0.5F, 4.0F, 4.0F, 6.0F),
				PartPose.offsetAndRotation(0.0F, 0.0F, 4.5F, -TIP_CURL, 0.0F, 0.0F));
			// the front legs are the man's legs (so the pose code still finds them), the hind pair new; each reaches well up into the body
			root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-1.5F, -3.0F, -1.5F, 3.0F, 13.0F, 3.0F),
				PartPose.offset(-LEG_X, 14.0F, FRONT_Z));
			root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-1.5F, -3.0F, -1.5F, 3.0F, 13.0F, 3.0F),
				PartPose.offset(LEG_X, 14.0F, FRONT_Z));
			root.addOrReplaceChild("hind_right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-1.5F, -3.0F, -1.5F, 3.0F, 13.0F, 3.0F),
				PartPose.offset(-LEG_X, 14.0F, HIND_Z));
			root.addOrReplaceChild("hind_left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-1.5F, -3.0F, -1.5F, 3.0F, 13.0F, 3.0F),
				PartPose.offset(LEG_X, 14.0F, HIND_Z));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.hat.visible = this.rightArm.visible = this.leftArm.visible = this.body.visible = false;
			for (ModelPart part : new ModelPart[] {this.head, this.body, this.rightLeg, this.leftLeg, this.hindRight, this.hindLeft,
				this.torso, this.chest, this.tail, this.tailTip, this.jaw, this.neck, this.rightEar, this.leftEar}) {
				part.resetPose();
			}
			XenoMotion.Read m = XenoMotion.read(state);
			float t = state.ageInTicks;
			float speed = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
			float air = Mth.clamp(m.air(), 0.0F, 1.0F);
			float fall = Mth.clamp(m.fall(), -1.0F, 1.0F);
			float land = Math.max(0.0F, m.land());
			boolean crouch = state.isCrouching;
			// the ground, in model y: a sneaking player is drawn CROUCH_SINK lower, so the stalk stands that much higher
			float ground = 24.0F - (crouch ? CROUCH_SINK : 0.0F);
			float phase = state.walkAnimationPos * GAIT;
			float run = speed * (1.0F - air);
			float swing = Mth.clamp(state.swingAnimation, 0.0F, 1.0F);
			// a bite: the head lunges, the jaw gapes and snaps shut
			float lunge = Mth.sin(swing * Mth.PI);
			float open = swing < OPEN_AT ? Mth.sin(swing / OPEN_AT * Mth.HALF_PI)
				: Math.max(0.0F, 1.0F - (swing - OPEN_AT) / (SNAP_END - OPEN_AT));

			// ---- the body: level, bobbing with the stride, tipped with a leap, dropped low when stalking ----
			float pitch = air * fall * LEAP_PITCH + (crouch ? STALK_PITCH : 0.0F) + land * 0.1F;
			float torsoY = TORSO_Y + (crouch ? CROUCH_DROP - CROUCH_SINK : 0.0F) + Mth.cos(phase * 2.0F) * BOB * run + land * LAND_DROP;
			float cosP = Mth.cos(pitch);
			float sinP = Mth.sin(pitch);
			this.torso.y = torsoY;
			this.torso.xRot = pitch;
			float chestYaw = Mth.sin(phase) * FLEX * run - m.turn() * TURN_BEND;
			this.chest.yRot = chestYaw;
			this.chest.yScale = 1.0F + m.breath() * 0.02F;
			// the hidden body lies on its back under the insignia
			this.body.y = torsoY;
			this.body.xRot = -Mth.HALF_PI + pitch;

			// ---- the legs: a trot (front right with hind left, front left with hind right), planted flat ----
			float stride = Mth.cos(phase) * SWING * run * (crouch ? CROUCH_STRIDE : 1.0F);
			float splay = crouch ? SPLAY : 0.0F;
			float reach = BITE_REACH * lunge * (1.0F - air);
			float lift = LIFT * run * (crouch ? 0.6F : 1.0F);
			float liftA = lift * Math.max(0.0F, Mth.sin(phase));
			float liftB = lift * Math.max(0.0F, -Mth.sin(phase));
			// a leap: the front legs reach (less on the way down), the hind trail behind
			float reachUp = LEAP_REACH + (LAND_REACH - LEAP_REACH) * Math.max(0.0F, fall);
			float airFront = -reachUp;
			float airHind = TRAIL_REACH * (1.0F - 0.5F * Math.max(0.0F, fall));
			float frontRight = airFront * air + (1.0F - air) * (-splay - reach + stride);
			float frontLeft = airFront * air + (1.0F - air) * (-splay - reach - stride);
			float hindRightA = airHind * air + (1.0F - air) * (splay - stride);
			float hindLeftA = airHind * air + (1.0F - air) * (splay + stride);
			this.leg(this.rightLeg, -1.0F, FRONT_Z, frontRight, liftA, ground, torsoY, cosP, sinP, chestYaw, air, true);
			this.leg(this.leftLeg, 1.0F, FRONT_Z, frontLeft, liftB, ground, torsoY, cosP, sinP, chestYaw, air, true);
			this.leg(this.hindRight, -1.0F, HIND_Z, hindRightA, liftB, ground, torsoY, cosP, sinP, chestYaw, air, false);
			this.leg(this.hindLeft, 1.0F, HIND_Z, hindLeftA, liftA, ground, torsoY, cosP, sinP, chestYaw, air, false);

			// ---- the head: at the front of the chest, turning where it looks ----
			float relY = -1.0F;
			float relZ = -5.0F - lunge * LUNGE;
			float hx = relZ * Mth.sin(chestYaw);
			float hz = relZ * Mth.cos(chestYaw) - 1.0F;
			float headDrop = crouch ? STALK_HEAD : 0.0F;
			this.head.x = hx;
			this.head.y = torsoY + relY * cosP - hz * sinP + headDrop;
			this.head.z = relY * sinP + hz * cosP;
			this.head.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -1.0F, 1.0F);
			// sniffing now and then when it stands about
			float sniff = Math.max(0.0F, Mth.sin(t * 0.045F));
			sniff = sniff * sniff;
			sniff = sniff * sniff * sniff * sniff * (1.0F - speed) * (crouch ? 0.0F : 1.0F);
			this.head.xRot = state.xRot * Mth.DEG_TO_RAD - pitch * 0.6F + (crouch ? 0.12F : 0.0F) + lunge * 0.3F + sniff * 0.25F
				+ Mth.cos(phase * 2.0F - 0.6F) * 0.05F * run;
			this.head.zRot = -m.turn() * 0.12F;
			// the neck stays level in the shoulders however the head nods (it only turns with it)
			this.neck.xRot = -this.head.xRot;
			float pant = (Mth.sin(t * 0.9F) * 0.5F + 0.5F) * 0.1F * speed;
			this.jaw.xRot = open * JAW_OPEN + pant;

			// ---- the ears: up and alert, flattening back when it runs, stalks or bites, each twitching on its own ----
			float back = Math.min(1.0F, (crouch ? 0.7F : 0.0F) + speed * 0.4F + lunge * 0.6F + open * 0.3F);
			float twitchR = Math.max(0.0F, Mth.sin(t * 0.07F + 1.3F));
			twitchR *= twitchR;
			twitchR *= twitchR;
			twitchR *= twitchR;
			float twitchL = Math.max(0.0F, Mth.sin(t * 0.057F));
			twitchL *= twitchL;
			twitchL *= twitchL;
			twitchL *= twitchL;
			this.rightEar.xRot = 0.1F - back * 0.9F + twitchR * 0.35F;
			this.rightEar.zRot = -0.18F - back * 0.35F - twitchR * 0.2F;
			this.leftEar.xRot = 0.1F - back * 0.9F + twitchL * 0.35F;
			this.leftEar.zRot = 0.18F + back * 0.35F + twitchL * 0.2F;

			// ---- the tail: hangs low, streams out when it runs, flares as it falls, whips down on landing ----
			this.tail.xRot = -TAIL_HANG + TAIL_RUN * run + tailLift(m) + Mth.sin(phase * 2.0F) * 0.05F * run;
			this.tail.yRot = Mth.sin(t * 0.09F) * 0.12F * (1.0F - run) + Mth.sin(phase) * 0.3F * run - m.trail() * 0.5F - m.turn() * 0.2F;
			this.tailTip.xRot = -TIP_CURL + m.breath() * 0.05F + m.trail() * 0.1F;
			this.tailTip.yRot = Mth.sin(t * 0.09F - 0.8F) * 0.2F * (1.0F - run) + Mth.sin(phase - 0.9F) * 0.25F * run - m.trail() * 0.35F;
		}

		/**
		 * One leg: swung to {@code theta} (+ is the foot back) and its hip set so the lowest corner of the foot is on {@code ground}
		 * (less {@code lift}; hanging from the torso's underside in the air), the hip kept under the torso whatever the torso's tilt and the chest's flex.
		 */
		private void leg(ModelPart leg, float side, float zTorso, float theta, float lift, float ground, float torsoY, float cosP, float sinP,
			float chestYaw, float air, boolean front) {
			float planted = ground - LEG_LENGTH * Mth.cos(theta) - FOOT_HALF * Mth.abs(Mth.sin(theta)) - lift;
			float hip = planted + (Math.min(planted, torsoY + 3.5F) - planted) * air;
			leg.x = side * LEG_X + (front ? (zTorso + 1.0F) * Mth.sin(chestYaw) : 0.0F);
			leg.y = hip;
			leg.z = zTorso * cosP + (hip - torsoY) * sinP;
			leg.xRot = theta;
		}
	}

	/**
	 * (0.18.109, Scan C; 0.19.60 a cat on four legs) Prowl: a big cat, long and low: a chest and a haunch that flex
	 * about the spine, a round-eared head, four two-jointed legs on paws that stay flat on the ground, walking in a
	 * cat's lateral order and falling into a bounding gallop at speed (the spine arching, forepaws together, hindpaws
	 * together), and a long tail that droops, curls up at the tip and twitches. Sneaking is a stalk: belly low, paws
	 * tucked under, shoulders down, ears back, slow high-lifted steps; a swing is a forepaw rake; a leap stretches it
	 * out and a landing crouches it. In the cat the man's parts are hidden and the (hidden) body is laid on the chest's
	 * back, so the insignia sits between the shoulders.
	 */
	static final class Prowl extends FormModel {
		// ---- tuning ----
		/** The leg bones (model pixels): the shoulder's upper arm and forearm, the hip's thigh and shin (to the ankle). */
		static final float FRONT_UPPER = 4.0F, FRONT_LOWER = 4.0F, HIND_UPPER = 5.0F, HIND_LOWER = 4.0F;
		/** Where the legs hang from: how far below the spine, and how far along it (shoulders forward, hips back); sideways. */
		static final float HIP_DROP = 1.5F, SHOULDER_Z = -5.5F, HIP_Z = 5.5F, LEG_X = 2.0F;
		/** How far the ankle sits above the ground (the paw's height under it), so the ankle's ground line. */
		static final float PAW_DROP = 1.5F, GROUND = 24.0F - PAW_DROP;
		/** How far the hips stand above the ankles at rest, so the spine's height, and where the hind paw rests (behind the hip). */
		static final float REST_REACH = 6.8F, HIND_REST_Z = 6.5F;
		static final float SPINE_Y = GROUND - REST_REACH - HIP_DROP;
		/** The gait: the cycle's speed against the walk, and the speeds the legs start (full steps at STEP_FULL) and a gallop builds (GALLOP_FROM to GALLOP_FULL). */
		static final float GAIT = 0.54F, STEP_FULL = 0.25F, GALLOP_FROM = 0.8F, GALLOP_FULL = 1.0F;
		/** The part of a step a paw is down (walk, stalk, gallop), how far a paw reaches fore and aft (half the stride) and how high it lifts. */
		static final float WALK_DUTY = 0.62F, STALK_DUTY = 0.72F, GALLOP_DUTY = 0.4F;
		static final float WALK_STRIDE = 3.0F, STALK_STRIDE = 1.6F, GALLOP_STRIDE = 3.8F, HIND_STRIDE = 1.1F;
		static final float WALK_LIFT = 2.4F, STALK_LIFT = 2.2F, GALLOP_LIFT = 3.2F;
		/** Each paw's place in the cycle (right hind, right fore, left hind, left fore): a walk is lateral (hind, then fore on its side), a gallop pairs them. */
		private static final float[] WALK_PHASE = {0.0F, 0.25F, 0.5F, 0.75F};
		private static final float[] GALLOP_PHASE = {0.5F, 0.0F, 0.6F, 0.1F};
		/** The stalk: how far the belly drops, the chest tips nose-down, the haunch stays higher (relative), the head dips, the ears go back. */
		static final float STALK_DROP = 3.0F, STALK_PITCH = 0.16F, STALK_HAUNCH = -0.12F, STALK_HEAD = 0.2F, STALK_EARS = -0.35F;
		/** The stalk's paws tucked under the belly (so the folded legs clear the ground): the forepaws back, the hindpaws forward. */
		static final float STALK_FORE_Z = 1.5F, STALK_HIND_Z = -3.0F;
		/** Stalking, the lowest the head may look (radians against the world), so the nose never digs in. */
		static final float STALK_LOOK_DOWN = 0.5F;
		/** The gallop's spine flex (radians), its phase, and the body's dip a step (walk, gallop). */
		static final float FLEX = 0.14F, FLEX_PHASE = -0.6F, WALK_BOB = 0.5F, GALLOP_BOB = 0.9F;
		/** A landing squats the body this much (pixels; little when already stalking, less on the move); a fall pitches the nose this much. */
		static final float LAND_SQUAT = 2.5F, STALK_LAND_SQUAT = 0.5F, LAND_MOVING = 0.65F, FALL_PITCH = 0.35F;
		/** A leap: where the forepaws reach (forward, up) and the hindpaws trail (back, up). */
		static final float AIR_FORE_Z = -4.0F, AIR_FORE_UP = 5.0F, AIR_HIND_Z = 4.5F, AIR_HIND_UP = 3.5F;
		/** The rake: how high the right forepaw rises, how far it reaches forward, how far it drags back, how far the chest rears (and rises, from a stalk), how far the paw tips its pads forward, and the least the leg stretches out. */
		static final float RAKE_UP = 7.0F, RAKE_REACH = 5.0F, RAKE_DRAG = 4.0F, RAKE_REAR = 0.15F, STALK_RAKE_RISE = 2.0F, RAKE_CURL = 0.9F, RAKE_MIN_REACH = 6.0F;
		/** The tail: how it hangs (radians, + is up), its curl at the tip, how far a gallop lifts it, how strongly the fall lifts it. */
		static final float TAIL_HANG = -0.45F, TAIL_CURL_MID = 0.25F, TAIL_CURL_TIP = 0.9F, TAIL_GALLOP = 0.6F, TAIL_FALL = 0.6F;
		/** The lowest the tail's root may hang (radians), walking and stalking. */
		static final float TAIL_MIN = -0.75F, TAIL_MIN_STALK = -0.6F;
		/** How far the head can turn and tip against the chest. */
		static final float HEAD_YAW = 1.2F, HEAD_PITCH = 0.9F;
		/** Where along the chest's back (from its rear) the insignia plate sits. */
		static final float INSIGNIA_AT = 4.5F;
		private static final float TAU = (float)Math.PI * 2.0F;

		private final ModelPart chest, haunch, catHead, muzzle, rightEar, leftEar, tail1, tail2, tail3;
		/** Right hind, right fore, left hind, left fore. */
		private final ModelPart[] upper = new ModelPart[4];
		private final ModelPart[] lower = new ModelPart[4];
		private final ModelPart[] paw = new ModelPart[4];
		private final ModelPart[] moving;
		/** Scratch for the leg solver: the thigh's and shin's angles. */
		private final float[] angles = new float[2];

		Prowl() {
			super(bake(mesh()), "prowl");
			// the plate sits on the chest's back, between the shoulders (the body is laid face-up on it, see setupAnim)
			this.insigniaY = 0.0F;
			this.insigniaZ = -3.0F;
			this.insigniaSize = 0.8F;
			ModelPart root = this.root();
			this.chest = root.getChild("chest");
			this.haunch = this.chest.getChild("haunch");
			this.catHead = this.chest.getChild("cat_head");
			this.muzzle = this.catHead.getChild("muzzle");
			this.rightEar = this.catHead.getChild("right_ear");
			this.leftEar = this.catHead.getChild("left_ear");
			this.tail1 = this.haunch.getChild("tail1");
			this.tail2 = this.tail1.getChild("tail2");
			this.tail3 = this.tail2.getChild("tail3");
			String[] names = {"hind_right", "fore_right", "hind_left", "fore_left"};
			for (int i = 0; i < 4; i++) {
				this.upper[i] = (i % 2 == 0 ? this.haunch : this.chest).getChild(names[i] + "_upper");
				this.lower[i] = this.upper[i].getChild(names[i] + "_lower");
				this.paw[i] = this.lower[i].getChild(names[i] + "_paw");
			}
			ModelPart[] all = new ModelPart[21];
			int n = 0;
			for (ModelPart part : new ModelPart[] {this.chest, this.haunch, this.catHead, this.muzzle, this.rightEar, this.leftEar, this.tail1, this.tail2, this.tail3}) {
				all[n++] = part;
			}
			for (int i = 0; i < 4; i++) {
				all[n++] = this.upper[i];
				all[n++] = this.lower[i];
				all[n++] = this.paw[i];
			}
			this.moving = all;
		}

		/**
		 * A leg: the thigh or upper arm hung at the hip (a pixel of it above the pivot, to sit in the body), the shin below
		 * it to the ankle, and a paw on the ankle that is kept level with the ground (see setupAnim).
		 */
		private static void leg(PartDefinition parent, String name, boolean hind, boolean left, float x, float z, int upperU, int upperV, int lowerU, int lowerV, int pawU, int pawV) {
			float upperLength = hind ? HIND_UPPER : FRONT_UPPER;
			float lowerLength = hind ? HIND_LOWER : FRONT_LOWER;
			CubeListBuilder thigh = CubeListBuilder.create().texOffs(upperU, upperV);
			if (hind) {
				thigh.addBox(-2.0F, -1.0F, -2.0F, 4.0F, 6.0F, 4.0F);
			} else {
				thigh.addBox(-1.5F, -1.0F, -1.5F, 3.0F, 5.0F, 3.0F);
			}
			PartDefinition upper = parent.addOrReplaceChild(name + "_upper", thigh, PartPose.offset(left ? x : -x, HIP_DROP, z));
			// the shin is a hair narrower than the thigh so they never fight for the same pixels
			CubeListBuilder shin = CubeListBuilder.create().texOffs(lowerU, lowerV).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 5.0F, 3.0F, new CubeDeformation(-0.1F, 0.0F, -0.1F));
			PartDefinition lower = upper.addOrReplaceChild(name + "_lower", shin, PartPose.offset(0.0F, upperLength, 0.0F));
			// the paw: its top half a pixel up the shin (covering the shin's end however it tilts), its bottom PAW_DROP below the ankle, toes forward
			lower.addOrReplaceChild(name + "_paw", CubeListBuilder.create().texOffs(pawU, pawV).addBox(-1.5F, -0.5F, -2.0F, 3.0F, 2.0F, 3.0F),
				PartPose.offset(0.0F, lowerLength, 0.0F));
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
			PartDefinition root = mesh.getRoot();
			// the man's parts stay (hidden) for the poses and the first-person hand to find
			PartDefinition chest = root.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(0, 64).addBox(-3.0F, -3.0F, -8.0F, 6.0F, 6.0F, 8.0F),
				PartPose.offset(0.0F, SPINE_Y, 0.0F));
			// the haunch runs a pixel into the chest (and is a hair slimmer) so the spine never opens a gap when it flexes
			PartDefinition haunch = chest.addOrReplaceChild("haunch", CubeListBuilder.create().texOffs(28, 64).addBox(-3.0F, -3.0F, -1.0F, 6.0F, 6.0F, 9.0F, new CubeDeformation(-0.15F)),
				PartPose.ZERO);
			PartDefinition head = chest.addOrReplaceChild("cat_head", CubeListBuilder.create().texOffs(0, 80).addBox(-3.0F, -3.0F, -4.5F, 6.0F, 5.0F, 5.0F),
				PartPose.offset(0.0F, -1.0F, -7.5F));
			head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(22, 80).addBox(-1.5F, -0.5F, -2.0F, 3.0F, 2.0F, 2.0F),
				PartPose.offset(0.0F, 0.0F, -4.5F));
			head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(32, 80).addBox(-1.0F, -2.0F, -0.5F, 2.0F, 2.0F, 1.0F),
				PartPose.offsetAndRotation(-2.0F, -3.0F, -1.5F, 0.0F, 0.0F, -0.2F));
			head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(38, 80).addBox(-1.0F, -2.0F, -0.5F, 2.0F, 2.0F, 1.0F),
				PartPose.offsetAndRotation(2.0F, -3.0F, -1.5F, 0.0F, 0.0F, 0.2F));
			// the tail: a stout root, a tapering middle, a tip that curls up
			CubeDeformation taper = new CubeDeformation(-0.1F, -0.1F, 0.0F);
			PartDefinition t1 = haunch.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(44, 80).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 6.0F),
				PartPose.offsetAndRotation(0.0F, -1.0F, 7.5F, TAIL_HANG, 0.0F, 0.0F));
			PartDefinition t2 = t1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(0, 92).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 6.0F, taper),
				PartPose.offset(0.0F, 0.0F, 5.5F));
			t2.addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(16, 92).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 4.0F, taper),
				PartPose.offset(0.0F, 0.0F, 5.5F));
			// legs, the right ones at -x: forelegs off the chest, hind legs off the haunch
			leg(haunch, "hind_right", true, false, LEG_X, HIP_Z, 0, 110, 44, 100, 32, 110);
			leg(chest, "fore_right", false, false, LEG_X, SHOULDER_Z, 28, 92, 0, 100, 32, 116);
			leg(haunch, "hind_left", true, true, LEG_X, HIP_Z, 16, 110, 0, 120, 44, 110);
			leg(chest, "fore_left", false, true, LEG_X, SHOULDER_Z, 40, 92, 12, 100, 44, 116);
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.head.visible = this.hat.visible = this.rightArm.visible = this.leftArm.visible = false;
			this.rightLeg.visible = this.leftLeg.visible = this.body.visible = false;
			this.body.resetPose();
			for (ModelPart part : this.moving) {
				part.resetPose();
			}
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			float speed = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
			boolean stalk = state.isCrouching;
			float amp = Mth.clamp(speed / STEP_FULL, 0.0F, 1.0F);
			float gallop = stalk ? 0.0F : smooth(GALLOP_FROM, GALLOP_FULL, speed);
			float cycle = state.walkAnimationPos * GAIT / TAU;
			float ph = cycle * TAU;
			float duty = stalk ? STALK_DUTY : Mth.lerp(gallop, WALK_DUTY, GALLOP_DUTY);
			float stride = (stalk ? STALK_STRIDE : Mth.lerp(gallop, WALK_STRIDE, GALLOP_STRIDE)) * amp;
			float lift = (stalk ? STALK_LIFT : Mth.lerp(gallop, WALK_LIFT, GALLOP_LIFT)) * amp;
			float air = m.air();
			float swing = Mth.clamp(state.swingAnimation, 0.0F, 1.0F);
			float sw = Mth.sin(swing * (float)Math.PI);

			// the spine: the gallop arches it (chest and haunch bow against each other), the stalk tips it nose-down
			float flex = FLEX * gallop * amp * Mth.sin(ph + FLEX_PHASE);
			float airPitch = m.fall() * air * FALL_PITCH;
			float chestPitch = (stalk ? STALK_PITCH : 0.0F) + flex + airPitch - sw * RAKE_REAR;
			float haunchPitch = (stalk ? STALK_HAUNCH : 0.0F) - flex * 0.9F - airPitch * 0.5F;
			float haunchTotal = chestPitch + haunchPitch;
			// the body drops (never rises) a little each step, more in a gallop, and squats on landing (less on the move, the legs already bent)
			float bob = amp * ((1.0F - gallop) * WALK_BOB * (0.5F - 0.5F * Mth.cos(4.0F * ph))
				+ gallop * GALLOP_BOB * (0.5F - 0.5F * Mth.cos(2.0F * ph + 1.0F)));
			float squat = m.land() * (stalk ? STALK_LAND_SQUAT : LAND_SQUAT) * (1.0F - LAND_MOVING * amp);
			float spineY = SPINE_Y + (stalk ? STALK_DROP - sw * STALK_RAKE_RISE : 0.0F) + bob + squat;
			float breath = m.breath() * 0.12F * (1.0F - amp);
			this.chest.y = spineY + breath;
			this.chest.xRot = chestPitch;
			this.haunch.xRot = haunchPitch;
			this.haunch.yRot = Mth.sin(ph) * 0.06F * amp * (1.0F - gallop);

			// the body, hidden, lies face-up on the chest so the insignia plate sits on its back
			float plateZ = -INSIGNIA_AT;
			this.body.x = 0.0F;
			this.body.y = spineY + breath - plateZ * Mth.sin(chestPitch);
			this.body.z = plateZ * Mth.cos(chestPitch);
			this.body.xRot = chestPitch - (float)Math.PI * 0.5F;

			// the legs: each ankle follows its own path; two-bone solving keeps every joint attached and the paw on the ground
			for (int i = 0; i < 4; i++) {
				boolean hind = i % 2 == 0;
				float theta = hind ? haunchTotal : chestPitch;
				float hipZ = hind ? HIP_Z : SHOULDER_Z;
				float restZ = (hind ? HIND_REST_Z : SHOULDER_Z) + (stalk ? (hind ? STALK_HIND_Z : STALK_FORE_Z) : 0.0F);
				float phase = Mth.lerp(gallop, WALK_PHASE[i], GALLOP_PHASE[i]) + cycle;
				float[] foot = pawPath(phase - (float)Math.floor(phase), duty, stride * (hind ? HIND_STRIDE : 1.0F), lift);
				float targetZ = restZ + foot[0];
				float targetY = GROUND - foot[1];
				if (air > 0.0F) {
					float reach = Mth.clamp(m.fall(), 0.0F, 1.0F);
					float airZ = hind ? restZ + AIR_HIND_Z : restZ + AIR_FORE_Z;
					float airY = GROUND - (hind ? AIR_HIND_UP : Mth.lerp(reach, AIR_FORE_UP, 1.5F));
					targetZ = Mth.lerp(air, targetZ, airZ);
					targetY = Mth.lerp(air, targetY, airY);
				}
				float hipY = spineY + breath + HIP_DROP * Mth.cos(theta) - hipZ * Mth.sin(theta);
				float hipWorldZ = HIP_DROP * Mth.sin(theta) + hipZ * Mth.cos(theta);
				boolean rake = i == 1 && sw > 0.0F;
				if (rake) {
					// the rake: the right forepaw comes up and forward, the leg stretched out, then drags back down
					float w = Mth.clamp(sw * 3.0F, 0.0F, 1.0F);
					float rakeZ = restZ - RAKE_REACH * sw + RAKE_DRAG * (swing - 0.4F) * sw;
					float rakeY = GROUND - RAKE_UP * sw;
					// swung about the shoulder (angle and reach blended, not the points), so the leg never folds up on the way:
					// folded close, the elbow would dig into the ground (a stalk's low shoulders); it reaches out instead
					float fromAngle = (float)Math.atan2(targetZ - hipWorldZ, targetY - hipY);
					float fromReach = (float)Math.sqrt((targetZ - hipWorldZ) * (targetZ - hipWorldZ) + (targetY - hipY) * (targetY - hipY));
					float toAngle = (float)Math.atan2(rakeZ - hipWorldZ, rakeY - hipY);
					float toReach = Math.max(RAKE_MIN_REACH, (float)Math.sqrt((rakeZ - hipWorldZ) * (rakeZ - hipWorldZ) + (rakeY - hipY) * (rakeY - hipY)));
					float angle = Mth.lerp(w, fromAngle, toAngle);
					float reach = Mth.lerp(w, fromReach, toReach);
					targetZ = hipWorldZ + reach * Mth.sin(angle);
					targetY = Math.min(GROUND, hipY + reach * Mth.cos(angle));
				}
				float a = hind ? HIND_UPPER : FRONT_UPPER;
				float b = hind ? HIND_LOWER : FRONT_LOWER;
				// the hind knee points forward, the fore elbow back
				solve(hipWorldZ, hipY, targetZ, targetY, a, b, hind ? -1.0F : 1.0F, this.angles);
				this.upper[i].xRot = this.angles[0] - theta;
				this.lower[i].xRot = this.angles[1] - this.angles[0];
				// the paw stays level with the ground; the raking one, once off it, tips its pads forward
				float curl = rake ? sw * RAKE_CURL * Mth.clamp((GROUND - targetY) / PAW_DROP, 0.0F, 1.0F) : 0.0F;
				this.paw[i].xRot = -this.angles[1] - curl;
			}

			// the head: looks where the player looks, level with the world whatever the chest does
			float look = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -HEAD_PITCH, HEAD_PITCH);
			float nod = Mth.sin(ph * 2.0F - 0.8F) * 0.05F * amp;
			float headPitch = Mth.clamp(look - chestPitch + (stalk ? STALK_HEAD : 0.0F) + nod - sw * 0.1F, -HEAD_PITCH, HEAD_PITCH);
			if (stalk) {
				// the stalk carries the head low already: looking down any further would put the nose in the ground
				headPitch = Math.min(headPitch, STALK_LOOK_DOWN - chestPitch);
			}
			this.catHead.xRot = headPitch;
			this.catHead.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -HEAD_YAW, HEAD_YAW);
			this.catHead.zRot = m.turn() * 0.1F;
			this.catHead.y -= breath * 0.5F;
			// the ears: flat back when stalking, a flick now and then
			float flick = Mth.sin(age * 0.37F) * Math.max(0.0F, Mth.sin(age * 0.05F + 1.0F)) * 0.25F;
			float back = stalk ? STALK_EARS : 0.0F;
			this.rightEar.xRot = back + flick;
			this.leftEar.xRot = back - flick * 0.7F;
			this.rightEar.zRot += stalk ? -0.25F : 0.0F;
			this.leftEar.zRot += stalk ? 0.25F : 0.0F;

			// the tail: hangs and curls at the tip, trails turns, streams out in a gallop, whips on landing, the tip twitching
			float hang = TAIL_HANG + gallop * amp * TAIL_GALLOP + tailLift(m) * TAIL_FALL + Mth.sin(ph * 2.0F) * 0.05F * amp;
			// never so low the tip digs into the ground (a stalk carries it low already)
			this.tail1.xRot = Math.max(hang, stalk ? TAIL_MIN_STALK : TAIL_MIN);
			float curl = 1.0F - gallop * amp * 0.7F;
			this.tail2.xRot = TAIL_CURL_MID * curl;
			this.tail3.xRot = TAIL_CURL_TIP * curl + Mth.sin(age * 0.11F) * 0.06F;
			float sway = Mth.sin(age * 0.07F) * 0.2F;
			this.tail1.yRot = -m.trail() * 0.5F + sway;
			this.tail2.yRot = -m.trail() * 0.35F + Mth.sin(age * 0.07F - 0.8F) * 0.25F;
			float gate = Math.max(0.0F, Mth.sin(age * 0.043F));
			this.tail3.yRot = Mth.sin(age * 0.9F) * gate * gate * gate * 0.5F + Mth.sin(age * 0.07F - 1.6F) * 0.2F;
		}

		/** 0 below {@code from}, 1 above {@code to}, eased between. */
		private static float smooth(float from, float to, float x) {
			float t = Mth.clamp((x - from) / (to - from), 0.0F, 1.0F);
			return t * t * (3.0F - 2.0F * t);
		}

		/**
		 * An ankle's path against the body for a point in the cycle: on the ground it slides back at a steady pace, then
		 * it swings forward in an arc. Returns {fore-aft offset (+ is back), height off the ground}.
		 */
		private float[] pawPath(float p, float duty, float stride, float lift) {
			if (p < duty) {
				return new float[] {-stride + 2.0F * stride * (p / duty), 0.0F};
			}
			float v = (p - duty) / (1.0F - duty);
			return new float[] {stride * Mth.cos((float)Math.PI * v), lift * Mth.sin((float)Math.PI * v)};
		}

		/**
		 * Two bones from a hip to an ankle in the side plane (z along the body, y down): the thigh's and the shin's angles,
		 * as rotations about x from straight down (+ swings the foot back). {@code bend} is +1 when the middle joint points
		 * back, -1 when it points forward. A target out of reach is stretched to the nearest reachable point.
		 */
		private static void solve(float hipZ, float hipY, float targetZ, float targetY, float upperLength, float lowerLength, float bend, float[] out) {
			float dz = targetZ - hipZ;
			float dy = targetY - hipY;
			float dist = Mth.clamp((float)Math.sqrt(dz * dz + dy * dy), Math.abs(upperLength - lowerLength) + 0.05F, upperLength + lowerLength - 0.01F);
			float toTarget = (float)Math.atan2(dz, dy);
			float cos = Mth.clamp((upperLength * upperLength + dist * dist - lowerLength * lowerLength) / (2.0F * upperLength * dist), -1.0F, 1.0F);
			float t1 = toTarget + bend * (float)Math.acos(cos);
			float kneeZ = hipZ + upperLength * Mth.sin(t1);
			float kneeY = hipY + upperLength * Mth.cos(t1);
			out[0] = t1;
			out[1] = (float)Math.atan2(targetZ - kneeZ, targetY - kneeY);
		}
	}

	/**
	 * (0.18.109, Scan C) Tusk: a flat snout with two curved tusks, and a stiff mane down its back; (0.19.60) a boar on
	 * four legs: a barrel body lying along the ground, short thick legs, a big low head that bobs as it trots, a curly
	 * tail. Its Charge drops the head and gallops; an attack tosses the tusks up. The man's body, head and legs are the
	 * barrel, the head and the front pair (so the pose code finds them), the hind pair new; the arms and hat are hidden.
	 */
	static final class Tusk extends FormModel {
		// ---- tuning ----
		/** The barrel's middle height (model y) and where its rear end pivots (z); it is 10 wide, 16 long, 8 thick. */
		static final float BODY_Y = 13.0F, BODY_Z = 8.0F;
		/** The legs: hip height (the barrel's underside), length below it, how far up into the barrel the box reaches, half its depth, and where they stand. */
		static final float HIP_Y = 17.0F, LEG_LEN = 7.0F, LEG_UP = 3.0F, LEG_HALF_DEPTH = 2.5F;
		static final float LEG_X = 2.8F, FRONT_Z = -5.0F, HIND_Z = 5.0F;
		/** The head's pivot (its back, the neck) in model space. */
		static final float HEAD_Y = 14.0F, HEAD_Z = -6.5F;
		/** The lowest the snout and jaw may reach (model y; the ground is 24), and how far up the head may tip (radians). */
		static final float HEAD_FLOOR = 23.5F, HEAD_UP = -1.2F;
		/** The lowest corners of the snout and of the jaw, below and ahead of the neck (pixels), which {@code HEAD_FLOOR} keeps off the ground. */
		static final float SNOUT_LOW_Y = 3.5F, SNOUT_LOW_Z = 12.5F, JAW_LOW_Y = 4.8F, JAW_LOW_Z = 8.8F;
		/** The trot: how fast the legs go against the walk, how far they swing (radians), how high a foot lifts (pixels). */
		static final float TROT = 0.6662F, TROT_SWING = 0.55F, TROT_LIFT = 1.5F;
		/** The gallop of a charge: the same, the least a charge swings, and how far the hind pair trails the front. */
		static final float GALLOP = 0.6662F * 1.6F, GALLOP_SWING = 0.95F, GALLOP_LIFT = 2.4F, GALLOP_MIN = 0.85F, GALLOP_TRAIL = 2.7F;
		/** The bounce of a step (pixels), the sway of the barrel (yaw, pitch), the head's nod (radians). */
		static final float BOB = 0.5F, SWAY = 0.05F, ROCK = 0.03F, NOD = 0.12F;
		/** How far a sneak drops the barrel (pixels), and how much of a foot's lift is left then (the legs stay put while the barrel sinks, so a lift would poke them through its back). */
		static final float CROUCH_DROP = 2.5F, CROUCH_LIFT = 0.0F;
		/** A charge: the head's pitch (radians, + is down; the tusks then point forward), how far the head is raised so the snout clears the ground (pixels), the barrel's nose-down tilt, how much the mane bristles. */
		static final float CHARGE_HEAD = 0.5F, CHARGE_RAISE = 1.0F, CHARGE_TILT = 0.1F, CHARGE_MANE = 0.35F;
		/** An attack: the head dips (radians) then tosses up, the front rears (radians) with its legs reaching forward (radians), how far the head lunges and lifts (pixels). */
		static final float TOSS_DIP = 0.35F, TOSS_UP = 0.95F, TOSS_REAR = 0.12F, TOSS_REACH = 0.4F, TOSS_LUNGE = 1.0F, TOSS_LIFT = 1.5F;

		private final ModelPart hindRight, hindLeft, mane, tail;

		Tusk() {
			super(bake(mesh()), "tusk");
			// the plate lies on top of the barrel's rear half: the body's front face (-z) faces up once it is laid down
			this.insigniaY = 4.5F;
			this.insigniaZ = -4.0F;
			this.insigniaSize = 1.0F;
			this.hindRight = this.root().getChild("hind_right_leg");
			this.hindLeft = this.root().getChild("hind_left_leg");
			this.mane = this.body.getChild("mane");
			this.tail = this.body.getChild("tail");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the head: the man's 8x8x8 head (his skin's head), grown, pivoting at the back of the neck
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -8.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.8F)),
				PartPose.offset(0.0F, HEAD_Y, HEAD_Z));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(0, 98).addBox(-3.0F, -2.0F, -4.0F, 6.0F, 4.0F, 4.0F),
				PartPose.offset(0.0F, 1.5F, -8.5F));
			for (int i = 0; i < 2; i++) {
				boolean left = i == 1;
				float side = left ? 1.0F : -1.0F;
				PartDefinition tusk = head.addOrReplaceChild(left ? "left_tusk" : "right_tusk",
					CubeListBuilder.create().texOffs(20, 98).mirror(left).addBox(-0.5F, -1.0F, -4.0F, 1.0F, 2.0F, 4.0F),
					PartPose.offsetAndRotation(side * 3.3F, 2.0F, -9.5F, -0.45F, -side * 0.12F, 0.0F));
				tusk.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(30, 98).mirror(left).addBox(-0.5F, -0.5F, -3.0F, 1.0F, 1.0F, 4.0F),
					PartPose.offsetAndRotation(0.0F, 0.0F, -3.5F, -0.7F, 0.0F, 0.0F));
				head.addOrReplaceChild(left ? "left_ear" : "right_ear",
					CubeListBuilder.create().texOffs(40, 98).mirror(left).addBox(-1.0F, -2.0F, -0.5F, 2.0F, 2.0F, 1.0F),
					PartPose.offsetAndRotation(side * 3.6F, -4.6F, -1.5F, -0.2F, 0.0F, side * 0.4F));
			}
			// the barrel: laid along the ground by xRot -90 (its front face is the back, facing up; y is the length, rear to front)
			PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 64).addBox(-5.0F, 0.0F, -4.0F, 10.0F, 16.0F, 8.0F),
				PartPose.offsetAndRotation(0.0F, BODY_Y, BODY_Z, -(float)Math.PI * 0.5F, 0.0F, 0.0F));
			// the mane over the shoulders (the barrel's front half), a ridge standing off its back
			body.addOrReplaceChild("mane", CubeListBuilder.create().texOffs(0, 88).addBox(-1.0F, -3.5F, -3.0F, 2.0F, 7.0F, 3.0F),
				PartPose.offset(0.0F, 12.5F, -3.5F));
			body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(48, 98).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
				PartPose.offsetAndRotation(0.0F, 0.5F, -2.5F, -0.4F, 0.0F, 0.0F));
			// the front legs are the man's legs, the hind pair new; all four share one skin box (the left mirrored)
			root.addOrReplaceChild("right_leg", legBox(false), PartPose.offset(-LEG_X, HIP_Y, FRONT_Z));
			root.addOrReplaceChild("left_leg", legBox(true), PartPose.offset(LEG_X, HIP_Y, FRONT_Z));
			root.addOrReplaceChild("hind_right_leg", legBox(false), PartPose.offset(-LEG_X, HIP_Y, HIND_Z));
			root.addOrReplaceChild("hind_left_leg", legBox(true), PartPose.offset(LEG_X, HIP_Y, HIND_Z));
			return mesh;
		}

		/** A thick leg: a 5x10x5 box from {@code LEG_UP} above the hip to {@code LEG_LEN} below it. */
		private static CubeListBuilder legBox(boolean left) {
			return CubeListBuilder.create().texOffs(0, 106).mirror(left).addBox(-2.5F, -LEG_UP, -LEG_HALF_DEPTH, 5.0F, LEG_UP + LEG_LEN, 2.0F * LEG_HALF_DEPTH);
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.hat.visible = this.rightArm.visible = this.leftArm.visible = false;
			for (ModelPart part : new ModelPart[] {this.head, this.body, this.rightLeg, this.leftLeg, this.hindRight, this.hindLeft, this.mane, this.tail}) {
				part.resetPose();
			}
			XenoMotion.Read m = XenoMotion.read(state);
			boolean charge = "charge".equals(state.getData(XenoformClient.ACT));
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			float amp = charge ? Math.max(speed, GALLOP_MIN) : speed;
			float walk = state.walkAnimationPos * (charge ? GALLOP : TROT);
			float swing = (charge ? GALLOP_SWING : TROT_SWING) * amp;
			float lift = (charge ? GALLOP_LIFT : TROT_LIFT) * amp * (state.isCrouching ? CROUCH_LIFT : 1.0F);
			float crouch = state.isCrouching ? CROUCH_DROP : 0.0F;
			// an attack: the head dips, then the tusks toss up
			float sw = Mth.clamp(state.swingAnimation, 0.0F, 1.0F);
			float toss = tossAngle(sw);
			float tossAmt = Mth.sin(sw * (float)Math.PI);
			// the barrel: bounces twice a stride, sways, and tilts nose-down in a charge and nose-up in a toss
			float bounce = Mth.abs(Mth.cos(walk)) * BOB * amp;
			float yaw = Mth.sin(walk) * SWAY * amp;
			float pitch = (charge ? CHARGE_TILT : 0.0F) - tossAmt * TOSS_REAR + Mth.sin(walk * 2.0F + 0.5F) * ROCK * amp;
			this.body.x = 0.0F;
			this.body.y = BODY_Y - bounce + crouch;
			this.body.z = BODY_Z;
			this.body.xRot = -(float)Math.PI * 0.5F + pitch;
			this.body.yRot = yaw;
			// the legs: a trot has diagonal pairs together; a gallop has the fronts close and the hinds trailing
			float frontOther = walk + (charge ? 0.6F : (float)Math.PI);
			float hind = walk + (charge ? GALLOP_TRAIL : (float)Math.PI);
			float hindOther = walk + (charge ? GALLOP_TRAIL + 0.6F : 0.0F);
			// a toss rears the front: its legs reach forward and rise with the barrel's front
			float rear = tossAmt * TOSS_REACH;
			float rise = (BODY_Z - FRONT_Z) * Mth.sin(tossAmt * TOSS_REAR);
			this.leg(this.rightLeg, walk, -LEG_X, FRONT_Z, swing, lift, yaw, rear, rise);
			this.leg(this.leftLeg, frontOther, LEG_X, FRONT_Z, swing, lift, yaw, rear, rise);
			this.leg(this.hindRight, hind, -LEG_X, HIND_Z, swing, lift, yaw, 0.0F, 0.0F);
			this.leg(this.hindLeft, hindOther, LEG_X, HIND_Z, swing, lift, yaw, 0.0F, 0.0F);
			// the head: low on the neck, follows the look, nods a beat after each step, never digs its snout in
			float headDrop = (BODY_Z - HEAD_Z) * Mth.sin(pitch);
			this.head.x = (HEAD_Z - BODY_Z) * Mth.sin(yaw);
			this.head.y = HEAD_Y - bounce + crouch + headDrop - tossAmt * TOSS_LIFT - (charge ? CHARGE_RAISE : 0.0F);
			this.head.z = HEAD_Z - tossAmt * TOSS_LUNGE;
			float look = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -1.1F, 1.1F);
			float aim = state.xRot * Mth.DEG_TO_RAD;
			this.head.yRot = look;
			float headPitch = (charge ? CHARGE_HEAD + Math.min(aim, 0.0F) * 0.3F : Mth.clamp(aim * 0.6F, -0.8F, 0.35F))
				+ Mth.sin(walk * 2.0F - 0.8F) * NOD * amp * (charge ? 0.4F : 1.0F) + toss;
			float room = HEAD_FLOOR - this.head.y;
			this.head.xRot = Mth.clamp(headPitch, HEAD_UP, Math.min(reach(room, SNOUT_LOW_Y, SNOUT_LOW_Z), reach(room, JAW_LOW_Y, JAW_LOW_Z)));
			// the mane sways and bristles; the tail wags, and lifts when it falls or charges
			this.mane.xRot = Mth.sin(state.ageInTicks * 0.09F) * 0.02F + m.fall() * 0.12F;
			this.mane.zRot = m.trail() * 0.1F;
			this.mane.zScale = 1.0F + (charge ? CHARGE_MANE : 0.0F) + tossAmt * 0.2F;
			this.tail.xRot = (charge ? 0.25F : -0.4F) + tailLift(m);
			this.tail.zRot = Mth.sin(state.ageInTicks * 0.15F) * 0.15F + Mth.sin(walk) * 0.2F * amp + m.trail() * 0.5F;
		}

		/**
		 * One leg: swings about the hip (a rearing front leg reaches forward by {@code rear} and rises {@code rise}),
		 * follows the barrel's sway, drops its hip so the lowest corner of its foot stays on the ground, and lifts on the
		 * way forward.
		 */
		private void leg(ModelPart leg, float phase, float x, float z, float swingAmt, float liftAmt, float yaw, float rear, float rise) {
			float angle = Mth.sin(phase) * swingAmt - rear;
			float up = Math.max(0.0F, -Mth.cos(phase)) * liftAmt;
			leg.x = x + (z - BODY_Z) * Mth.sin(yaw);
			leg.z = z;
			leg.y = HIP_Y + LEG_LEN * (1.0F - Mth.cos(angle)) - LEG_HALF_DEPTH * Mth.abs(Mth.sin(angle)) - up - rise;
			leg.xRot = angle;
		}

		/** The head's angle through an attack (radians, + is down): a dip, then the toss up. */
		private static float tossAngle(float swing) {
			float a = Mth.sin(swing * 2.0F * (float)Math.PI);
			return a > 0.0F ? a * TOSS_DIP : a * TOSS_UP;
		}

		/**
		 * The most the head may pitch down (radians) before a corner {@code below} under and {@code ahead} in front of
		 * the neck sinks past {@code room} pixels below the neck.
		 */
		private static float reach(float room, float below, float ahead) {
			float r = (float)Math.sqrt(below * below + ahead * ahead);
			if (room >= r) {
				return (float)Math.PI * 0.5F;
			}
			return (float)(Math.asin(Math.max(-1.0F, room / r)) - Math.atan2(below, ahead));
		}
	}

	/** (0.18.109, Scan C) Talon: a hooked beak, broad feathered wings down its arms that spread when it lifts them, a tail fan. */
	static final class Talon extends FormModel {
		private final ModelPart rightWing, leftWing;

		Talon() {
			super(bake(mesh()), "talon");
			this.insigniaZ = -1.9F;
			this.rightWing = this.rightArm.getChild("wing");
			this.leftWing = this.leftArm.getChild("wing");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(-0.1F), 0.0F);
			PartDefinition root = mesh.getRoot();
			root.getChild("head").addOrReplaceChild("beak", CubeListBuilder.create().texOffs(0, 64).addBox(-1.0F, -2.0F, -3.0F, 2.0F, 2.0F, 3.0F),
				PartPose.offsetAndRotation(0.0F, -2.5F, -4.0F, 0.25F, 0.0F, 0.0F));
			root.getChild("right_arm").addOrReplaceChild("wing", CubeListBuilder.create().texOffs(12, 64).addBox(-0.5F, -1.0F, 0.0F, 1.0F, 10.0F, 6.0F),
				PartPose.offset(-1.5F, 0.0F, 1.0F));
			root.getChild("left_arm").addOrReplaceChild("wing", CubeListBuilder.create().texOffs(12, 64).mirror().addBox(-0.5F, -1.0F, 0.0F, 1.0F, 10.0F, 6.0F),
				PartPose.offset(1.5F, 0.0F, 1.0F));
			root.getChild("body").addOrReplaceChild("tail_fan", CubeListBuilder.create().texOffs(0, 82).addBox(-3.0F, 0.0F, 0.0F, 6.0F, 1.0F, 5.0F),
				PartPose.offsetAndRotation(0.0F, 11.0F, 1.5F, -0.5F, 0.0F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			// the feathers fan out as the arm lifts away from the side
			float spread = Math.min(1.0F, Math.abs(this.rightArm.zRot) + Math.abs(this.rightArm.xRot) * 0.3F);
			this.rightWing.yRot = -spread * 0.6F;
			this.leftWing.yRot = spread * 0.6F;
		}
	}

	/** (0.18.111, Scan D) Murk: a long soft mantle swept back from its head, four tentacles hanging from its jaw that drift. */
	static final class Murk extends FormModel {
		private final ModelPart[] tentacles = new ModelPart[4];

		Murk() {
			super(bake(mesh()), "murk");
			this.insigniaZ = -2.2F;
			for (int i = 0; i < 4; i++) {
				this.tentacles[i] = this.head.getChild("tentacle_" + i);
			}
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.2F), 0.0F);
			PartDefinition head = mesh.getRoot().getChild("head");
			head.addOrReplaceChild("mantle", CubeListBuilder.create().texOffs(0, 64).addBox(-3.0F, -8.0F, 0.0F, 6.0F, 8.0F, 4.0F),
				PartPose.offsetAndRotation(0.0F, -6.0F, 1.0F, -0.6F, 0.0F, 0.0F));
			for (int i = 0; i < 4; i++) {
				float x = -2.25F + i * 1.5F;
				head.addOrReplaceChild("tentacle_" + i, CubeListBuilder.create().texOffs(20, 64).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 6.0F, 1.0F),
					PartPose.offset(x, 0.0F, -3.0F));
			}
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			for (int i = 0; i < 4; i++) {
				this.tentacles[i].xRot = -0.15F + Mth.sin(state.ageInTicks * 0.12F + i * 1.3F) * 0.15F;
				this.tentacles[i].zRot = Mth.sin(state.ageInTicks * 0.09F + i) * 0.1F;
			}
		}
	}

	/** (0.18.111, Scan D) Gnasher: a blunt snout over a toothed jaw, a tall fin on its back, a heavy tail that sweeps. */
	static final class Gnasher extends FormModel {
		private final ModelPart tail;

		Gnasher() {
			super(bake(mesh()), "gnasher");
			this.insigniaZ = -2.5F;
			this.tail = this.body.getChild("tail");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.5F), 0.0F);
			PartDefinition root = mesh.getRoot();
			root.getChild("head").addOrReplaceChild("snout", CubeListBuilder.create().texOffs(12, 64).addBox(-3.0F, -2.0F, -3.0F, 6.0F, 2.0F, 3.0F),
				PartPose.offset(0.0F, -2.0F, -4.4F));
			PartDefinition body = root.getChild("body");
			body.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(0, 64).addBox(-0.5F, -6.0F, 0.0F, 1.0F, 6.0F, 4.0F),
				PartPose.offsetAndRotation(0.0F, 4.0F, 2.4F, -0.5F, 0.0F, 0.0F));
			body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(30, 64).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 6.0F),
				PartPose.offsetAndRotation(0.0F, 10.5F, 2.0F, -0.4F, 0.0F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.tail.yRot = Mth.sin(state.ageInTicks * 0.2F) * 0.2F + Mth.sin(state.walkAnimationPos * 0.6662F) * 0.3F * state.walkAnimationSpeed;
		}
	}

	/** (0.18.111, Scan D) Lumen: small and slight, two thin wing-fins on its back that flutter. */
	static final class Lumen extends FormModel {
		private final ModelPart rightFin, leftFin;

		Lumen() {
			super(bake(mesh()), "lumen");
			this.insigniaZ = -1.7F;
			this.rightFin = this.body.getChild("right_fin");
			this.leftFin = this.body.getChild("left_fin");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(-0.3F), 0.0F);
			PartDefinition body = mesh.getRoot().getChild("body");
			body.addOrReplaceChild("right_fin", CubeListBuilder.create().texOffs(0, 64).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 5.0F, 4.0F),
				PartPose.offsetAndRotation(-1.5F, 1.0F, 1.8F, 0.0F, -0.6F, 0.0F));
			body.addOrReplaceChild("left_fin", CubeListBuilder.create().texOffs(0, 64).mirror().addBox(-0.5F, 0.0F, 0.0F, 1.0F, 5.0F, 4.0F),
				PartPose.offsetAndRotation(1.5F, 1.0F, 1.8F, 0.0F, 0.6F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float flutter = Mth.sin(state.ageInTicks * 0.9F) * 0.25F;
			this.rightFin.yRot = -0.6F - flutter;
			this.leftFin.yRot = 0.6F + flutter;
		}
	}

	/** (0.18.111, Scan D) Keen: huge ears, and leathery wings down its arms that spread when it lifts them. */
	static final class Keen extends FormModel {
		private final ModelPart rightWing, leftWing;

		Keen() {
			super(bake(mesh()), "keen");
			this.insigniaZ = -1.9F;
			this.rightWing = this.rightArm.getChild("wing");
			this.leftWing = this.leftArm.getChild("wing");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(-0.1F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(0, 64).addBox(-1.0F, -4.0F, -0.5F, 2.0F, 4.0F, 1.0F),
				PartPose.offsetAndRotation(-2.5F, -8.0F, -0.5F, 0.0F, 0.0F, -0.3F));
			head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(0, 64).mirror().addBox(-1.0F, -4.0F, -0.5F, 2.0F, 4.0F, 1.0F),
				PartPose.offsetAndRotation(2.5F, -8.0F, -0.5F, 0.0F, 0.0F, 0.3F));
			root.getChild("right_arm").addOrReplaceChild("wing", CubeListBuilder.create().texOffs(6, 64).addBox(-0.5F, -1.0F, 0.0F, 1.0F, 10.0F, 5.0F),
				PartPose.offset(-1.5F, 0.0F, 1.0F));
			root.getChild("left_arm").addOrReplaceChild("wing", CubeListBuilder.create().texOffs(6, 64).mirror().addBox(-0.5F, -1.0F, 0.0F, 1.0F, 10.0F, 5.0F),
				PartPose.offset(1.5F, 0.0F, 1.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float spread = Math.min(1.0F, Math.abs(this.rightArm.zRot) + Math.abs(this.rightArm.xRot) * 0.3F);
			this.rightWing.yRot = -spread * 0.7F;
			this.leftWing.yRot = spread * 0.7F;
		}
	}

	/** (0.18.113, Scan E) Hive: antennae, a striped abdomen behind, and two clear wings that buzz. */
	static final class Hive extends FormModel {
		private final ModelPart rightWing, leftWing;

		Hive() {
			super(bake(mesh()), "hive");
			this.insigniaZ = -1.8F;
			this.rightWing = this.body.getChild("right_wing");
			this.leftWing = this.body.getChild("left_wing");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(-0.2F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			head.addOrReplaceChild("right_antenna", CubeListBuilder.create().texOffs(0, 64).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
				PartPose.offsetAndRotation(-1.5F, -8.0F, -3.0F, -0.4F, 0.0F, -0.3F));
			head.addOrReplaceChild("left_antenna", CubeListBuilder.create().texOffs(0, 64).mirror().addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
				PartPose.offsetAndRotation(1.5F, -8.0F, -3.0F, -0.4F, 0.0F, 0.3F));
			PartDefinition body = root.getChild("body");
			body.addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(20, 64).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 6.0F),
				PartPose.offsetAndRotation(0.0F, 10.0F, 1.5F, 0.5F, 0.0F, 0.0F));
			body.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(6, 64).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 7.0F, 5.0F),
				PartPose.offsetAndRotation(-1.5F, 1.0F, 1.8F, 0.0F, -0.6F, 0.0F));
			body.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(6, 64).mirror().addBox(-0.5F, 0.0F, 0.0F, 1.0F, 7.0F, 5.0F),
				PartPose.offsetAndRotation(1.5F, 1.0F, 1.8F, 0.0F, 0.6F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float buzz = Mth.sin(state.ageInTicks * 2.6F) * 0.3F;
			this.rightWing.yRot = -0.7F - buzz;
			this.leftWing.yRot = 0.7F + buzz;
		}
	}

	/**
	 * (0.18.113, Scan E; 0.19.60 upright, a spider when prone) Skitter: standing, the shape it always had, a man with
	 * four thin spider legs off its back and a small abdomen behind. Prone (the stance wheel's crawl) it drops into a
	 * spider ({@link SkitterCeiling#CRAWL_KEY}): a small thorax, a head with fangs that turns where it looks, a big round
	 * abdomen behind (the insignia on top) and eight long legs stepping in a spider's alternating fours, the body bobbing
	 * and the abdomen swaying; it crouches flat when sneaking, a bite lunges the head and rears the front legs, and up a
	 * wall (tipped head-up by SkitterCeiling) the legs keep climbing. Standing back up turns it back. In the spider the
	 * man's parts are hidden and the body is laid on the abdomen, so the insignia sits on its back.
	 */
	static final class Skitter extends FormModel {
		// ---- tuning ----
		/** The spider's body height off the ground (model y), how far it drops when sneaking, how much it bobs a step. */
		static final float BODY_Y = 15.0F, CROUCH_DROP = 3.0F, BOB = 0.8F;
		/** How fast the legs step against the walk, how far they swing and lift. */
		static final float GAIT = 0.6662F * 1.4F, SWING = 0.4F, LIFT = 0.45F;
		/** (0.19.60) Up a wall, the legs step this much faster (the walk barely moves while climbing). */
		static final float CLIMB_GAIT = 0.35F;
		/** Rest: each leg pair's yaw (hind to front, the right side's; the left mirrors) and droop. */
		private static final float[] REST_YAW = {0.785F, 0.39F, -0.39F, -0.785F};
		private static final float[] REST_DROOP = {0.785F, 0.58F, 0.58F, 0.785F};
		/** Each pair's step phase (hind to front): the spider's alternating fours. */
		private static final float[] PHASE = {0.0F, (float)Math.PI, (float)Math.PI * 0.5F, (float)Math.PI * 1.5F};
		/** (0.19.60) Past this far into the crawl it's drawn as the spider. */
		static final float SPIDER_AT = 0.5F;

		private final ModelPart thorax, spiderHead, abdomen;
		/** Hind to front: 0-3 right, 4-7 left. */
		private final ModelPart[] legs = new ModelPart[8];
		/** (0.19.60) Upright: the four thin legs off its back, and the small abdomen behind. */
		private final ModelPart[] backLegs = new ModelPart[4];
		private final ModelPart backAbdomen;

		Skitter() {
			super(bake(mesh()), "skitter");
			ModelPart root = this.root();
			this.thorax = root.getChild("thorax");
			this.spiderHead = root.getChild("spider_head");
			this.abdomen = root.getChild("abdomen");
			for (int i = 0; i < 8; i++) {
				this.legs[i] = root.getChild("leg" + i);
			}
			this.backAbdomen = this.body.getChild("back_abdomen");
			for (int i = 0; i < 4; i++) {
				this.backLegs[i] = this.body.getChild("back_leg" + i);
			}
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// upright: the shape it always had, a man with four spider legs off its back and an abdomen behind
			PartDefinition body = root.getChild("body");
			body.addOrReplaceChild("back_abdomen", CubeListBuilder.create().texOffs(20, 64).addBox(-3.5F, -3.0F, 0.0F, 7.0F, 6.0F, 7.0F),
				PartPose.offsetAndRotation(0.0F, 9.0F, 1.5F, 0.3F, 0.0F, 0.0F));
			// legs 0 and 1 on its right, 2 and 3 on its left, the front pair higher
			for (int i = 0; i < 4; i++) {
				boolean left = i >= 2;
				float y = (i % 2 == 0) ? 3.0F : 6.5F;
				CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 64);
				if (left) {
					leg.mirror().addBox(0.0F, -0.5F, -0.5F, 8.0F, 1.0F, 1.0F);
				} else {
					leg.addBox(-8.0F, -0.5F, -0.5F, 8.0F, 1.0F, 1.0F);
				}
				body.addOrReplaceChild("back_leg" + i, leg, PartPose.offsetAndRotation(left ? 3.5F : -3.5F, y, 0.0F, 0.0F, 0.0F, left ? 0.6F : -0.6F));
			}
			// prone: the spider
			root.addOrReplaceChild("thorax", CubeListBuilder.create().texOffs(32, 98).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F),
				PartPose.offset(0.0F, BODY_Y, 0.0F));
			PartDefinition head = root.addOrReplaceChild("spider_head", CubeListBuilder.create().texOffs(0, 98).addBox(-4.0F, -4.0F, -8.0F, 8.0F, 8.0F, 8.0F),
				PartPose.offset(0.0F, BODY_Y, -3.0F));
			head.addOrReplaceChild("right_fang", CubeListBuilder.create().texOffs(44, 78).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F),
				PartPose.offsetAndRotation(-1.5F, 3.5F, -7.0F, -0.3F, 0.0F, 0.15F));
			head.addOrReplaceChild("left_fang", CubeListBuilder.create().texOffs(44, 78).mirror().addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F),
				PartPose.offsetAndRotation(1.5F, 3.5F, -7.0F, -0.3F, 0.0F, -0.15F));
			root.addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(0, 78).addBox(-5.0F, -4.0F, -6.0F, 10.0F, 8.0F, 12.0F),
				PartPose.offset(0.0F, BODY_Y - 1.0F, 9.0F));
			// hind to front along the thorax, right side then left
			for (int i = 0; i < 8; i++) {
				boolean left = i >= 4;
				float z = 2.0F - (i % 4);
				CubeListBuilder leg = left
					? CubeListBuilder.create().texOffs(0, 118).mirror().addBox(-1.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F)
					: CubeListBuilder.create().texOffs(0, 114).addBox(-15.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F);
				root.addOrReplaceChild("leg" + i, leg, PartPose.offset(left ? 4.0F : -4.0F, BODY_Y, z));
			}
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			Float crawl = state.getData(SkitterCeiling.CRAWL_KEY);
			boolean spider = crawl != null && crawl > SPIDER_AT;
			this.thorax.visible = this.spiderHead.visible = this.abdomen.visible = spider;
			for (ModelPart leg : this.legs) {
				leg.visible = spider;
			}
			this.head.visible = this.hat.visible = this.body.visible = !spider;
			this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = !spider;
			if (spider) {
				this.spider(state);
			} else {
				this.upright(state);
			}
		}

		/** Standing: the old upright gait, its back legs stepping as it walks. */
		private void upright(AvatarRenderState state) {
			this.insigniaY = 7.5F;
			this.insigniaZ = -2.0F;
			this.insigniaSize = 1.0F;
			float walk = state.walkAnimationPos * 0.9F;
			float amount = state.walkAnimationSpeed;
			for (int i = 0; i < 4; i++) {
				boolean left = i >= 2;
				float spread = (i % 2 == 0) ? 0.45F : -0.45F;
				float step = Mth.cos(walk + i * (float)Math.PI * 0.5F) * 0.4F * amount;
				this.backLegs[i].yRot = (left ? -spread : spread) + step;
				this.backLegs[i].zRot = (left ? 0.6F : -0.6F) + Mth.abs(Mth.sin(walk + i)) * 0.25F * amount * (left ? -1.0F : 1.0F);
			}
		}

		/** Prone: the spider, on eight legs stepping in alternating fours (on a wall too, see SkitterCeiling). */
		private void spider(AvatarRenderState state) {
			// the insignia on top of the abdomen: the man's (hidden) body lies face-up there
			this.insigniaY = 0.0F;
			this.insigniaZ = -4.0F;
			this.insigniaSize = 1.3F;
			Float climb = state.getData(SkitterCeiling.CLIMB_KEY);
			boolean climbing = climb != null && climb > 0.5F;
			// up a wall the walk hardly moves, so the legs run on the clock instead
			float walk = climbing ? state.ageInTicks * CLIMB_GAIT : state.walkAnimationPos * GAIT;
			float speed = climbing ? 1.0F : Math.min(1.0F, state.walkAnimationSpeed);
			float drop = state.isCrouching && !climbing ? CROUCH_DROP : 0.0F;
			float bob = Mth.abs(Mth.sin(walk)) * BOB * speed;
			float y = BODY_Y + drop - bob;
			// a bite: the head lunges and the fangs open, the front legs rear
			float bite = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
			this.thorax.y = y;
			this.spiderHead.y = y;
			this.spiderHead.z = -3.0F - bite * 2.0F;
			this.spiderHead.yRot = climbing ? 0.0F : state.yRot * Mth.DEG_TO_RAD;
			this.spiderHead.xRot = (climbing ? 0.0F : state.xRot * Mth.DEG_TO_RAD) - bite * 0.3F;
			this.abdomen.y = y - 1.0F + Mth.sin(walk + 0.8F) * 0.4F * speed;
			this.abdomen.yRot = Mth.sin(walk * 0.5F) * 0.12F * speed;
			this.abdomen.xRot = -0.1F + Mth.sin(state.ageInTicks * 0.1F) * 0.03F;
			float breathe = 1.0F + Mth.sin(state.ageInTicks * 0.1F) * 0.02F;
			this.abdomen.xScale = this.abdomen.yScale = this.abdomen.zScale = breathe;
			for (int i = 0; i < 8; i++) {
				int pair = i % 4;
				float side = i >= 4 ? -1.0F : 1.0F;
				ModelPart leg = this.legs[i];
				leg.y = y;
				// the right side's step; the left's runs half a cycle out (alternating fours), mirrored
				float phase = PHASE[pair] + (i >= 4 ? (float)Math.PI : 0.0F);
				float swing = -Mth.cos(walk * 2.0F + phase) * SWING * speed;
				float lift = Mth.abs(Mth.sin(walk + phase)) * LIFT * speed;
				// idle: the odd leg twitches
				float twitch = Mth.sin(state.ageInTicks * 0.13F + i * 1.9F) * 0.03F;
				leg.yRot = side * (REST_YAW[pair] + swing + twitch);
				leg.zRot = side * (-REST_DROOP[pair] + lift + (drop > 0.0F ? 0.35F : 0.0F));
				if (pair == 3) {
					leg.zRot += side * bite * 0.6F;
				}
			}
			this.body.x = 0.0F;
			this.body.y = this.abdomen.y;
			this.body.z = 9.0F;
			this.body.xRot = -(float)Math.PI * 0.5F;
			this.body.yRot = this.body.zRot = 0.0F;
		}
	}

	/** (0.18.113, Scan E) Shade: very tall and thin; nothing but the body, its eyes do the rest. */
	static final class Shade extends FormModel {
		Shade() {
			super(bake(HumanoidModel.createMesh(new CubeDeformation(-0.4F), 0.0F)), "shade");
			this.insigniaZ = -1.6F;
		}
	}

	/** (0.18.113, Scan E) Mirage: a deep hood over the head and a robe skirt from the waist. */
	static final class Mirage extends FormModel {
		Mirage() {
			super(bake(mesh()), "mirage");
			this.insigniaZ = -1.9F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(-0.1F), 0.0F);
			PartDefinition root = mesh.getRoot();
			root.getChild("head").addOrReplaceChild("hood", CubeListBuilder.create().texOffs(0, 64).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F,
				new CubeDeformation(0.6F)), PartPose.ZERO);
			root.getChild("body").addOrReplaceChild("robe", CubeListBuilder.create().texOffs(0, 82).addBox(-4.0F, 12.0F, -2.0F, 8.0F, 8.0F, 4.0F,
				new CubeDeformation(0.5F)), PartPose.ZERO);
			return mesh;
		}
	}

	/** (0.18.117, Scan F) Ooze (and Magma Ooze): a soft, swollen body with a blob on its head that wobbles as it moves. */
	static final class Ooze extends FormModel {
		private final ModelPart blob;

		Ooze(String id) {
			super(bake(mesh()), id);
			this.insigniaZ = -2.5F;
			this.blob = this.head.getChild("blob");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.5F), 0.0F);
			mesh.getRoot().getChild("head").addOrReplaceChild("blob", CubeListBuilder.create().texOffs(0, 64).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 3.0F, 6.0F),
				PartPose.offset(0.0F, -8.5F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float wobble = Mth.sin(state.ageInTicks * 0.3F + state.walkAnimationPos * 0.8F);
			this.blob.y = -8.5F + wobble * 0.4F;
			this.blob.zRot = wobble * 0.08F;
		}
	}

	/**
	 * (0.18.117, Scan F; 0.19.34 four-legged) Fuse: a creeper's build, no arms: a blocky head on a tall narrow body
	 * on four stubby legs. It moves its own way: quick shuffling steps, diagonal legs together, with a side-to-side
	 * waddle and a bounce, the head nodding a beat behind; when it punches (every punch pops) it swells up for a moment.
	 */
	static final class Fuse extends FormModel {
		// ---- tuning ----
		/** How fast its little legs shuffle against the walk, and how far they swing. */
		static final float SHUFFLE = 0.6662F * 1.6F, STEP = 1.2F;
		/** The waddle (roll), the bounce (pixels) and the head's nod. */
		static final float WADDLE = 0.12F, BOUNCE = 0.7F, NOD = 0.1F;
		/** How much it swells on a punch. */
		static final float PUNCH_SWELL = 0.18F;

		private final ModelPart hindRight, hindLeft;

		Fuse() {
			super(bake(mesh()), "fuse");
			this.insigniaZ = -2.0F;
			this.hindRight = this.root().getChild("hind_right_leg");
			this.hindLeft = this.root().getChild("hind_left_leg");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
				PartPose.offset(0.0F, 6.0F, 0.0F));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F),
				PartPose.offset(0.0F, 6.0F, 0.0F));
			// the front legs are the man's legs (so the pose code still finds them), the hind pair new
			root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F),
				PartPose.offset(-2.0F, 18.0F, -4.0F));
			root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F),
				PartPose.offset(2.0F, 18.0F, -4.0F));
			root.addOrReplaceChild("hind_right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F),
				PartPose.offset(-2.0F, 18.0F, 4.0F));
			root.addOrReplaceChild("hind_left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F),
				PartPose.offset(2.0F, 18.0F, 4.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.hat.visible = this.rightArm.visible = this.leftArm.visible = false;
			for (ModelPart part : new ModelPart[] {this.head, this.body, this.rightLeg, this.leftLeg, this.hindRight, this.hindLeft}) {
				part.resetPose();
			}
			float walk = state.walkAnimationPos * SHUFFLE;
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			float step = Mth.cos(walk) * STEP * speed;
			// diagonal pairs together: front right with hind left
			this.rightLeg.xRot = step;
			this.hindLeft.xRot = step;
			this.leftLeg.xRot = -step;
			this.hindRight.xRot = -step;
			float waddle = Mth.sin(walk) * WADDLE * speed;
			float bounce = Mth.abs(Mth.cos(walk)) * BOUNCE * speed;
			float crouch = state.isCrouching ? 2.0F : 0.0F;
			this.body.y = 6.0F - bounce + crouch;
			this.body.zRot = waddle;
			this.head.y = 6.0F - bounce + crouch;
			this.head.zRot = waddle * 1.4F;
			this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
			// the nod comes a beat after the step
			this.head.xRot = state.xRot * Mth.DEG_TO_RAD + Mth.sin(walk * 2.0F - 0.8F) * NOD * speed;
			float swell = 1.0F + Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI) * PUNCH_SWELL;
			for (ModelPart part : new ModelPart[] {this.head, this.body}) {
				part.xScale = part.yScale = part.zScale = swell;
			}
		}
	}

	/** (0.18.117, Scan F) Thornwild: a crown of twigs, and thorns along its arms. */
	static final class Thornwild extends FormModel {
		Thornwild() {
			super(bake(mesh()), "thornwild");
			this.insigniaZ = -2.2F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.2F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			float[][] twigs = {{-3.0F, -0.4F, -0.3F}, {0.0F, -0.2F, 0.0F}, {3.0F, -0.4F, 0.3F}};
			for (int i = 0; i < twigs.length; i++) {
				head.addOrReplaceChild("twig" + i, CubeListBuilder.create().texOffs(0, 64).addBox(-0.5F, -5.0F, -0.5F, 1.0F, 5.0F, 1.0F),
					PartPose.offsetAndRotation(twigs[i][0], -8.0F, 1.0F, twigs[i][1], 0.0F, twigs[i][2]));
			}
			for (String arm : new String[] {"right_arm", "left_arm"}) {
				PartDefinition limb = root.getChild(arm);
				float side = arm.startsWith("right") ? -1.0F : 1.0F;
				for (int i = 0; i < 3; i++) {
					limb.addOrReplaceChild("thorn" + i, CubeListBuilder.create().texOffs(6, 64).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 2.0F),
						PartPose.offsetAndRotation(side * (1.0F + 0.3F * i), 1.0F + i * 3.0F, 2.0F, 0.4F, side * 0.3F, 0.0F));
				}
			}
			return mesh;
		}
	}

	/**
	 * (0.18.117, Scan F; 0.19.34 a real cloud) Billow: a cloud, not a man: a broad soft mass low over the ground with
	 * its calm face in a puff at the front (it turns where it looks), and heaps of smaller puffs churning on top and
	 * swelling out of its sides, each breathing and drifting on its own. Moving, it leans into the way it goes and its
	 * top trails behind. Its box is the swimming pose's (it seeps through gaps like smoke: {@code BillowCloudMixins}),
	 * and its render state is turned back upright ({@code XenoformClient.extract}). The humanoid parts stay, hidden:
	 * the body is the mass and the head the face puff.
	 */
	static final class Billow extends FormModel {
		// ---- tuning ----
		/** How far the puffs bob and breathe, how fast they churn, and how far the top trails when it moves. */
		static final float BOB = 0.7F, BREATHE = 0.07F, CHURN = 0.06F, TRAIL = 2.0F, LEAN = 0.12F;
		/** The puffs: where each sits (x, y, z) and which box it is (0 big, 1 small). */
		private static final float[][] PUFFS = {
			{-5.5F, 12.5F, 2.0F, 0}, {5.5F, 12.0F, 3.0F, 0}, {0.0F, 11.5F, 6.0F, 1}, {-10.0F, 18.0F, 0.0F, 1}, {10.0F, 18.5F, -1.0F, 1},
			{0.0F, 13.0F, -2.0F, 1}};

		private final ModelPart[] puffs = new ModelPart[PUFFS.length];

		Billow() {
			super(bake(mesh()), "billow");
			for (int i = 0; i < PUFFS.length; i++) {
				this.puffs[i] = this.root().getChild("puff" + i);
			}
			// on the front of the mass, under the face
			this.insigniaY = 1.0F;
			this.insigniaZ = -9.0F;
			this.insigniaSize = 0.9F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F,
				new CubeDeformation(1.5F)), PartPose.offset(0.0F, 15.0F, -4.0F));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 64).addBox(-9.0F, -3.5F, -7.0F, 18.0F, 7.0F, 14.0F,
				new CubeDeformation(2.0F)), PartPose.offset(0.0F, 18.0F, 0.0F));
			for (int i = 0; i < PUFFS.length; i++) {
				float[] p = PUFFS[i];
				CubeListBuilder puff = p[3] == 0
					? CubeListBuilder.create().texOffs(0, 86).addBox(-5.0F, -4.0F, -5.0F, 10.0F, 8.0F, 10.0F, new CubeDeformation(1.0F))
					: CubeListBuilder.create().texOffs(0, 104).addBox(-4.0F, -3.0F, -4.0F, 8.0F, 6.0F, 8.0F, new CubeDeformation(1.0F));
				root.addOrReplaceChild("puff" + i, puff, PartPose.offset(p[0], p[1], p[2]));
			}
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.hat.visible = this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
			this.head.resetPose();
			this.body.resetPose();
			float age = state.ageInTicks;
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			// it leans into the way it goes, the whole mass rolling slowly as it hangs
			this.body.xRot = speed * LEAN;
			this.body.zRot = Mth.sin(age * 0.03F) * 0.03F;
			this.body.y = 18.0F + Mth.sin(age * 0.05F) * 0.4F;
			float big = 1.0F + Mth.sin(age * 0.04F) * BREATHE * 0.5F;
			this.body.xScale = this.body.zScale = big;
			this.head.y = 15.0F + Mth.sin(age * 0.05F + 0.6F) * 0.5F;
			this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
			this.head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.5F;
			for (int i = 0; i < PUFFS.length; i++) {
				float[] p = PUFFS[i];
				ModelPart puff = this.puffs[i];
				float t = age * CHURN + i * 1.7F;
				// the top ones trail behind as it moves
				float trail = p[1] < 15.0F ? speed * TRAIL : speed * TRAIL * 0.4F;
				puff.x = p[0] + Mth.sin(t * 0.7F) * 0.5F;
				puff.y = p[1] + Mth.sin(t) * BOB;
				puff.z = p[2] + trail + Mth.cos(t * 0.8F) * 0.4F;
				puff.yRot = Mth.sin(t * 0.3F) * 0.25F;
				float swell = 1.0F + Mth.sin(t * 1.3F) * BREATHE;
				puff.xScale = puff.yScale = puff.zScale = swell;
			}
		}
	}

	/**
	 * (0.18.119, Scan G) Towerback: a hulk of stone, a heavy brow, mossy boulders for pauldrons, and a broken watchtower
	 * grown out of its back, its battlements on top. It plays its grab
	 * ({@link com.jaymospowers.power.xenoform.forms.Towerback#grabPhase}): the right hand rears back, comes down, holds its
	 * catch at its chest and hurls it into the ground.
	 * <p>
	 * (0.19.28) Andy: "long exaggerated steps and motions while still covering alot of ground", and "the backhand should be
	 * slow too, like a charged ability". Its legs follow its own stride over the ground ({@link XenoformClient#TOWER_STRIDE},
	 * a step every {@code Towerback#STRIDE_PER_SCALE} blocks per scale) instead of vanilla's walk, swung wide and lifted;
	 * it sinks onto each footfall so its feet stay planted, its weight rolls over the planted foot, its shoulders turn and
	 * its arms swing big against its legs. The swat ({@link XenoformClient#TOWER_SWAT}) draws the right arm across its
	 * chest and coils, trembling, then whips it out wide.
	 */
	static final class Towerback extends FormModel {
		/**
		 * (0.19.28) Its walk (radians, pixels): how far the legs swing (wide of what the stride needs, so it reads as
		 * striding), how high the foot coming forward lifts, its legs' length (for how far it sinks onto a footfall), and
		 * an extra drop as the foot lands; its arms' swing and flare, its roll over the planted foot, its shoulders' turn,
		 * its lean into the walk.
		 */
		static final float LEG_SWING = 0.85F, FOOT_LIFT = 2.5F, LEG_LENGTH = 12.0F, LAND_DROP = 1.2F;
		static final float ARM_SWING = 0.9F, ARM_FLARE = 0.12F, ROLL = 0.11F, SHOULDER_TURN = 0.22F, LEAN = 0.12F;
		/** (0.19.28) The swat's arm (radians): drawn across the chest and coiled, then flung out wide to its right; how it turns its shoulders and trembles. */
		static final float SWAT_RAISE = -1.45F, SWAT_ACROSS = -1.15F, SWAT_OUT = 1.35F, SWAT_COIL = -0.5F, SWAT_UNCOIL = 0.45F, SWAT_TREMBLE = 0.05F;
		/** The grab's arm (radians, x): reared back, reaching down, held at the chest, slammed down. */
		static final float REAR = -2.9F, REACH = -1.1F, HOLD_UP = -1.45F, SLAMMED = -0.35F;
		private final ModelPart tower;

		Towerback() {
			super(bake(mesh()), "towerback");
			this.insigniaZ = -2.8F;
			this.tower = this.body.getChild("tower");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.6F), 0.0F);
			PartDefinition root = mesh.getRoot();
			root.getChild("head").addOrReplaceChild("brow", CubeListBuilder.create().texOffs(0, 64).addBox(-5.0F, -9.0F, -5.5F, 10.0F, 2.0F, 3.0F),
				PartPose.ZERO);
			root.getChild("right_arm").addOrReplaceChild("boulder", CubeListBuilder.create().texOffs(28, 64).addBox(-4.5F, -3.5F, -2.5F, 5.0F, 4.0F, 5.0F,
				new CubeDeformation(0.5F)), PartPose.ZERO);
			root.getChild("left_arm").addOrReplaceChild("boulder", CubeListBuilder.create().texOffs(28, 64).mirror().addBox(-0.5F, -3.5F, -2.5F, 5.0F, 4.0F,
				5.0F, new CubeDeformation(0.5F)), PartPose.ZERO);
			PartDefinition tower = root.getChild("body").addOrReplaceChild("tower", CubeListBuilder.create().texOffs(0, 74).addBox(-3.0F, -5.0F, 0.0F, 6.0F,
				11.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 2.4F, 0.08F, 0.0F, 0.06F));
			float[][] merlons = {{-3.0F, 0.0F}, {1.0F, 0.0F}, {-3.0F, 3.0F}, {1.0F, 3.0F}};
			for (int i = 0; i < merlons.length; i++) {
				tower.addOrReplaceChild("merlon" + i, CubeListBuilder.create().texOffs(24, 74).addBox(0.0F, -2.0F, 0.0F, 2.0F, 2.0F, 2.0F),
					PartPose.offset(merlons[i][0], -5.0F, merlons[i][1]));
			}
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			Float stride = state.getData(XenoformClient.TOWER_STRIDE);
			float speed = state.walkAnimationSpeed;
			if (stride != null && !state.isPassenger) {
				// its own walk replaces vanilla's quick swing
				state.walkAnimationSpeed = 0.0F;
				super.setupAnim(state);
				state.walkAnimationSpeed = speed;
				walk(stride, Math.min(1.0F, speed * 1.3F));
			} else {
				super.setupAnim(state);
			}
			float breath = Mth.sin(state.ageInTicks * 0.05F);
			this.tower.xRot = 0.08F + breath * 0.02F;
			Float swat = state.getData(XenoformClient.TOWER_SWAT);
			if (swat != null && swat >= 0.0F) {
				swat(swat);
			}
			Float phase = state.getData(XenoformClient.TOWER_GRAB);
			if (phase == null || phase < 0.0F) {
				return;
			}
			int windup = com.jaymospowers.power.xenoform.forms.Towerback.WINDUP, snatch = com.jaymospowers.power.xenoform.forms.Towerback.SNATCH;
			int hold = com.jaymospowers.power.xenoform.forms.Towerback.HOLD, slam = com.jaymospowers.power.xenoform.forms.Towerback.SLAM;
			int recover = com.jaymospowers.power.xenoform.forms.Towerback.RECOVER;
			float t = phase, arm;
			float weight = 1.0F;
			if (t < windup) {
				arm = Mth.lerp(ease(t / windup), this.rightArm.xRot, REAR);
			} else if ((t -= windup) < snatch) {
				arm = Mth.lerp(ease(t / snatch), REAR, REACH);
			} else if ((t -= snatch) < hold) {
				arm = Mth.lerp(ease(Math.min(1.0F, t / 10.0F)), REACH, HOLD_UP) + Mth.sin(t * 0.8F) * 0.04F;
			} else if ((t -= hold) < slam) {
				arm = Mth.lerp(ease(t / slam), HOLD_UP, SLAMMED);
			} else {
				t -= slam;
				arm = SLAMMED;
				weight = 1.0F - ease(Math.min(1.0F, t / recover));
			}
			this.rightArm.xRot = Mth.lerp(weight, this.rightArm.xRot, arm);
			this.rightArm.zRot = Mth.lerp(weight, this.rightArm.zRot, 0.15F);
			// it leans into the throw
			this.body.xRot = Mth.lerp(weight, this.body.xRot, arm < -2.0F ? -0.15F : 0.2F);
		}

		/**
		 * (0.19.28) One step of its walk: {@code stride} in radians (a foot down at each half turn past a quarter),
		 * {@code amount} how far into walking it is (0 standing).
		 */
		private void walk(float stride, float amount) {
			if (amount <= 0.001F) {
				return;
			}
			float swing = Mth.sin(stride), forward = Mth.cos(stride);
			// legs: wide swings, the foot coming forward lifted clear of the ground
			this.rightLeg.xRot = swing * LEG_SWING * amount;
			this.leftLeg.xRot = -swing * LEG_SWING * amount;
			this.rightLeg.y -= Math.max(0.0F, -forward) * FOOT_LIFT * amount;
			this.leftLeg.y -= Math.max(0.0F, forward) * FOOT_LIFT * amount;
			// it sinks onto each footfall (a swung leg reaches less far down), and a little more as the foot lands
			float sink = LEG_LENGTH * (1.0F - Mth.cos(this.rightLeg.xRot)) + Mth.abs(swing) * Mth.abs(swing) * Mth.abs(swing) * LAND_DROP * amount;
			for (ModelPart part : new ModelPart[] {this.head, this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg}) {
				part.y += sink;
			}
			// its weight rolls over the planted foot; its head keeps level; it leans into the walk
			this.body.zRot += forward * ROLL * amount;
			this.head.zRot -= forward * ROLL * 0.6F * amount;
			this.body.xRot += LEAN * amount;
			this.head.xRot -= LEAN * 0.5F * amount;
			// its shoulders turn with the arm swinging forward, and the arms swing big against the legs, flared out
			turn(-swing * SHOULDER_TURN * amount);
			this.rightArm.xRot += -swing * ARM_SWING * amount;
			this.leftArm.xRot += swing * ARM_SWING * amount;
			this.rightArm.zRot += ARM_FLARE * amount;
			this.leftArm.zRot -= ARM_FLARE * amount;
		}

		/** Its shoulders turned by {@code yaw} (radians): the body turns and the arms go round with it, as vanilla's swing does. */
		private void turn(float yaw) {
			this.body.yRot += yaw;
			float total = this.body.yRot;
			this.rightArm.z = Mth.sin(total) * 5.0F;
			this.rightArm.x = -Mth.cos(total) * 5.0F;
			this.leftArm.z = -Mth.sin(total) * 5.0F;
			this.leftArm.x = Mth.cos(total) * 5.0F;
			this.rightArm.yRot += yaw;
			this.leftArm.yRot += yaw;
		}

		/**
		 * (0.19.28) The swat, {@code t} ticks in: the right arm drawn across the chest as the shoulders coil, trembling
		 * as it builds; then whipped out wide as they uncoil (the blow lands halfway); then easing back.
		 */
		private void swat(float t) {
			int windup = com.jaymospowers.power.xenoform.forms.Towerback.SWAT_WINDUP;
			int strike = com.jaymospowers.power.xenoform.forms.Towerback.SWAT_STRIKE;
			int recover = com.jaymospowers.power.xenoform.forms.Towerback.SWAT_RECOVER;
			float across, coil, weight = 1.0F, tremble = 0.0F;
			if (t < windup) {
				float k = ease(t / windup);
				weight = k;
				across = SWAT_ACROSS;
				coil = SWAT_COIL;
				tremble = Mth.sin(t * 2.3F) * SWAT_TREMBLE * k;
			} else if ((t -= windup) < strike) {
				// fast out of the coil: the swing itself is quick, after the long draw
				float k = t / strike;
				k = k * k;
				across = Mth.lerp(k, SWAT_ACROSS, SWAT_OUT);
				coil = Mth.lerp(k, SWAT_COIL, SWAT_UNCOIL);
			} else {
				t -= strike;
				across = SWAT_OUT;
				coil = SWAT_UNCOIL;
				weight = 1.0F - ease(t / recover);
			}
			float bodyTurn = Mth.lerp(weight, this.body.yRot, coil) - this.body.yRot;
			turn(bodyTurn);
			this.rightArm.xRot = Mth.lerp(weight, this.rightArm.xRot, SWAT_RAISE + tremble);
			this.rightArm.yRot = Mth.lerp(weight, this.rightArm.yRot, across + this.body.yRot);
			this.rightArm.zRot = Mth.lerp(weight, this.rightArm.zRot, 0.0F);
			this.body.xRot = Mth.lerp(weight, this.body.xRot, 0.15F);
		}

		private static float ease(float x) {
			x = Mth.clamp(x, 0.0F, 1.0F);
			return x * x * (3.0F - 2.0F * x);
		}
	}

	/** Bulwark: thick-set, a heavy brow, pauldrons, and a second pair of arms under the first. */
	static final class Bulwark extends FormModel {
		private final ModelPart rightLowerArm, leftLowerArm;

		Bulwark() {
			super(bake(mesh()), "bulwark");
			this.insigniaZ = -2.9F;
			this.rightLowerArm = this.body.getChild("right_lower_arm");
			this.leftLowerArm = this.body.getChild("left_lower_arm");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.9F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			head.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(0, 64).addBox(-5.0F, -10.0F, -6.0F, 10.0F, 3.0F, 3.0F), PartPose.ZERO);
			PartDefinition body = root.getChild("body");
			body.addOrReplaceChild("plates", CubeListBuilder.create().texOffs(0, 72).addBox(-6.0F, -1.0F, -3.0F, 12.0F, 4.0F, 7.0F, new CubeDeformation(0.9F)),
				PartPose.ZERO);
			body.addOrReplaceChild("right_lower_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.6F)),
				PartPose.offset(-5.5F, 8.0F, 0.0F));
			body.addOrReplaceChild("left_lower_arm", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.6F)),
				PartPose.offset(5.5F, 8.0F, 0.0F));
			// pauldrons on the upper arms
			root.getChild("right_arm").addOrReplaceChild("pauldron", CubeListBuilder.create().texOffs(40, 64).addBox(-4.0F, -3.5F, -2.5F, 5.0F, 3.0F, 5.0F, new CubeDeformation(0.9F)),
				PartPose.ZERO);
			root.getChild("left_arm").addOrReplaceChild("pauldron", CubeListBuilder.create().texOffs(40, 64).mirror().addBox(-1.0F, -3.5F, -2.5F, 5.0F, 3.0F, 5.0F, new CubeDeformation(0.9F)),
				PartPose.ZERO);
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			XenoMotion.Read m = XenoMotion.read(state);
			// the lower arms swing the opposite way, half as far, and hang a little out; they rise and fall with its
			// breath, swing out behind a turn, lift as it falls and jolt down on landing
			float jolt = m.land() * 0.5F - Math.max(0.0F, m.fall()) * 0.35F * m.air();
			this.rightLowerArm.xRot = -this.rightArm.xRot * 0.5F;
			this.leftLowerArm.xRot = -this.leftArm.xRot * 0.5F;
			this.rightLowerArm.zRot = 0.25F + m.breath() * 0.04F + m.trail() * 0.2F - jolt;
			this.leftLowerArm.zRot = -0.25F - m.breath() * 0.04F + m.trail() * 0.2F + jolt;
		}
	}

	/**
	 * Pyre: a body of living flame, hair of fire streaming up and back off the head, flames licking off the shoulders
	 * and forearms, all swaying; its skin is drawn a second time, fullbright, from {@link #GLOW} so it shines in the dark.
	 */
	static final class Pyre extends FormModel {
		static final Identifier GLOW = JaymosPowers.id("textures/entity/xenoform_pyre_glow.png");
		private final ModelPart hair, hairTip, rightPlume, leftPlume, rightLick, leftLick;

		Pyre() {
			super(bake(mesh()), "pyre");
			this.insigniaY = 4.5F;
			this.insigniaZ = -3.0F;
			this.insigniaSize = 0.8F;
			this.hair = this.head.getChild("hair");
			this.hairTip = this.hair.getChild("tip");
			this.rightPlume = this.body.getChild("right_plume");
			this.leftPlume = this.body.getChild("left_plume");
			this.rightLick = this.rightArm.getChild("lick");
			this.leftLick = this.leftArm.getChild("lick");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.3F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			// a ring of flame round the top of the head, and the hair rising out of it, a thinner tongue at its tip
			head.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(0, 64).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 2.0F, 8.0F, new CubeDeformation(0.4F)),
				PartPose.ZERO);
			PartDefinition hair = head.addOrReplaceChild("hair", CubeListBuilder.create().texOffs(32, 64).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 8.0F, 4.0F),
				PartPose.offset(0.0F, -7.0F, 1.0F));
			hair.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(48, 64).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F),
				PartPose.offset(0.0F, -7.0F, 0.0F));
			PartDefinition body = root.getChild("body");
			body.addOrReplaceChild("right_plume", CubeListBuilder.create().texOffs(0, 78).addBox(-2.0F, -5.0F, -1.5F, 3.0F, 6.0F, 3.0F),
				PartPose.offset(-6.0F, 0.0F, 0.0F));
			body.addOrReplaceChild("left_plume", CubeListBuilder.create().texOffs(0, 78).mirror().addBox(-1.0F, -5.0F, -1.5F, 3.0F, 6.0F, 3.0F),
				PartPose.offset(6.0F, 0.0F, 0.0F));
			body.addOrReplaceChild("core", CubeListBuilder.create().texOffs(12, 78).addBox(-2.5F, 2.0F, -3.0F, 5.0F, 5.0F, 1.0F), PartPose.ZERO);
			// a lick of flame off the outside of each forearm
			root.getChild("right_arm").addOrReplaceChild("lick", CubeListBuilder.create().texOffs(24, 78).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F),
				PartPose.offset(-3.0F, 6.0F, 0.5F));
			root.getChild("left_arm").addOrReplaceChild("lick", CubeListBuilder.create().texOffs(24, 78).mirror().addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F),
				PartPose.offset(3.0F, 6.0F, 0.5F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float t = state.ageInTicks;
			XenoMotion.Read m = XenoMotion.read(state);
			// the flames flicker faster and stream back the faster it goes, lean out of a turn, and leap up as it falls
			float flicker = t * (1.0F + m.speed() * 0.8F);
			float stream = m.speed() * 0.45F + Math.max(0.0F, m.fall()) * 0.4F;
			this.hair.xRot = Mth.cos(flicker * 0.27F) * 0.08F - 0.35F - stream;
			this.hair.zRot = Mth.sin(flicker * 0.35F) * 0.12F - m.trail() * 0.25F;
			this.hairTip.xRot = Mth.cos(flicker * 0.41F + 0.7F) * 0.15F - 0.3F - stream * 0.8F;
			this.hairTip.zRot = Mth.sin(flicker * 0.53F + 1.3F) * 0.2F - m.trail() * 0.3F;
			this.rightPlume.zRot = 0.3F + Mth.sin(flicker * 0.4F) * 0.15F - m.trail() * 0.2F;
			this.leftPlume.zRot = -0.3F - Mth.sin(flicker * 0.4F + 1.0F) * 0.15F - m.trail() * 0.2F;
			this.rightPlume.xRot = -stream;
			this.leftPlume.xRot = -stream;
			// the forearm flames point up whatever the arm does (cancelling its swing) and stream back
			this.rightLick.xRot = -this.rightArm.xRot - stream + Mth.sin(flicker * 0.47F) * 0.15F;
			this.leftLick.xRot = -this.leftArm.xRot - stream + Mth.sin(flicker * 0.47F + 2.0F) * 0.15F;
			this.rightLick.zRot = 0.25F + Mth.cos(flicker * 0.38F) * 0.12F;
			this.leftLick.zRot = -0.25F - Mth.cos(flicker * 0.38F + 0.5F) * 0.12F;
		}

		/** The forearm flames upright for the first-person arm (the model is shared, so they hold whoever was drawn last). */
		void handPose() {
			this.rightLick.xRot = 0.0F;
			this.leftLick.xRot = 0.0F;
			this.rightLick.zRot = 0.25F;
			this.leftLick.zRot = -0.25F;
		}
	}

	/**
	 * (0.19.60 a raptor) Velox: slight, a swept-back crest, a visor that drops over the eyes at speed, a short snout and
	 * a long tail. A bird-legged biped leaning forward, its body near horizontal on long digitigrade legs (thigh, shin,
	 * tarsus, toes: the ankle bends backward, the thigh and tarsus kept parallel so the knee never rises into the body)
	 * that stride long and fast, tucked clawed forearms that slash, a four-part tail held out to balance it, a long
	 * head on an S-neck with a jaw that snaps; the visor still slides down over its eyes as it speeds up. At full run
	 * the body lowers, leans and stretches, the neck reaches and the tail straightens. The man's body is hidden and
	 * lies face-up on the torso so the insignia sits on its back.
	 */
	static final class Velox extends FormModel {
		// ---- tuning ----
		/** The hip's height off the ground (model y) at rest, how far it drops at full speed, when sneaking, and on a landing. */
		static final float HIP_Y = 8.0F, RUN_DROP = 1.0F, CROUCH_DROP = 2.6F, LAND_DROP = 2.0F;
		/** How far apart the hips are from the middle, and how far the feet stand from the hip's line when still (front/back). */
		static final float HIP_X = 2.5F, FOOT_Z = -1.0F, IDLE_STAGGER = 0.8F;
		/** Leg segments (pixels): thigh (hip to knee), shin (knee to ankle), tarsus (ankle to toes). */
		static final float THIGH = 5.5F, SHIN = 7.0F, TARSUS = 5.0F;
		/** The foot's pivot is this far below its top; its toes reach this far forward of it (the lowest point of a pitched foot). */
		static final float TOE_REACH = 4.0F;
		/** The gait: steps per unit of the walk (a full stride cycle), the stride's half-length and the swing's lift at full speed (pixels). */
		static final float GAIT = 0.2F, MAX_STRIDE = 5.2F, MAX_LIFT = 2.4F;
		/** The share of a stride a foot is on the ground: walking, and flat out (under half is a flight). */
		static final float DUTY_WALK = 0.62F, DUTY_RUN = 0.42F;
		/** How far the hip bounces over a stride at full speed (pixels), and how much the body pitches (radians: + is nose down). */
		static final float BOB = 0.9F, REST_PITCH = 0.1F, RUN_PITCH = 0.17F, CROUCH_PITCH = 0.22F;
		/** The neck's two segments' lengths (pixels) and their angles from the vertical (radians, + is forward): resting, and at full run. */
		static final float NECK1 = 4.0F, NECK2 = 4.0F;
		static final float NECK1_REST = 0.85F, NECK2_REST = 0.05F, NECK1_RUN = 1.15F, NECK2_RUN = 0.7F;
		/** Where the neck starts on the body (y, z in the body's space). */
		static final float NECK_Y = -1.0F, NECK_Z = -7.0F;
		/** The arms: where they hang off the chest (body space), their tucked angle and the hand's bend (radians). */
		static final float ARM_X = 2.5F, ARM_Y = 0.5F, ARM_Z = -6.0F, ARM_REST = -0.95F, HAND_REST = 0.7F, CLAW_REST = 0.4F;
		/** The slash: how far the arm winds up and how far it rakes down (radians). */
		static final float SLASH_UP = -1.25F, SLASH_DOWN = 1.0F;
		/** The tail: its segment lengths are in the mesh; how far each sways at rest and at a run (radians), and how fast at rest. */
		static final float TAIL_IDLE_SWAY = 0.15F, TAIL_RUN_SWAY = 0.05F, TAIL_IDLE_RATE = 0.07F;
		/** The lowest the tail droops (radians of tailLift): a hard landing while sneaking would otherwise drive its tip into the ground. */
		static final float TAIL_LOW = -0.35F;

		private final ModelPart torso, neck1, neck2, tail1, tail2, tail3, tail4;
		private final ModelPart rightShin, rightTarsus, rightFoot, leftShin, leftTarsus, leftFoot;
		private final ModelPart rightHand, rightClaw, leftHand, leftClaw;
		private final ModelPart visor, jaw;
		private final float[] solved = new float[6];

		Velox() {
			super(bake(mesh()), "blur");
			ModelPart root = this.root();
			// the plate lies on the torso's back: the (hidden) body is laid face-up there (see setupAnim)
			this.insigniaY = -0.5F;
			this.insigniaZ = -2.5F;
			this.insigniaSize = 0.9F;
			this.torso = root.getChild("torso");
			this.neck1 = this.torso.getChild("neck1");
			this.neck2 = this.neck1.getChild("neck2");
			this.tail1 = this.torso.getChild("tail1");
			this.tail2 = this.tail1.getChild("tail2");
			this.tail3 = this.tail2.getChild("tail3");
			this.tail4 = this.tail3.getChild("tail4");
			this.rightShin = this.rightLeg.getChild("shin");
			this.rightTarsus = this.rightShin.getChild("tarsus");
			this.rightFoot = this.rightTarsus.getChild("foot");
			this.leftShin = this.leftLeg.getChild("shin");
			this.leftTarsus = this.leftShin.getChild("tarsus");
			this.leftFoot = this.leftTarsus.getChild("foot");
			this.rightHand = this.rightArm.getChild("hand");
			this.rightClaw = this.rightHand.getChild("claw");
			this.leftHand = this.leftArm.getChild("hand");
			this.leftClaw = this.leftHand.getChild("claw");
			this.visor = this.head.getChild("visor");
			this.jaw = this.head.getChild("jaw");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the head (at the end of the neck, set each frame): a skull, a long snout, a jaw that opens, a crest, the visor
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -2.0F, -5.0F, 4.0F, 4.0F, 6.0F),
				PartPose.offset(0.0F, 4.0F, -10.0F));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(0, 10).addBox(-1.5F, -1.0F, -4.0F, 3.0F, 2.0F, 5.0F),
				PartPose.offset(0.0F, 0.5F, -5.0F));
			head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 17).addBox(-1.5F, 0.0F, -7.0F, 3.0F, 1.0F, 7.0F),
				PartPose.offset(0.0F, 1.5F, -1.5F));
			head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(38, 0).addBox(-0.5F, -2.0F, -1.5F, 1.0F, 2.0F, 4.0F),
				PartPose.offset(0.0F, -1.5F, -1.5F));
			head.addOrReplaceChild("crest_sweep", CubeListBuilder.create().texOffs(48, 0).addBox(-0.5F, -1.0F, 0.0F, 1.0F, 2.0F, 6.0F),
				PartPose.offsetAndRotation(0.0F, -2.8F, 1.0F, -0.3F, 0.0F, 0.0F));
			head.addOrReplaceChild("visor", CubeListBuilder.create().texOffs(20, 0).addBox(-2.5F, 0.0F, -4.0F, 5.0F, 2.0F, 4.0F),
				PartPose.offsetAndRotation(0.0F, -2.9F, -1.5F, -0.4F, 0.0F, 0.0F));
			// the man's body is hidden; it lies face-up on the torso (the insignia sits on its back)
			root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, HIP_Y, 0.0F));
			PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 26).addBox(-2.5F, -2.5F, -4.0F, 5.0F, 5.0F, 8.0F),
				PartPose.offsetAndRotation(0.0F, HIP_Y, 0.0F, REST_PITCH, 0.0F, 0.0F));
			torso.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(26, 26).addBox(-2.0F, -2.0F, -4.0F, 4.0F, 4.0F, 5.0F),
				PartPose.offset(0.0F, 0.5F, -4.0F));
			PartDefinition neck1 = torso.addOrReplaceChild("neck1", CubeListBuilder.create().texOffs(44, 26).addBox(-1.5F, -4.0F, -1.5F, 3.0F, 5.0F, 3.0F),
				PartPose.offsetAndRotation(0.0F, NECK_Y, NECK_Z, 0.75F, 0.0F, 0.0F));
			neck1.addOrReplaceChild("neck2", CubeListBuilder.create().texOffs(56, 26).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 5.0F, 2.0F),
				PartPose.offsetAndRotation(0.0F, -NECK1, 0.0F, -0.8F, 0.0F, 0.0F));
			// the balancing tail: four segments, thinning out
			PartDefinition tail1 = torso.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(0, 40).addBox(-2.0F, -1.5F, -1.0F, 4.0F, 3.0F, 7.0F),
				PartPose.offset(0.0F, -0.5F, 4.0F));
			PartDefinition tail2 = tail1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(22, 40).addBox(-1.5F, -1.0F, -1.0F, 3.0F, 2.0F, 7.0F),
				PartPose.offset(0.0F, 0.0F, 6.0F));
			PartDefinition tail3 = tail2.addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(42, 40).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 6.0F),
				PartPose.offset(0.0F, 0.0F, 6.0F));
			tail3.addOrReplaceChild("tail4", CubeListBuilder.create().texOffs(0, 51).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 1.0F, 4.0F),
				PartPose.offset(0.0F, 0.0F, 5.0F));
			// the little arms: an upper arm, a hand bent down off it, a hooked claw
			arm(root, "right_arm", -1, 20, 36, 52);
			arm(root, "left_arm", 1, 28, 44, 56);
			// the legs: thigh, shin, tarsus and toes (a sickle claw held up off each foot)
			leg(root, "right_leg", -1, 0, 28, 48, 0, 36);
			leg(root, "left_leg", 1, 14, 38, 56, 18, 44);
			return mesh;
		}

		private static void arm(PartDefinition root, String name, int side, int uArm, int uHand, int uClaw) {
			// rests at the man's shoulder, hanging: the first-person hand resets it to this (setupAnim tucks it at the chest)
			PartDefinition arm = root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(uArm, 51).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 5.0F, 2.0F),
				PartPose.offset(side * 5.0F, 2.0F, 0.0F));
			PartDefinition hand = arm.addOrReplaceChild("hand", CubeListBuilder.create().texOffs(uHand, 51).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F),
				PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, HAND_REST, 0.0F, 0.0F));
			hand.addOrReplaceChild("claw", CubeListBuilder.create().texOffs(uClaw, 51).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F),
				PartPose.offsetAndRotation(0.0F, 3.0F, 0.0F, CLAW_REST, 0.0F, 0.0F));
		}

		private static void leg(PartDefinition root, String name, int side, int uThigh, int uShin, int uTarsus, int uFoot, int uSickle) {
			PartDefinition thigh = root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(uThigh, 64).addBox(-1.5F, -1.5F, -2.0F, 3.0F, 7.0F, 4.0F),
				PartPose.offset(side * HIP_X, HIP_Y, 0.0F));
			PartDefinition shin = thigh.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(uShin, 64).addBox(-1.0F, -1.0F, -1.5F, 2.0F, 8.0F, 3.0F),
				PartPose.offset(0.0F, THIGH, 0.0F));
			PartDefinition tarsus = shin.addOrReplaceChild("tarsus", CubeListBuilder.create().texOffs(uTarsus, 64).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 6.0F, 2.0F),
				PartPose.offset(0.0F, SHIN, 0.0F));
			PartDefinition foot = tarsus.addOrReplaceChild("foot", CubeListBuilder.create().texOffs(uFoot, 76).addBox(-2.0F, -1.0F, -4.0F, 4.0F, 2.0F, 5.0F),
				PartPose.offset(0.0F, TARSUS, 0.0F));
			foot.addOrReplaceChild("sickle", CubeListBuilder.create().texOffs(uSickle, 76).addBox(-0.5F, -0.5F, -3.0F, 1.0F, 1.0F, 3.0F),
				PartPose.offsetAndRotation(0.0F, -1.2F, -1.0F, -0.5F, 0.0F, 0.0F));
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			XenoMotion.Read m = XenoMotion.read(state);
			for (ModelPart part : new ModelPart[] {this.head, this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg}) {
				part.resetPose();
			}
			this.hat.visible = false;
			this.body.visible = false;
			float speed = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
			// the legs only really stride once it is going; a still raptor stands with its feet flat
			float blend = Mth.clamp(speed * 4.0F, 0.0F, 1.0F);
			float air = m.air();
			float swing = Mth.clamp(state.swingAnimation, 0.0F, 1.0F);
			float bite = Mth.sin(swing * (float)Math.PI);
			float ticks = state.ageInTicks;
			boolean crouch = state.isCrouching;
			// ---- the gait: right leg on the clock, left half a stride behind ----
			float cycle = state.walkAnimationPos * GAIT;
			float duty = Mth.lerp(speed, DUTY_WALK, DUTY_RUN);
			float stride = MAX_STRIDE * Math.min(1.0F, speed * 1.1F);
			float lift = MAX_LIFT * Math.min(1.0F, speed * 1.2F);
			// the hip dips mid-stance (a spring) and rises in flight, twice a cycle
			float bob = BOB * speed * 0.5F * (1.0F + Mth.cos((cycle - duty * 0.5F) * 4.0F * (float)Math.PI));
			float hipY = HIP_Y + RUN_DROP * speed + (crouch ? CROUCH_DROP : 0.0F) + bob + m.land() * LAND_DROP + m.breath() * 0.12F * (1.0F - speed);
			float pitch = REST_PITCH + RUN_PITCH * speed + (crouch ? CROUCH_PITCH : 0.0F) + bite * 0.1F
				+ Mth.sin((cycle - duty * 0.5F) * 4.0F * (float)Math.PI) * 0.03F * speed + Math.max(0.0F, m.fall()) * air * 0.2F;
			this.legs(this.rightLeg, this.rightShin, this.rightTarsus, this.rightFoot, hipY, cycle, stride, lift, duty, FOOT_Z + IDLE_STAGGER * (1.0F - blend), blend, air, 2.0F);
			this.legs(this.leftLeg, this.leftShin, this.leftTarsus, this.leftFoot, hipY, cycle + 0.5F, stride, lift, duty, FOOT_Z - IDLE_STAGGER * (1.0F - blend), blend, air, -2.0F);
			// ---- the body: near horizontal, pitched over the hips ----
			this.torso.x = 0.0F;
			this.torso.y = hipY;
			this.torso.z = 0.0F;
			this.torso.xRot = pitch;
			this.torso.yRot = this.torso.zRot = 0.0F;
			// the hidden body lies face-up on the torso's back, for the insignia
			this.body.x = 0.0F;
			this.body.y = hipY;
			this.body.z = 0.0F;
			this.body.xRot = pitch - (float)Math.PI * 0.5F;
			this.body.yRot = this.body.zRot = 0.0F;
			// ---- the neck: an S, reaching forward as it runs, the head held level on the end of it ----
			float reach = Mth.clamp(speed * 1.3F, 0.0F, 1.0F);
			float n1 = Mth.lerp(reach, NECK1_REST, NECK1_RUN) + bite * 0.35F + (crouch ? 0.2F : 0.0F) + m.breath() * 0.03F * (1.0F - speed);
			float n2 = Mth.lerp(reach, NECK2_REST, NECK2_RUN) - bite * 0.15F;
			this.neck1.xRot = n1 - pitch;
			this.neck2.xRot = n2 - n1;
			float cos = Mth.cos(pitch);
			float sin = Mth.sin(pitch);
			this.head.x = 0.0F;
			this.head.y = hipY + NECK_Y * cos - NECK_Z * sin - NECK1 * Mth.cos(n1) - NECK2 * Mth.cos(n2);
			this.head.z = NECK_Y * sin + NECK_Z * cos - NECK1 * Mth.sin(n1) - NECK2 * Mth.sin(n2);
			float look = Mth.sin(ticks * 0.05F) * 0.1F * (1.0F - speed);
			this.head.yRot = state.yRot * Mth.DEG_TO_RAD + look;
			this.head.xRot = state.xRot * Mth.DEG_TO_RAD + 0.1F - 0.1F * speed + bite * 0.25F;
			this.head.zRot = 0.0F;
			this.jaw.xRot = 0.05F + bite * 0.7F + Math.max(0.0F, Mth.sin(ticks * 0.11F + 1.0F) - 0.9F) * 2.0F * (1.0F - speed);
			// the visor slides down over the eyes as it picks up speed and rides up on the brow when it slows
			float down = Mth.clamp((speed - 0.35F) / 0.4F, 0.0F, 1.0F);
			down = down * down * (3.0F - 2.0F * down);
			this.visor.y = Mth.lerp(down, -2.9F, -0.9F);
			this.visor.xRot = Mth.lerp(down, -0.4F, 0.0F);
			// ---- the arms: tucked at the chest, clawing in a slash, the left a beat behind ----
			this.arm(this.rightArm, this.rightHand, this.rightClaw, -1, hipY, pitch, slash(swing), speed, ticks);
			this.arm(this.leftArm, this.leftHand, this.leftClaw, 1, hipY, pitch, slash(Mth.clamp((swing - 0.12F) / 0.88F, 0.0F, 1.0F)), speed, ticks);
			// ---- the tail: out behind, countering the body, swaying when idle, steady at a run ----
			float rise = Math.max(TAIL_LOW, tailLift(m));
			float idle = 1.0F - speed;
			float wave = ticks * TAIL_IDLE_RATE;
			float stepWave = cycle * 2.0F * (float)Math.PI;
			this.tail1.xRot = -pitch + 0.04F * idle + rise;
			this.tail2.xRot = -0.1F * idle + rise * 0.5F;
			this.tail3.xRot = -0.08F * idle + 0.03F * speed + rise * 0.4F;
			this.tail4.xRot = -0.06F * idle + 0.05F * speed + rise * 0.3F;
			ModelPart[] tail = {this.tail1, this.tail2, this.tail3, this.tail4};
			for (int i = 0; i < 4; i++) {
				float idleSway = Mth.sin(wave - i * 0.9F) * TAIL_IDLE_SWAY * (0.6F + 0.3F * i);
				float runSway = -Mth.sin(stepWave - i * 0.7F) * TAIL_RUN_SWAY;
				tail[i].yRot = idleSway * idle + runSway * speed + (i == 0 ? -m.trail() * 0.3F : (m.turn() - m.trail()) * 0.12F);
				tail[i].zRot = 0.0F;
			}
		}

		/** The slash's arm angle added to the tuck, for the attack's progress (0-1): wind up, rake down, come back. */
		private static float slash(float swing) {
			if (swing <= 0.0F) {
				return 0.0F;
			}
			if (swing < 0.25F) {
				return Mth.lerp(swing / 0.25F, 0.0F, SLASH_UP);
			}
			if (swing < 0.55F) {
				float k = (swing - 0.25F) / 0.3F;
				return Mth.lerp(k * k * (3.0F - 2.0F * k), SLASH_UP, SLASH_DOWN);
			}
			float k = (swing - 0.55F) / 0.45F;
			return Mth.lerp(k * k * (3.0F - 2.0F * k), SLASH_DOWN, 0.0F);
		}

		private void arm(ModelPart arm, ModelPart hand, ModelPart claw, int side, float hipY, float pitch, float slash, float speed, float ticks) {
			float cos = Mth.cos(pitch);
			float sin = Mth.sin(pitch);
			arm.x = side * ARM_X;
			arm.y = hipY + ARM_Y * cos - ARM_Z * sin;
			arm.z = ARM_Y * sin + ARM_Z * cos;
			// held tucked, a little twitch at rest, drawn in tighter at a run
			float tuck = ARM_REST - 0.15F * speed + Mth.sin(ticks * 0.09F + side) * 0.04F * (1.0F - speed);
			arm.xRot = pitch + tuck + slash;
			arm.zRot = -side * 0.2F;
			hand.xRot = HAND_REST + slash * 0.5F + 0.2F * speed;
			claw.xRot = CLAW_REST + slash * 0.3F;
		}

		/**
		 * One leg: its foot's path through the stride, then the leg's angles from it ({@link #solveLeg}), kept on the
		 * ground while it's down.
		 */
		private void legs(ModelPart thigh, ModelPart shin, ModelPart tarsus, ModelPart foot, float hipY, float cycle, float stride, float lift,
			float duty, float footZ, float blend, float air, float tuckZ) {
			thigh.x = Math.signum(thigh.x) * HIP_X;
			thigh.y = hipY;
			thigh.z = 0.0F;
			solveLeg(hipY, cycle, stride, lift, duty, footZ, blend, air, tuckZ, this.solved);
			thigh.xRot = this.solved[0];
			shin.xRot = this.solved[1] - this.solved[0];
			tarsus.xRot = this.solved[2] - this.solved[1];
			foot.xRot = this.solved[3] - this.solved[2];
		}

		/** How far below its pivot a foot's lowest point is when pitched {@code pitch} (+ toes down): the sole, or the toe tips. */
		static float toeDrop(float pitch) {
			float sin = (float)Math.sin(pitch);
			return (float)Math.cos(pitch) + (pitch >= 0.0F ? TOE_REACH * sin : -sin);
		}

		/**
		 * Solves one leg into {@code out}: the thigh, shin, tarsus and foot angles from the vertical (radians, + swings the
		 * bottom backward), then the foot pivot's y and z from the hip. A foot is on the ground for {@code duty} of the
		 * cycle, sweeping from {@code footZ - stride} (front) to {@code footZ + stride}, then swings back through the air,
		 * lifted; {@code air} tucks it for a leap ({@code tuckZ} behind or ahead of the hip); {@code blend} (0-1) fades the
		 * stride's foot angles out for a raptor standing still. The thigh and tarsus stay parallel (a bird's leg), so the
		 * hip-to-foot reach is a two-bone solve of (thigh + tarsus) and shin: the knee always forward and below the hip's
		 * line, the ankle behind, however low the hip sinks.
		 */
		static void solveLeg(float hipY, float cycle, float stride, float lift, float duty, float footZ, float blend, float air, float tuckZ, float[] out) {
			float t = cycle - (float)Math.floor(cycle);
			float fz;
			float rise = 0.0F;
			float toes;
			if (t < duty) {
				float u = t / duty;
				fz = footZ + stride * (2.0F * u - 1.0F);
				// down flat on the heel's first touch, flat through the stance, rolling onto the toes to push off
				toes = u < 0.2F ? Mth.lerp(u / 0.2F, -0.15F, 0.0F) : u > 0.7F ? Mth.lerp((u - 0.7F) / 0.3F, 0.0F, 0.55F) : 0.0F;
			} else {
				float u = (t - duty) / (1.0F - duty);
				float s = u * u * (3.0F - 2.0F * u);
				fz = footZ + stride * (1.0F - 2.0F * s);
				rise = lift * (float)Math.sin(Math.PI * u);
				toes = Mth.lerp(s, 0.55F, -0.15F) + 0.2F * (float)Math.sin(Math.PI * u);
			}
			toes *= blend;
			// a leap: the feet come up and tuck, one back and one forward
			rise = Math.max(rise, 2.4F * air);
			fz = Mth.lerp(air, fz, footZ + tuckZ);
			toes = Mth.lerp(air, toes, 0.5F);
			float dy = 24.0F - toeDrop(toes) - rise - hipY;
			float dz = fz;
			float reach = (float)Math.sqrt(dy * dy + dz * dz);
			float paired = THIGH + TARSUS;
			float min = paired - SHIN + 0.3F;
			float max = paired + SHIN - 0.05F;
			float d = Mth.clamp(reach, min, max);
			if (reach > 1.0E-4F && d != reach) {
				dy *= d / reach;
				dz *= d / reach;
			}
			float psi = (float)Math.atan2(dz, dy);
			float cosA = Mth.clamp((paired * paired + d * d - SHIN * SHIN) / (2.0F * paired * d), -1.0F, 1.0F);
			// the thigh (and the tarsus with it) leans forward of the hip-to-foot line: knee ahead, ankle back
			float lean = psi - (float)Math.acos(cosA);
			float cosL = (float)Math.cos(lean);
			float sinL = (float)Math.sin(lean);
			float kneeY = THIGH * cosL;
			float kneeZ = THIGH * sinL;
			float ankleY = dy - TARSUS * cosL;
			float ankleZ = dz - TARSUS * sinL;
			out[0] = lean;
			out[1] = (float)Math.atan2(ankleZ - kneeZ, ankleY - kneeY);
			out[2] = lean;
			out[3] = toes;
			out[4] = hipY + dy;
			out[5] = dz;
		}
	}

	/** Prism: angular, a crown of crystal and crystals jutting from its back and shoulders. */
	static final class Prism extends FormModel {
		Prism() {
			super(bake(mesh()), "prism");
			this.insigniaZ = -2.4F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.4F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			head.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(0, 64).addBox(-1.0F, -14.0F, -1.0F, 2.0F, 6.0F, 2.0F), PartPose.ZERO);
			head.addOrReplaceChild("right_crystal", CubeListBuilder.create().texOffs(8, 64).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F),
				PartPose.offsetAndRotation(-3.5F, -8.0F, 0.0F, 0.0F, 0.0F, -0.5F));
			head.addOrReplaceChild("left_crystal", CubeListBuilder.create().texOffs(8, 64).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F),
				PartPose.offsetAndRotation(3.5F, -8.0F, 0.0F, 0.0F, 0.0F, 0.5F));
			PartDefinition body = root.getChild("body");
			body.addOrReplaceChild("back", CubeListBuilder.create().texOffs(16, 64).addBox(-3.0F, 1.0F, 2.0F, 6.0F, 6.0F, 3.0F), PartPose.ZERO);
			body.addOrReplaceChild("spine", CubeListBuilder.create().texOffs(34, 64).addBox(-1.0F, -2.0F, 3.0F, 2.0F, 5.0F, 2.0F),
				PartPose.offsetAndRotation(0.0F, 2.0F, 2.0F, -0.4F, 0.0F, 0.0F));
			// crystals jutting from the shoulders
			root.getChild("right_arm").addOrReplaceChild("shard", CubeListBuilder.create().texOffs(42, 64).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F),
				PartPose.offsetAndRotation(-2.0F, -1.0F, 0.0F, 0.0F, 0.0F, -0.5F));
			root.getChild("left_arm").addOrReplaceChild("shard", CubeListBuilder.create().texOffs(42, 64).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F),
				PartPose.offsetAndRotation(2.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.5F));
			return mesh;
		}
	}

	/**
	 * Wisp: a hooded thing with no legs, trailing off into tatters that sway. (0.18.90) Andy: "wisp should float instead
	 * of walk and be made of ki with the current texture overlaid". It never walks: its arms hang and drift instead of
	 * swinging with its steps, trail back as it moves, and {@link XenoformLayer} draws it floating, bobbing, its skin
	 * see-through and fullbright over glowing shells of ki ({@link #shells}, the same body puffed out).
	 */
	static final class Wisp extends FormModel {
		/** How far the ki shells stand out from the body (model pixels), inside out. */
		static final float[] KI_SHELLS = {0.45F, 1.1F};
		/** How far back its arms trail at a run (radians), and their slow drift. */
		static final float ARM_TRAIL = 0.5F, ARM_DRIFT = 0.08F;
		private final ModelPart tatters;
		/** Its ki, the body again a little bigger each time ({@link #KI_SHELLS}); none on a shell itself. */
		Wisp[] shells = new Wisp[0];

		Wisp() {
			this(0.0F);
			this.shells = new Wisp[KI_SHELLS.length];
			for (int i = 0; i < KI_SHELLS.length; i++) {
				this.shells[i] = new Wisp(KI_SHELLS[i]);
			}
		}

		private Wisp(float grow) {
			super(bake(mesh(grow)), "wisp");
			this.tatters = this.body.getChild("tatters");
		}

		private static MeshDefinition mesh(float grow) {
			CubeDeformation puff = new CubeDeformation(grow);
			MeshDefinition mesh = HumanoidModel.createMesh(puff, 0.0F);
			PartDefinition root = mesh.getRoot();
			root.getChild("head").addOrReplaceChild("hood", CubeListBuilder.create().texOffs(0, 64).addBox(-5.0F, -9.0F, -5.0F, 10.0F, 10.0F, 10.0F, puff), PartPose.ZERO);
			root.getChild("body").addOrReplaceChild("tatters", CubeListBuilder.create().texOffs(0, 86).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 7.0F, 4.0F, puff),
				PartPose.offset(0.0F, 12.0F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			// it doesn't walk: the arms take no swing from its steps (the state is put back for the next model)
			float walk = state.walkAnimationSpeed;
			state.walkAnimationSpeed = 0.0F;
			super.setupAnim(state);
			state.walkAnimationSpeed = walk;
			this.rightLeg.visible = false;
			this.leftLeg.visible = false;
			XenoMotion.Read m = XenoMotion.read(state);
			// arms hang loose, drifting, and trail back as it glides along
			float move = Math.min(1.0F, walk);
			this.rightArm.xRot += move * ARM_TRAIL + Mth.sin(state.ageInTicks * 0.07F) * ARM_DRIFT;
			this.leftArm.xRot += move * ARM_TRAIL + Mth.sin(state.ageInTicks * 0.07F + 1.7F) * ARM_DRIFT;
			this.rightArm.zRot += 0.08F + Mth.cos(state.ageInTicks * 0.05F) * 0.04F;
			this.leftArm.zRot -= 0.08F + Mth.cos(state.ageInTicks * 0.05F + 1.1F) * 0.04F;
			// the tatters drag behind it, billow up as it drops and swing out of a turn
			this.tatters.xRot = Mth.sin(state.ageInTicks * 0.12F) * 0.15F + walk * 0.5F + Math.abs(m.fall()) * 0.6F * m.air()
				+ m.land() * 0.3F;
			this.tatters.zRot = Mth.cos(state.ageInTicks * 0.09F) * 0.1F - m.trail() * 0.35F;
			XenoformFlightPose.Flight flight = state.getData(XenoformFlightPose.KEY);
			if (flight != null && flight.glide() > 0.001F) {
				// gliding, the tatters stream out behind and ripple
				float g = XenoformFlightPose.smooth(flight.glide());
				this.tatters.xRot = Mth.lerp(g, this.tatters.xRot, 1.15F + Mth.sin(state.ageInTicks * 0.35F) * 0.22F);
				this.tatters.zRot = Mth.lerp(g, this.tatters.zRot, Mth.sin(state.ageInTicks * 0.23F) * 0.2F);
			}
		}
	}

	/**
	 * Nadir: a tall dark silhouette, broad at the shoulder, a face of nothing; a thin mantle of void drifts behind it.
	 * The insignia is the skin's alone.
	 */
	static final class Nadir extends FormModel {
		private final ModelPart mantle;

		Nadir() {
			super(bake(mesh()), "nadir");
			this.insigniaZ = -2.3F;
			this.mantle = this.body.getChild("mantle");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.3F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// heavy shoulders
			root.getChild("body").addOrReplaceChild("shoulders", CubeListBuilder.create().texOffs(0, 64).addBox(-6.0F, -1.0F, -3.0F, 12.0F, 4.0F, 6.0F),
				PartPose.ZERO);
			// the mantle: a flat sheet hanging from the back
			root.getChild("body").addOrReplaceChild("mantle", CubeListBuilder.create().texOffs(36, 64).addBox(-5.0F, 0.0F, 0.0F, 10.0F, 14.0F, 1.0F),
				PartPose.offset(0.0F, 1.0F, 3.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			XenoMotion.Read m = XenoMotion.read(state);
			// the mantle drifts with its breath, streams out behind as it moves or drops, and swings out of a turn
			this.mantle.xRot = 0.1F + Mth.sin(state.ageInTicks * 0.07F) * 0.06F + state.walkAnimationSpeed * 0.5F
				+ Math.max(0.0F, m.fall()) * 0.9F * m.air() + m.land() * 0.25F;
			this.mantle.zRot = -m.trail() * 0.3F;
		}
	}

	/**
	 * Duplex: a copper golem's build, box for box: a wide flat head with a nose, a short body, long arms hanging near
	 * the ground, stubby legs and the lightning rod with its ball tip, plus a copper belt. It's built at the golem's own
	 * size (16 pixels to the top of its head) and drawn {@link #RENDER_SCALE} times up from a player at Duplex's scale,
	 * so the whole is a copper golem's height.
	 */
	static final class Duplex extends FormModel {
		/** 16 px tall, at the player's 0.9375 and Duplex's scale, comes to a copper golem's 0.98 blocks. */
		static final float RENDER_SCALE = 0.98F / (0.9375F * com.jaymospowers.power.xenoform.forms.Duplex.SCALE);
		private final ModelPart rod;

		Duplex() {
			super(bake(mesh()), "duplex");
			this.insigniaY = 2.5F;
			this.insigniaZ = -3.0F;
			this.insigniaSize = 0.6F;
			this.renderScale = RENDER_SCALE;
			this.rod = this.head.getChild("rod");
		}

		private static MeshDefinition mesh() {
			// the golem's boxes (CopperGolemModel), stood on the humanoid's feet line (y 24): legs from 19, body and
			// head from 13; the arms hang from the top of the body
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -5.0F, -5.0F, 8.0F, 5.0F, 10.0F)
				.texOffs(40, 0).addBox(-1.0F, -2.0F, -6.0F, 2.0F, 3.0F, 2.0F), PartPose.offset(0.0F, 13.0F, 0.0F));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			PartDefinition rod = head.addOrReplaceChild("rod", CubeListBuilder.create().texOffs(48, 0).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F),
				PartPose.offset(0.0F, -5.0F, 0.0F));
			rod.addOrReplaceChild("knob", CubeListBuilder.create().texOffs(40, 8).addBox(-2.0F, -4.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.offset(0.0F, -4.0F, 0.0F));
			PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 16).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 6.0F, 6.0F),
				PartPose.offset(0.0F, 13.0F, 0.0F));
			body.addOrReplaceChild("belt", CubeListBuilder.create().texOffs(0, 64).addBox(-4.0F, 4.0F, -3.0F, 8.0F, 2.0F, 6.0F, new CubeDeformation(0.3F)),
				PartPose.ZERO);
			root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(28, 16).addBox(-3.0F, -1.0F, -2.0F, 3.0F, 10.0F, 4.0F),
				PartPose.offset(-4.0F, 14.0F, 0.0F));
			root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(42, 16).addBox(0.0F, -1.0F, -2.0F, 3.0F, 10.0F, 4.0F),
				PartPose.offset(4.0F, 14.0F, 0.0F));
			root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 32).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F),
				PartPose.offset(-2.0F, 19.0F, 0.0F));
			root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 32).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F),
				PartPose.offset(2.0F, 19.0F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			XenoMotion.Read m = XenoMotion.read(state);
			// the rod nods with each step, lags a turn and whips back on landing
			float step = Mth.sin(state.walkAnimationPos * 0.6662F * 2.0F) * 0.12F * state.walkAnimationSpeed;
			this.rod.xRot = step - m.land() * 0.4F + Math.max(0.0F, m.fall()) * 0.3F * m.air();
			this.rod.zRot = -m.trail() * 0.35F + Mth.sin(state.ageInTicks * 0.05F) * 0.03F;
		}
	}

	/** Gremlin: a big-headed imp with long ears that twitch and a thin tail that flicks. */
	static final class Gremlin extends FormModel {
		private final ModelPart rightEar, leftEar, tail;

		Gremlin() {
			super(bake(mesh()), "gremlin");
			this.rightEar = this.head.getChild("right_ear");
			this.leftEar = this.head.getChild("left_ear");
			this.tail = this.body.getChild("tail");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition head = mesh.getRoot().getChild("head");
			head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 64).addBox(-5.0F, -10.0F, -5.0F, 10.0F, 10.0F, 10.0F), PartPose.ZERO);
			head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(40, 64).addBox(-4.0F, -5.0F, -0.5F, 4.0F, 5.0F, 1.0F),
				PartPose.offsetAndRotation(-5.0F, -4.0F, 0.0F, 0.0F, 0.0F, 0.6F));
			head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(40, 64).mirror().addBox(0.0F, -5.0F, -0.5F, 4.0F, 5.0F, 1.0F),
				PartPose.offsetAndRotation(5.0F, -4.0F, 0.0F, 0.0F, 0.0F, -0.6F));
			mesh.getRoot().getChild("body").addOrReplaceChild("tail", CubeListBuilder.create().texOffs(40, 72).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 6.0F),
				PartPose.offsetAndRotation(0.0F, 11.0F, 2.0F, 0.6F, 0.0F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			XenoMotion.Read m = XenoMotion.read(state);
			// the ears bounce with each step, flop on landing, blow back as it falls and lag a turn
			float twitch = Mth.sin(state.ageInTicks * 0.3F) * 0.1F + state.walkAnimationSpeed * 0.2F
				+ Mth.abs(Mth.sin(state.walkAnimationPos * 0.6662F)) * 0.15F * state.walkAnimationSpeed;
			float flop = m.land() * 0.7F - Math.max(0.0F, m.fall()) * 0.4F * m.air();
			this.rightEar.zRot = 0.6F + twitch + flop - m.trail() * 0.25F;
			this.leftEar.zRot = -0.6F - twitch - flop - m.trail() * 0.25F;
			this.tail.yRot = Mth.sin(state.ageInTicks * 0.2F) * 0.5F + Mth.cos(state.walkAnimationPos * 0.6662F) * 0.6F * state.walkAnimationSpeed
				- m.trail() * 0.8F;
			this.tail.xRot = 0.6F + tailLift(m);
		}
	}

	/** Knell: hulking, horned, with a glowing ribcage, heavy shoulders and sculk tendrils at the jaw. */
	static final class Knell extends FormModel {
		private final ModelPart[] tendrils = new ModelPart[3];

		Knell() {
			super(bake(mesh()), "knell");
			this.insigniaY = 5.5F;
			this.insigniaZ = -4.0F;
			for (int i = 0; i < 3; i++) {
				this.tendrils[i] = this.head.getChild("tendril_" + i);
			}
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			XenoMotion.Read m = XenoMotion.read(state);
			// the tendrils hang straight down whichever way it looks, sway on their own and swing out of a turn
			for (int i = 0; i < 3; i++) {
				float own = Mth.sin(state.ageInTicks * 0.11F + i * 1.7F);
				this.tendrils[i].xRot = 0.15F - this.head.xRot * 0.85F + own * 0.08F + m.speed() * 0.25F + m.land() * 0.4F;
				this.tendrils[i].zRot = 0.2F - 0.2F * i + own * 0.06F - m.trail() * 0.4F;
			}
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(1.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			head.addOrReplaceChild("right_horn", CubeListBuilder.create().texOffs(0, 64).addBox(-1.0F, -7.0F, -1.0F, 2.0F, 7.0F, 2.0F),
				PartPose.offsetAndRotation(-4.0F, -8.0F, 0.0F, 0.0F, 0.0F, -0.45F));
			head.addOrReplaceChild("left_horn", CubeListBuilder.create().texOffs(0, 64).addBox(-1.0F, -7.0F, -1.0F, 2.0F, 7.0F, 2.0F),
				PartPose.offsetAndRotation(4.0F, -8.0F, 0.0F, 0.0F, 0.0F, 0.45F));
			PartDefinition body = root.getChild("body");
			body.addOrReplaceChild("ribs", CubeListBuilder.create().texOffs(8, 64).addBox(-5.0F, 1.0F, -4.0F, 10.0F, 8.0F, 2.0F), PartPose.ZERO);
			body.addOrReplaceChild("shoulders", CubeListBuilder.create().texOffs(0, 76).addBox(-6.0F, -3.0F, -2.5F, 12.0F, 3.0F, 5.0F, new CubeDeformation(1.0F)),
				PartPose.ZERO);
			// sculk tendrils hanging from the jaw
			for (int i = 0; i < 3; i++) {
				head.addOrReplaceChild("tendril_" + i, CubeListBuilder.create().texOffs(34, 64).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F),
					PartPose.offsetAndRotation(-2.5F + 2.5F * i, 0.0F, -4.5F, 0.15F, 0.0F, 0.2F - 0.2F * i));
			}
			return mesh;
		}
	}

	/**
	 * Wyrm: horned, winged and long-tailed. Each wing has an inner and an outer half: flying they spread wide and beat;
	 * on the ground they hang from the shoulders and wrap round the body like a cloak, the outer halves folded across the
	 * chest. The tail runs in three joints to a finned tip.
	 */
	static final class Wyrm extends FormModel {
		private final ModelPart tail, tailMid, tailTip;

		Wyrm() {
			super(bake(mesh()), "wyrm");
			this.insigniaZ = -2.5F;
			this.tail = this.body.getChild("tail");
			this.tailMid = this.tail.getChild("mid");
			this.tailTip = this.tailMid.getChild("tip");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.5F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			head.addOrReplaceChild("right_horn", CubeListBuilder.create().texOffs(0, 64).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 7.0F),
				PartPose.offsetAndRotation(-3.0F, -7.5F, 2.0F, -0.6F, 0.0F, 0.0F));
			head.addOrReplaceChild("left_horn", CubeListBuilder.create().texOffs(0, 64).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 7.0F),
				PartPose.offsetAndRotation(3.0F, -7.5F, 2.0F, -0.6F, 0.0F, 0.0F));
			PartDefinition body = root.getChild("body");
			PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 106).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 13.0F),
				PartPose.offsetAndRotation(0.0F, 11.0F, 2.0F, TAIL_REST[0], 0.0F, 0.0F));
			PartDefinition mid = tail.addOrReplaceChild("mid", CubeListBuilder.create().texOffs(32, 106).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 10.0F),
				PartPose.offsetAndRotation(0.0F, 0.0F, 12.5F, TAIL_REST[1], 0.0F, 0.0F));
			PartDefinition tip = mid.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(0, 78).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 8.0F),
				PartPose.offsetAndRotation(0.0F, 0.0F, 9.5F, TAIL_REST[2], 0.0F, 0.0F));
			tip.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(44, 64).addBox(-0.5F, -4.0F, 0.0F, 1.0F, 4.0F, 3.0F), PartPose.offset(0.0F, -0.5F, 5.0F));
			// the wings are the ender dragon's own, drawn small by XenoformLayer (WyrmWings)
			return mesh;
		}

		/** The tail's resting pitch at each joint (+ is up): down from the back, levelling out near the ground. */
		static final float[] TAIL_REST = {-0.35F, -0.12F, 0.32F};

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float t = state.ageInTicks;
			XenoMotion.Read m = XenoMotion.read(state);
			float walk = state.walkAnimationSpeed;

			// the tail swings behind each turn, each joint a beat after the last; it lifts as it drops, whips down on
			// landing and droops when crouched
			float sway = Mth.cos(state.walkAnimationPos * 0.6662F) * 0.25F * walk + Mth.sin(t * 0.08F) * 0.08F;
			float lag = m.trail() - m.turn();
			float tailUp = tailLift(m);
			this.tail.yRot = sway - m.trail() * 0.5F;
			this.tailMid.yRot = Mth.sin(t * 0.08F - 0.8F) * 0.1F + sway * 0.6F - lag * 0.6F - m.trail() * 0.2F;
			this.tailTip.yRot = Mth.sin(t * 0.08F - 1.6F) * 0.14F + sway * 0.5F - lag * 0.8F;
			this.tail.xRot = TAIL_REST[0] + walk * 0.15F + tailUp * 0.6F;
			this.tailMid.xRot = TAIL_REST[1] + tailUp * 0.5F;
			this.tailTip.xRot = TAIL_REST[2] + tailUp * 0.4F;
		}
	}
}
