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
	 * (0.18.96; 0.19.62 a real armadillo) Gorger: a squat armadillo on four short legs: a pointed snout with a bone horn,
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
	 * (0.18.96; 0.19.66 axolotl) Slipfin: an axolotl, long and low. A wide flat head with a hinged jaw and three
	 * feathery gill stalks each side that wave as it breathes and sweep back as it swims, a soft pink trunk in two
	 * parts, a deep flat finned tail of four segments, four small splayed legs. On land it belly-crawls the way a
	 * salamander does: the legs paddle in diagonal pairs (front right with hind left) while the trunk and tail bend
	 * into a travelling S, the head holding where it looks; sneaking presses it flat. In water it swims by undulating:
	 * the legs tucked back along its sides and a wave running down the body and tail, nose pitched where it looks. A
	 * bite is a darting lunge of the head with the jaw flung open. Every segment hangs off the one in front of it
	 * (trunk, rear trunk, tail; legs on the trunk they belong to), so nothing comes apart as it bends. The man's own
	 * parts are hidden; the hidden body carries the insignia on the back of the front trunk.
	 */
	static final class Slipfin extends FormModel {
		// ---- tuning ----
		/** Model y of the ground, of the trunk's centre belly-crawling, how far sneaking sinks it, and swimming. */
		static final float GROUND = 24.0F, CRAWL_Y = 20.0F, CROUCH_DROP = 0.4F, SWIM_Y = 13.5F;
		/** The renderer draws a sneaking player this many pixels lower (AvatarRenderer.getRenderOffset): the animal is raised that much to stay on the ground. */
		static final float CROUCH_SINK = 2.0F;
		/** Where the front trunk's centre sits (model z), so the whole length is centred on the player. */
		static final float HUB_Z = -8.0F;
		/** A leg's length (its box height), and the lowest its pivot may sit on the trunk (so the leg stays attached). */
		static final float LEG_LEN = 3.0F, LEG_TOP_MAX = 1.8F;
		/** The paddling gait: how fast against the walk, how far a leg swings (radians), how high a stepping foot lifts (px). */
		static final float GAIT = 0.6662F * 1.25F, LEG_SWING = 0.7F, LEG_LIFT = 1.1F;
		/** Splay: a leg's outward angle at rest and sneaking, and how much more it opens mid-step (radians). */
		static final float SPLAY = 0.35F, SPLAY_CROUCH = 0.7F, SPLAY_STEP = 0.2F;
		/** How far the walk reads as full (walk speed times this, capped at 1), and the idle sway at a standstill. */
		static final float WALK_FULL = 1.6F, IDLE_SWAY = 0.35F;
		/** The land S-wave: each joint's yaw amplitude and phase (front trunk, rear trunk, then the four tail segments). */
		private static final float[] WAVE_YAW = {0.12F, 0.14F, 0.2F, 0.28F, 0.36F, 0.44F};
		private static final float[] WAVE_PHASE = {0.0F, -3.1416F, -3.9F, -4.7F, -5.5F, -6.3F};
		/** The swim wave: each joint's pitch amplitude (radians), the lag between joints, and its speed. */
		private static final float[] SWIM_PITCH = {0.04F, 0.07F, 0.11F, 0.16F, 0.21F, 0.26F};
		static final float SWIM_LAG = 0.75F, SWIM_RATE = 0.33F, SWIM_STRIDE = 1.0F, SWIM_LOOK = 0.35F;
		/** Swimming: the legs' tuck (back along the body), their outward angle, and their flutter. */
		static final float TUCK = 1.25F, TUCK_SPLAY = 0.2F, TUCK_FLUTTER = 0.12F;
		/** How far the head may turn on its neck, how far it nods up and down (radians); lunge: reach (px) and jaw (radians). */
		static final float HEAD_TURN = 0.75F, NOD_UP = 0.45F, NOD_DOWN = 0.04F, LUNGE_REACH = 2.2F, LUNGE_JAW = 0.6F;
		/** The skull's pivot height on the front trunk, and how far it rises as the nose tips up (its length behind the pivot, so its back never digs in). */
		static final float SKULL_Y = 1.0F, SKULL_LIFT = 3.0F;
		/** The gill stalks' heights on the skull's sides and their fan (radians, + is up): top, middle, bottom. */
		private static final float[] GILL_Y = {-1.5F, -0.5F, 0.5F};
		private static final float[] GILL_FAN = {0.75F, 0.35F, 0.0F};
		private static final int JOINTS = 6;
		/** Where the rear trunk hangs off the front one (hub z), the hind legs' z on it, and the front legs' z on the hub. */
		private static final float REAR_Z = 3.0F, HIND_LEG_Z = 4.0F, FORE_LEG_Z = 0.5F;
		/** How far out the legs' pivots sit: the front pair on the front trunk, the hind pair on the (slightly shrunk) rear one. */
		private static final float FORE_LEG_X = 3.5F, HIND_LEG_X = 3.2F;
		/** Tail segments: depth (their next one hangs one pixel inside the end). */
		private static final float[] TAIL_DEPTH = {5.0F, 5.0F, 5.0F, 4.0F};

		private final ModelPart hub, skull, jaw, rear;
		private final ModelPart[] gills = new ModelPart[6];
		private final ModelPart[] tail = new ModelPart[4];
		/** Front right, front left, hind right, hind left. */
		private final ModelPart[] legs = new ModelPart[4];
		/** Each joint's yaw and pitch, from the front trunk to the tail tip (relative to the world, not the joint before). */
		private final float[] yaw = new float[JOINTS], pitch = new float[JOINTS];

		Slipfin() {
			super(bake(mesh()), "slipfin");
			ModelPart root = this.root();
			this.hub = root.getChild("hub");
			this.skull = this.hub.getChild("skull");
			this.jaw = this.skull.getChild("jaw");
			this.rear = this.hub.getChild("rear");
			for (int i = 0; i < 6; i++) {
				this.gills[i] = this.skull.getChild("gill_" + i);
			}
			ModelPart parent = this.rear;
			for (int i = 0; i < 4; i++) {
				this.tail[i] = parent.getChild("tail_" + i);
				parent = this.tail[i];
			}
			this.legs[0] = this.hub.getChild("leg_0");
			this.legs[1] = this.hub.getChild("leg_1");
			this.legs[2] = this.rear.getChild("leg_2");
			this.legs[3] = this.rear.getChild("leg_3");
			// the plate lies on the back of the front trunk (the hidden body is laid face-up there each frame)
			this.insigniaY = 0.0F;
			this.insigniaZ = 0.0F;
			this.insigniaSize = 0.9F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the front trunk (6 wide, 4 tall, 8 long), centred on its pivot
			PartDefinition hub = root.addOrReplaceChild("hub", CubeListBuilder.create().texOffs(0, 64).addBox(-3.0F, -2.0F, -4.0F, 6.0F, 4.0F, 8.0F),
				PartPose.offset(0.0F, CRAWL_Y, HUB_Z));
			// the head: a wide flat skull reaching back inside the trunk, so a lunge never opens a neck gap, and the jaw under it
			PartDefinition skull = hub.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 76).addBox(-4.0F, -2.0F, -6.0F, 8.0F, 3.0F, 9.0F),
				PartPose.offset(0.0F, SKULL_Y, -3.5F));
			skull.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(34, 76).addBox(-3.0F, 0.0F, -5.0F, 6.0F, 1.0F, 5.0F),
				PartPose.offset(0.0F, 1.0F, -1.0F));
			// three gill stalks a side: 5 long, fanned from steeply up to level (a lower one would drag on the ground); the left ones mirror
			for (int i = 0; i < 6; i++) {
				boolean left = i >= 3;
				int row = i % 3;
				CubeListBuilder gill = CubeListBuilder.create().texOffs(34, 82);
				if (left) {
					gill.mirror().addBox(0.0F, -0.5F, -1.0F, 5.0F, 1.0F, 2.0F);
				} else {
					gill.addBox(-5.0F, -0.5F, -1.0F, 5.0F, 1.0F, 2.0F);
				}
				skull.addOrReplaceChild("gill_" + i, gill, PartPose.offsetAndRotation(left ? 4.0F : -4.0F, GILL_Y[row], 0.0F, 0.0F, 0.0F, (left ? -1.0F : 1.0F) * GILL_FAN[row]));
			}
			// the rear trunk hangs one pixel inside the front one's end (a hair smaller, so their faces never share a plane)
			CubeDeformation shrink = new CubeDeformation(-0.3F);
			PartDefinition rear = hub.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(28, 64).addBox(-3.0F, -2.0F, 0.0F, 6.0F, 4.0F, 8.0F, shrink),
				PartPose.offset(0.0F, 0.0F, REAR_Z));
			// the tail: four segments, each hung one pixel inside the end of the one before, deep and flat like a fin
			PartDefinition t0 = rear.addOrReplaceChild("tail_0", CubeListBuilder.create().texOffs(0, 88).addBox(-2.0F, -2.5F, 0.0F, 4.0F, 5.0F, 5.0F),
				PartPose.offset(0.0F, 0.0F, 7.0F));
			PartDefinition t1 = t0.addOrReplaceChild("tail_1", CubeListBuilder.create().texOffs(18, 88).addBox(-1.5F, -2.5F, 0.0F, 3.0F, 5.0F, 5.0F, shrink),
				PartPose.offset(0.0F, 0.0F, TAIL_DEPTH[0] - 1.0F));
			PartDefinition t2 = t1.addOrReplaceChild("tail_2", CubeListBuilder.create().texOffs(34, 88).addBox(-1.0F, -2.0F, 0.0F, 2.0F, 4.0F, 5.0F),
				PartPose.offset(0.0F, 0.0F, TAIL_DEPTH[1] - 1.0F));
			t2.addOrReplaceChild("tail_3", CubeListBuilder.create().texOffs(48, 88).addBox(-1.0F, -1.5F, 0.0F, 2.0F, 3.0F, 4.0F, shrink),
				PartPose.offset(0.0F, 0.0F, TAIL_DEPTH[2] - 1.0F));
			// the legs: stubby 2x3x3 paddles, their inner face half a pixel inside the trunk; right then left, front pair on the front trunk
			for (int i = 0; i < 4; i++) {
				boolean left = (i & 1) == 1;
				CubeListBuilder leg = CubeListBuilder.create().texOffs(48, 82);
				if (left) {
					leg.mirror();
				}
				leg.addBox(-1.0F, 0.0F, -1.5F, 2.0F, 3.0F, 3.0F);
				PartDefinition parent = i < 2 ? hub : rear;
				parent.addOrReplaceChild("leg_" + i, leg, PartPose.offset((left ? 1.0F : -1.0F) * (i < 2 ? FORE_LEG_X : HIND_LEG_X), 1.2F, i < 2 ? FORE_LEG_Z : HIND_LEG_Z));
			}
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			// the man's parts are all hidden; the hidden body only carries the insignia
			this.head.visible = this.hat.visible = this.body.visible = false;
			this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
			this.body.resetPose();
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			boolean swim = state.isInWater;
			float speed = Math.min(1.0F, state.walkAnimationSpeed * WALK_FULL);
			float bite = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
			// a sneaking player is drawn CROUCH_SINK lower: the ground, in model y, is that much higher
			float sink = state.isCrouching ? CROUCH_SINK : 0.0F;
			float ground = GROUND - sink;
			float hubY;
			if (swim) {
				hubY = SWIM_Y - sink + Mth.sin(age * 0.09F) * 0.6F;
				// the nose pitched where it looks (more as it moves), a wave running down the length of it
				float look = Mth.clamp(state.xRot * Mth.DEG_TO_RAD * 0.7F, -SWIM_LOOK, SWIM_LOOK) * (0.4F + 0.6F * speed);
				float t = age * SWIM_RATE * 0.5F + state.walkAnimationPos * SWIM_STRIDE;
				float amp = 0.45F + 0.55F * speed;
				for (int j = 0; j < JOINTS; j++) {
					this.pitch[j] = look + SWIM_PITCH[j] * amp * Mth.sin(t * 2.0F - j * SWIM_LAG);
					this.yaw[j] = 0.05F * Mth.sin(age * 0.07F - j * 0.5F);
				}
			} else {
				hubY = CRAWL_Y + (state.isCrouching ? CROUCH_DROP : 0.0F) - sink;
				float phi = state.walkAnimationPos * GAIT;
				float idle = (1.0F - speed) * IDLE_SWAY;
				// how far the tail is lifted (it flares as it falls, whips down on landing), spread along its length
				float lift = Mth.clamp(tailLift(m), -0.15F, 0.5F);
				for (int j = 0; j < JOINTS; j++) {
					this.yaw[j] = WAVE_YAW[j] * (speed * Mth.sin(phi + WAVE_PHASE[j]) + idle * Mth.sin(age * 0.05F - j * 0.8F));
					this.pitch[j] = 0.0F;
					if (j >= 2) {
						this.yaw[j] -= m.trail() * 0.1F * (j - 1);
						this.pitch[j] = lift * (j - 1) * 0.25F;
					}
				}
			}
			// the spine: the front trunk is placed in the world, each joint after it bends by the difference in angle
			// (swimming, the whole animal pitches about its middle, not about the front trunk)
			float tilt = swim ? this.pitch[0] : 0.0F;
			this.hub.x = 0.0F;
			this.hub.y = hubY - HUB_Z * Mth.sin(tilt);
			this.hub.z = HUB_Z * Mth.cos(tilt);
			this.hub.xRot = this.pitch[0];
			this.hub.yRot = this.yaw[0];
			this.hub.zRot = 0.0F;
			this.rear.xRot = this.pitch[1] - this.pitch[0];
			this.rear.yRot = this.yaw[1] - this.yaw[0];
			for (int i = 0; i < 4; i++) {
				this.tail[i].xRot = this.pitch[i + 2] - this.pitch[i + 1];
				this.tail[i].yRot = this.yaw[i + 2] - this.yaw[i + 1];
			}
			// the insignia: the hidden body laid face-up on the middle of the front trunk's back, tilted and turned with it
			float sp = Mth.sin(this.pitch[0]);
			this.body.x = -2.0F * sp * Mth.sin(this.yaw[0]);
			this.body.y = this.hub.y - 2.0F * Mth.cos(this.pitch[0]);
			this.body.z = this.hub.z - 2.0F * sp * Mth.cos(this.yaw[0]);
			this.body.xRot = this.pitch[0] - Mth.HALF_PI;
			this.body.yRot = this.yaw[0];
			this.legs(state, m, swim, ground, hubY, speed, age);
			this.face(state, m, swim, speed, age, bite);
		}

		/** The legs: paddling in diagonal pairs on land (feet planted through the stance), tucked back and fluttering swimming. */
		private void legs(AvatarRenderState state, XenoMotion.Read m, boolean swim, float ground, float hubY, float speed, float age) {
			float phi = state.walkAnimationPos * GAIT;
			for (int k = 0; k < 4; k++) {
				boolean left = (k & 1) == 1;
				ModelPart leg = this.legs[k];
				if (swim) {
					leg.xRot = TUCK + TUCK_FLUTTER * Mth.sin(age * 0.3F + k * 1.3F);
					leg.zRot = (left ? -1.0F : 1.0F) * TUCK_SPLAY;
					leg.y = 1.0F;
					continue;
				}
				// front right with hind left, front left with hind right
				float ph = phi + ((k == 0 || k == 3) ? 0.0F : (float)Math.PI);
				// + swings the foot back (the stance, on the ground), - brings it forward (the swing, lifted)
				float a = LEG_SWING * speed * Mth.sin(ph);
				float step = Math.max(0.0F, -Mth.cos(ph)) * speed;
				// in the air the legs dangle and kick
				a += m.air() * (0.5F + 0.3F * Mth.sin(age * 0.5F + k * 1.7F));
				float splay = (state.isCrouching ? SPLAY_CROUCH : SPLAY) + SPLAY_STEP * step;
				leg.xRot = a;
				leg.zRot = (left ? -1.0F : 1.0F) * splay;
				// the pivot rides so the lowest corner of the paw stays on the ground (up by the lift on the swing), and
				// never so low that the leg's top leaves the trunk's side
				leg.y = Math.min(LEG_TOP_MAX, ground - hubY - drop(a, (left ? -1.0F : 1.0F) * splay) - LEG_LIFT * step);
			}
		}

		/** How far below its pivot a leg's lowest corner hangs once turned by {@code a} (forward-back) and {@code zr} (out). */
		private static float drop(float a, float zr) {
			float lowest = -1.0E9F;
			for (int x = -1; x <= 1; x += 2) {
				for (int y = 0; y <= 1; y++) {
					for (int z = -1; z <= 1; z += 2) {
						float y1 = y * LEG_LEN * Mth.cos(a) - z * 1.5F * Mth.sin(a);
						lowest = Math.max(lowest, x * Mth.sin(zr) + y1 * Mth.cos(zr));
					}
				}
			}
			return lowest;
		}

		/** The head on its neck, the jaw, and the gills. */
		private void face(AvatarRenderState state, XenoMotion.Read m, boolean swim, float speed, float age, float bite) {
			float turn = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -HEAD_TURN, HEAD_TURN) - this.yaw[0] * 0.5F;
			float nod;
			if (swim) {
				nod = Mth.clamp(state.xRot * Mth.DEG_TO_RAD * 0.3F, -0.3F, 0.3F) - this.pitch[0] * 0.4F;
			} else {
				nod = Mth.clamp(state.xRot * Mth.DEG_TO_RAD * 0.5F, -NOD_UP, NOD_DOWN);
			}
			// a bite: the head darts out, tips up, the jaw flung open; tipped up, it rises so its back (and the swept
			// gills behind its pivot) stay off the ground
			float tip = nod - bite * 0.4F;
			this.skull.x = 0.0F;
			this.skull.y = SKULL_Y - bite * 0.8F + m.breath() * 0.08F - SKULL_LIFT * Mth.sin(Math.max(0.0F, -tip));
			this.skull.z = -3.5F - bite * LUNGE_REACH;
			this.skull.yRot = turn;
			this.skull.xRot = tip;
			this.jaw.xRot = 0.05F + m.breath() * 0.03F + bite * LUNGE_JAW + (swim ? 0.04F + 0.04F * Mth.sin(age * 0.2F) : 0.0F);
			for (int i = 0; i < 6; i++) {
				boolean left = i >= 3;
				int row = i % 3;
				float s = left ? -1.0F : 1.0F;
				// the gills sweep back as it moves or swims, and flutter all the time
				float sweep = 0.4F + (swim ? 0.45F : 0.0F) + speed * 0.3F + bite * 0.2F;
				float flutter = Mth.sin(age * 0.22F + i * 1.1F) * 0.16F * (1.0F + row * 0.3F) + m.breath() * 0.05F;
				this.gills[i].yRot = s * (sweep + flutter);
				this.gills[i].zRot = s * (GILL_FAN[row] + Mth.sin(age * 0.17F + i * 0.7F) * 0.1F);
			}
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
	 * (0.18.103, patch 5; 0.19.62 a turtle on four flippers) Gyre: a broad domed shell over a pale plastron, four
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

	/**
	 * (0.18.103, patch 5; 0.19.66 a grasshopper) Locust: lean, wing cases folded down its back, long feelers that twitch,
	 * spurs on its legs. Now a big grasshopper, not a man: a long level body (head, thorax, two abdomen segments), folded
	 * wings on its back, feelers that sway and stream back in the air, two short pairs of walking legs that step in an
	 * insect's tripod gait (front-left, middle-right and hind-left together, then the other three) and two huge folded
	 * hind legs (thigh up and back, shin folded forward to the ground). Every leg is solved by two-bone IK from its hip
	 * to a foot target, so it stays joined and its foot stays on the ground while it walks. Crouching (the Crash Leap
	 * charge) lowers the body and folds the hind legs tighter; rising in a leap (or the attack swing, one hind leg) kicks
	 * them straight back, and the wings open and flutter in the air. The humanoid parts stay, hidden: the body carries the
	 * insignia on the thorax's back.
	 */
	static final class Locust extends FormModel {
		// ---- tuning ----
		/** The thorax's height above the ground (model y), how far crouching and a landing sink it, how much a step bobs it, how much it trembles when crouched. */
		static final float BODY_Y = 15.0F, CROUCH_DROP = 2.6F, LAND_DIP = 1.8F, BOB = 0.35F, TREMBLE = 0.12F;
		/** The renderer draws a sneaking player this many pixels lower (AvatarRenderer.getRenderOffset): crouched, the feet are planted that much higher to stay on the ground. */
		static final float CROUCH_SINK = 2.0F;
		/** How fast the legs step against the walk, and how far (pixels) the walking legs and the hind legs stride and lift a foot. */
		static final float GAIT = 0.6662F * 2.2F, STRIDE = 3.2F, LIFT = 2.6F, HIND_STRIDE = 2.0F, HIND_LIFT = 1.6F;
		/** In the air: how high the walking feet are drawn up (resting, and extra while rising), and how far the front feet reach forward when falling. */
		static final float TUCK = 2.5F, TUCK_RISE = 3.5F, REACH = 3.0F;
		/** The walking feet spread this much wider crouched and in the air, and are never drawn up closer than this under their hip (a short leg's knee would fold into the body). */
		static final float CROUCH_SPREAD = 2.0F, AIR_SPREAD = 2.0F, FOOT_BELOW = 4.0F;
		/** The hind feet stand this far above the ground line, so the slanted shin's lower edge rests on it instead of sinking in. */
		static final float HIND_FOOT_LIFT = 0.6F;
		/** A hind leg's kick: how far out it already is in the air before the rise, and the extra pull of the attack swing on the left leg (the right goes fully). */
		static final float KICK_AIR = 0.4F, KICK_SWING_LEFT = 0.35F;
		/** Body lean (radians): nose up rising, nose down falling, a kick's lunge, a step's roll. */
		static final float RISE_PITCH = 0.3F, FALL_PITCH = 0.2F, KICK_PITCH = 0.1F, ROLL = 0.03F;
		/** The bones (pixels): the walking legs' femur and tibia, the hind legs' thigh and shin, a feeler's two halves. */
		private static final float FEMUR = 5.0F, TIBIA = 9.0F, THIGH = 10.0F, SHIN = 14.0F, FEELER = 7.0F, FEELER_TIP = 6.0F;
		/** Where the legs join the thorax (x out from the middle, y down, z back) and where their feet rest: front, middle, hind. */
		private static final float[] HIP_X = {3.0F, 3.0F, 3.4F}, HIP_Y = {1.5F, 1.5F, 0.4F}, HIP_Z = {-2.8F, 0.6F, 3.2F};
		private static final float[] REST_X = {7.0F, 7.6F, 5.6F}, REST_Z = {-7.5F, 1.6F, 6.0F};
		/** The hind feet when crouched: closer in and forward, so the knees fold up. */
		private static final float CROUCH_HIND_X = 5.0F, CROUCH_HIND_Z = 4.0F;
		/** A fully kicked hind foot, from its hip (x out, y down, z back): nearly the legs' whole length straight back. */
		private static final float KICK_X = 1.2F, KICK_Y = 5.0F, KICK_Z = 21.5F;
		/** Off the ground a kicked hind foot may reach this far below the ground line (so a nose-up push-off still kicks straight). */
		private static final float KICK_BELOW = 6.0F;

		private final ModelPart thorax, abdomen, tail;
		/** Right then left. */
		private final ModelPart[] wing = new ModelPart[2], wingTip = new ModelPart[2], feeler = new ModelPart[2], feelerTip = new ModelPart[2];
		/** Front right, front left, middle right, middle left. */
		private final ModelPart[] femur = new ModelPart[4], tibia = new ModelPart[4];
		private final ModelPart[] thigh = new ModelPart[2], shin = new ModelPart[2];
		/** Scratch: the solved knee and foot, a rotated point, and the body's lean for this frame. */
		private final float[] knee = new float[3], foot = new float[3], pt = new float[3];
		private float sinP, cosP, sinR, cosR;

		Locust() {
			super(bake(mesh()), "locust");
			ModelPart root = this.root();
			this.thorax = root.getChild("thorax");
			this.abdomen = this.thorax.getChild("abdomen");
			this.tail = this.abdomen.getChild("tail");
			for (int i = 0; i < 2; i++) {
				String side = i == 0 ? "right" : "left";
				this.wing[i] = this.thorax.getChild(side + "_wing");
				this.wingTip[i] = this.wing[i].getChild("tip");
				this.feeler[i] = this.head.getChild(side + "_feeler");
				this.feelerTip[i] = this.feeler[i].getChild("tip");
				this.thigh[i] = root.getChild(side + "_thigh");
				this.shin[i] = root.getChild(side + "_shin");
			}
			for (int i = 0; i < 4; i++) {
				String side = i % 2 == 0 ? "right" : "left";
				String pair = i < 2 ? "front" : "mid";
				this.femur[i] = root.getChild(pair + "_" + side + "_femur");
				this.tibia[i] = root.getChild(pair + "_" + side + "_tibia");
			}
			// the plate on the thorax's back: the (hidden) body lies face-up there, a little forward of the wings
			this.insigniaY = 0.5F;
			this.insigniaZ = -2.5F;
			this.insigniaSize = 0.9F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -3.5F, -6.0F, 6.0F, 7.0F, 6.0F),
				PartPose.offset(0.0F, BODY_Y + 0.5F, -3.6F));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			for (int i = 0; i < 2; i++) {
				boolean left = i == 1;
				String side = left ? "left" : "right";
				float sx = left ? 1.0F : -1.0F;
				CubeListBuilder base = CubeListBuilder.create().texOffs(52, 64);
				CubeListBuilder tipBox = CubeListBuilder.create().texOffs(56, 64);
				if (left) {
					base.mirror();
					tipBox.mirror();
				}
				PartDefinition feeler = head.addOrReplaceChild(side + "_feeler", base.addBox(-0.5F, 0.0F, -0.5F, 1.0F, FEELER, 1.0F),
					PartPose.offsetAndRotation(sx * 1.5F, -3.4F, -5.2F, -0.9F, 0.0F, sx * -0.3F));
				feeler.addOrReplaceChild("tip", tipBox.addBox(-0.5F, 0.0F, -0.5F, 1.0F, FEELER_TIP, 1.0F), PartPose.offsetAndRotation(0.0F, FEELER, 0.0F, 0.35F, 0.0F, 0.0F));
			}
			// the body: thorax (the pronotum on top), two abdomen segments behind it
			PartDefinition thorax = root.addOrReplaceChild("thorax", CubeListBuilder.create().texOffs(0, 64).addBox(-3.0F, -2.5F, -4.0F, 6.0F, 5.0F, 8.0F),
				PartPose.offset(0.0F, BODY_Y, 0.0F));
			PartDefinition abdomen = thorax.addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(28, 64).addBox(-2.5F, -2.5F, 0.0F, 5.0F, 5.0F, 7.0F),
				PartPose.offset(0.0F, 0.0F, 3.8F));
			abdomen.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 78).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 6.0F), PartPose.offset(0.0F, 0.0F, 6.8F));
			// folded wings, a roof over the back, each with a tip that flutters
			for (int i = 0; i < 2; i++) {
				boolean left = i == 1;
				String side = left ? "left" : "right";
				CubeListBuilder wing = CubeListBuilder.create().texOffs(20, 78);
				CubeListBuilder tipBox = CubeListBuilder.create().texOffs(44, 78);
				if (left) {
					wing.mirror().addBox(0.0F, -1.0F, 0.0F, 3.0F, 1.0F, 9.0F);
					tipBox.mirror().addBox(0.0F, -1.0F, 0.0F, 3.0F, 1.0F, 6.0F);
				} else {
					wing.addBox(-3.0F, -1.0F, 0.0F, 3.0F, 1.0F, 9.0F);
					tipBox.addBox(-3.0F, -1.0F, 0.0F, 3.0F, 1.0F, 6.0F);
				}
				PartDefinition w = thorax.addOrReplaceChild(side + "_wing", wing, PartPose.offsetAndRotation(left ? 0.1F : -0.1F, -2.5F, 2.5F, -0.08F, 0.0F, left ? 0.3F : -0.3F));
				w.addOrReplaceChild("tip", tipBox, PartPose.offsetAndRotation(0.0F, 0.0F, 8.8F, -0.06F, 0.0F, 0.0F));
			}
			// legs: the walking pairs (femur and tibia, each a root part placed by the animation) and the hind pair
			for (int i = 0; i < 4; i++) {
				boolean left = i % 2 == 1;
				String name = (i < 2 ? "front_" : "mid_") + (left ? "left" : "right");
				CubeListBuilder femur = CubeListBuilder.create().texOffs(20, 90);
				CubeListBuilder tibia = CubeListBuilder.create().texOffs(28, 90);
				if (left) {
					femur.mirror();
					tibia.mirror();
				}
				root.addOrReplaceChild(name + "_femur", femur.addBox(-1.0F, 0.0F, -1.0F, 2.0F, FEMUR, 2.0F), PartPose.ZERO);
				root.addOrReplaceChild(name + "_tibia", tibia.addBox(-0.5F, 0.0F, -0.5F, 1.0F, TIBIA, 1.0F), PartPose.ZERO);
			}
			for (int i = 0; i < 2; i++) {
				boolean left = i == 1;
				String side = left ? "left" : "right";
				CubeListBuilder thigh = CubeListBuilder.create().texOffs(0, 90);
				CubeListBuilder shin = CubeListBuilder.create().texOffs(12, 90);
				if (left) {
					thigh.mirror();
					shin.mirror();
				}
				root.addOrReplaceChild(side + "_thigh", thigh.addBox(-1.5F, 0.0F, -1.5F, 3.0F, THIGH, 3.0F), PartPose.ZERO);
				root.addOrReplaceChild(side + "_shin", shin.addBox(-1.0F, 0.0F, -1.0F, 2.0F, SHIN, 2.0F), PartPose.ZERO);
			}
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.hat.visible = this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
			this.body.visible = false;
			this.head.resetPose();
			this.body.resetPose();
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			float walk = state.walkAnimationPos * GAIT;
			boolean crouch = m.crouching();
			float air = m.air();
			float ground = 1.0F - air;
			// rising is negative fall: a leap's push-off
			float rise = Mth.clamp(-m.fall() * 2.5F, 0.0F, 1.0F) * air;
			float drop = Mth.clamp(m.fall() * 2.0F, 0.0F, 1.0F) * air;
			float swing = Mth.clamp(state.swingAnimation, 0.0F, 1.0F);
			float snap = Mth.sin(swing * (float)Math.PI);
			// the ground line in model space: crouched, the renderer draws the whole model CROUCH_SINK lower
			float floor = 24.0F - (crouch ? CROUCH_SINK : 0.0F);
			float by = BODY_Y + (crouch ? CROUCH_DROP - CROUCH_SINK : 0.0F) + m.land() * LAND_DIP - Mth.cos(walk * 2.0F) * BOB * speed * ground
				+ (crouch ? Mth.sin(age * 2.3F) * TREMBLE : 0.0F) + m.breath() * 0.1F;
			float pitch = -RISE_PITCH * rise + FALL_PITCH * drop + snap * KICK_PITCH + Mth.sin(walk * 2.0F) * 0.02F * speed * ground;
			float roll = Mth.sin(walk) * ROLL * speed * ground - m.turn() * 0.08F;
			this.sinP = Mth.sin(pitch);
			this.cosP = Mth.cos(pitch);
			this.sinR = Mth.sin(roll);
			this.cosR = Mth.cos(roll);
			// the body: thorax (and with it the wings and abdomen), the hidden body for the plate, the head at the front
			this.thorax.x = 0.0F;
			this.thorax.y = by;
			this.thorax.z = 0.0F;
			this.thorax.xRot = pitch;
			this.thorax.zRot = roll;
			this.body.x = 0.0F;
			this.body.y = by;
			this.body.z = 0.0F;
			this.body.xRot = pitch - (float)Math.PI * 0.5F;
			this.body.zRot = roll;
			this.rot(0.0F, 0.5F, -3.6F);
			this.head.x = this.pt[0];
			this.head.y = by + this.pt[1];
			this.head.z = this.pt[2];
			float nod = Mth.sin(walk * 2.0F - 0.8F) * 0.04F * speed * ground;
			this.head.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -1.0F, 1.0F);
			this.head.xRot = pitch + Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -0.7F, 0.9F) * 0.8F + nod + snap * 0.15F;
			this.head.zRot = roll;
			// the abdomen sways with the turn and the stride, lifts as it falls; the tail trails a beat after it
			float lift = tailLift(m);
			this.abdomen.xRot = 0.04F + lift * 0.35F + m.breath() * 0.015F;
			this.abdomen.yRot = m.trail() * 0.2F + Mth.sin(walk) * 0.06F * speed * ground;
			this.tail.xRot = 0.02F + lift * 0.3F;
			this.tail.yRot = m.trail() * 0.25F + Mth.sin(walk - 0.8F) * 0.05F * speed * ground;
			float breathe = 1.0F + m.breath() * 0.025F;
			this.abdomen.xScale = this.abdomen.yScale = breathe;
			this.wings(age, air, rise, speed, m);
			this.feelers(age, air, rise, m);
			// the walking legs: tripod gait, the front pair also fidgeting at rest
			for (int i = 0; i < 4; i++) {
				int pair = i / 2;
				int s = i % 2;
				float sx = s == 0 ? -1.0F : 1.0F;
				this.hip(pair, sx, by);
				float hx = this.pt[0], hy = this.pt[1], hz = this.pt[2];
				float cycle = frac(walk / ((float)Math.PI * 2.0F) + ((pair + s) % 2) * 0.5F);
				float dz;
				float up = 0.0F;
				if (cycle < 0.5F) {
					dz = -STRIDE + 2.0F * STRIDE * (cycle * 2.0F);
				} else {
					float u = (cycle - 0.5F) * 2.0F;
					dz = STRIDE - 2.0F * STRIDE * u;
					up = Mth.sin(u * (float)Math.PI) * LIFT;
				}
				float idle = (1.0F - speed) * ground;
				float fidgetZ = pair == 0 ? Mth.sin(age * 0.09F + s * 1.7F) * 0.5F * idle : 0.0F;
				float fidgetUp = pair == 0 ? Math.max(0.0F, Mth.sin(age * 0.045F + s * 3.1F)) * 2.0F * idle : 0.0F;
				float tx = sx * (REST_X[pair] + (crouch ? CROUCH_SPREAD : 0.0F) + air * AIR_SPREAD);
				float tz = REST_Z[pair] + dz * speed * ground + fidgetZ - drop * (pair == 0 ? REACH : REACH * 0.4F);
				float tuck = air * (TUCK + TUCK_RISE * rise - TUCK * 0.6F * drop);
				float ty = Math.max(floor - up * speed * ground - fidgetUp - tuck, Math.min(floor, hy + FOOT_BELOW));
				this.solve(hx, hy, hz, tx, Math.min(floor, ty), tz, FEMUR, TIBIA, sx * 0.8F, -1.0F, 0.0F);
				aim(this.femur[i], hx, hy, hz, this.knee[0], this.knee[1], this.knee[2]);
				aim(this.tibia[i], this.knee[0], this.knee[1], this.knee[2], this.foot[0], this.foot[1], this.foot[2]);
			}
			// the hind legs: folded (thigh up and back, shin forward to the ground), kicked straight back in a leap or a swing
			for (int s = 0; s < 2; s++) {
				float sx = s == 0 ? -1.0F : 1.0F;
				this.hip(2, sx, by);
				float hx = this.pt[0], hy = this.pt[1], hz = this.pt[2];
				float cycle = frac(walk / ((float)Math.PI * 2.0F) + s * 0.5F);
				float dz;
				float up = 0.0F;
				if (cycle < 0.5F) {
					dz = -HIND_STRIDE + 2.0F * HIND_STRIDE * (cycle * 2.0F);
				} else {
					float u = (cycle - 0.5F) * 2.0F;
					dz = HIND_STRIDE - 2.0F * HIND_STRIDE * u;
					up = Mth.sin(u * (float)Math.PI) * HIND_LIFT;
				}
				float tx = sx * (crouch ? CROUCH_HIND_X : REST_X[2]);
				float tz = (crouch ? CROUCH_HIND_Z : REST_Z[2]) + dz * speed * ground;
				float ty = floor - HIND_FOOT_LIFT - up * speed * ground;
				// the kick: out in the air (fuller the harder it rises), and one leg snapping back on the swing
				float kick = air * Mth.clamp(KICK_AIR + rise * (1.0F - KICK_AIR) - drop * 0.15F, 0.0F, 1.0F);
				kick = Math.max(kick, s == 0 ? snap : snap * KICK_SWING_LEFT);
				this.rot(sx * KICK_X, KICK_Y, KICK_Z);
				float kx = hx + this.pt[0], ky = hy + this.pt[1], kz = hz + this.pt[2];
				this.solve(hx, hy, hz, Mth.lerp(kick, tx, kx), Math.min(floor + air * KICK_BELOW, Mth.lerp(kick, ty, ky)), Mth.lerp(kick, tz, kz), THIGH, SHIN, sx * 0.15F, -0.9F, 0.9F);
				aim(this.thigh[s], hx, hy, hz, this.knee[0], this.knee[1], this.knee[2]);
				aim(this.shin[s], this.knee[0], this.knee[1], this.knee[2], this.foot[0], this.foot[1], this.foot[2]);
			}
		}

		/** The wing cases: a roof over the back, opening and fluttering in the air, their tips trembling a little always. */
		private void wings(float age, float air, float rise, float speed, XenoMotion.Read m) {
			float open = air * (0.3F + 0.4F * rise) + m.land() * 0.3F;
			for (int i = 0; i < 2; i++) {
				float sx = i == 0 ? -1.0F : 1.0F;
				float flutter = Mth.sin(age * 1.9F + i * 0.7F) * (0.03F + air * 0.14F);
				ModelPart w = this.wing[i];
				w.zRot = sx * (0.3F - open * 0.9F + flutter);
				w.yRot = sx * open * 0.45F;
				w.xRot = -0.08F + Mth.sin(age * 1.9F + 1.3F) * air * 0.06F;
				this.wingTip[i].xRot = -0.06F + Mth.sin(age * 2.3F + i * 0.6F) * (0.02F + air * 0.16F + speed * 0.03F);
				this.wingTip[i].zRot = sx * Mth.sin(age * 2.1F) * air * 0.08F;
			}
		}

		/** The feelers: swaying on their own, lagging a turn, streaming back in the air; the tips curl and trail behind the bases. */
		private void feelers(float age, float air, float rise, XenoMotion.Read m) {
			for (int i = 0; i < 2; i++) {
				float sx = i == 0 ? -1.0F : 1.0F;
				float t = age * 0.12F + i * 1.9F;
				float sway = Mth.sin(t) * 0.12F + Mth.sin(t * 2.3F) * 0.04F - m.trail() * 0.3F;
				// where the feeler points (head space, x out, y down, z back), then aimed from its socket
				float dx = sx * 0.28F + sway;
				float dy = -0.62F + Mth.sin(t * 0.7F) * 0.05F;
				float dz = -0.78F + air * (0.7F + 0.4F * rise);
				float ox = sx * 1.5F, oy = -3.4F, oz = -5.2F;
				aim(this.feeler[i], ox, oy, oz, ox + dx, oy + dy, oz + dz);
				this.feelerTip[i].xRot = 0.35F + air * 0.5F + Mth.sin(t - 0.9F) * 0.15F;
				this.feelerTip[i].zRot = Mth.sin(t - 1.0F) * 0.2F;
			}
		}

		/** Sets {@code pt} to a body-space offset turned by the body's lean (pitch, then roll). */
		private void rot(float x, float y, float z) {
			float y1 = y * this.cosP - z * this.sinP;
			float z1 = y * this.sinP + z * this.cosP;
			this.pt[0] = x * this.cosR - y1 * this.sinR;
			this.pt[1] = x * this.sinR + y1 * this.cosR;
			this.pt[2] = z1;
		}

		/** Sets {@code pt} to where a hip (0 front, 1 middle, 2 hind; sx -1 right, +1 left) is, in model space, for a thorax at height {@code by}. */
		private void hip(int pair, float sx, float by) {
			this.rot(sx * HIP_X[pair], HIP_Y[pair], HIP_Z[pair]);
			this.pt[1] += by;
		}

		/**
		 * Two-bone IK: the knee of a leg of bones {@code l1}, {@code l2} from the hip (h) toward the foot target (f), bending
		 * toward {@code pole}. Writes {@code knee} and {@code foot} (where the foot really is: the target, held to what the
		 * bones can reach, so the leg is never torn off its hip nor bent past straight).
		 */
		private void solve(float hx, float hy, float hz, float fx, float fy, float fz, float l1, float l2, float px, float py, float pz) {
			float dx = fx - hx, dy = fy - hy, dz = fz - hz;
			float d = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (d < 1.0E-4F) {
				dx = 0.0F;
				dy = 1.0F;
				dz = 0.0F;
				d = 1.0F;
			}
			float ux = dx / d, uy = dy / d, uz = dz / d;
			float reach = Mth.clamp(d, Math.abs(l1 - l2) + 0.1F, l1 + l2 - 0.05F);
			// the pole with its part along the leg taken out, to say which way the knee goes
			float dot = px * ux + py * uy + pz * uz;
			float qx = px - dot * ux, qy = py - dot * uy, qz = pz - dot * uz;
			float ql = (float)Math.sqrt(qx * qx + qy * qy + qz * qz);
			if (ql < 1.0E-3F) {
				qx = 0.0F;
				qy = -1.0F;
				qz = 0.0F;
				ql = 1.0F;
			}
			qx /= ql;
			qy /= ql;
			qz /= ql;
			float a = (l1 * l1 - l2 * l2 + reach * reach) / (2.0F * reach);
			float h = (float)Math.sqrt(Math.max(0.0F, l1 * l1 - a * a));
			this.knee[0] = hx + ux * a + qx * h;
			this.knee[1] = hy + uy * a + qy * h;
			this.knee[2] = hz + uz * a + qz * h;
			this.foot[0] = hx + ux * reach;
			this.foot[1] = hy + uy * reach;
			this.foot[2] = hz + uz * reach;
		}

		/** Puts a box that hangs along +y from its pivot at (sx, sy, sz) pointing at (ex, ey, ez) (x, then z; the ZYX order turns the leg as it needs). */
		private static void aim(ModelPart part, float sx, float sy, float sz, float ex, float ey, float ez) {
			float dx = ex - sx, dy = ey - sy, dz = ez - sz;
			float len = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
			part.x = sx;
			part.y = sy;
			part.z = sz;
			part.yRot = 0.0F;
			if (len < 1.0E-4F) {
				part.xRot = part.zRot = 0.0F;
				return;
			}
			dx /= len;
			dy /= len;
			dz /= len;
			part.xRot = (float)Math.asin(Mth.clamp(dz, -1.0F, 1.0F));
			part.zRot = (float)Math.atan2(-dx, dy);
		}

		private static float frac(float v) {
			return v - (float)Math.floor(v);
		}
	}

	/**
	 * (0.18.103, patch 5; 0.19.66 a hopping frog) Croaker: a squat, low FROG (about nine pixels tall), no longer a man in
	 * green: a wide flat torso sitting with its nose tipped up, a broad head with a wide mouth and two big eyes on top, a
	 * throat sac under the jaw, short front legs planted under its chest and big hind legs folded Z-wise along its flanks.
	 * It hops instead of walking: the walk's clock drives a hop (coil, push, flight, landing) in which the hind legs are
	 * solved by two-bone IK so the foot stays flat on the ground until the leg is straight, the hips rising as it
	 * unfolds, then the legs trail behind in the air and tuck for the landing. At rest the throat sac swells and sinks
	 * slowly and the eyes blink; on an attack swing (the tongue, the croak) the mouth opens and the sac balloons. The
	 * hidden humanoid body is only the anchor of the insignia, laid face-up on the frog's back.
	 */
	static final class Croaker extends FormModel {
		// ---- tuning ----
		/** Where the torso's centre sits at rest (model y, z) and how far its nose is tipped up (radians, - is up). */
		static final float TORSO_Y = 19.9F, TORSO_Z = 2.0F, TILT = -0.2F;
		/** How far it sinks when coiled (pixels), how far the nose lifts as the legs push, the extra tip when coiled and the tip when it falls. */
		static final float COIL_DROP = 1.0F, PUSH_TILT = 0.2F, COIL_TILT = 0.08F, FALL_TILT = 0.4F;
		/** How much the torso flattens (and widens) when coiled or landing. */
		static final float SQUASH = 0.14F;
		/** The hop: radians of clock per unit of walk, how much walking speed it takes to hop fully, the arc's height and the hips' rise as the legs unfold (pixels). */
		static final float HOP_RATE = 0.6662F * 0.85F, SPEED_GAIN = 1.4F, HOP_HEIGHT = 2.2F, PUSH_LIFT = 3.3F;
		/** The hop's timeline (fractions of a cycle): the push ends, the flight ends, the landing settles; how deep the landing coils; how much a landing adds to the squash. */
		static final float PUSH_END = 0.15F, FLIGHT_END = 0.6F, LAND_END = 0.7F, LAND_COIL = 0.6F, LAND_SQUASH = 0.8F;
		/** How far the front legs reach forward in the air (radians per unit of reach). */
		static final float FRONT_REACH = 1.0F;
		/** The throat sac: its idle swell, the croak's swell, how far the jaw opens and how far the head lifts for a croak. */
		static final float IDLE_SWELL = 0.3F, CROAK_SWELL = 0.7F, OPEN = 0.5F, CROAK_LIFT = 0.35F;
		/** The hind leg: thigh and shin lengths, and the hip's place on the torso (x out from the middle; y, z from the torso's centre). */
		private static final float L1 = 4.0F, L2 = 5.0F, HIP_X = 3.6F, HIP_Y = 0.5F, HIP_Z = 2.5F;
		/** The ankle's place from the hip (y down, z back), folded and fully stretched, and its height standing flat (the foot is 2 thick). */
		private static final float REST_DY = 2.9F, REST_DZ = -0.9F, EXT_DY = 3.6F, EXT_DZ = 6.0F, ANKLE_GROUND = 23.0F;
		/** The head's hinge on the torso (y, z from its centre), the front legs' shoulder (x, y, z) and the foot's length. */
		private static final float HEAD_Y = 0.2F, HEAD_Z = -3.2F, SHOULDER_X = 2.6F, SHOULDER_Y = 19.0F, SHOULDER_Z = -0.8F, FOOT_LEN = 3.5F;
		/** How far the head may nod down as you look down (radians of look), and the lowest the throat sac may hang (model y): the head lifts to keep it off the ground. */
		private static final float LOOK_DOWN = 0.5F, SAC_FLOOR = 23.9F;
		/** The jaw's hinge under the head (y, z), the sac's place under the jaw (y, z), its height and half its depth: the mesh's numbers, which the sac's floor check reuses. */
		private static final float JAW_Y = 0.5F, JAW_Z = -1.0F, SAC_Y = 1.0F, SAC_Z = -2.0F, SAC_H = 2.0F, SAC_HALF = 1.5F;

		private final ModelPart torso, jaw, sac, eyeRight, eyeLeft;
		private final ModelPart thighRight, thighLeft, shinRight, shinLeft, footRight, footLeft, handRight, handLeft;
		/** The hop's phase this frame: coil, extension, arc. */
		private final float[] hop = new float[3];

		Croaker() {
			super(bake(mesh()), "croaker");
			ModelPart root = this.root();
			this.torso = root.getChild("torso");
			this.jaw = this.head.getChild("jaw");
			this.sac = this.jaw.getChild("sac");
			this.eyeRight = this.head.getChild("right_eye");
			this.eyeLeft = this.head.getChild("left_eye");
			this.thighRight = root.getChild("right_thigh");
			this.thighLeft = root.getChild("left_thigh");
			this.shinRight = this.thighRight.getChild("shin");
			this.shinLeft = this.thighLeft.getChild("shin");
			this.footRight = this.shinRight.getChild("foot");
			this.footLeft = this.shinLeft.getChild("foot");
			this.handRight = this.rightLeg.getChild("hand");
			this.handLeft = this.leftLeg.getChild("hand");
			// the hidden body lies face-up on the frog's back (as Skitter's does): the plate sits on top of the torso
			this.insigniaY = 0.0F;
			this.insigniaZ = -2.0F;
			this.insigniaSize = 1.0F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the head: wide and flat, the jaw hinged under it, the throat sac hung from the jaw, the eyes on top
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -2.5F, -5.0F, 7.0F, 3.0F, 5.0F),
				PartPose.offset(0.0F, 19.1F, -1.1F));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 64).addBox(-3.0F, 0.0F, -4.0F, 6.0F, 1.0F, 4.0F),
				PartPose.offset(0.0F, JAW_Y, JAW_Z));
			jaw.addOrReplaceChild("sac", CubeListBuilder.create().texOffs(40, 64).addBox(-2.0F, 0.0F, -SAC_HALF, 4.0F, SAC_H, 2.0F * SAC_HALF),
				PartPose.offset(0.0F, SAC_Y, SAC_Z));
			head.addOrReplaceChild("right_eye", CubeListBuilder.create().texOffs(24, 64).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F),
				PartPose.offset(-2.4F, -1.8F, -2.0F));
			head.addOrReplaceChild("left_eye", CubeListBuilder.create().texOffs(32, 64).mirror().addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F),
				PartPose.offset(2.4F, -1.8F, -2.0F));
			// the body: wide and flat
			root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 72).addBox(-3.0F, -2.0F, -3.5F, 6.0F, 4.0F, 7.0F),
				PartPose.offset(0.0F, TORSO_Y, TORSO_Z));
			// the front legs are the man's legs (so the pose code still finds them): short, a flat hand on each
			PartDefinition right = root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F),
				PartPose.offset(-SHOULDER_X, SHOULDER_Y, SHOULDER_Z));
			right.addOrReplaceChild("hand", CubeListBuilder.create().texOffs(28, 106).addBox(-1.5F, 0.0F, -3.5F, 3.0F, 1.0F, 4.0F),
				PartPose.offset(0.0F, 4.0F, 0.0F));
			PartDefinition left = root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 48).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F),
				PartPose.offset(SHOULDER_X, SHOULDER_Y, SHOULDER_Z));
			left.addOrReplaceChild("hand", CubeListBuilder.create().texOffs(42, 106).mirror().addBox(-1.5F, 0.0F, -3.5F, 3.0F, 1.0F, 4.0F),
				PartPose.offset(0.0F, 4.0F, 0.0F));
			// the hind legs, hip to thigh to shin to foot: each box runs a pixel past its joints so the bends have no gaps
			PartDefinition thighRight = root.addOrReplaceChild("right_thigh", CubeListBuilder.create().texOffs(0, 88).addBox(-1.0F, -1.0F, -5.0F, 2.0F, 2.0F, 6.0F),
				PartPose.offset(-HIP_X, 21.0F, 6.0F));
			PartDefinition shinRight = thighRight.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(0, 96).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 6.0F),
				PartPose.offset(0.0F, 0.0F, -L1));
			shinRight.addOrReplaceChild("foot", CubeListBuilder.create().texOffs(0, 106).addBox(-1.5F, -1.0F, -3.5F, 3.0F, 2.0F, 4.0F),
				PartPose.offset(0.0F, 0.0F, L2));
			PartDefinition thighLeft = root.addOrReplaceChild("left_thigh", CubeListBuilder.create().texOffs(16, 88).mirror().addBox(-1.0F, -1.0F, -5.0F, 2.0F, 2.0F, 6.0F),
				PartPose.offset(HIP_X, 21.0F, 6.0F));
			PartDefinition shinLeft = thighLeft.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(16, 96).mirror().addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 6.0F),
				PartPose.offset(0.0F, 0.0F, -L1));
			shinLeft.addOrReplaceChild("foot", CubeListBuilder.create().texOffs(14, 106).mirror().addBox(-1.5F, -1.0F, -3.5F, 3.0F, 2.0F, 4.0F),
				PartPose.offset(0.0F, 0.0F, L2));
			return mesh;
		}

		/** The hop's phase at {@code u} (0-1 through a cycle) into {@link #hop}: coil (0-1), the legs' extension (0-1), the arc (0-1). */
		private void cycle(float u) {
			float coil;
			float extend;
			float arc = 0.0F;
			if (u < PUSH_END) {
				// pushing off: the coil unwinds, the legs unfold, the feet still on the ground
				float s = smooth(u / PUSH_END);
				coil = 1.0F - s;
				extend = s;
			} else if (u < FLIGHT_END) {
				// in the air: the legs trail behind, then fold up for the landing
				float f = (u - PUSH_END) / (FLIGHT_END - PUSH_END);
				arc = Mth.sin(f * (float)Math.PI);
				extend = 1.0F - smooth(Mth.clamp((f - 0.25F) / 0.75F, 0.0F, 1.0F));
				coil = 0.0F;
			} else if (u < LAND_END) {
				// touching down: it settles into a coil
				coil = LAND_COIL * (u - FLIGHT_END) / (LAND_END - FLIGHT_END);
				extend = 0.0F;
			} else {
				// gathering itself for the next hop
				coil = LAND_COIL + (1.0F - LAND_COIL) * (u - LAND_END) / (1.0F - LAND_END);
				extend = 0.0F;
			}
			this.hop[0] = coil;
			this.hop[1] = extend;
			this.hop[2] = arc;
		}

		private static float smooth(float s) {
			return s * s * (3.0F - 2.0F * s);
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.hat.visible = this.body.visible = this.rightArm.visible = this.leftArm.visible = false;
			for (ModelPart part : new ModelPart[] {this.head, this.body, this.rightLeg, this.leftLeg}) {
				part.resetPose();
			}
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			float speed = Mth.clamp(state.walkAnimationSpeed * SPEED_GAIN, 0.0F, 1.0F);
			float swing = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
			float air = Mth.clamp(m.air(), 0.0F, 1.0F);
			// the hop clock follows the walk, so it hops as fast as it moves and settles when it stops
			float turns = state.walkAnimationPos * HOP_RATE / ((float)Math.PI * 2.0F);
			this.cycle(turns - (float)Math.floor(turns));
			float coil = this.hop[0] * speed;
			float extend = this.hop[1] * speed;
			float arc = this.hop[2] * speed;
			if (state.isCrouching || Boolean.TRUE.equals(state.getData(XenoformClient.SNEAK))) {
				// low and flat, but it still straightens to push off
				coil = Math.max(coil, 0.9F * (1.0F - extend));
			}
			// a real fall or leap: stretched out behind, nose pitched with the fall; a landing squashes it flat
			float rising = Mth.clamp(-m.fall(), 0.0F, 1.0F);
			extend = Mth.lerp(air, extend, 0.75F + 0.25F * rising);
			arc *= 1.0F - air;
			coil = Math.min(1.3F, coil * (1.0F - air) + m.land() * LAND_SQUASH * (1.0F - extend));
			float lift = HOP_HEIGHT * arc + PUSH_LIFT * (float)Math.sqrt(extend);
			float tilt = TILT - extend * PUSH_TILT + coil * COIL_TILT + Mth.clamp(m.fall(), -1.0F, 1.0F) * air * FALL_TILT;
			float ty = TORSO_Y + coil * COIL_DROP - lift;

			// the torso: flattens when coiled, breathes at rest, swells a little on a croak
			float puff = 1.0F + swing * 0.06F;
			this.torso.setPos(0.0F, ty, TORSO_Z);
			this.torso.xRot = tilt;
			this.torso.yRot = m.turn() * 0.06F;
			this.torso.zRot = 0.0F;
			this.torso.yScale = (1.0F - SQUASH * coil + 0.02F * m.breath()) * puff;
			this.torso.xScale = (1.0F + 0.3F * SQUASH * coil) * puff;
			this.torso.zScale = (1.0F + 0.5F * SQUASH * coil) * puff;
			// the hidden body is the insignia's anchor: face-up on the frog's back, the plate flush with the top
			this.body.setPos(0.0F, ty, TORSO_Z);
			this.body.xRot = tilt - (float)Math.PI * 0.5F;
			this.insigniaY = 0.0F;
			this.insigniaZ = -2.0F * this.torso.yScale;
			this.insigniaSize = 1.0F;

			// the head rides the front of the torso, lifting out of a coil, looking where you look, thrown up for a croak
			float cs = Mth.cos(tilt);
			float sn = Mth.sin(tilt);
			this.head.setPos(0.0F, ty + HEAD_Y * cs - HEAD_Z * sn - coil * 0.7F, TORSO_Z + HEAD_Y * sn + HEAD_Z * cs);
			float pitch = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -0.7F, LOOK_DOWN);
			this.head.xRot = tilt * 0.8F + pitch * 0.5F - swing * CROAK_LIFT + m.land() * 0.2F * (1.0F - swing);
			this.head.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -0.9F, 0.9F);
			this.head.zRot = -m.turn() * 0.08F;

			// the throat sac swells and sinks, balloons on a croak with the mouth open; the eyes bulge with it and blink
			float idle = 0.5F + 0.5F * Mth.sin(age * 0.07F);
			float swell = IDLE_SWELL * idle + CROAK_SWELL * swing;
			this.jaw.xRot = OPEN * swing + 0.05F * idle;
			this.sac.yScale = 1.0F + 0.3F * swell;
			this.sac.xScale = this.sac.zScale = 1.0F + 0.5F * swell;
			// a croak looking down out of a deep coil would push the sac into the ground: lift the head just clear of it
			float jc = Mth.cos(this.jaw.xRot);
			float js = Mth.sin(this.jaw.xRot);
			float hc = Mth.cos(this.head.xRot);
			float hs = Mth.sin(this.head.xRot);
			float low = -Float.MAX_VALUE;
			for (int end = -1; end <= 1; end += 2) {
				// the sac's front and back bottom edges in the jaw's space, then the head's, then below the head's hinge
				float y1 = SAC_Y + SAC_H * this.sac.yScale;
				float z1 = SAC_Z + SAC_HALF * end * this.sac.zScale;
				float y2 = JAW_Y + y1 * jc - z1 * js;
				float z2 = JAW_Z + y1 * js + z1 * jc;
				low = Math.max(low, y2 * hc - z2 * hs);
			}
			this.head.y = Math.min(this.head.y, SAC_FLOOR - low);
			float blink = Math.max(0.0F, 1.0F - Math.abs(age % 90.0F - 2.0F) * 0.5F);
			float bulge = 1.0F + 0.12F * swell;
			this.eyeRight.xScale = this.eyeRight.zScale = this.eyeLeft.xScale = this.eyeLeft.zScale = bulge;
			this.eyeRight.yScale = this.eyeLeft.yScale = Mth.lerp(blink, bulge, 0.1F);

			// the front legs stay planted; they reach forward in the air and brace on a croak, the hands kept flat
			float reach = Math.max(Math.max(air, arc * 0.9F), extend * 0.5F);
			float brace = swing * 0.2F;
			this.rightLeg.setPos(-SHOULDER_X, SHOULDER_Y - lift, SHOULDER_Z);
			this.leftLeg.setPos(SHOULDER_X, SHOULDER_Y - lift, SHOULDER_Z);
			this.rightLeg.xRot = this.leftLeg.xRot = -reach * FRONT_REACH + brace;
			this.handRight.xRot = this.handLeft.xRot = reach * FRONT_REACH - brace;

			this.hind(ty, tilt, extend);
		}

		/**
		 * Both hind legs: the ankle goes where a foot would be (flat on the ground, sliding back as the leg unfolds, then
		 * hanging behind once the body has left it), and the thigh and shin are solved to reach it with the knee up and
		 * forward, so the leg never breaks at a joint.
		 */
		private void hind(float ty, float tilt, float extend) {
			float cs = Mth.cos(tilt);
			float sn = Mth.sin(tilt);
			float hipY = ty + HIP_Y * cs - HIP_Z * sn;
			float hipZ = TORSO_Z + HIP_Y * sn + HIP_Z * cs;
			float ankleY = Math.min(ANKLE_GROUND, hipY + Mth.lerp(extend, REST_DY, EXT_DY));
			float ankleZ = hipZ + Mth.lerp(extend, REST_DZ, EXT_DZ);
			float dy = ankleY - hipY;
			float dz = ankleZ - hipZ;
			float far = Math.max(0.01F, (float)Math.sqrt(dy * dy + dz * dz));
			float reach = Mth.clamp(far, Math.abs(L1 - L2) + 0.1F, L1 + L2 - 0.05F);
			float uy = dy / far;
			float uz = dz / far;
			dy = uy * reach;
			dz = uz * reach;
			// two-bone IK: the knee is a along the chord and h out of it, on the up-and-forward side
			float a = (L1 * L1 - L2 * L2 + reach * reach) / (2.0F * reach);
			float h = (float)Math.sqrt(Math.max(0.0F, L1 * L1 - a * a));
			float kneeY = hipY + a * uy + h * uz;
			float kneeZ = hipZ + a * uz - h * uy;
			float thigh = (float)Math.atan2(kneeY - hipY, -(kneeZ - hipZ));
			float shin = (float)Math.atan2(-(hipY + dy - kneeY), hipZ + dz - kneeZ);
			// the foot hangs flat on the ground and tips toes-down as the ankle rises off it
			float gap = Math.max(0.0F, ANKLE_GROUND - (hipY + dy));
			float foot = (float)Math.asin(Math.min(1.0F, gap / FOOT_LEN)) * 0.85F;
			for (int side = 0; side < 2; side++) {
				ModelPart t = side == 0 ? this.thighRight : this.thighLeft;
				ModelPart s = side == 0 ? this.shinRight : this.shinLeft;
				ModelPart f = side == 0 ? this.footRight : this.footLeft;
				t.setPos(side == 0 ? -HIP_X : HIP_X, hipY, hipZ);
				t.xRot = thigh;
				s.xRot = shin - thigh;
				f.xRot = foot - shin;
			}
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
	 * (0.18.109, Scan C; 0.19.62 a four-legged wolf) Howl: a wolf's muzzle and tall ears, a bushy tail that swings as it
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
	 * (0.18.109, Scan C; 0.19.62 a cat on four legs) Prowl: a big cat, long and low: a chest and a haunch that flex
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
	 * (0.18.109, Scan C) Tusk: a flat snout with two curved tusks, and a stiff mane down its back; (0.19.62) a boar on
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

	/**
	 * (0.18.109, Scan C; 0.19.67 a bird of prey) Talon: a hooked beak, broad feathered wings and a tail fan, scaly legs
	 * with hooked talons; now a hawk, not a man. On the ground it perches upright (the body leaning 60 degrees up from
	 * level, the wings folded at its sides, the tail hanging behind), the head turning where it looks with the beak
	 * hooked below it, and it walks in short two-footed hops. Flying, the body goes level, the wings spread (two parts,
	 * an arm and a hand with four long flight feathers) and beat as it climbs ({@code Flight.wings}), hold out stiff in
	 * a glide ({@code Flight.glide}) and sweep back on a stoop ({@code Flight.fold}), with the legs tucked under the tail
	 * (talons forward on a stoop). A strike rakes both legs forward and pecks the head down. The man's parts are hidden
	 * (the arm stays for the first-person hand); the torso is the rig every bird part hangs on, so the insignia plate sits on
	 * its breast. Wind's cruise tilt ({@code WindPose}) and the glide's lean ({@code XenoformFlightPose#rotate}) turn the whole
	 * drawn model, so the body undoes the part of them it does not want ({@link #setupAnim}).
	 */
	static final class Talon extends FormModel {
		// ---- tuning ----
		/** The perch: the body leans this far from upright (radians; 0.52 is 60 degrees up from level), more crouched or striking, and the rock of a hop. */
		static final float PERCH_TILT = 0.52F, CROUCH_TILT = 0.95F, STRIKE_TILT = 0.4F, ROCK = 0.07F;
		/** Standing legs (radians from straight down, + is back): the thigh forward, the shin back, and how much more a crouch bends them. */
		static final float THIGH_REST = -0.45F, SHIN_REST = 0.25F, CROUCH_THIGH = -0.4F, CROUCH_SHIN = 0.55F;
		/** The hop: radians of clock per unit of walk, its height (pixels), how far the legs tuck in it (thigh, shin), and the legs' alternate shuffle. */
		static final float HOP = 1.6F, HOP_LIFT = 1.4F, HOP_TUCK_THIGH = 0.45F, HOP_TUCK_SHIN = 0.55F, SHUFFLE = 0.2F;
		/** The head bobs forward and back as it hops (pixels). */
		static final float HEAD_BOB = 0.8F;
		/** A strike: how far the thighs rake forward and the shins with them (radians), how far the body lunges ahead (pixels) and the head pecks down. */
		static final float STRIKE_THIGH = 1.35F, STRIKE_SHIN = 0.25F, STRIKE_LUNGE = 1.5F, PECK = 0.6F;
		/** In the air: the body's height (model y; the ground is 24), how far it bobs with each wingbeat (pixels) and the wingbeat's rate (radians a tick). */
		static final float AIR_Y = 10.5F, WING_BOB = 0.9F, BEAT = 0.95F;
		/** Falling (not flying), the wings come this far open and flutter this hard. */
		static final float FALL_SPREAD = 0.85F, FALL_FLAP = 0.6F;
		/** The stroke: the wing's mean lift above level and how far it swings (radians), the nose-down of a glide and the body's pitch with each beat. */
		static final float FLAP_BIAS = 0.1F, FLAP_SPAN = 0.55F, GLIDE_PITCH = 0.2F, BEAT_PITCH = 0.06F;
		/** A glide holds the wings up at this angle and rocks them a little. */
		static final float GLIDE_DIHEDRAL = 0.16F, GLIDE_ROCK = 0.04F;
		/** The wings on a stoop sweep back this far; the hand follows this much more, and lags a beat this much. */
		static final float SWEEP = 1.15F, HAND_SWEEP = 0.45F, HAND_LAG = 0.55F;
		/** Folded: the wing leans out from the body this much and its hand folds back along the body this much (radians); spread, the wing pitches about its length this much. */
		static final float FOLD_OUT = 0.1F, FOLD_HAND = 0.75F, WING_PITCH = 0.2F;
		/** Legs in the air (radians back from hanging): hovering, tucked in a glide, forward on a stoop; the shin's and the foot's bend. */
		private static final float HOVER_LEG = 0.7F, TUCK_LEG = 1.15F, STOOP_LEG = -0.9F;
		private static final float HOVER_SHIN = 0.55F, TUCK_SHIN = 0.9F, STOOP_SHIN = 0.1F, HOVER_CURL = 0.5F, STOOP_CURL = -0.3F;
		/** The tail: how it rests (the feathers' pitch), its fan (radians between feathers) perched and spread, and its lowest pitch (so its tip clears the ground). */
		static final float TAIL_REST = 0.15F, TAIL_FAN = 0.12F, TAIL_FAN_WIDE = 0.34F, TAIL_LOW = -0.05F;
		/** Where the parts hang in the torso's space (x of the right side; the left mirrors): the shoulders, the head and the hips (z and y as well). */
		private static final float SHOULDER_X = 3.7F, SHOULDER_Y = -3.0F, SHOULDER_Z = 0.3F;
		private static final float HEAD_Y = -5.0F, HEAD_Z = -0.5F;
		private static final float HIP_X = 1.9F, HIP_Y = 3.0F, HIP_Z = 0.0F;
		/** The legs' bones (pixels) and the foot's half thickness: a standing foot's sole is the sum below the hip. */
		private static final float THIGH_LENGTH = 4.0F, SHIN_LENGTH = 6.0F, FOOT_HALF = 0.5F;
		/** How far the front talons and the hind talon reach from the middle of the foot (pixels), for how low a curled foot hangs. */
		private static final float TOE_REACH = 4.5F, HIND_REACH = 2.5F;
		/**
		 * A sneaking player is drawn 2/16 of a block lower (AvatarRenderer.getRenderOffset), 2/0.9375 model pixels: crouched,
		 * the ground is that much higher in model space.
		 */
		private static final float CROUCH_SINK = 2.0F / 0.9375F;
		/** The four flight feathers of a hand: where each is hinged (y, z), its length against the longest, and its fan open and shut (radians, the right wing's). */
		private static final float[] FEATHER_Y = {-1.5F, -0.5F, 0.5F, 1.5F}, FEATHER_Z = {0.0F, 0.1F, 0.2F, 0.3F};
		private static final float[] FEATHER_LENGTH = {1.0F, 0.95F, 0.88F, 0.78F};
		private static final float[] FAN_OPEN = {0.14F, -0.05F, -0.24F, -0.42F}, FAN_SHUT = {0.0F, -0.02F, -0.04F, -0.06F};
		/** The five tail feathers: their z (stacked so none shares a plane), and their length against the middle one. */
		private static final float[] TAIL_Z = {1.0F, 1.1F, 1.2F, 1.1F, 1.0F}, TAIL_LENGTH = {0.8F, 0.92F, 1.0F, 0.92F, 0.8F};
		private static final float HALF_PI = (float)Math.PI * 0.5F;

		private final ModelPart skull;
		/** 0 right, 1 left. */
		private final ModelPart[] arm = new ModelPart[2], hand = new ModelPart[2];
		private final ModelPart[][] feather = new ModelPart[2][4], toe = new ModelPart[2][4];
		private final ModelPart[] thigh = new ModelPart[2], shin = new ModelPart[2], foot = new ModelPart[2];
		private final ModelPart[] tail = new ModelPart[5];

		Talon() {
			super(bake(mesh()), "talon");
			// on the breast, a small plate (the skin's insignia is the small one)
			this.insigniaY = 0.5F;
			this.insigniaZ = -2.5F;
			this.insigniaSize = 0.7F;
			this.skull = this.body.getChild("skull");
			for (int s = 0; s < 2; s++) {
				String p = s == 0 ? "r_" : "l_";
				this.arm[s] = this.body.getChild(p + "arm");
				this.hand[s] = this.arm[s].getChild("hand");
				this.thigh[s] = this.body.getChild(p + "thigh");
				this.shin[s] = this.thigh[s].getChild("shin");
				this.foot[s] = this.shin[s].getChild("foot");
				for (int i = 0; i < 4; i++) {
					this.feather[s][i] = this.hand[s].getChild("feather" + i);
					this.toe[s][i] = this.foot[s].getChild("toe" + i);
				}
			}
			for (int i = 0; i < 5; i++) {
				this.tail[i] = this.body.getChild("tail" + i);
			}
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the man's head, hat, arms and legs stay (hidden; the arms for the first-person hand); his body is replaced by the
			// bird's torso, 7 wide, 10 long and 5 deep, which every other part hangs on
			PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 64).addBox(-3.5F, -5.0F, -2.5F, 7.0F, 10.0F, 5.0F),
				PartPose.offset(0.0F, 12.0F, 0.0F));
			// the head, sunk two pixels into the shoulders so it joins however the body leans; a brow, a hooked beak
			PartDefinition skull = body.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(24, 64).addBox(-2.5F, -5.0F, -2.5F, 5.0F, 7.0F, 5.0F),
				PartPose.offset(0.0F, HEAD_Y, HEAD_Z));
			skull.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(44, 70).addBox(-2.5F, -0.5F, -1.0F, 5.0F, 1.0F, 1.0F),
				PartPose.offset(0.0F, -3.6F, -2.4F));
			PartDefinition beak = skull.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(44, 64).addBox(-1.0F, -1.0F, -3.0F, 2.0F, 2.0F, 3.0F),
				PartPose.offset(0.0F, -2.0F, -2.4F));
			beak.addOrReplaceChild("hook", CubeListBuilder.create().texOffs(54, 64).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F),
				PartPose.offsetAndRotation(0.0F, 0.3F, -2.2F, 0.15F, 0.0F, 0.0F));
			for (int s = 0; s < 2; s++) {
				boolean left = s == 1;
				String p = left ? "l_" : "r_";
				float sx = left ? 1.0F : -1.0F;
				// the wing: an arm (7 across, 5 chord) hinged on the shoulder, a hand on its tip, four flight feathers on the hand
				PartDefinition wing = body.addOrReplaceChild(p + "arm", flip(CubeListBuilder.create().texOffs(0, 80), left).addBox(left ? -1.0F : -6.0F, -2.5F, -0.5F, 7.0F, 5.0F, 1.0F),
					PartPose.offset(sx * SHOULDER_X, SHOULDER_Y, SHOULDER_Z));
				PartDefinition palm = wing.addOrReplaceChild("hand", flip(CubeListBuilder.create().texOffs(16, 80), left).addBox(left ? 0.0F : -3.0F, -2.0F, -0.5F, 3.0F, 4.0F, 1.0F),
					PartPose.offset(sx * 6.0F, 0.0F, 0.0F));
				for (int i = 0; i < 4; i++) {
					palm.addOrReplaceChild("feather" + i, flip(CubeListBuilder.create().texOffs(24, 80), left).addBox(left ? 0.0F : -8.0F, -1.0F, -0.5F, 8.0F, 2.0F, 1.0F),
						PartPose.offset(sx * 3.0F, FEATHER_Y[i], FEATHER_Z[i]));
				}
				// the leg: a feathered thigh, a scaly shin, a foot with three toes forward and one back
				PartDefinition leg = body.addOrReplaceChild(p + "thigh", flip(CubeListBuilder.create().texOffs(0, 90), left).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F),
					PartPose.offset(sx * HIP_X, HIP_Y, HIP_Z));
				PartDefinition shank = leg.addOrReplaceChild("shin", flip(CubeListBuilder.create().texOffs(12, 90), left).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F),
					PartPose.offset(0.0F, THIGH_LENGTH, 0.0F));
				PartDefinition paw = shank.addOrReplaceChild("foot", flip(CubeListBuilder.create().texOffs(20, 90), left).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 1.0F, 2.0F),
					PartPose.offset(0.0F, SHIN_LENGTH, 0.0F));
				for (int i = 0; i < 4; i++) {
					// the outer toe to the side, the inner toe to the other, the hind toe (turned round and half the length, in setupAnim) behind
					float x = (i == 1 ? -0.6F : i == 2 ? 0.6F : 0.0F) * -sx;
					float z = i == 3 ? 0.5F : -0.5F;
					paw.addOrReplaceChild("toe" + i, flip(CubeListBuilder.create().texOffs(28, 90), left).addBox(-0.5F, -0.5F, -4.0F, 1.0F, 1.0F, 4.0F),
						PartPose.offsetAndRotation(x, i == 1 || i == 2 ? -0.04F : 0.0F, z, 0.0F, i == 3 ? (float)Math.PI : 0.0F, 0.0F));
				}
			}
			// the tail: five feathers fanned from the rump
			for (int i = 0; i < 5; i++) {
				body.addOrReplaceChild("tail" + i, CubeListBuilder.create().texOffs(42, 80).addBox(-1.0F, 0.0F, -0.5F, 2.0F, 9.0F, 1.0F),
					PartPose.offset(0.0F, 4.5F, TAIL_Z[i]));
			}
			return mesh;
		}

		/** The left side's boxes are drawn with the skin mirrored. */
		private static CubeListBuilder flip(CubeListBuilder builder, boolean left) {
			return left ? builder.mirror() : builder;
		}

		/** The standing legs this frame (world angles, radians from straight down, + back), kept to avoid allocating each frame. */
		private final float[] groundThigh = new float[2], groundShin = new float[2];

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			// the man is hidden (the arms are drawn alone in first person); the torso is the rig
			this.head.visible = this.hat.visible = this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
			XenoMotion.Read m = XenoMotion.read(state);
			XenoformFlightPose.Flight flight = state.getData(XenoformFlightPose.KEY);
			Float wind = state.getData(com.jaymospowers.client.power.wind.WindPose.KEY);
			float age = state.ageInTicks;
			float fly = flight == null ? 0.0F : XenoformFlightPose.smooth(flight.wings());
			float glide = flight == null ? 0.0F : XenoformFlightPose.smooth(flight.glide());
			float fold = flight == null ? 0.0F : XenoformFlightPose.smooth(flight.fold()) * fly;
			float cruise = wind == null ? 0.0F : Mth.clamp(wind, 0.0F, 1.0F);
			// airborne (flying, gliding, or just off the ground) against perched
			float air = m.air();
			float lift = Math.max(Math.max(fly, glide), air);
			float spread = Math.max(Math.max(fly, glide), air * FALL_SPREAD);
			float flap = Math.max(fly, air * FALL_FLAP * (1.0F - glide));
			float beat = age * BEAT;
			float strike = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
			float crouch = Math.max(m.crouching() ? 1.0F : 0.0F, m.land()) * (1.0F - lift);
			// walking: short two-footed hops
			float sp = Mth.clamp(state.walkAnimationSpeed * 1.5F, 0.0F, 1.0F) * (1.0F - lift);
			float phase = state.walkAnimationPos * HOP;
			float hop = Math.max(0.0F, Mth.sin(phase)) * sp;

			// the body's pitch: the perch's lean, or level (following the look at speed); undoing what the whole-model tilts add
			float look = state.xRot * Mth.DEG_TO_RAD;
			float tGround = Mth.lerp(crouch, PERCH_TILT, CROUCH_TILT) + STRIKE_TILT * strike + Mth.sin(phase) * ROCK * sp + m.breath() * 0.02F;
			float tAir = HALF_PI + GLIDE_PITCH * glide + BEAT_PITCH * fly * Mth.sin(beat) + cruise * look;
			float t = Mth.lerp(lift, tGround, tAir);
			// Wind's cruise tips the drawn model forward to lie along the flight, the glide leans it back; both about the middle of
			// its box. The bird is already level, so it stands up in the model to arrive level (its pitch kept) after them.
			float tilt = cruise * (HALF_PI + look) - XenoformFlightPose.GLIDE_LEAN * Mth.DEG_TO_RAD * glide;

			// perched, the lowest foot is on the ground (a hop lifts the body further than the legs tuck up)
			float lowest = 0.0F;
			float chainZ = 0.0F;
			for (int s = 0; s < 2; s++) {
				float side = s == 0 ? 1.0F : -1.0F;
				float swing = Mth.cos(phase) * SHUFFLE * sp * side;
				this.groundThigh[s] = THIGH_REST + CROUCH_THIGH * crouch + swing;
				this.groundShin[s] = SHIN_REST + CROUCH_SHIN * crouch - swing * 0.6F;
				// (a tilted shin's lower corners hang a little below the foot)
				lowest = Math.max(lowest, THIGH_LENGTH * Mth.cos(this.groundThigh[s]) + SHIN_LENGTH * Mth.cos(this.groundShin[s])
					+ Math.max(FOOT_HALF, Math.abs(Mth.sin(this.groundShin[s]))));
				chainZ += (THIGH_LENGTH * Mth.sin(this.groundThigh[s]) + SHIN_LENGTH * Mth.sin(this.groundShin[s])) * 0.5F;
			}
			float hipDrop = HIP_Y * Mth.cos(tGround) - HIP_Z * Mth.sin(tGround);
			float hipBack = HIP_Y * Mth.sin(tGround) + HIP_Z * Mth.cos(tGround);
			float floor = 24.0F - (state.isCrouching ? CROUCH_SINK : 0.0F);
			float groundY = floor - lowest - hipDrop - hop * HOP_LIFT;
			float groundZ = -(hipBack + chainZ) - strike * STRIKE_LUNGE;
			float airY = AIR_Y + WING_BOB * fly * Mth.sin(beat);
			float wantY = Mth.lerp(lift, groundY, airY);
			float wantZ = Mth.lerp(lift, groundZ, 0.0F);
			// between perched and airborne (a landing, the wings easing out) the legs swing through hanging straight down
			// while the body has barely risen: lift it so neither foot goes through the ground
			for (int s = 0; s < 2; s++) {
				this.leg(s, lift, t, hop, strike, fold, glide, age);
				wantY = Math.min(wantY, floor - this.footDrop(s, t));
			}
			// the tilt turns the model about its box's middle (in the renderer's frame, which is the model's scaled by 0.9375
			// with its ground at y 24.016): put the body where, once turned, it is wanted
			float pivot = 24.016F - state.boundingBoxHeight / Math.max(state.scale, 0.01F) * 8.0F / 0.9375F;
			float vy = wantY - pivot;
			float ce = Mth.cos(tilt), se = Mth.sin(tilt);
			this.body.x = 0.0F;
			this.body.y = pivot + vy * ce + wantZ * se;
			this.body.z = -vy * se + wantZ * ce;
			this.body.xRot = t - tilt;
			this.body.yRot = this.body.zRot = 0.0F;

			this.head(state, t, cruise, strike, sp, phase, age);
			for (int s = 0; s < 2; s++) {
				this.wing(s, spread, flap, beat, fold, glide, m.land() + Math.abs(m.turn()) * 0.25F, age);
			}
			this.tail(m, spread, glide, fold, fly, beat, age);
		}

		/** How far below the body's middle this leg reaches as it is posed, the body pitched {@code t} (pixels, down). */
		private float footDrop(int s, float t) {
			float a = t + this.thigh[s].xRot, b = a + this.shin[s].xRot, f = b + this.foot[s].xRot;
			float toes = FOOT_HALF * Mth.abs(Mth.cos(f)) + Math.max(0.0F, Math.max(TOE_REACH * Mth.sin(f), -HIND_REACH * Mth.sin(f)));
			return HIP_Y * Mth.cos(t) - HIP_Z * Mth.sin(t) + THIGH_LENGTH * Mth.cos(a) + SHIN_LENGTH * Mth.cos(b) + Math.max(Mth.abs(Mth.sin(b)), toes);
		}

		/** The head looks where the player does whatever the body is doing (the turn the body's tilt gives it undone), and bobs as it hops. */
		private void head(AvatarRenderState state, float t, float cruise, float strike, float sp, float phase, float age) {
			float idle = 1.0F - sp;
			float yaw = Mth.clamp(state.yRot * (1.0F - cruise), -75.0F, 75.0F) * Mth.DEG_TO_RAD + Mth.sin(age * 0.047F) * 0.06F * idle;
			float pitch = Mth.clamp(state.xRot, -80.0F, 80.0F) * Mth.DEG_TO_RAD + PECK * strike + Mth.sin(age * 0.031F + 1.0F) * 0.04F * idle;
			// the head's turn in the world, Ry(yaw) Rx(pitch), seen from the body's frame, Rx(-t) first; as ModelPart's Rz Ry Rx
			float cy = Mth.cos(yaw), sy = Mth.sin(yaw), cp = Mth.cos(pitch), sn = Mth.sin(pitch), ct = Mth.cos(t), st = Mth.sin(t);
			this.skull.xRot = (float)Math.atan2(-st * cp + ct * cy * sn, st * sn + ct * cy * cp);
			this.skull.yRot = (float)Math.asin(Mth.clamp(ct * sy, -1.0F, 1.0F));
			this.skull.zRot = (float)Math.atan2(-st * sy, cy);
			this.skull.x = 0.0F;
			this.skull.y = HEAD_Y;
			this.skull.z = HEAD_Z + Mth.sin(phase) * HEAD_BOB * sp - strike * STRIKE_LUNGE * 0.5F;
		}

		/**
		 * One wing. Folded (spread 0) it lies against the body's side; spread, the arm goes out level and swings up and down
		 * with the beat, the hand behind it by a beat's lag; a glide holds it up and still, a stoop sweeps it back, and the
		 * flight feathers fan out when it is open.
		 */
		private void wing(int s, float spread, float flap, float beat, float fold, float glide, float flare, float age) {
			float side = s == 0 ? 1.0F : -1.0F;
			float up = flap * (FLAP_BIAS + FLAP_SPAN * Mth.sin(beat)) * (1.0F - glide)
				+ glide * (GLIDE_DIHEDRAL + Mth.sin(age * 0.07F + side) * GLIDE_ROCK);
			ModelPart a = this.arm[s];
			// the roll about its length turns the folded wing's flat to the body's side
			a.xRot = Mth.lerp(spread, HALF_PI, WING_PITCH * flap * Mth.cos(beat) * (1.0F - glide) - 0.1F * glide);
			a.yRot = side * up * spread;
			a.zRot = side * Mth.lerp(spread, -(HALF_PI - FOLD_OUT - Mth.clamp(flare, 0.0F, 1.0F) * 0.4F), -SWEEP * fold);
			ModelPart h = this.hand[s];
			h.xRot = 0.0F;
			h.yRot = -side * spread * flap * HAND_LAG * Mth.cos(beat) * (1.0F - glide);
			h.zRot = side * Mth.lerp(spread, -FOLD_HAND, -HAND_SWEEP * fold);
			float open = spread * (1.0F - 0.85F * fold);
			for (int i = 0; i < 4; i++) {
				ModelPart f = this.feather[s][i];
				f.xRot = 0.0F;
				f.xScale = FEATHER_LENGTH[i];
				f.zRot = side * (Mth.lerp(open, FAN_SHUT[i], FAN_OPEN[i]) + Mth.sin(age * 0.3F + i * 1.1F + side) * 0.02F * glide);
				f.yRot = -side * spread * flap * Mth.cos(beat) * 0.3F * (0.4F + 0.2F * i) * (1.0F - glide);
			}
		}

		/** The tail: five feathers that fan wider in the air and on a landing, close on a stoop, lift as it falls and swing out of a turn. */
		private void tail(XenoMotion.Read m, float spread, float glide, float fold, float fly, float beat, float age) {
			float open = Mth.clamp(spread * 0.6F + glide * 0.4F + m.land() * 0.6F, 0.0F, 1.0F) * (1.0F - 0.8F * fold);
			float fan = Mth.lerp(open, TAIL_FAN, TAIL_FAN_WIDE);
			float pitch = Math.max(TAIL_LOW, TAIL_REST + tailLift(m) * 0.5F);
			for (int i = 0; i < 5; i++) {
				ModelPart feather = this.tail[i];
				feather.xRot = pitch + Mth.sin(age * 0.11F + i * 0.5F) * 0.02F + fly * Mth.sin(beat + 1.2F) * 0.05F;
				feather.yRot = 0.0F;
				feather.zRot = (i - 2) * fan + m.trail() * 0.3F + Mth.sin(age * 0.05F) * 0.02F;
				feather.yScale = TAIL_LENGTH[i];
			}
		}

		/**
		 * One leg. Perched, the thigh and shin stand it on the ground, its foot flat (the walk's hop tucks it, a strike rakes
		 * it forward, talons open); airborne it hangs from the belly, tucked back under the tail (forward on a stoop).
		 */
		private void leg(int s, float lift, float t, float hop, float strike, float fold, float glide, float age) {
			float side = s == 0 ? 1.0F : -1.0F;
			float a = this.groundThigh[s] - HOP_TUCK_THIGH * hop - STRIKE_THIGH * strike;
			float b = this.groundShin[s] + HOP_TUCK_SHIN * hop - STRIKE_SHIN * strike;
			float curl = 0.4F * hop + 0.5F * strike;
			float tau = Mth.lerp(fold, Mth.lerp(glide, HOVER_LEG, TUCK_LEG), STOOP_LEG) - STRIKE_THIGH * 0.9F * strike
				+ Mth.sin(age * 0.1F + side * 1.3F) * 0.04F * (1.0F - glide);
			float shinAir = Mth.lerp(fold, Mth.lerp(glide, HOVER_SHIN, TUCK_SHIN), STOOP_SHIN);
			float curlAir = Mth.lerp(fold, HOVER_CURL + 0.3F * glide, STOOP_CURL);
			this.thigh[s].xRot = Mth.lerp(lift, a - t, -HALF_PI + tau);
			this.shin[s].xRot = Mth.lerp(lift, b - a, shinAir);
			this.foot[s].xRot = Mth.lerp(lift, -b + curl, curlAir);
			// the talons spread wider when they strike or stoop
			float splay = 0.3F + 0.35F * Math.max(strike, fold);
			this.toe[s][0].yRot = 0.0F;
			this.toe[s][1].yRot = side * splay;
			this.toe[s][2].yRot = -side * splay;
			this.toe[s][3].yRot = (float)Math.PI;
			this.toe[s][3].zScale = 0.5F;
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

	/**
	 * (0.18.111, Scan D) Lumen: small and slight, two thin wing-fins on its back that flutter.
	 * <p>
	 * (0.19.67) An orb of light: no body or limbs. A white-gold core (the head box, 7x7x8, with two crossed boxes to round
	 * its corners) inside a dithered halo shell (its front left clear, so the core and the insignia on its front show),
	 * hovering {@link #HOVER} pixels above the ground and bobbing slowly, with {@link #MOTES} tiny motes orbiting it on
	 * tilted rings. Moving, the motes tighten and stream out behind it (+z), swinging away from its turns and rising as it
	 * falls. An attack swing pulses the orb bigger and bursts the motes outward. The humanoid body and limbs are hidden (the
	 * body is still posed at the core, for the insignia plate). Uses no {@code XenoformClient.ACT}: Lumen has no charge, roll or spin.
	 */
	static final class Lumen extends FormModel {
		// ---- tuning ----
		/** How high the core's centre floats (model pixels up from the ground at y 24: 22 is ~1 block at its 0.7 scale, the halo's underside ~0.7 block up), its bob, the bob's speed, how far it sinks when sneaking. */
		static final float HOVER = 22.0F, BOB = 1.2F, BOB_RATE = 0.07F, CROUCH_DROP = 2.5F;
		/** The core's idle breath (fraction) and how fast, and how much bigger it pulses on an attack swing. */
		static final float BREATH = 0.04F, BREATH_RATE = 0.12F, BURST_CORE = 0.4F;
		/** The halo shell's extra swell on a swing, and how far it tilts as it moves. */
		static final float BURST_SHELL = 0.3F, SHELL_LEAN = 0.15F;
		/** The motes: the number, ring radius (pixels), orbit speed (radians a tick), how far a swing flings them (fraction of the radius). */
		static final int MOTES = 8;
		static final float ORBIT_R = 8.5F, ORBIT_RATE = 0.05F, BURST_MOTES = 1.1F;
		/** Moving: how far behind the longest-trailing mote streams (pixels), how much the rings tighten, a turn's sway, a fall's lift. */
		static final float TRAIL_LEN = 15.0F, TRAIL_TIGHT = 0.4F, TURN_SWING = 5.0F, FALL_RISE = 4.0F;
		/** Each mote's ring: radius scale, tilt about x, roll about z, direction and speed, how much of the stream it takes (0-1). */
		private static final float[] RADIUS = {1.0F, 0.85F, 1.15F, 0.95F, 1.1F, 0.8F, 1.2F, 0.9F};
		private static final float[] TILT = {0.3F, 1.0F, -0.6F, 0.7F, -1.1F, 1.4F, -0.2F, 0.5F};
		private static final float[] ROLL = {0.0F, 0.9F, -0.8F, 1.6F, 0.4F, -1.3F, 1.1F, -0.4F};
		private static final float[] RATE = {1.0F, -0.8F, 1.25F, -1.1F, 0.9F, -1.3F, 0.75F, 1.15F};
		private static final float[] LAG = {0.35F, 0.9F, 0.55F, 1.0F, 0.2F, 0.75F, 0.45F, 0.65F};

		private final ModelPart halo;
		private final ModelPart[] motes = new ModelPart[MOTES];

		Lumen() {
			super(bake(mesh()), "lumen");
			ModelPart root = this.root();
			this.halo = root.getChild("halo");
			for (int i = 0; i < MOTES; i++) {
				this.motes[i] = root.getChild("mote" + i);
			}
			// flush on the front of the core (the head box's front is at z = -4), centred on the 7-pixel face
			this.insigniaY = 0.0F;
			this.insigniaZ = -4.0F;
			this.insigniaSize = 0.95F;
		}

		private static MeshDefinition mesh() {
			// the slim limbs it always had (-0.3), kept for the first-person hand
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(-0.3F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the core: the head, 7x7x8 (z the long way), with the crossed 8x7x7 and 7x8x7 boxes on it
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -3.5F, -4.0F, 7.0F, 7.0F, 8.0F),
				PartPose.offset(0.0F, 24.0F - HOVER, 0.0F));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			head.addOrReplaceChild("core_x", CubeListBuilder.create().texOffs(0, 94).addBox(-4.0F, -3.5F, -3.5F, 8.0F, 7.0F, 7.0F), PartPose.ZERO);
			head.addOrReplaceChild("core_y", CubeListBuilder.create().texOffs(30, 94).addBox(-3.5F, -4.0F, -3.5F, 7.0F, 8.0F, 7.0F), PartPose.ZERO);
			// the halo shell round it (its front face is clear in the skin)
			root.addOrReplaceChild("halo", CubeListBuilder.create().texOffs(0, 70).addBox(-5.5F, -5.5F, -5.5F, 11.0F, 11.0F, 11.0F),
				PartPose.offset(0.0F, 24.0F - HOVER, 0.0F));
			for (int i = 0; i < MOTES; i++) {
				root.addOrReplaceChild("mote" + i, CubeListBuilder.create().texOffs(i < 4 ? 0 : 8, 64)
					.addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.35F)), PartPose.offset(0.0F, 24.0F - HOVER, 0.0F));
			}
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.hat.visible = this.body.visible = this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
			this.head.resetPose();
			this.body.resetPose();
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			float burst = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
			boolean crouch = state.isCrouching;
			float cy = 24.0F - HOVER + Mth.sin(age * BOB_RATE) * BOB + (crouch ? CROUCH_DROP : 0.0F);
			// the core swells with its breath and a swing
			float pulse = 1.0F + Mth.sin(age * BREATH_RATE) * BREATH + burst * BURST_CORE;
			this.head.y = cy;
			this.head.xScale = this.head.yScale = this.head.zScale = pulse;
			// the body (hidden) sits at the core's centre so the insignia plate lies on its front face
			this.body.y = cy;
			this.insigniaZ = -4.0F * pulse;
			this.insigniaSize = 0.95F * pulse;
			// the halo: breathing a beat behind the core, leaning into its way and wobbling
			float shell = pulse + Mth.sin(age * 0.08F + 1.0F) * 0.04F + burst * BURST_SHELL;
			this.halo.y = cy;
			this.halo.xScale = this.halo.yScale = this.halo.zScale = shell;
			this.halo.xRot = Mth.sin(age * 0.037F) * 0.08F + speed * SHELL_LEAN;
			this.halo.zRot = Mth.sin(age * 0.05F) * 0.08F;
			this.halo.yRot = 0.0F;
			// the motes: each round its own tilted ring, streamed back by the walk
			float tight = (1.0F - TRAIL_TIGHT * speed) * (crouch ? 0.75F : 1.0F) * (1.0F + BURST_MOTES * burst);
			float rise = Math.max(0.0F, m.fall()) * m.air() * FALL_RISE;
			for (int i = 0; i < MOTES; i++) {
				float r = ORBIT_R * RADIUS[i] * tight;
				float ph = age * ORBIT_RATE * RATE[i] + i * (float)Math.PI * 2.0F / MOTES;
				float x0 = r * Mth.cos(ph);
				float z0 = r * Mth.sin(ph);
				// tilt the ring about x, then roll it about z
				float y1 = -z0 * Mth.sin(TILT[i]);
				float z1 = z0 * Mth.cos(TILT[i]);
				float x2 = x0 * Mth.cos(ROLL[i]) - y1 * Mth.sin(ROLL[i]);
				float y2 = x0 * Mth.sin(ROLL[i]) + y1 * Mth.cos(ROLL[i]);
				float lag = LAG[i];
				ModelPart mote = this.motes[i];
				// a turn (+ to its left, +x) swings the stream the other way, as the tails' -trail does
				mote.x = x2 - m.trail() * TURN_SWING * lag;
				mote.y = cy + y2 + speed * lag * 1.5F - rise * lag;
				mote.z = z1 + TRAIL_LEN * speed * lag;
				float twinkle = (0.75F + Mth.sin(age * 0.3F + i * 2.1F) * 0.35F) * (1.0F + burst * 0.5F);
				mote.xScale = mote.yScale = mote.zScale = twinkle;
			}
		}
	}

	/**
	 * (0.18.111, Scan D; 0.19.67 a real bat) Keen: a small furry bat, not a man: a round fur body with the insignia on
	 * its back, a pug head with huge ears, a jaw and fangs, two hind legs and two long membrane wings (a humerus, a
	 * long forearm, three finger bones fanned from the wrist, a thumb claw, and the membrane stretched between them).
	 * On the ground the wings fold up against it and it crawls on its wrists and feet (a diagonal gait, the arms
	 * solved so each wrist plants on the ground and the elbow rides up behind it); in the air the wings spread and beat
	 * fast (FLY_WINGS), folding on the upstroke and sweeping back at speed, or hold out and ride the air (FLY_GLIDE);
	 * under Wind's cruise pose the model is turned back level (the body is laid along the flight), and gliding is turned
	 * back too. While it hangs ({@link XenoformClient#ACT} "hang") it hangs head-down by its feet from the ceiling, turned
	 * belly to the front with the wings drawn across it like a cloak, swaying. A bite lunges the head, opens the jaw and
	 * lays the ears back. The man's parts are hidden; the body is the bat's torso, laid face-up so the insignia sits on
	 * its back, and every other part hangs off it.
	 */
	static final class Keen extends FormModel {
		// ---- tuning ----
		/** The body's height (model y; the ground is 24) crawling and flying, how far it drops when sneaking, and what the renderer sinks a crouching player. */
		static final float CRAWL_Y = 17.0F, FLY_Y = 10.5F, CROUCH_DROP = 2.5F, CROUCH_SINK = 2.0F;
		/** The middle of the player box (model y), which Wind's cruise pose and the glide tip the whole model about. */
		static final float MIDDLE_Y = 9.6F;
		/** The crawl: how fast the limbs step against the walk, how far each wrist and foot swings and lifts (pixels), how much the body bobs. */
		static final float GAIT = 0.6662F * 1.3F, FORE_STRIDE = 2.6F, HIND_STRIDE = 2.2F, FORE_LIFT = 2.2F, HIND_LIFT = 1.6F, BOB = 0.5F;
		/** Where a planted wrist rests (body space: out from the middle, and ahead of it) and its radius, and how much wider the wrists go when it sneaks. */
		static final float WRIST_X = 4.6F, WRIST_Y = 5.2F, WRIST_R = 1.7F, CROUCH_WIDEN = 1.6F;
		/** How far the hind feet splay out at rest and at most (radians), and a pad under the foot (pixels) so its sole sits on the ground. */
		static final float SPLAY = 0.22F, SPLAY_MAX = 0.5F, FOOT_PAD = 0.25F;
		/** The wing beat: radians a tick, the stroke and the mean raise (radians); the glide's slow dip likewise. */
		static final float FLAP_RATE = 1.9F, FLAP_AMP = 0.75F, FLAP_UP = 0.12F, GLIDE_RATE = 0.16F, GLIDE_AMP = 0.10F, GLIDE_UP = 0.22F;
		/** Pitch of the body hovering (negative is nose up) and cruising, how far it banks into a turn, and how far the wings spread when it falls. */
		static final float HOVER_PITCH = -0.5F, CRUISE_PITCH = 0.1F, BANK = 0.3F, AIR_SPREAD = 0.6F;
		/**
		 * How much of the glide's tip ({@link XenoformFlightPose#GLIDE_LEAN}) is turned back out of the model (1 level the
		 * bat out, 0 leave it to the renderer). The renderer turns the model by +38 degrees about the X axis, which leans the
		 * model back; flip this to -1 if it is seen to lean forward in game.
		 */
		static final float GLIDE_COUNTER = 1.0F;
		/** A bite lunges the body this far (pixels), and the head this much further out of the torso (kept under its 0.5 pixel overlap so it stays on). */
		static final float LUNGE = 2.0F, NECK_LUNGE = 0.4F;
		/** Hanging: the ceiling (model y), how far the body hangs below the grip of its feet, the sway (radians) and how far the belly sheets reach in. */
		static final float CEILING_Y = -4.8F, HANG_REACH = 7.7F, HANG_SWAY = 0.07F, HANG_SHEET = 6.2F;
		/** Hanging: how far each wing's sheet sits in from its root (pixels, toward the belly) so it lies on the belly, not a hair off it. */
		static final float HANG_SHEET_IN = 0.65F;
		/** Wind's air brake leans the body back this much (degrees); WindPose keeps it private. */
		static final float BRAKE_LEAN_BACK = 25.0F;

		private static final float HALF_PI = (float)Math.PI * 0.5F;
		private static final float DEG = Mth.DEG_TO_RAD;
		/** The skeleton (pixels): the shoulder on the torso's side, the humerus, forearm and three fingers. */
		private static final float SHOULDER_X = 3.0F, SHOULDER_Y = 1.6F, SHOULDER_Z = -1.0F, HUMERUS = 5.0F, FOREARM = 9.0F;
		private static final float[] FINGER = {10.0F, 9.0F, 7.0F};
		/** Hips (body space) and the leg's length hip to sole. */
		private static final float HIP_X = 2.2F, HIP_Y = -2.4F, HIP_Z = 1.5F, LEG = 5.5F;
		/** The neck's joint on the torso's head end. */
		private static final float NECK_Y = 3.0F, NECK_Z = -0.2F;
		/** The wing's pose at the far ends: spread (finger angles from the forearm) and folded on the ground. */
		private static final float[] FINGER_SPREAD = {-0.12F, -0.55F, -1.0F};
		private static final float[] FINGER_FOLD = {-2.99F, -2.87F, -2.75F};
		private static final CubeDeformation THIN = new CubeDeformation(0.0F, 0.0F, -0.25F);
		private static final CubeDeformation BONE = new CubeDeformation(0.1F);

		private final ModelPart batHead, jaw, web;
		private final ModelPart[] ears = new ModelPart[2], legs = new ModelPart[2], feet = new ModelPart[2];
		/** Index 0 right, 1 left. */
		private final ModelPart[] shoulder = new ModelPart[2], arm = new ModelPart[2], fore = new ModelPart[2], thumb = new ModelPart[2];
		private final ModelPart[] webIn = new ModelPart[2], webA = new ModelPart[2], webB = new ModelPart[2];
		private final ModelPart[][] fingers = new ModelPart[2][3];
		/** Scratch: the wing angles being solved (alpha, beta, A, F, g1, g2, g3): the crawl's (right, left), the flight's, the hang's, and the blend drawn. */
		private final float[][] wingGround = new float[2][7];
		private final float[] wingFly = new float[7], wingHang = new float[7], wingNow = new float[7];

		Keen() {
			super(bake(mesh()), "keen");
			ModelPart torso = this.body;
			this.batHead = torso.getChild("bat_head");
			this.jaw = this.batHead.getChild("jaw");
			this.web = torso.getChild("web");
			for (int s = 0; s < 2; s++) {
				String p = s == 1 ? "left_" : "right_";
				this.ears[s] = this.batHead.getChild(p + "ear");
				this.legs[s] = torso.getChild(p + "hind");
				this.feet[s] = this.legs[s].getChild("foot");
				this.shoulder[s] = torso.getChild(p + "shoulder");
				this.webIn[s] = this.shoulder[s].getChild("web_in");
				this.arm[s] = this.shoulder[s].getChild("arm");
				this.fore[s] = this.arm[s].getChild("fore");
				ModelPart knob = this.fore[s].getChild("knob");
				this.thumb[s] = knob.getChild("thumb");
				for (int i = 0; i < 3; i++) {
					this.fingers[s][i] = this.fore[s].getChild("finger" + i);
				}
				this.webA[s] = this.fore[s].getChild("web_a");
				this.webB[s] = this.fore[s].getChild("web_b");
			}
			// the plate lies on the torso's back (the body is laid face-up, see setupAnim)
			this.insigniaY = -0.5F;
			this.insigniaZ = -2.5F;
			this.insigniaSize = 1.0F;
		}

		/** A box that is on the left (+x) side as given, or mirrored on the right (its texture too). */
		private static CubeListBuilder cube(boolean left, int u, int v, float x, float y, float z, int w, int h, int d, CubeDeformation grow) {
			CubeListBuilder builder = CubeListBuilder.create().texOffs(u, v);
			return left ? builder.addBox(x, y, z, w, h, d, grow) : builder.mirror().addBox(-x - w, y, z, w, h, d, grow);
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the torso replaces the man's body; lying face-up (-90 degrees), its local +y is forward and +z is down
			PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 64).addBox(-3.0F, -3.5F, -2.5F, 6.0F, 7.0F, 5.0F),
				PartPose.offsetAndRotation(0.0F, CRAWL_Y, 0.0F, -HALF_PI, 0.0F, 0.0F));
			// the head sits in the man's frame (its face on -z) turned a quarter to face forward along the spine
			PartDefinition head = body.addOrReplaceChild("bat_head", CubeListBuilder.create().texOffs(22, 64).addBox(-2.5F, -2.0F, -4.0F, 5.0F, 3.0F, 4.0F),
				PartPose.offsetAndRotation(0.0F, NECK_Y, NECK_Z, HALF_PI, 0.0F, 0.0F));
			head.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(40, 64).addBox(-1.5F, -1.0F, -5.0F, 3.0F, 2.0F, 1.0F), PartPose.ZERO);
			head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(48, 64).addBox(-2.0F, 0.0F, -3.0F, 4.0F, 1.0F, 3.0F),
				PartPose.offset(0.0F, 1.0F, -0.6F));
			head.addOrReplaceChild("right_fang", CubeListBuilder.create().texOffs(8, 76).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F),
				PartPose.offset(-1.4F, 1.0F, -3.5F));
			head.addOrReplaceChild("left_fang", CubeListBuilder.create().texOffs(8, 76).mirror().addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F),
				PartPose.offset(1.4F, 1.0F, -3.5F));
			head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(0, 76).addBox(-1.5F, -6.0F, -0.5F, 3.0F, 6.0F, 1.0F),
				PartPose.offsetAndRotation(-1.4F, -1.8F, -1.2F, 0.1F, 0.0F, -0.25F));
			head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(0, 76).mirror().addBox(-1.5F, -6.0F, -0.5F, 3.0F, 6.0F, 1.0F),
				PartPose.offsetAndRotation(1.4F, -1.8F, -1.2F, 0.1F, 0.0F, 0.25F));
			// the tail membrane between the hind legs, hinged at the torso's tail end
			body.addOrReplaceChild("web", CubeListBuilder.create().texOffs(0, 90).addBox(-3.5F, -5.0F, -0.5F, 7.0F, 5.0F, 1.0F, THIN),
				PartPose.offset(0.0F, -3.0F, 1.0F));
			for (int s = 0; s < 2; s++) {
				boolean left = s == 1;
				String p = left ? "left_" : "right_";
				float side = left ? 1.0F : -1.0F;
				// a hind leg hangs off the hip along +y turned a quarter (so it points down), a flat foot at its end
				PartDefinition leg = body.addOrReplaceChild(p + "hind", cube(left, 12, 76, -1.0F, 0.0F, -1.0F, 2, 5, 2, CubeDeformation.NONE),
					PartPose.offsetAndRotation(side * HIP_X, HIP_Y, HIP_Z, HALF_PI, 0.0F, 0.0F));
				leg.addOrReplaceChild("foot", cube(left, 20, 76, -1.0F, -0.5F, -3.0F, 2, 1, 3, CubeDeformation.NONE), PartPose.offset(0.0F, 5.0F, 0.0F));
				// a wing: a shoulder joint (roll and twist), the humerus, the forearm, a wrist knob with a thumb, three fingers
				PartDefinition sh = body.addOrReplaceChild(p + "shoulder", CubeListBuilder.create(), PartPose.offset(side * SHOULDER_X, SHOULDER_Y, SHOULDER_Z));
				sh.addOrReplaceChild("web_in", cube(left, 16, 90, 0.0F, -8.5F, -0.5F, 16, 9, 1, THIN), PartPose.offset(0.0F, 0.0F, 0.05F));
				PartDefinition upper = sh.addOrReplaceChild("arm", cube(left, 30, 76, -1.0F, -1.0F, -1.0F, 6, 2, 2, CubeDeformation.NONE), PartPose.ZERO);
				PartDefinition lower = upper.addOrReplaceChild("fore", cube(left, 0, 84, -1.0F, -0.5F, -0.5F, 11, 1, 1, CubeDeformation.NONE),
					PartPose.offset(side * HUMERUS, 0.0F, 0.0F));
				PartDefinition knob = lower.addOrReplaceChild("knob", cube(left, 40, 86, -1.0F, -1.0F, -1.0F, 2, 2, 2, CubeDeformation.NONE),
					PartPose.offset(side * FOREARM, 0.0F, 0.0F));
				knob.addOrReplaceChild("thumb", CubeListBuilder.create().texOffs(48, 84).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.ZERO);
				int[][] fingerUv = {{24, 84, 11}, {0, 86, 10}, {22, 86, 8}};
				for (int i = 0; i < 3; i++) {
					lower.addOrReplaceChild("finger" + i, cube(left, fingerUv[i][0], fingerUv[i][1], -0.5F, -0.5F, -0.5F, fingerUv[i][2], 1, 1, BONE),
						PartPose.offset(side * FOREARM, 0.0F, 0.0F));
				}
				lower.addOrReplaceChild("web_a", cube(left, 0, 100, 0.0F, -3.0F, -0.5F, 10, 6, 1, THIN), PartPose.offset(side * FOREARM, 0.0F, 0.12F));
				lower.addOrReplaceChild("web_b", cube(left, 22, 100, 0.0F, -3.0F, -0.5F, 9, 6, 1, THIN), PartPose.offset(side * FOREARM, 0.0F, -0.12F));
			}
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.head.visible = this.hat.visible = false;
			this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
			this.body.visible = true;
			this.body.resetPose();
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			boolean hang = "hang".equals(state.getData(XenoformClient.ACT));
			XenoformFlightPose.Flight flight = state.getData(XenoformFlightPose.KEY);
			Float windAmount = state.getData(com.jaymospowers.client.power.wind.WindPose.KEY);
			Float brakeAmount = state.getData(com.jaymospowers.client.power.wind.WindPose.BRAKE_KEY);
			float cruise = hang || windAmount == null ? 0.0F : windAmount;
			float wings = flight == null ? 0.0F : XenoformFlightPose.smooth(flight.wings());
			float glide = flight == null ? 0.0F : XenoformFlightPose.smooth(flight.glide());
			float fold = flight == null ? 0.0F : flight.fold();
			float time = flight == null ? age : flight.time();
			// how far it is airborne (the body lifts and the legs tuck) and how far the wings are spread
			float fly = hang ? 0.0F : Math.max(wings, glide);
			float spread = hang ? 0.0F : Mth.clamp(Math.max(fly, m.air() * AIR_SPREAD), 0.0F, 1.0F);
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			float walk = state.walkAnimationPos * GAIT;
			float turn = m.turn();
			float bite = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
			// under Wind's cruise the renderer turns the whole model to the look (WindPose.tilt), so the head's own turn and nod fade out
			float lookYaw = Mth.clamp(state.yRot * DEG, -1.1F, 1.1F) * (1.0F - cruise);
			float lookPitch = Mth.clamp(state.xRot * DEG, -0.9F, 0.9F) * (1.0F - cruise);
			float sinkFix = state.isCrouching ? -CROUCH_SINK : 0.0F;
			// ---- the body ----
			float crouch = state.isCrouching && !hang ? CROUCH_DROP * (1.0F - fly) : 0.0F;
			float bob = Mth.abs(Mth.sin(walk)) * BOB * speed * (1.0F - fly);
			float groundBodyY = CRAWL_Y + crouch - bob;
			float hover = Mth.sin(time * 0.21F) * 0.5F * fly;
			float bodyY = Mth.lerp(fly, groundBodyY, FLY_Y + hover);
			float flyPitch = Mth.lerp(Math.min(1.0F, speed * 1.6F), HOVER_PITCH, CRUISE_PITCH) + m.fall() * 0.3F * m.air();
			float pitch = (hang ? 0.0F : flyPitch * fly) * (1.0F - cruise);
			// Wind's cruise tips the whole model by (90 + look pitch) degrees about the player box's middle (a man laid out
			// along the flight), and a glide by a few more. The body is turned back by the 90 degrees (and the glide), so it
			// lies along the flight; the look pitch it keeps, so it dives and climbs where it looks. tilt is what the
			// renderer does (its position is moved to match), turnBack the part undone.
			float tilt = 0.0F, turnBack = 0.0F;
			if (cruise > 0.001F) {
				float brake = brakeAmount == null ? 0.0F : brakeAmount;
				float laid = cruise * (1.0F - 0.75F * brake);
				tilt += laid * (90.0F + state.xRot) * DEG - brake * BRAKE_LEAN_BACK * DEG;
				turnBack += laid * 90.0F * DEG;
			}
			if (!hang && glide > 0.001F && state.deathTime <= 0.0F) {
				float lean = -XenoformFlightPose.GLIDE_LEAN * glide * DEG * GLIDE_COUNTER;
				tilt += lean;
				turnBack += lean;
			}
			float forwardY = Mth.sin(pitch), forwardZ = -Mth.cos(pitch);
			float wantY = bodyY + sinkFix + forwardY * bite * LUNGE;
			float wantZ = forwardZ * bite * LUNGE;
			float cs = Mth.cos(tilt), sn = Mth.sin(tilt);
			this.body.x = 0.0F;
			this.body.y = MIDDLE_Y + (wantY - MIDDLE_Y) * cs + wantZ * sn;
			this.body.z = -(wantY - MIDDLE_Y) * sn + wantZ * cs;
			this.body.xRot = -HALF_PI + pitch - turnBack;
			this.body.yRot = 0.0F;
			// rolled into a turn (under Wind's cruise it banks the model itself)
			this.body.zRot = hang || cruise > 0.001F ? 0.0F : -turn * BANK * spread + Mth.sin(walk) * 0.05F * speed * (1.0F - fly);
			if (hang) {
				// head-down by its feet, belly to the front (turned half round), swaying about its grip on the ceiling
				float sway = Mth.sin(age * 0.045F) * HANG_SWAY;
				this.body.xRot = sway;
				this.body.yRot = (float)Math.PI;
				this.body.y = CEILING_Y + HANG_REACH * Mth.cos(sway) + sinkFix;
				this.body.z = -HANG_REACH * Mth.sin(sway);
			}
			// ---- the head ----
			float headPitch;
			float neckY = NECK_Y + bite * NECK_LUNGE;
			float neckZ = NECK_Z;
			if (hang) {
				// the face turned out to the front (the belly's side) from the hanging line of the body; hanging head-down,
				// a smaller angle turns the face further down, so looking down takes from it
				neckY = NECK_Y + 0.6F;
				neckZ = 0.4F;
				headPitch = HALF_PI + 0.9F - lookPitch * 0.4F;
			} else {
				float ground = HALF_PI - 0.3F + lookPitch * 0.7F + bite * 0.2F + Mth.sin(age * 0.07F) * 0.03F;
				float air = HALF_PI + 0.02F + lookPitch + Mth.sin(time * FLAP_RATE + 0.8F) * 0.05F * fly;
				headPitch = Mth.lerp(fly, ground, air);
			}
			this.batHead.x = 0.0F;
			this.batHead.y = neckY;
			this.batHead.z = neckZ;
			this.batHead.xRot = headPitch;
			// turned where it looks: about the body's down axis, or hanging head-down, about the vertical
			this.batHead.zRot = hang ? 0.0F : lookYaw;
			this.batHead.yRot = hang ? lookYaw : 0.0F;
			this.jaw.xRot = 0.12F + bite * 0.9F + (hang ? 0.0F : Math.max(0.0F, Mth.sin(age * 0.05F)) * 0.04F);
			for (int s = 0; s < 2; s++) {
				float side = s == 1 ? 1.0F : -1.0F;
				float twitch = Mth.sin(age * 0.19F + s * 2.3F) * (0.05F + (1.0F - speed) * 0.03F);
				// ears: pricked at rest, laid back flying and biting, drooped hanging
				float back = bite * 0.6F + fly * 0.35F + cruise * 0.3F + (hang ? 0.9F : 0.0F);
				this.ears[s].xRot = 0.1F - back + twitch;
				this.ears[s].zRot = side * (0.25F + twitch - bite * 0.1F);
			}
			// ---- the tail membrane ----
			this.web.xRot = (hang ? 0.5F : Mth.lerp(fly, -0.1F, -0.3F + Mth.sin(time * FLAP_RATE) * 0.12F * wings)) + Math.max(-0.4F, tailLift(m)) * 0.8F;
			// ---- the hind legs ----
			float groundZ = 24.0F - groundBodyY;
			for (int s = 0; s < 2; s++) {
				float side = s == 1 ? 1.0F : -1.0F;
				ModelPart leg = this.legs[s];
				ModelPart foot = this.feet[s];
				if (hang) {
					leg.xRot = (float)Math.PI - 0.12F + Mth.sin(age * 0.045F + s) * 0.02F;
					leg.yRot = side * 0.35F;
					leg.yScale = 1.0F;
					foot.xRot = -0.3F;
					continue;
				}
				// the right hind steps with the left wrist: diagonal pairs
				float psi = walk + (s == 1 ? (float)Math.PI : 0.0F);
				float p = HIND_STRIDE * speed * Mth.sin(psi);
				float lift = HIND_LIFT * speed * Math.max(0.0F, Mth.cos(psi));
				float delta = -(float)Math.asin(Mth.clamp(p / LEG, -0.9F, 0.9F));
				float hv = groundZ - HIP_Z - lift - FOOT_PAD;
				float cd = LEG * Mth.cos(delta);
				float minCos = Mth.cos(SPLAY);
				// a lifted or lowered hip is met by splaying the foot out, and past that by shortening the leg; the foot's
				// outer edge dips as it rolls out, so it is solved twice, the second time a foot's half width higher
				float splay = SPLAY;
				float ratio = hv / cd;
				for (int pass = 0; pass < 2; pass++) {
					ratio = (hv - (pass == 0 ? 0.0F : Mth.sin(splay))) / cd;
					splay = ratio >= minCos ? SPLAY : Math.min(SPLAY_MAX, (float)Math.acos(Math.max(0.0F, ratio)));
				}
				float stretch = ratio / Mth.cos(splay);
				float groundAngle = HALF_PI + delta;
				float flightAngle = 2.55F + Mth.sin(time * FLAP_RATE + 0.5F + s) * 0.1F * wings + m.trail() * 0.1F;
				leg.xRot = Mth.lerp(fly, groundAngle, flightAngle);
				leg.yRot = side * Mth.lerp(fly, splay, 0.3F);
				leg.yScale = Mth.lerp(fly, stretch, 1.0F);
				foot.xRot = Mth.lerp(fly, -delta - lift * 0.15F, 0.5F);
			}
			// ---- the wings ----
			for (int s = 0; s < 2; s++) {
				this.solveGround(s, walk, speed, groundZ, crouch);
			}
			this.solveFlight(time, wings, fold, cruise);
			this.solveHang(age);
			for (int s = 0; s < 2; s++) {
				float[] w = this.wingNow;
				for (int i = 0; i < 7; i++) {
					w[i] = hang ? this.wingHang[i] : Mth.lerp(spread, this.wingGround[s][i], this.wingFly[i]);
				}
				// banking: the inside wing drops, the outside lifts
				if (!hang) {
					w[1] += (s == 1 ? -turn : turn) * BANK * spread;
				}
				float side = s == 1 ? 1.0F : -1.0F;
				// hanging, the wing's root sits under the belly (the second wing a little lower so the two sheets never share a plane)
				this.shoulder[s].x = side * (hang ? 3.4F : SHOULDER_X);
				this.shoulder[s].y = hang ? 2.6F : SHOULDER_Y;
				this.shoulder[s].z = hang ? (s == 1 ? 3.2F : 3.55F) : SHOULDER_Z;
				this.placeWing(s, w, hang ? 0.0F : spread, hang);
			}
		}

		/** The crawl: a wing reaching to its planted (or stepping) wrist, its fingers folded back along the forearm. */
		private void solveGround(int s, float walk, float speed, float groundZ, float crouch) {
			// diagonal pairs: the left wrist steps with the right hind foot; both sides are solved as a left wing
			float psi = walk + (s == 1 ? 0.0F : (float)Math.PI);
			float tx = WRIST_X + crouch * (CROUCH_WIDEN / CROUCH_DROP) - SHOULDER_X;
			float ty = WRIST_Y + FORE_STRIDE * speed * Mth.sin(psi) - SHOULDER_Y;
			float lift = FORE_LIFT * speed * Math.max(0.0F, Mth.cos(psi));
			float tz = groundZ - WRIST_R - lift - SHOULDER_Z;
			float[] w = this.wingGround[s];
			reach(tx, ty, tz, 0.4F, -0.7F, -0.6F, w);
			for (int i = 0; i < 3; i++) {
				w[4 + i] = FINGER_FOLD[i];
			}
		}

		/** In the air: the beat (the wing lifts and drops, folding at the elbow and closing its fingers as it rises), swept back at speed. */
		private void solveFlight(float time, float wings, float fold, float cruise) {
			float rate = Mth.lerp(wings, GLIDE_RATE, FLAP_RATE);
			float amp = Mth.lerp(wings, GLIDE_AMP, FLAP_AMP);
			float up = Mth.lerp(wings, GLIDE_UP, FLAP_UP);
			float theta = time * rate;
			float flex = wings * Math.max(0.0F, Mth.cos(theta)) * 0.9F;
			float[] w = this.wingFly;
			w[0] = 0.1F + 0.2F * Mth.cos(theta) * wings;
			w[1] = up + amp * Mth.sin(theta);
			w[2] = 0.2F - fold * 0.7F - cruise * 0.1F;
			w[3] = -0.2F - flex - fold * 0.5F;
			float close = 1.0F - 0.45F * flex - 0.4F * fold;
			for (int i = 0; i < 3; i++) {
				w[4 + i] = FINGER_SPREAD[i] * close;
			}
		}

		/**
		 * Hanging: both wings drawn across the belly like a cloak: the humerus reaches in across the chest, the forearm
		 * runs back along the belly toward the feet, the fingers fold forward along it, the membrane lying under the belly.
		 */
		private void solveHang(float age) {
			float[] w = this.wingHang;
			float loose = Mth.sin(age * 0.04F) * 0.03F;
			w[0] = 0.0F;
			w[1] = loose;
			w[2] = (float)Math.PI;
			w[3] = HALF_PI + loose;
			for (int i = 0; i < 3; i++) {
				w[4 + i] = FINGER_FOLD[i];
			}
		}

		/**
		 * Poses wing {@code s} from its canonical (left wing) angles: the shoulder's twist and roll, the humerus' sweep, the
		 * elbow's bend and each finger's fan, then sizes the membranes to the shape the bones make, and points the thumb forward.
		 */
		private void placeWing(int s, float[] w, float spread, boolean hang) {
			float side = s == 1 ? 1.0F : -1.0F;
			float alpha = w[0], beta = w[1], a = w[2], f = w[3];
			this.shoulder[s].xRot = alpha;
			this.shoulder[s].yRot = side * beta;
			this.shoulder[s].zRot = 0.0F;
			this.arm[s].zRot = side * a;
			this.fore[s].zRot = side * f;
			float phi = a + f;
			// the bones in the wing's plane (u out along the shoulder's axis, v forward): the wrist and the finger tips
			float wu = HUMERUS * Mth.cos(a) + FOREARM * Mth.cos(phi);
			float wv = HUMERUS * Mth.sin(a) + FOREARM * Mth.sin(phi);
			for (int i = 0; i < 3; i++) {
				this.fingers[s][i].zRot = side * w[4 + i];
			}
			float tip2U = wu + FINGER[2] * Mth.cos(phi + w[6]);
			float tip1V = wv + FINGER[1] * Mth.sin(phi + w[5]), tip2V = wv + FINGER[2] * Mth.sin(phi + w[6]);
			// the membrane from the body out to the wrist, as deep as the trailing finger reaches back
			float reachU = Math.max(0.5F, Math.max(wu, tip2U) * 0.97F);
			float depth = Mth.clamp(1.5F - Math.min(tip1V, tip2V), 0.5F, 8.5F);
			if (hang) {
				// the sheet lies on the belly, reaching in across it (the membrane's box shifted in, not turned)
				this.webIn[s].x = -side * HANG_SHEET;
				this.webIn[s].z = 0.05F - HANG_SHEET_IN;
				this.webIn[s].xScale = HANG_SHEET / 16.0F;
				this.webIn[s].yScale = 1.0F;
				this.webIn[s].zRot = 0.0F;
			} else {
				this.webIn[s].x = 0.0F;
				this.webIn[s].z = 0.05F;
				this.webIn[s].xScale = Mth.lerp(spread, 0.4F, reachU / 16.0F);
				this.webIn[s].yScale = Mth.lerp(spread, 0.55F, depth / 8.5F);
				this.webIn[s].zRot = side * a * 0.4F;
			}
			// the panels between the fingers: along the bisector, as wide as the gap
			float gapA = Math.abs(w[4] - w[5]), gapB = Math.abs(w[5] - w[6]);
			this.webA[s].zRot = side * (w[4] + w[5]) * 0.5F;
			this.webB[s].zRot = side * (w[5] + w[6]) * 0.5F;
			this.webA[s].xScale = (FINGER[0] + FINGER[1]) * 0.5F * 0.92F / 10.0F;
			this.webB[s].xScale = (FINGER[1] + FINGER[2]) * 0.5F * 0.92F / 9.0F;
			this.webA[s].yScale = Math.max(0.04F, (FINGER[0] + FINGER[1]) * 0.5F * gapA * 0.65F / 6.0F);
			this.webB[s].yScale = Math.max(0.04F, (FINGER[1] + FINGER[2]) * 0.5F * gapB * 0.65F / 6.0F);
			// the thumb points ahead of the body whichever way the forearm lies: its direction in the forearm's own frame
			float ca = Mth.cos(alpha), sa = Mth.sin(alpha), cb = Mth.cos(beta), sb = Mth.sin(beta);
			float e1x = cb, e1y = 0.0F, e1z = -sb;
			float e3x = ca * sb, e3y = -sa, e3z = ca * cb;
			float e2x = e3y * e1z - e3z * e1y, e2y = e3z * e1x - e3x * e1z, e2z = e3x * e1y - e3y * e1x;
			float cp = Mth.cos(phi), sp = Mth.sin(phi);
			float fxx = cp * e1x + sp * e2x, fxy = cp * e1y + sp * e2y, fxz = cp * e1z + sp * e2z;
			float fyx = -sp * e1x + cp * e2x, fyy = -sp * e1y + cp * e2y, fyz = -sp * e1z + cp * e2z;
			// ahead and a little down
			float dx = 0.0F, dy = 0.96F, dz = 0.29F;
			float ux = dx * fxx + dy * fxy + dz * fxz;
			float uy = dx * fyx + dy * fyy + dz * fyz;
			float uz = dx * e3x + dy * e3y + dz * e3z;
			this.thumb[s].xRot = (float)Math.asin(Mth.clamp(uz, -1.0F, 1.0F));
			this.thumb[s].zRot = side * (float)Math.atan2(-ux, uy);
		}

		/**
		 * Solves a left wing's joint angles to put the wrist at (tx, ty, tz) from the shoulder (body space), the elbow
		 * toward the pole (px, py, pz). out = alpha (the shoulder's twist), beta (its roll), A (the humerus' sweep in the
		 * wing's plane), F (the elbow's bend). The wing's plane is the one through the shoulder, the wrist and the pole,
		 * which always contains a line across the body, the one the shoulder's twist and roll can lay the wing along.
		 */
		private static void reach(float tx, float ty, float tz, float px, float py, float pz, float[] out) {
			float d = (float)Math.sqrt(tx * tx + ty * ty + tz * tz);
			float dc = Mth.clamp(d, Math.abs(HUMERUS - FOREARM) + 0.05F, HUMERUS + FOREARM - 0.05F);
			float ex = tx / d, ey = ty / d, ez = tz / d;
			// the pole's part across the line to the wrist is which way the elbow bows
			float dot = px * ex + py * ey + pz * ez;
			float qx = px - dot * ex, qy = py - dot * ey, qz = pz - dot * ez;
			float ql = (float)Math.sqrt(qx * qx + qy * qy + qz * qz);
			if (ql < 1.0E-4F) {
				qx = 0.0F;
				qy = 0.0F;
				qz = -1.0F;
				dot = ez * -1.0F;
				qx -= dot * ex;
				qy -= dot * ey;
				qz -= dot * ez;
				ql = (float)Math.sqrt(qx * qx + qy * qy + qz * qz);
			}
			qx /= ql;
			qy /= ql;
			qz /= ql;
			float nx = ey * qz - ez * qy, ny = ez * qx - ex * qz, nz = ex * qy - ey * qx;
			// the humerus' direction, the forearm's
			float cosD = (HUMERUS * HUMERUS + dc * dc - FOREARM * FOREARM) / (2.0F * HUMERUS * dc);
			float sinD = (float)Math.sqrt(Math.max(0.0F, 1.0F - cosD * cosD));
			float hx = cosD * ex + sinD * qx, hy = cosD * ey + sinD * qy, hz = cosD * ez + sinD * qz;
			float fx = (dc * ex - HUMERUS * hx) / FOREARM, fy = (dc * ey - HUMERUS * hy) / FOREARM, fz = (dc * ez - HUMERUS * hz) / FOREARM;
			// the wing's frame: e3 (its face) down-ish, e1 across the body and outward, e2 = e3 x e1
			if (nz < 0.0F) {
				nx = -nx;
				ny = -ny;
				nz = -nz;
			}
			float e1x = -nz, e1z = nx;
			float l1 = (float)Math.sqrt(e1x * e1x + e1z * e1z);
			if (l1 < 1.0E-4F) {
				// the wing's face straight along the body: lay it across the body
				e1x = 1.0F;
				e1z = 0.0F;
				l1 = 1.0F;
			}
			e1x /= l1;
			e1z /= l1;
			if (e1x < 0.0F) {
				e1x = -e1x;
				e1z = -e1z;
			}
			float e2x = ny * e1z, e2y = nz * e1x - nx * e1z, e2z = -ny * e1x;
			float beta = (float)Math.atan2(-e1z, e1x);
			float alpha = (float)Math.atan2(-ny, nx * Mth.sin(beta) + nz * Mth.cos(beta));
			float a = (float)Math.atan2(hx * e2x + hy * e2y + hz * e2z, hx * e1x + hz * e1z);
			float phi = (float)Math.atan2(fx * e2x + fy * e2y + fz * e2z, fx * e1x + fz * e1z);
			float bend = phi - a;
			while (bend > Math.PI) {
				bend -= (float)(Math.PI * 2.0);
			}
			while (bend < -Math.PI) {
				bend += (float)(Math.PI * 2.0);
			}
			out[0] = alpha;
			out[1] = beta;
			out[2] = a;
			out[3] = bend;
		}
	}

	/**
	 * (0.18.113, Scan E; 0.19.66 a flying insect) Hive: antennae, a striped abdomen behind, and two clear wings that buzz;
	 * now a big bee, not a man. It hovers, never touching down (its lowest part about 4 pixels off the ground, a little
	 * lower when sneaking), bobbing a pixel and pitched nose-down as it moves: a round fuzzy thorax with the insignia on
	 * its back, a head with big compound eyes and waving antennae, two pairs of wings that always buzz, six thin legs
	 * that dangle and sway and are drawn up in flight, and a striped abdomen with a stinger hanging under and behind it,
	 * which curls forward under it when it attacks. The man's parts are hidden (the hidden body carries the insignia plate on the thorax).
	 */
	static final class Hive extends FormModel {
		// ---- tuning ----
		/** Hover: the thorax's centre height (model y; ground is 24), how far it bobs and how fast (radians a tick). */
		static final float HOVER_Y = 11.8F, BOB = 0.6F, BOB_RATE = 0.13F;
		/** Nose-down pitch at full speed, and how far it banks into a turn (radians). */
		static final float PITCH = 0.45F, BANK = 0.25F;
		/** The wings: beats a tick (radians), the stroke's size, the lift at rest and the sweep back. */
		static final float FLAP = 3.1F, FLAP_AMP = 0.26F, WING_REST = 0.35F, WING_SWEEP = 0.5F;
		/** The abdomen and stinger hanging pitch (radians, negative hangs the back end down) and how far a sting curls them. */
		static final float ABDOMEN_REST = -0.55F, STINGER_REST = -0.65F, CURL = 1.0F, STINGER_CURL = 0.5F;
		/** How much the thorax lifts (pixels) as it stings, so the curled stinger clears the ground. */
		static final float STING_LIFT = 1.2F;
		/**
		 * Sneaking it hovers this much lower (pixels); the player renderer already sinks a crouching player 2 pixels
		 * (AvatarRenderer.getRenderOffset), so it rises that much back first and never touches down.
		 */
		static final float SNEAK_DROP = 1.0F, CROUCH_SINK = 2.0F;
		/** How far the abdomen may droop on a landing or a sneak (radians of tailLift), so its tip stays off the ground. */
		static final float ABDOMEN_DROOP = 0.35F;
		/** How far out from the middle of its back each wing is hinged (pixels), so the right and left pairs never cross. */
		static final float WING_X = 2.2F;
		/** The legs' z along the thorax (front to back) and how far each tucks back at full speed. */
		private static final float[] LEG_Z = {-2.2F, 0.0F, 2.2F};
		private static final float[] LEG_TUCK = {0.9F, 1.0F, 1.2F};

		private final ModelPart thorax, beeHead, abdomen, stinger, proboscis;
		private final ModelPart rightWing, leftWing, rightHind, leftHind, rightAntenna, leftAntenna;
		/** 0-2 right (front to back), 3-5 left. */
		private final ModelPart[] legs = new ModelPart[6];

		Hive() {
			super(bake(mesh()), "hive");
			ModelPart root = this.root();
			this.thorax = root.getChild("thorax");
			this.beeHead = this.thorax.getChild("bee_head");
			this.proboscis = this.beeHead.getChild("proboscis");
			this.rightAntenna = this.beeHead.getChild("right_antenna");
			this.leftAntenna = this.beeHead.getChild("left_antenna");
			this.abdomen = this.thorax.getChild("abdomen");
			this.stinger = this.abdomen.getChild("stinger");
			this.rightWing = this.thorax.getChild("right_wing");
			this.leftWing = this.thorax.getChild("left_wing");
			this.rightHind = this.thorax.getChild("right_hind_wing");
			this.leftHind = this.thorax.getChild("left_hind_wing");
			for (int i = 0; i < 6; i++) {
				this.legs[i] = this.thorax.getChild("leg" + i);
			}
			// the plate lies on the thorax's back (the hidden body is laid face-up there, see setupAnim)
			this.insigniaY = 0.0F;
			this.insigniaZ = -3.0F;
			this.insigniaSize = 0.6F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition thorax = root.addOrReplaceChild("thorax", CubeListBuilder.create().texOffs(0, 64).addBox(-3.0F, -3.0F, -3.5F, 6.0F, 6.0F, 7.0F),
				PartPose.offset(0.0F, HOVER_Y, 0.0F));
			PartDefinition abdomen = thorax.addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(28, 64).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 8.0F),
				PartPose.offsetAndRotation(0.0F, 0.8F, 2.5F, ABDOMEN_REST, 0.0F, 0.0F));
			abdomen.addOrReplaceChild("stinger", CubeListBuilder.create().texOffs(0, 92).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 3.0F),
				PartPose.offsetAndRotation(0.0F, 0.0F, 8.0F, STINGER_REST, 0.0F, 0.0F));
			PartDefinition head = thorax.addOrReplaceChild("bee_head", CubeListBuilder.create().texOffs(0, 80).addBox(-2.5F, -2.5F, -4.0F, 5.0F, 5.0F, 4.0F),
				PartPose.offset(0.0F, -0.5F, -2.6F));
			head.addOrReplaceChild("right_eye", CubeListBuilder.create().texOffs(20, 80).addBox(-4.5F, -2.0F, -4.0F, 2.0F, 4.0F, 4.0F), PartPose.ZERO);
			head.addOrReplaceChild("left_eye", CubeListBuilder.create().texOffs(20, 80).mirror().addBox(2.5F, -2.0F, -4.0F, 2.0F, 4.0F, 4.0F), PartPose.ZERO);
			head.addOrReplaceChild("proboscis", CubeListBuilder.create().texOffs(48, 80).addBox(-1.0F, -0.5F, -2.0F, 2.0F, 1.0F, 2.0F),
				PartPose.offsetAndRotation(0.0F, 1.6F, -3.6F, 0.3F, 0.0F, 0.0F));
			head.addOrReplaceChild("right_antenna", CubeListBuilder.create().texOffs(34, 80).addBox(-0.5F, -0.5F, -5.0F, 1.0F, 1.0F, 5.0F),
				PartPose.offsetAndRotation(-1.4F, -2.3F, -3.6F, -0.7F, 0.3F, 0.0F));
			head.addOrReplaceChild("left_antenna", CubeListBuilder.create().texOffs(34, 80).mirror().addBox(-0.5F, -0.5F, -5.0F, 1.0F, 1.0F, 5.0F),
				PartPose.offsetAndRotation(1.4F, -2.3F, -3.6F, -0.7F, -0.3F, 0.0F));
			// wings: the right pair reaches toward -x, the left mirrors; the fore pair sits forward, the hind pair behind
			thorax.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(0, 100).addBox(-9.0F, -0.5F, -1.5F, 9.0F, 1.0F, 4.0F),
				PartPose.offset(-WING_X, -2.7F, -1.2F));
			thorax.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(0, 100).mirror().addBox(0.0F, -0.5F, -1.5F, 9.0F, 1.0F, 4.0F),
				PartPose.offset(WING_X, -2.7F, -1.2F));
			thorax.addOrReplaceChild("right_hind_wing", CubeListBuilder.create().texOffs(28, 100).addBox(-6.0F, -0.5F, -1.0F, 6.0F, 1.0F, 3.0F),
				PartPose.offset(-WING_X, -2.7F, 1.8F));
			thorax.addOrReplaceChild("left_hind_wing", CubeListBuilder.create().texOffs(28, 100).mirror().addBox(0.0F, -0.5F, -1.0F, 6.0F, 1.0F, 3.0F),
				PartPose.offset(WING_X, -2.7F, 1.8F));
			// six legs off the underside of the thorax, three a side
			for (int i = 0; i < 6; i++) {
				boolean left = i >= 3;
				thorax.addOrReplaceChild("leg" + i, CubeListBuilder.create().texOffs(0, 108).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F),
					PartPose.offset(left ? 1.8F : -1.8F, 2.6F, LEG_Z[i % 3]));
			}
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.head.visible = this.hat.visible = this.body.visible = false;
			this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
			this.body.resetPose();
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			float speed = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
			boolean sneak = state.isCrouching;
			// a sting: the abdomen curls forward under it and back
			float sting = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
			// hover: bobbing, and a quicker flutter of the bob when it flies; nose-down flying, flaring up as it falls
			float bob = Mth.sin(age * BOB_RATE) * BOB + Mth.sin(age * 0.4F) * 0.3F * speed;
			float pitch = speed * PITCH - m.fall() * m.air() * 0.25F + Mth.sin(age * 0.09F) * 0.03F + sting * 0.2F;
			float bank = m.turn() * BANK + Mth.sin(age * 0.07F) * 0.03F;
			this.thorax.y = HOVER_Y + bob - sting * STING_LIFT + (sneak ? SNEAK_DROP - CROUCH_SINK : 0.0F);
			this.thorax.xRot = pitch;
			this.thorax.zRot = bank;
			// the head turns where it looks, holding level against the pitch
			this.beeHead.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -1.2F, 1.2F);
			this.beeHead.xRot = Mth.clamp(state.xRot * Mth.DEG_TO_RAD * 0.7F, -0.9F, 0.9F) - pitch * 0.6F;
			this.proboscis.xRot = 0.3F + Mth.sin(age * 0.2F) * 0.1F + sting * 0.3F;
			// antennae wave, and sweep back as it flies
			float feel = Mth.sin(age * 0.17F) * 0.08F;
			this.rightAntenna.xRot = -0.7F - speed * 0.4F + feel;
			this.leftAntenna.xRot = -0.7F - speed * 0.4F - feel;
			this.rightAntenna.yRot = 0.3F + Mth.sin(age * 0.13F) * 0.08F + m.turn() * 0.2F;
			this.leftAntenna.yRot = -0.3F - Mth.sin(age * 0.13F + 1.0F) * 0.08F + m.turn() * 0.2F;
			// the abdomen hangs behind, rising as it flies and falls, curling forward under it on a sting
			this.abdomen.xRot = ABDOMEN_REST - speed * 0.15F - sting * CURL + Mth.sin(age * 0.35F) * 0.04F
				+ Math.max(-ABDOMEN_DROOP, tailLift(m)) * 0.6F;
			this.abdomen.yRot = m.trail() * 0.3F + Mth.sin(age * 0.1F) * 0.05F;
			float breath = 1.0F + m.breath() * 0.03F;
			this.abdomen.xScale = this.abdomen.yScale = this.abdomen.zScale = breath;
			this.stinger.xRot = STINGER_REST - sting * STINGER_CURL;
			// wings: always buzzing, the hind pair a beat behind, swept back more in flight
			float lift = WING_REST + Mth.sin(age * FLAP) * FLAP_AMP;
			float sweep = WING_SWEEP + speed * 0.25F + Mth.cos(age * FLAP) * 0.1F;
			this.rightWing.zRot = lift;
			this.leftWing.zRot = -lift;
			this.rightWing.yRot = sweep;
			this.leftWing.yRot = -sweep;
			float hindLift = WING_REST * 0.8F + Mth.sin(age * FLAP + 0.9F) * FLAP_AMP * 0.9F;
			float hindSweep = sweep + 0.25F;
			this.rightHind.zRot = hindLift;
			this.leftHind.zRot = -hindLift;
			this.rightHind.yRot = hindSweep;
			this.leftHind.yRot = -hindSweep;
			// legs: dangle under it (held plumb against the pitch and bank), swaying, drawn up and back when it flies
			for (int i = 0; i < 6; i++) {
				float side = i >= 3 ? -1.0F : 1.0F;
				float tuck = speed * LEG_TUCK[i % 3] + (sneak ? 0.5F : 0.0F);
				ModelPart leg = this.legs[i];
				leg.xRot = -pitch + tuck + Mth.sin(age * 0.11F + i * 1.3F) * 0.1F + m.trail() * 0.15F;
				leg.zRot = side * (0.12F + m.air() * 0.35F + Mth.sin(age * 0.09F + i) * 0.06F) - bank;
			}
			// the hidden body lies face-up on the thorax's back, so the insignia plate sits flush there
			this.body.x = 0.0F;
			this.body.y = this.thorax.y;
			this.body.z = 0.0F;
			this.body.xRot = pitch - (float)Math.PI * 0.5F;
			this.body.yRot = 0.0F;
			// banked with the thorax too: Rz(bank)Rx(pitch - PI/2) is the thorax's own turn, then laid face-up
			this.body.zRot = bank;
		}
	}

	/**
	 * (0.18.113, Scan E; 0.19.62 upright, a spider when prone) Skitter: standing, the shape it always had, a man with
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
		/** (0.19.62) Up a wall, the legs step this much faster (the walk barely moves while climbing). */
		static final float CLIMB_GAIT = 0.35F;
		/** Rest: each leg pair's yaw (hind to front, the right side's; the left mirrors) and droop. */
		private static final float[] REST_YAW = {0.785F, 0.39F, -0.39F, -0.785F};
		private static final float[] REST_DROOP = {0.785F, 0.58F, 0.58F, 0.785F};
		/** Each pair's step phase (hind to front): the spider's alternating fours. */
		private static final float[] PHASE = {0.0F, (float)Math.PI, (float)Math.PI * 0.5F, (float)Math.PI * 1.5F};
		/** (0.19.62) Past this far into the crawl it's drawn as the spider. */
		static final float SPIDER_AT = 0.5F;

		private final ModelPart thorax, spiderHead, abdomen;
		/** Hind to front: 0-3 right, 4-7 left. */
		private final ModelPart[] legs = new ModelPart[8];
		/** (0.19.62) Upright: the four thin legs off its back, and the small abdomen behind. */
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
	 * (0.19.62 a raptor) Velox: slight, a swept-back crest, a visor that drops over the eyes at speed, a short snout and
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

	/**
	 * (0.18.xxx, Gremlin: a big-headed imp with long ears that twitch and a thin tail that flicks; 0.19.66 a hunched
	 * knuckle-walker) A small near-horizontal body carried between long arms planted on the knuckles and short bowed
	 * hind legs, the huge skull held up and forward on a short neck with its ears flopping. It lopes: arms and legs
	 * step in diagonal pairs (the legs a beat behind), each limb solved to a foot on the ground (two-bone IK) so
	 * nothing floats or stretches, the body bobs and the BIG SKULL BOBS and nods with every footfall a beat behind the
	 * body; a swing is a bite (the head darts, the jaw snaps) with a swipe of the right arm; a leap throws the arms
	 * forward and tucks the legs. In the imp the man's parts are hidden or replaced: the head is the skull, the arms
	 * and legs are the two-jointed limbs, and the hidden body lies face-up on the back so the insignia sits there.
	 */
	static final class Gremlin extends FormModel {
		// ---- tuning ----
		/** The torso's centre (model y, z), its resting pitch (negative: shoulders up, hips low), and how far it drops when sneaking. */
		static final float TORSO_Y = 11.0F, TORSO_Z = 1.5F, TORSO_PITCH = -0.30F, CROUCH_DROP = 2.5F;
		/** Where the shoulders and hips sit on the torso (local x, y, z; the right side is negative x). */
		static final float SHOULDER_X = 4.6F, SHOULDER_Y = -0.5F, SHOULDER_Z = -3.0F;
		static final float HIP_X = 3.2F, HIP_Y = 1.0F, HIP_Z = 3.0F;
		/** The neck: where the skull is hung on the torso's front (local y, z) and how far below it the pivot sits. */
		static final float NECK_Y = -1.5F, NECK_Z = -4.0F, NECK_DROP = 1.0F;
		/** Bone lengths: arm upper and lower, leg upper and lower; how far a fist and a foot reach below their joint. */
		static final float ARM_UPPER = 7.0F, ARM_LOWER = 7.0F, LEG_UPPER = 5.0F, LEG_LOWER = 6.0F;
		static final float FIST_DROP = 2.5F, FOOT_DROP = 1.5F;
		/** Where the fists and feet plant (model z) at rest, how far a stride sweeps (the same for both, so no foot slips), and how high a swinging limb lifts. */
		static final float HAND_Z = -5.0F, FOOT_Z = 3.5F, HAND_STRIDE = 5.0F, FOOT_STRIDE = 5.0F, HAND_LIFT = 3.0F, FOOT_LIFT = 2.5F;
		/** The share of a cycle a limb is planted, and how fast the cycle runs against the walk. */
		static final float DUTY = 0.6F, GAIT = 0.6662F * 1.25F;
		/** Where each limb is in the cycle: the arms alternate, the legs a beat behind the opposite arm. */
		static final float RIGHT_ARM_AT = 0.0F, LEFT_LEG_AT = 0.12F, LEFT_ARM_AT = 0.5F, RIGHT_LEG_AT = 0.62F;
		/** The body's bob (pixels) and rock (radians) a step. */
		static final float BOB = 0.9F, ROCK = 0.06F;
		/** The big skull's bob (pixels) and nod (radians) each footfall, and the phase it lags the body by. */
		static final float SKULL_BOB = 1.3F, SKULL_NOD = 0.2F, SKULL_LAG = 0.9F;
		/** The skull's resting pitch (negative: held up) and how far a bite darts it forward (pixels; keep under 2.5 so the skull stays on the torso). */
		static final float HEAD_PITCH = -0.1F, BITE_DART = 2.0F;
		/** The jaw: how wide a bite opens it, and the idle chatter. */
		static final float JAW_BITE = 0.85F, JAW_IDLE = 0.04F;
		/** A swipe: how high and how far forward the right fist goes. */
		static final float SWIPE_LIFT = 7.0F, SWIPE_REACH = 4.0F;
		/** A leap: how high the fists and feet are drawn up, and how far forward the fists reach. */
		static final float AIR_HAND_LIFT = 8.0F, AIR_HAND_REACH = 3.0F, AIR_FOOT_LIFT = 5.0F;
		/** The ears' resting droop (zRot; right ear, negative is outward), their sweep back at a run, and their flop. */
		static final float EAR_REST = -0.2F, EAR_SWEEP = 0.45F, EAR_FLOP = 0.25F;
		/** The tail's resting rise, and the insignia's size on the back. */
		static final float TAIL_REST = 0.5F, PLATE_SIZE = 0.85F;

		private final ModelPart torso, tail, jaw, rightEar, leftEar;
		private final ModelPart rightLower, leftLower, rightFist, leftFist, rightShin, leftShin, rightFoot, leftFoot;
		/** The foot a limb is aimed at (set by {@link #aim}). */
		private float aimY, aimZ;

		Gremlin() {
			super(bake(mesh()), "gremlin");
			this.insigniaY = 0.0F;
			this.insigniaZ = -3.0F;
			this.insigniaSize = PLATE_SIZE;
			ModelPart root = this.root();
			this.torso = root.getChild("torso");
			this.tail = root.getChild("tail");
			this.jaw = this.head.getChild("jaw");
			this.rightEar = this.head.getChild("right_ear");
			this.leftEar = this.head.getChild("left_ear");
			this.rightLower = this.rightArm.getChild("forearm");
			this.leftLower = this.leftArm.getChild("forearm");
			this.rightFist = this.rightLower.getChild("fist");
			this.leftFist = this.leftLower.getChild("fist");
			this.rightShin = this.rightLeg.getChild("shin");
			this.leftShin = this.leftLeg.getChild("shin");
			this.rightFoot = this.rightShin.getChild("foot");
			this.leftFoot = this.leftShin.getChild("foot");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the skull is the head: big, held up and forward of the neck pivot, with a hinged jaw and two great ears
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 64).addBox(-5.0F, -9.0F, -8.0F, 10.0F, 10.0F, 10.0F),
				PartPose.offset(0.0F, 8.0F, -4.0F));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, 0.0F, -6.5F, 7.0F, 3.0F, 6.0F),
				PartPose.offset(0.0F, 1.0F, -1.5F));
			head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(40, 64).addBox(-5.0F, -6.0F, -0.5F, 5.0F, 6.0F, 1.0F),
				PartPose.offsetAndRotation(-4.5F, -5.5F, -3.0F, 0.0F, 0.0F, EAR_REST));
			head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(40, 64).mirror().addBox(0.0F, -6.0F, -0.5F, 5.0F, 6.0F, 1.0F),
				PartPose.offsetAndRotation(4.5F, -5.5F, -3.0F, 0.0F, 0.0F, -EAR_REST));
			// the man's body is hidden (no cubes): it only carries the insignia, laid on the back of the real torso
			root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, TORSO_Y, TORSO_Z));
			PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(16, 16).addBox(-3.5F, -3.0F, -4.5F, 7.0F, 6.0F, 9.0F),
				PartPose.offsetAndRotation(0.0F, TORSO_Y, TORSO_Z, TORSO_PITCH, 0.0F, 0.0F));
			for (int i = 0; i < 4; i++) {
				boolean left = (i & 1) == 1;
				float z = i < 2 ? -2.5F : 2.5F;
				CubeListBuilder spine = CubeListBuilder.create().texOffs(56, 64);
				if (left) {
					spine.mirror();
				}
				torso.addOrReplaceChild("spine" + i, spine.addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F),
					PartPose.offsetAndRotation(left ? 2.9F : -2.9F, -2.6F, z, 0.4F, 0.0F, left ? 0.2F : -0.2F));
			}
			root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(40, 72).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 6.0F),
				PartPose.offsetAndRotation(0.0F, 11.0F, 6.0F, TAIL_REST, 0.0F, 0.0F));
			// long arms (upper, lower, knuckled fist) and short bowed legs (thigh, shin, foot)
			for (int side = 0; side < 2; side++) {
				boolean left = side == 1;
				String s = left ? "left" : "right";
				float sx = left ? SHOULDER_X : -SHOULDER_X;
				float hx = left ? HIP_X : -HIP_X;
				PartDefinition arm = root.addOrReplaceChild(s + "_arm", limb(48, 16, left).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 8.0F, 3.0F),
					PartPose.offset(sx, 9.6F, -1.2F));
				PartDefinition fore = arm.addOrReplaceChild("forearm", limb(48, 32, left).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 8.0F, 2.0F),
					PartPose.offset(0.0F, ARM_UPPER, 0.0F));
				fore.addOrReplaceChild("fist", limb(48, 44, left).addBox(-1.5F, -0.5F, -2.5F, 3.0F, 3.0F, 4.0F),
					PartPose.offset(0.0F, ARM_LOWER, 0.0F));
				PartDefinition leg = root.addOrReplaceChild(s + "_leg", limb(0, 16, left).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 6.0F, 3.0F),
					PartPose.offset(hx, 12.8F, 4.0F));
				PartDefinition shin = leg.addOrReplaceChild("shin", limb(0, 32, left).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 7.0F, 2.0F),
					PartPose.offset(0.0F, LEG_UPPER, 0.0F));
				shin.addOrReplaceChild("foot", limb(0, 44, left).addBox(-1.5F, -0.5F, -3.5F, 3.0F, 2.0F, 5.0F),
					PartPose.offset(0.0F, LEG_LOWER, 0.0F));
			}
			return mesh;
		}

		private static CubeListBuilder limb(int u, int v, boolean left) {
			CubeListBuilder b = CubeListBuilder.create().texOffs(u, v);
			return left ? b.mirror() : b;
		}

		/** Where in its cycle (0-1) a limb is, given the walk clock and its offset. */
		private static float cycle(float walk, float at) {
			float u = walk / (2.0F * (float)Math.PI) + at;
			return u - (float)Math.floor(u);
		}

		/**
		 * Aims a limb's foot for a point in the cycle: planted and sweeping back for {@link #DUTY}, then lifted and
		 * swinging forward. Idle (speed 0) it stands on its rest spot.
		 */
		private void aim(float u, float speed, float groundY, float restZ, float stride, float lift) {
			float s = stride * speed;
			if (u < DUTY) {
				this.aimY = groundY;
				this.aimZ = restZ - s * 0.5F + s * (u / DUTY);
			} else {
				float q = (u - DUTY) / (1.0F - DUTY);
				this.aimY = groundY - lift * speed * Mth.sin(q * (float)Math.PI);
				this.aimZ = restZ + s * 0.5F - s * q;
			}
		}

		/**
		 * Two-bone IK in the side plane: swings {@code upper} (hung at {@code hy, hz}) and {@code lower} so the end of
		 * {@code end} reaches (ty, tz), or as near as the bones allow. {@code bend} is which way the joint folds (+1
		 * back, like an elbow; -1 forward, like a knee); {@code tilt} is the end part's pitch from level.
		 */
		private static void solve(ModelPart upper, ModelPart lower, ModelPart end, float hy, float hz, float ty, float tz, float l1, float l2,
			float bend, float tilt) {
			float dy = ty - hy;
			float dz = tz - hz;
			float d = Math.max(1.0E-3F, (float)Math.sqrt(dy * dy + dz * dz));
			float reach = Mth.clamp(d, Math.abs(l1 - l2) + 0.05F, l1 + l2 - 0.05F);
			dy *= reach / d;
			dz *= reach / d;
			float phi = (float)Math.atan2(dz, dy);
			float cos = Mth.clamp((l1 * l1 + reach * reach - l2 * l2) / (2.0F * l1 * reach), -1.0F, 1.0F);
			float a1 = phi + bend * (float)Math.acos(cos);
			float a2 = (float)Math.atan2(dz - l1 * Mth.sin(a1), dy - l1 * Mth.cos(a1));
			upper.y = hy;
			upper.z = hz;
			upper.xRot = a1;
			lower.xRot = a2 - a1;
			end.xRot = tilt - a2;
		}

		/** The model-space y of a point (local y, z) on the torso at this centre height and pitch. */
		private static float worldY(float cy, float pitch, float ly, float lz) {
			return cy + ly * Mth.cos(pitch) - lz * Mth.sin(pitch);
		}

		private static float worldZ(float pitch, float ly, float lz) {
			return TORSO_Z + ly * Mth.sin(pitch) + lz * Mth.cos(pitch);
		}

		/** The forearm and fist straight for the first-person arm (the model is shared, so they hold whoever was drawn last). */
		void handPose() {
			this.rightLower.xRot = 0.0F;
			this.leftLower.xRot = 0.0F;
			this.rightFist.xRot = 0.0F;
			this.leftFist.xRot = 0.0F;
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
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			float walk = state.walkAnimationPos * GAIT;
			float age = state.ageInTicks;
			float air = m.air();
			float swing = Mth.clamp(state.swingAnimation, 0.0F, 1.0F);
			float bite = Mth.sin(swing * (float)Math.PI);
			float swipe = bite;
			boolean low = m.crouching();
			// the body: bobs with the lope, rocks nose-down on a bite, stretches in a leap and squashes on landing
			float cy = TORSO_Y + (low ? CROUCH_DROP : 0.0F) - BOB * Mth.abs(Mth.sin(walk)) * speed
				+ Mth.sin(age * 0.09F) * 0.15F + Math.max(0.0F, m.land()) * 1.5F - air * 0.5F;
			float pitch = TORSO_PITCH + ROCK * Mth.sin(walk * 2.0F + 0.4F) * speed + bite * 0.12F - air * 0.2F + m.land() * 0.1F;
			this.torso.y = cy;
			this.torso.xRot = pitch;
			// the hidden body lies face-up on the torso's back so the insignia plate sits flush there
			this.body.y = cy;
			this.body.z = TORSO_Z;
			this.body.xRot = pitch - (float)Math.PI * 0.5F;
			// limbs: arms alternate, the legs a beat behind the opposite arm; planted fists and feet reach the ground
			float shoulderY = worldY(cy, pitch, SHOULDER_Y, SHOULDER_Z);
			float shoulderZ = worldZ(pitch, SHOULDER_Y, SHOULDER_Z);
			float hipY = worldY(cy, pitch, HIP_Y, HIP_Z);
			float hipZ = worldZ(pitch, HIP_Y, HIP_Z);
			float handY = 24.0F - FIST_DROP;
			float footY = 24.0F - FOOT_DROP;
			for (int side = 0; side < 2; side++) {
				boolean left = side == 1;
				ModelPart arm = left ? this.leftArm : this.rightArm;
				ModelPart lower = left ? this.leftLower : this.rightLower;
				ModelPart fist = left ? this.leftFist : this.rightFist;
				ModelPart leg = left ? this.leftLeg : this.rightLeg;
				ModelPart shin = left ? this.leftShin : this.rightShin;
				ModelPart foot = left ? this.leftFoot : this.rightFoot;
				this.aim(cycle(walk, left ? LEFT_ARM_AT : RIGHT_ARM_AT), speed, handY, HAND_Z, HAND_STRIDE, HAND_LIFT);
				float ty = this.aimY;
				float tz = this.aimZ;
				if (!left) {
					ty -= swipe * SWIPE_LIFT;
					tz -= swipe * SWIPE_REACH;
				}
				ty = Mth.lerp(air, ty, handY - AIR_HAND_LIFT);
				tz = Mth.lerp(air, tz, HAND_Z - AIR_HAND_REACH);
				arm.x = left ? SHOULDER_X : -SHOULDER_X;
				solve(arm, lower, fist, shoulderY, shoulderZ, ty, tz, ARM_UPPER, ARM_LOWER, 1.0F, (handY - ty) * 0.05F);
				this.aim(cycle(walk, left ? LEFT_LEG_AT : RIGHT_LEG_AT), speed, footY, FOOT_Z, FOOT_STRIDE, FOOT_LIFT);
				ty = Mth.lerp(air, this.aimY, footY - AIR_FOOT_LIFT);
				tz = Mth.lerp(air, this.aimZ, FOOT_Z - 1.0F);
				leg.x = left ? HIP_X : -HIP_X;
				solve(leg, shin, foot, hipY, hipZ, ty, tz, LEG_UPPER, LEG_LOWER, -1.0F, (footY - ty) * 0.08F);
			}
			// the big skull: hung on the front of the torso, bobbing and nodding each footfall a beat behind the body
			float lag = walk * 2.0F - SKULL_LAG;
			float bob = Mth.sin(lag) * SKULL_BOB * speed + Mth.sin(age * 0.07F) * 0.25F;
			this.head.y = worldY(cy, pitch, NECK_Y, NECK_Z) + NECK_DROP + bob - bite * 0.5F;
			this.head.z = worldZ(pitch, NECK_Y, NECK_Z) - bite * BITE_DART;
			this.head.xRot = HEAD_PITCH + state.xRot * Mth.DEG_TO_RAD + Mth.sin(lag + 0.6F) * SKULL_NOD * speed
				- (pitch - TORSO_PITCH) * 0.8F + bite * 0.25F + m.land() * 0.25F - air * 0.2F;
			this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
			this.head.zRot = Mth.sin(walk) * 0.05F * speed;
			this.jaw.xRot = JAW_IDLE + Mth.sin(age * 0.23F) * JAW_IDLE * (0.5F + speed) + Mth.sin(Math.min(1.0F, swing * 1.3F) * (float)Math.PI) * JAW_BITE
				+ Math.max(0.0F, m.fall()) * 0.2F * air;
			// the ears bounce with each step, flop down on landing (and lift as it falls), blow back at a run and lag a turn
			float twitch = Mth.sin(age * 0.3F) * 0.1F + speed * 0.1F;
			float flop = m.land() * 0.7F - Math.max(0.0F, m.fall()) * 0.4F * air;
			float step = Mth.sin(lag - 0.6F) * EAR_FLOP * speed;
			float sweep = EAR_SWEEP * speed + Math.max(0.0F, m.fall()) * 0.3F * air;
			this.rightEar.zRot = EAR_REST + twitch - flop - step - m.trail() * 0.25F;
			this.leftEar.zRot = -EAR_REST - twitch + flop + step - m.trail() * 0.25F;
			this.rightEar.yRot = sweep;
			this.leftEar.yRot = -sweep;
			// the tail hangs off the back of the torso and flicks
			this.tail.y = worldY(cy, pitch, -0.5F, 4.5F);
			this.tail.z = worldZ(pitch, -0.5F, 4.5F);
			this.tail.yRot = Mth.sin(age * 0.2F) * 0.5F + Mth.cos(walk) * 0.6F * speed - m.trail() * 0.8F;
			this.tail.xRot = TAIL_REST + tailLift(m);
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
	 * Wyrm: horned, winged and long-tailed (0.19.67 a small dragon on four legs). A long low scaly body on four clawed
	 * legs that trot in a lizard's diagonal gait (each leg two bones and a foot, solved so the planted feet never slide off
	 * the ground), a neck that rises to a horned head with a hinged jaw (a bite throws the head forward and opens the jaw),
	 * a spine ridge, and a tail in three joints to a spade tip that sways behind each turn. The dragon hangs off its own
	 * torso on the root, not off the man's body, so the poses laid over a man's body after it animates (the chest turning
	 * after the head, the crouch's lean and sway, the knee) leave it alone; the man's parts are all hidden, and his body
	 * copies the torso's pose each frame so the insignia sits on the chest's front. The torso carries the wings
	 * ({@link WyrmWings}, which copy it: on its shoulders, folded along its back on the ground) and cancels Wind's cruise
	 * tilt (a dragon already lies level; see {@link #CRUISE_BRAKE_LEAN}). Flying the body is level and the legs tuck up.
	 */
	static final class Wyrm extends FormModel {
		// ---- tuning ----
		/** The torso's height (model y of its middle) at rest; how far it sinks when sneaking and on a landing. */
		static final float BODY_Y = 14.0F, CROUCH_DROP = 2.0F, LAND_DIP = 1.5F;
		/**
		 * Where Wind's cruise tilt pivots (WindPose.rotate: half the box's height over the feet), found in model space: the
		 * player renderer draws the model at this scale (AvatarRenderer.scale) from this far over the feet (blocks).
		 */
		static final float PLAYER_SCALE = 0.9375F, MODEL_ORIGIN = 1.501F;
		/**
		 * The renderer draws a sneaking player 2 pixels of the world lower (AvatarRenderer.getRenderOffset), this many model
		 * pixels: sneaking, the feet are planted that much higher to stay on the ground.
		 */
		static final float CROUCH_SINK = 2.0F / PLAYER_SCALE;
		/** The gait: radians of step cycle per unit of walkAnimationPos, half a stride (pixels), the lift of a swinging foot, the fraction of the cycle a foot is down. */
		static final float GAIT = 1.8F, STRIDE = 3.5F, LIFT = 2.2F, DUTY = 0.6F;
		/** How far the walk bobs the torso (pixels), rolls it (radians) and wriggles it side to side (radians). */
		static final float BOB = 0.6F, ROLL = 0.04F, WRIGGLE = 0.06F;
		/** A foot lifting off curls its toes up this much (radians). */
		static final float TOE_LIFT = 0.35F;
		/** walkAnimationSpeed at which the gait is at full stride. */
		static final float WALK_FULL = 0.3F;
		/** The legs: a hip's offset from the torso's middle (x either side, y, z fore and hind), the two bones' and the foot's lengths. */
		static final float HIP_X = 3.7F, HIP_Y = 1.5F, FRONT_Z = -4.2F, HIND_Z = 4.0F, THIGH = 4.0F, SHIN = 4.5F, FOOT_H = 1.5F;
		/** The legs splay out a little (radians). */
		static final float SPLAY = 0.08F;
		/** Where an ankle rests ahead of its hip (pixels): the forelegs a touch forward, the hind straight under. */
		static final float FRONT_REACH = -0.5F, HIND_REACH = 0.0F;
		/** Flying, the legs tuck up under the body: the ankle's offset from its hip (z: fore, hind; y), and the toes' angle. */
		static final float TUCK_FRONT_Z = -0.5F, TUCK_HIND_Z = 2.5F, TUCK_Y = 3.6F, TUCK_TOE = 0.5F;
		/** Falling, the legs reach down and out: how far they reach (0-1), the ankle's offset (front z, hind z, y). */
		static final float REACH = 0.7F, REACH_FRONT_Z = -2.0F, REACH_HIND_Z = 1.5F, REACH_Y = 7.6F;
		/** The tail's resting pitch at each joint (+ is up): arching a little from the back, then drooping. */
		static final float[] TAIL_REST = {0.22F, -0.16F, -0.10F, 0.0F};
		/** How far the tail's lift (tailLift) may drop it and raise it (radians), and how much of it each joint takes. */
		static final float TAIL_DROOP = 0.35F, TAIL_RISE = 0.5F;
		private static final float[] TAIL_SHARE = {0.3F, 0.3F, 0.2F, 0.1F};
		/** The neck's resting pitch at its two joints and the head (+ is down): up from the chest, then level. */
		static final float NECK1 = -0.7F, NECK2 = 0.45F, HEAD = 0.3F;
		/** A bite: how far the neck straightens (joint 1, joint 2), how far the head thrusts forward (pixels), how wide the jaw opens (radians). */
		static final float BITE_NECK1 = 0.6F, BITE_NECK2 = -0.2F, BITE_THRUST = 0.9F, JAW_OPEN = 1.0F, JAW_REST = 0.06F;
		/** Flying, the neck stretches out ahead. */
		static final float FLY_NECK1 = 0.35F, FLY_NECK2 = -0.1F;
		/** How far the head follows where the player looks (degrees clamp: yaw, pitch). */
		static final float LOOK_YAW = 80.0F, LOOK_PITCH = 60.0F;
		/** Cruising (Wind's pose), the body dives and climbs after the look by this much of it (degrees clamp, share). */
		static final float DIVE_MAX = 60.0F, DIVE_FOLLOW = 0.7F;
		/** WindPose's air brake leans the body back this much (degrees); copied from it so it can be cancelled. */
		static final float CRUISE_BRAKE_LEAN = 25.0F;
		/** The spine's ridge tilts back this much (radians). */
		static final float SPIKE_TILT = -0.35F;
		/** Its flying wing beat bobs the body (pixels). */
		static final float BEAT_BOB = 0.6F;

		private static final float TWO_PI = (float)Math.PI * 2.0F;
		/** Spikes on the back: where each sits (parent: 0 torso, 1 first neck joint, 2 second, 3 first tail joint, 4 second; z along it). */
		private static final float[][] SPIKES = {
			{0, -3.5F}, {0, -1.0F}, {0, 1.5F}, {0, 4.0F}, {1, -2.0F}, {2, -1.0F}, {3, 2.5F}, {3, 5.0F}, {4, 2.5F}};

		private final ModelPart torso, chest, neck1, neck2, skull, jaw, tail1, tail2, tail3, spade;
		/** Fore right, fore left, hind right, hind left. */
		private final ModelPart[] thighs = new ModelPart[4], shins = new ModelPart[4], feet = new ModelPart[4];
		private final ModelPart[] spikes = new ModelPart[SPIKES.length];
		/** Every part that is animated, to put back to its resting pose each frame. */
		private final ModelPart[] moving;

		Wyrm() {
			super(bake(mesh()), "wyrm");
			// on the chest's front, under the neck (the man's body copies the torso's pose, so this is in the torso's space)
			this.insigniaY = 1.0F;
			this.insigniaZ = -8.0F;
			this.insigniaSize = 0.8F;
			this.torso = this.root().getChild("torso");
			this.chest = this.torso.getChild("chest");
			this.neck1 = this.torso.getChild("neck1");
			this.neck2 = this.neck1.getChild("neck2");
			this.skull = this.neck2.getChild("skull");
			this.jaw = this.skull.getChild("jaw");
			this.tail1 = this.torso.getChild("tail1");
			this.tail2 = this.tail1.getChild("tail2");
			this.tail3 = this.tail2.getChild("tail3");
			this.spade = this.tail3.getChild("spade");
			java.util.List<ModelPart> all = new java.util.ArrayList<>(java.util.List.of(this.torso, this.chest, this.neck1, this.neck2, this.skull,
				this.jaw, this.tail1, this.tail2, this.tail3, this.spade));
			for (int i = 0; i < 4; i++) {
				this.thighs[i] = this.torso.getChild("thigh" + i);
				this.shins[i] = this.thighs[i].getChild("shin");
				this.feet[i] = this.shins[i].getChild("foot");
				all.add(this.thighs[i]);
				all.add(this.shins[i]);
				all.add(this.feet[i]);
			}
			for (int i = 0; i < SPIKES.length; i++) {
				this.spikes[i] = this.spikeParent(i).getChild("spike" + i);
				all.add(this.spikes[i]);
			}
			this.moving = all.toArray(new ModelPart[0]);
		}

		/** The dragon's torso as it is posed this frame: every other part of it hangs off this, and the wings copy it ({@link WyrmWings}). */
		ModelPart torso() {
			return this.torso;
		}

		private ModelPart spikeParent(int i) {
			return switch ((int)SPIKES[i][0]) {
				case 0 -> this.torso;
				case 1 -> this.neck1;
				case 2 -> this.neck2;
				case 3 -> this.tail1;
				default -> this.tail2;
			};
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.5F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the man's head, body and legs are never drawn (the arms are only for the first-person hand); his body is the
			// insignia's anchor, posed as the torso
			root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0.0F, BODY_Y, -6.0F)).addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, BODY_Y, 0.0F));
			root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-HIP_X, BODY_Y + HIP_Y, HIND_Z));
			root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(HIP_X, BODY_Y + HIP_Y, HIND_Z));
			PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 64).addBox(-3.5F, -3.0F, -5.5F, 7.0F, 6.0F, 11.0F),
				PartPose.offset(0.0F, BODY_Y, 0.0F));
			torso.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(36, 64).addBox(-3.5F, -3.5F, -2.0F, 7.0F, 7.0F, 4.0F), PartPose.offset(0.0F, 1.0F, -6.0F));
			// the neck: two joints rising from the chest, then the head with its snout, jaw and horns
			PartDefinition neck1 = torso.addOrReplaceChild("neck1", CubeListBuilder.create().texOffs(0, 92).addBox(-1.5F, -1.5F, -5.0F, 3.0F, 3.0F, 6.0F),
				PartPose.offsetAndRotation(0.0F, -2.0F, -6.0F, NECK1, 0.0F, 0.0F));
			PartDefinition neck2 = neck1.addOrReplaceChild("neck2", CubeListBuilder.create().texOffs(38, 92).addBox(-1.5F, -1.5F, -4.0F, 3.0F, 3.0F, 5.0F),
				PartPose.offsetAndRotation(0.0F, 0.0F, -4.5F, NECK2, 0.0F, 0.0F));
			PartDefinition skull = neck2.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(18, 92).addBox(-2.5F, -2.0F, -4.0F, 5.0F, 4.0F, 5.0F),
				PartPose.offsetAndRotation(0.0F, 0.0F, -3.5F, HEAD, 0.0F, 0.0F));
			skull.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(8, 109).addBox(-1.5F, -0.7F, -4.0F, 3.0F, 2.0F, 4.0F), PartPose.offset(0.0F, 0.0F, -4.0F));
			skull.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(28, 101).addBox(-1.5F, 0.0F, -6.0F, 3.0F, 1.0F, 6.0F), PartPose.offset(0.0F, 1.4F, -0.5F));
			// the horns sweep back and out, up a little
			skull.addOrReplaceChild("right_horn", CubeListBuilder.create().texOffs(46, 101).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 5.0F),
				PartPose.offsetAndRotation(-1.8F, -1.3F, 0.2F, 0.5F, -0.3F, 0.0F));
			skull.addOrReplaceChild("left_horn", CubeListBuilder.create().texOffs(46, 101).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 5.0F),
				PartPose.offsetAndRotation(1.8F, -1.3F, 0.2F, 0.5F, 0.3F, 0.0F));
			// the tail: three joints and a spade
			PartDefinition tail1 = torso.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(0, 81).addBox(-2.0F, -2.0F, -1.0F, 4.0F, 4.0F, 7.0F),
				PartPose.offsetAndRotation(0.0F, -0.5F, 5.2F, TAIL_REST[0], 0.0F, 0.0F));
			PartDefinition tail2 = tail1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(22, 81).addBox(-1.5F, -1.5F, -1.0F, 3.0F, 3.0F, 7.0F),
				PartPose.offsetAndRotation(0.0F, 0.0F, 5.5F, TAIL_REST[1], 0.0F, 0.0F));
			PartDefinition tail3 = tail2.addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(0, 101).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 6.0F),
				PartPose.offsetAndRotation(0.0F, 0.0F, 5.5F, TAIL_REST[2], 0.0F, 0.0F));
			tail3.addOrReplaceChild("spade", CubeListBuilder.create().texOffs(42, 81).addBox(-0.5F, -2.5F, -1.0F, 1.0F, 5.0F, 5.0F)
				.texOffs(36, 109).addBox(-2.5F, -0.5F, -0.5F, 5.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 4.5F, TAIL_REST[3], 0.0F, 0.0F));
			// the spine ridge
			for (int i = 0; i < SPIKES.length; i++) {
				PartDefinition parent = switch ((int)SPIKES[i][0]) {
					case 0 -> torso;
					case 1 -> neck1;
					case 2 -> neck2;
					case 3 -> tail1;
					default -> tail2;
				};
				float y = switch ((int)SPIKES[i][0]) {
					case 0 -> -3.0F;
					case 1, 2 -> -1.5F;
					case 3 -> -2.0F;
					default -> -1.5F;
				};
				parent.addOrReplaceChild("spike" + i, CubeListBuilder.create().texOffs(54, 109).addBox(-0.5F, -2.0F, -1.0F, 1.0F, 2.0F, 2.0F),
					PartPose.offsetAndRotation(0.0F, y, SPIKES[i][1], SPIKE_TILT, 0.0F, 0.0F));
			}
			// the legs: thigh, shin, foot; fore right, fore left, hind right, hind left
			for (int i = 0; i < 4; i++) {
				boolean front = i < 2, left = (i & 1) == 1;
				PartDefinition thigh = torso.addOrReplaceChild("thigh" + i, CubeListBuilder.create().texOffs(16, 101).addBox(-1.5F, -0.5F, -1.5F, 3.0F, 5.0F, 3.0F),
					PartPose.offset(left ? HIP_X : -HIP_X, HIP_Y, front ? FRONT_Z : HIND_Z));
				PartDefinition shin = thigh.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(0, 109).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 5.0F, 2.0F),
					PartPose.offset(0.0F, THIGH, 0.0F));
				shin.addOrReplaceChild("foot", CubeListBuilder.create().texOffs(22, 109).addBox(-1.5F, -0.5F, -3.5F, 3.0F, 2.0F, 4.0F), PartPose.offset(0.0F, SHIN, 0.0F));
			}
			return mesh;
		}

		/** Where a foot is along its stride (-1 forward .. +1 back, relative to the body) a fraction u of the way through its cycle. */
		private static float stride(float u) {
			if (u < DUTY) {
				// planted: the ground slides back under it
				return -1.0F + 2.0F * u / DUTY;
			}
			// swinging: forward again, easing at both ends
			float s = (u - DUTY) / (1.0F - DUTY);
			return 1.0F - 2.0F * (0.5F - 0.5F * Mth.cos(s * (float)Math.PI));
		}

		/** How high a foot is lifted (0-1) a fraction u of the way through its cycle. */
		private static float lifted(float u) {
			return u < DUTY ? 0.0F : Mth.sin((u - DUTY) / (1.0F - DUTY) * (float)Math.PI);
		}

		/**
		 * Two bones from the hip to an ankle {@code (tz, ty)} away (z back, y down, from the hip), the middle joint
		 * forward (hind legs) or back (forelegs); the foot is kept at {@code toe} to the ground.
		 */
		private static void solve(ModelPart thigh, ModelPart shin, ModelPart foot, float tz, float ty, boolean kneeForward, float toe) {
			float reach = Mth.clamp((float)Math.sqrt(tz * tz + ty * ty), Math.abs(THIGH - SHIN) + 0.3F, THIGH + SHIN - 0.05F);
			float dir = (float)Math.atan2(tz, ty);
			float bend = (float)Math.acos(Mth.clamp((THIGH * THIGH + reach * reach - SHIN * SHIN) / (2.0F * THIGH * reach), -1.0F, 1.0F));
			float a = kneeForward ? dir - bend : dir + bend;
			float ankleZ = reach * Mth.sin(dir), ankleY = reach * Mth.cos(dir);
			float b = (float)Math.atan2(ankleZ - THIGH * Mth.sin(a), ankleY - THIGH * Mth.cos(a)) - a;
			thigh.xRot = a;
			shin.xRot = b;
			foot.xRot = -(a + b) + toe;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			// the man is never drawn: his body is only the insignia's anchor (posed as the torso below)
			this.head.visible = this.hat.visible = this.body.visible = false;
			this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
			this.body.resetPose();
			for (ModelPart part : this.moving) {
				part.resetPose();
			}
			float t = state.ageInTicks;
			XenoMotion.Read m = XenoMotion.read(state);
			XenoformFlightPose.Flight flight = state.getData(XenoformFlightPose.KEY);
			float fly = flight == null ? 0.0F : XenoformFlightPose.smooth(flight.wings());
			float fold = flight == null ? 0.0F : XenoformFlightPose.smooth(flight.fold());
			Float cruiseData = state.getData(com.jaymospowers.client.power.wind.WindPose.KEY);
			float cruise = cruiseData == null || state.deathTime > 0.0F ? 0.0F : cruiseData;

			// ---- the walk: diagonal pairs (fore right with hind left) in step, the torso bobbing twice a cycle ----
			float walk = Mth.clamp(state.walkAnimationSpeed / WALK_FULL, 0.0F, 1.0F) * (1.0F - fly);
			float cycle = state.walkAnimationPos * GAIT / TWO_PI;
			cycle -= (float)Math.floor(cycle);
			float rise = 0.5F + 0.5F * Mth.cos(2.0F * TWO_PI * (cycle - 0.3F));
			float by = BODY_Y - BOB * rise * walk + m.land() * LAND_DIP;
			// the ground, in model y: higher when sneaking, as the whole model is drawn lower
			float ground = 24.0F;
			if (state.isCrouching) {
				by += CROUCH_DROP - CROUCH_SINK;
				ground -= CROUCH_SINK;
			}
			// flying, the wings' beat lifts it
			by += Mth.sin(t * WyrmWings.FLAP_RATE) * BEAT_BOB * fly * (1.0F - fold);
			float roll = Mth.sin(cycle * TWO_PI) * ROLL * walk;
			float wriggle = Mth.sin(cycle * TWO_PI + 0.4F) * WRIGGLE * walk;

			// ---- the torso. Wind's cruise tilt (WindPose.rotate) lays an upright body flat about the middle of the box, and rolls
			// it about its length; a dragon already lies level, so it tips back by the same angle about the same point, and
			// follows the look's pitch instead ----
			float tilt = 0.0F;
			if (cruise > 0.0F) {
				Float brakeData = state.getData(com.jaymospowers.client.power.wind.WindPose.BRAKE_KEY);
				float brake = brakeData == null ? 0.0F : brakeData;
				float laid = cruise * (1.0F - 0.75F * brake) * (-90.0F - state.xRot) + brake * CRUISE_BRAKE_LEAN;
				float dive = Mth.clamp(state.xRot, -DIVE_MAX, DIVE_MAX) * cruise * DIVE_FOLLOW;
				tilt = (laid + dive) * Mth.DEG_TO_RAD;
			}
			// the pivot, in model pixels: half the box's height over the feet (WindPose's), through the renderer's scale
			float middle = (MODEL_ORIGIN - state.boundingBoxHeight / Math.max(state.scale, 0.01F) * 0.5F / PLAYER_SCALE) * 16.0F;
			float toMiddle = by - middle;
			this.torso.y = middle + toMiddle * Mth.cos(tilt);
			this.torso.z = toMiddle * Mth.sin(tilt);
			this.torso.xRot = tilt;
			this.torso.yRot = wriggle;
			this.torso.zRot = roll;
			this.chest.yScale = 1.0F + m.breath() * 0.03F;
			// the man's body is the insignia's anchor: on the torso
			this.body.x = this.torso.x;
			this.body.y = this.torso.y;
			this.body.z = this.torso.z;
			this.body.xRot = this.torso.xRot;
			this.body.yRot = this.torso.yRot;
			this.body.zRot = this.torso.zRot;

			// ---- the legs ----
			float air = m.air() * (1.0F - fly) * REACH;
			for (int i = 0; i < 4; i++) {
				boolean front = i < 2, left = (i & 1) == 1;
				float u = cycle + (front == left ? 0.5F : 0.0F);
				u -= (float)Math.floor(u);
				float swing = lifted(u) * walk;
				float tz = (front ? FRONT_REACH : HIND_REACH) + STRIDE * walk * stride(u);
				// the ankle's height over the ground, in the torso's frame: the planted foot stays on the ground as the torso bobs
				float ty = ground - FOOT_H - by - HIP_Y - LIFT * swing;
				float toe = -TOE_LIFT * swing;
				// falling: reaching down; flying: tucked up under the body
				tz = Mth.lerp(air, tz, front ? REACH_FRONT_Z : REACH_HIND_Z);
				ty = Mth.lerp(air, ty, REACH_Y);
				tz = Mth.lerp(fly, tz, front ? TUCK_FRONT_Z : TUCK_HIND_Z);
				ty = Mth.lerp(fly, ty, TUCK_Y);
				toe = Mth.lerp(fly, toe, TUCK_TOE);
				solve(this.thighs[i], this.shins[i], this.feet[i], tz, ty, !front, toe);
				this.thighs[i].zRot = left ? -SPLAY : SPLAY;
				this.shins[i].zRot = left ? SPLAY * 0.8F : -SPLAY * 0.8F;
			}

			// ---- the neck and head: they follow where the player looks, and a bite throws the head forward ----
			float bite = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
			float yaw = Mth.clamp(state.yRot, -LOOK_YAW, LOOK_YAW) * Mth.DEG_TO_RAD * (1.0F - cruise);
			float pitch = Mth.clamp(state.xRot, -LOOK_PITCH, LOOK_PITCH) * Mth.DEG_TO_RAD * (1.0F - DIVE_FOLLOW * cruise);
			// the head rides level against the torso's bob, and sways a little as it breathes
			float nod = Mth.cos(2.0F * TWO_PI * (cycle - 0.3F)) * 0.06F * walk + m.breath() * 0.03F;
			float sway = Mth.sin(t * 0.05F) * 0.05F;
			this.neck1.xRot = NECK1 + pitch * 0.3F + BITE_NECK1 * bite + FLY_NECK1 * fly;
			this.neck2.xRot = NECK2 + pitch * 0.3F + BITE_NECK2 * bite + FLY_NECK2 * fly + nod;
			this.skull.xRot = HEAD + pitch * 0.4F - nod;
			this.neck1.yRot = yaw * 0.3F - wriggle * 1.5F + sway;
			this.neck2.yRot = yaw * 0.3F - sway * 0.5F;
			this.skull.yRot = yaw * 0.4F;
			this.skull.zRot = Mth.sin(t * 0.07F + 0.5F) * 0.03F;
			this.neck2.z -= BITE_THRUST * bite;
			this.skull.z -= BITE_THRUST * bite;
			this.jaw.xRot = JAW_REST + (1.0F + Mth.sin(t * 0.09F)) * 0.04F + JAW_OPEN * bite;

			// ---- the tail swings behind each turn, each joint a beat after the last; it lifts as it drops, whips down on landing and droops when crouched ----
			float lag = m.trail() - m.turn();
			float tailUp = Mth.clamp(tailLift(m), -TAIL_DROOP, TAIL_RISE);
			float wag = Mth.cos(cycle * TWO_PI) * 0.25F * walk + Mth.sin(t * 0.08F) * 0.08F;
			this.tail1.yRot = wag - m.trail() * 0.5F - wriggle * 1.2F;
			this.tail2.yRot = Mth.sin(t * 0.08F - 0.8F) * 0.1F + wag * 0.6F - lag * 0.6F - m.trail() * 0.2F;
			this.tail3.yRot = Mth.sin(t * 0.08F - 1.6F) * 0.14F + wag * 0.5F - lag * 0.8F;
			this.spade.yRot = Mth.sin(t * 0.08F - 2.4F) * 0.2F + wag * 0.4F - lag * 0.8F;
			this.tail1.xRot = TAIL_REST[0] + walk * 0.05F + tailUp * TAIL_SHARE[0] + fly * 0.1F;
			this.tail2.xRot = TAIL_REST[1] + tailUp * TAIL_SHARE[1] - fly * 0.05F;
			this.tail3.xRot = TAIL_REST[2] + tailUp * TAIL_SHARE[2];
			this.spade.xRot = TAIL_REST[3] + tailUp * TAIL_SHARE[3];

			// ---- the ridge ripples ----
			for (int i = 0; i < this.spikes.length; i++) {
				this.spikes[i].xRot = SPIKE_TILT + Mth.sin(t * 0.1F - i * 0.7F) * 0.04F + m.breath() * 0.02F;
			}
		}
	}
}
