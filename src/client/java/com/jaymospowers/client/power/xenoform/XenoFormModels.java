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

	/** (0.18.109, Scan C) Howl: a wolf's muzzle and tall ears, a bushy tail that swings as it runs. */
	static final class Howl extends FormModel {
		private final ModelPart tail;

		Howl() {
			super(bake(mesh()), "howl");
			this.insigniaZ = -2.1F;
			this.tail = this.body.getChild("tail");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.1F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(0, 64).addBox(-1.5F, -3.0F, -4.0F, 3.0F, 3.0F, 4.0F),
				PartPose.offset(0.0F, -0.5F, -4.0F));
			head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(16, 64).addBox(-1.0F, -3.0F, -0.5F, 2.0F, 3.0F, 1.0F),
				PartPose.offsetAndRotation(-2.5F, -8.0F, -1.0F, 0.0F, 0.0F, -0.15F));
			head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(16, 64).mirror().addBox(-1.0F, -3.0F, -0.5F, 2.0F, 3.0F, 1.0F),
				PartPose.offsetAndRotation(2.5F, -8.0F, -1.0F, 0.0F, 0.0F, 0.15F));
			root.getChild("body").addOrReplaceChild("tail", CubeListBuilder.create().texOffs(24, 64).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 8.0F),
				PartPose.offsetAndRotation(0.0F, 10.5F, 2.0F, -0.9F, 0.0F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.tail.yRot = Mth.sin(state.walkAnimationPos * 0.6662F) * 0.4F * state.walkAnimationSpeed;
			this.tail.xRot = -0.9F + Math.min(0.5F, state.walkAnimationSpeed * 0.5F);
		}
	}

	/** (0.18.109, Scan C) Prowl: round ears and a long tail that curls up at the tip and twitches. */
	static final class Prowl extends FormModel {
		private final ModelPart tail;

		Prowl() {
			super(bake(mesh()), "prowl");
			this.insigniaZ = -2.0F;
			this.tail = this.body.getChild("tail");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(0, 64).addBox(-1.0F, -2.0F, -0.5F, 2.0F, 2.0F, 1.0F),
				PartPose.offset(-2.8F, -8.0F, -0.5F));
			head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(0, 64).mirror().addBox(-1.0F, -2.0F, -0.5F, 2.0F, 2.0F, 1.0F),
				PartPose.offset(2.8F, -8.0F, -0.5F));
			root.getChild("body").addOrReplaceChild("tail", CubeListBuilder.create().texOffs(8, 64).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 10.0F),
				PartPose.offsetAndRotation(0.0F, 10.5F, 2.0F, -0.5F, 0.0F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.tail.yRot = Mth.sin(state.ageInTicks * 0.07F) * 0.25F;
			this.tail.xRot = -0.5F + Mth.sin(state.ageInTicks * 0.13F) * 0.08F;
		}
	}

	/** (0.18.109, Scan C) Tusk: a flat snout with two curved tusks, and a stiff mane down its back. */
	static final class Tusk extends FormModel {
		Tusk() {
			super(bake(mesh()), "tusk");
			this.insigniaZ = -2.6F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.6F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(0, 64).addBox(-2.0F, -3.0F, -2.0F, 4.0F, 3.0F, 2.0F),
				PartPose.offset(0.0F, -0.5F, -4.6F));
			head.addOrReplaceChild("right_tusk", CubeListBuilder.create().texOffs(14, 64).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F),
				PartPose.offsetAndRotation(-2.6F, -1.5F, -5.2F, -0.3F, 0.0F, -0.3F));
			head.addOrReplaceChild("left_tusk", CubeListBuilder.create().texOffs(14, 64).mirror().addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F),
				PartPose.offsetAndRotation(2.6F, -1.5F, -5.2F, -0.3F, 0.0F, 0.3F));
			root.getChild("body").addOrReplaceChild("mane", CubeListBuilder.create().texOffs(20, 64).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 12.0F, 3.0F),
				PartPose.offsetAndRotation(0.0F, -1.0F, 2.2F, 0.08F, 0.0F, 0.0F));
			return mesh;
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
	 * (0.18.113, Scan E; 0.19.34 a spider) Skitter: no longer a man with extra legs but a spider, low to the ground: a
	 * small thorax, a head with fangs that turns where it looks, a big round abdomen behind (the insignia on top), and
	 * eight long legs. They step in a spider's alternating fours, each pair a quarter out of step with the next, lifting
	 * as they reach; the body bobs and the abdomen sways as it scuttles, it crouches flat when sneaking, and a bite lunges
	 * the head and rears the front legs. The humanoid parts stay (the first-person view and the poses use them) but are
	 * hidden, and the body is laid on the abdomen so the insignia sits on its back.
	 */
	static final class Skitter extends FormModel {
		// ---- tuning ----
		/** The body's height off the ground (model y), how far it drops when sneaking, how much it bobs a step. */
		static final float BODY_Y = 15.0F, CROUCH_DROP = 3.0F, BOB = 0.8F;
		/** How fast the legs step against the walk, how far they swing and lift. */
		static final float GAIT = 0.6662F * 1.4F, SWING = 0.4F, LIFT = 0.45F;
		/** Rest: each leg pair's yaw (hind to front, the right side's; the left mirrors) and droop. */
		private static final float[] REST_YAW = {0.785F, 0.39F, -0.39F, -0.785F};
		private static final float[] REST_DROOP = {0.785F, 0.58F, 0.58F, 0.785F};
		/** Each pair's step phase (hind to front): the spider's alternating fours. */
		private static final float[] PHASE = {0.0F, (float)Math.PI, (float)Math.PI * 0.5F, (float)Math.PI * 1.5F};

		private final ModelPart thorax, spiderHead, abdomen;
		/** Hind to front: 0-3 right, 4-7 left. */
		private final ModelPart[] legs = new ModelPart[8];

		Skitter() {
			super(bake(mesh()), "skitter");
			ModelPart root = this.root();
			this.thorax = root.getChild("thorax");
			this.spiderHead = root.getChild("spider_head");
			this.abdomen = root.getChild("abdomen");
			for (int i = 0; i < 8; i++) {
				this.legs[i] = root.getChild("leg" + i);
			}
			// the insignia on top of the abdomen (the body is laid face-up there, see setupAnim)
			this.insigniaY = 0.0F;
			this.insigniaZ = -4.0F;
			this.insigniaSize = 1.3F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			root.addOrReplaceChild("thorax", CubeListBuilder.create().texOffs(0, 64).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F),
				PartPose.offset(0.0F, BODY_Y, 0.0F));
			PartDefinition head = root.addOrReplaceChild("spider_head", CubeListBuilder.create().texOffs(24, 64).addBox(-4.0F, -4.0F, -8.0F, 8.0F, 8.0F, 8.0F),
				PartPose.offset(0.0F, BODY_Y, -3.0F));
			head.addOrReplaceChild("right_fang", CubeListBuilder.create().texOffs(44, 80).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F),
				PartPose.offsetAndRotation(-1.5F, 3.5F, -7.0F, -0.3F, 0.0F, 0.15F));
			head.addOrReplaceChild("left_fang", CubeListBuilder.create().texOffs(44, 80).mirror().addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F),
				PartPose.offsetAndRotation(1.5F, 3.5F, -7.0F, -0.3F, 0.0F, -0.15F));
			root.addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(0, 80).addBox(-5.0F, -4.0F, -6.0F, 10.0F, 8.0F, 12.0F),
				PartPose.offset(0.0F, BODY_Y - 1.0F, 9.0F));
			// hind to front along the thorax, right side then left
			for (int i = 0; i < 8; i++) {
				boolean left = i >= 4;
				float z = 2.0F - (i % 4);
				CubeListBuilder leg = left
					? CubeListBuilder.create().texOffs(0, 104).mirror().addBox(-1.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F)
					: CubeListBuilder.create().texOffs(0, 100).addBox(-15.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F);
				root.addOrReplaceChild("leg" + i, leg, PartPose.offset(left ? 4.0F : -4.0F, BODY_Y, z));
			}
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			// the man's shape is hidden; the body (unseen) lies face-up on the abdomen, carrying the insignia there
			this.head.visible = this.hat.visible = this.body.visible = false;
			this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
			float walk = state.walkAnimationPos * GAIT;
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			float drop = state.isCrouching ? CROUCH_DROP : 0.0F;
			float bob = Mth.abs(Mth.sin(walk)) * BOB * speed;
			float y = BODY_Y + drop - bob;
			// a bite: the head lunges and the fangs open, the front legs rear
			float bite = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
			this.thorax.y = y;
			this.spiderHead.y = y;
			this.spiderHead.z = -3.0F - bite * 2.0F;
			this.spiderHead.yRot = state.yRot * Mth.DEG_TO_RAD;
			this.spiderHead.xRot = state.xRot * Mth.DEG_TO_RAD - bite * 0.3F;
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
					// the front pair rears on a bite
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
