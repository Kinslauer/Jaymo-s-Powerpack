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
	 * (0.18.96) Gorger: squat and plated, a horn on its snout, little ears, a banded shell over its back that bobs as it
	 * walks; rolled up (B) it tucks its head and limbs in.
	 */
	static final class Gorger extends FormModel {
		private final ModelPart shell;

		Gorger() {
			super(bake(mesh()), "gorger");
			this.insigniaZ = -2.6F;
			this.shell = this.body.getChild("shell");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.6F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			head.addOrReplaceChild("horn", CubeListBuilder.create().texOffs(0, 64).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F),
				PartPose.offsetAndRotation(0.0F, -5.0F, -4.6F, -0.5F, 0.0F, 0.0F));
			head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(8, 64).addBox(-1.0F, -3.0F, -0.5F, 2.0F, 3.0F, 1.0F),
				PartPose.offsetAndRotation(-3.5F, -8.5F, 1.0F, 0.0F, 0.0F, -0.35F));
			head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(8, 64).mirror().addBox(-1.0F, -3.0F, -0.5F, 2.0F, 3.0F, 1.0F),
				PartPose.offsetAndRotation(3.5F, -8.5F, 1.0F, 0.0F, 0.0F, 0.35F));
			root.getChild("body").addOrReplaceChild("shell", CubeListBuilder.create().texOffs(0, 72).addBox(-5.0F, 0.0F, 0.0F, 10.0F, 10.0F, 4.0F, new CubeDeformation(0.6F)),
				PartPose.offset(0.0F, -0.5F, 1.6F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			// the shell rides up and down with each step
			this.shell.y = -0.5F + Mth.abs(Mth.cos(state.walkAnimationPos * 0.6662F)) * 0.6F * state.walkAnimationSpeed;
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

	/** (0.18.103, patch 5) Gyre: broad and scaly, a domed shell over its back that rocks as it walks, a hooked beak. */
	static final class Gyre extends FormModel {
		private final ModelPart shell;

		Gyre() {
			super(bake(mesh()), "gyre");
			this.insigniaZ = -2.5F;
			this.shell = this.body.getChild("shell");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.5F), 0.0F);
			PartDefinition root = mesh.getRoot();
			root.getChild("body").addOrReplaceChild("shell", CubeListBuilder.create().texOffs(0, 64).addBox(-5.0F, 0.0F, 0.0F, 10.0F, 10.0F, 5.0F, new CubeDeformation(0.5F)),
				PartPose.offset(0.0F, -0.5F, 1.8F));
			root.getChild("head").addOrReplaceChild("beak", CubeListBuilder.create().texOffs(32, 64).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 2.0F, 2.0F),
				PartPose.offsetAndRotation(0.0F, -3.5F, -4.4F, 0.2F, 0.0F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.shell.zRot = Mth.sin(state.walkAnimationPos * 0.6662F) * 0.06F * state.walkAnimationSpeed;
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
	 * Velox: slight, a swept-back crest, a visor that drops over the eyes at speed, a short snout, ball-like feet and a
	 * long tail that swings as it runs.
	 */
	static final class Velox extends FormModel {
		private final ModelPart tail, tailTip, visor;

		Velox() {
			super(bake(mesh()), "blur");
			this.insigniaZ = -1.8F;
			this.tail = this.body.getChild("tail");
			this.tailTip = this.tail.getChild("tip");
			this.visor = this.head.getChild("visor");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(-0.2F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			// the crest rises off the brow and sweeps back along the skull
			head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(0, 64).addBox(-1.0F, -3.0F, -3.0F, 2.0F, 3.0F, 5.0F), PartPose.offset(0.0F, -8.0F, 0.0F));
			head.addOrReplaceChild("crest_sweep", CubeListBuilder.create().texOffs(14, 64).addBox(-1.0F, -1.5F, 0.0F, 2.0F, 3.0F, 8.0F),
				PartPose.offsetAndRotation(0.0F, -9.0F, 1.0F, -0.25F, 0.0F, 0.0F));
			head.addOrReplaceChild("visor", CubeListBuilder.create().texOffs(34, 64).addBox(-4.5F, 0.0F, -0.5F, 9.0F, 3.0F, 1.0F), PartPose.offset(0.0F, -9.0F, -4.5F));
			head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(34, 68).addBox(-2.0F, -2.0F, -7.0F, 4.0F, 2.0F, 3.0F), PartPose.ZERO);
			// ball feet at the bottom of each leg
			root.getChild("right_leg").addOrReplaceChild("foot", CubeListBuilder.create().texOffs(0, 76).addBox(-2.0F, 9.0F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.6F)),
				PartPose.ZERO);
			root.getChild("left_leg").addOrReplaceChild("foot", CubeListBuilder.create().texOffs(16, 76).addBox(-2.0F, 9.0F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.6F)),
				PartPose.ZERO);
			PartDefinition body = root.getChild("body");
			PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 84).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 10.0F),
				PartPose.offsetAndRotation(0.0F, 10.5F, 1.5F, 0.5F, 0.0F, 0.0F));
			tail.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(26, 84).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 10.0F),
				PartPose.offsetAndRotation(0.0F, 0.0F, 9.5F, 0.3F, 0.0F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float run = state.walkAnimationSpeed;
			XenoMotion.Read m = XenoMotion.read(state);
			// the visor slides down over the eyes as it picks up speed and rides up on the brow when it slows
			float down = Mth.clamp((run - 0.35F) / 0.4F, 0.0F, 1.0F);
			down = down * down * (3.0F - 2.0F * down);
			this.visor.y = Mth.lerp(down, -9.0F, -5.9F);
			this.visor.xRot = Mth.lerp(down, -0.45F, 0.0F);
			float sway = Mth.cos(state.walkAnimationPos * 0.6662F) * 0.4F * run + Mth.sin(state.ageInTicks * 0.1F) * 0.08F;
			float lift = tailLift(m);
			this.tail.yRot = sway - m.trail() * 0.6F;
			this.tail.xRot = 0.5F + run * 0.5F + lift;
			this.tailTip.yRot = sway * 1.3F - (m.trail() - m.turn()) * 0.9F;
			this.tailTip.xRot = 0.3F - run * 0.2F + lift * 0.6F;
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
