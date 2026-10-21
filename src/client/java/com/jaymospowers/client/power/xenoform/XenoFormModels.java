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

		FormModel(ModelPart root, String id) {
			super(root);
			this.texture = JaymosPowers.id("textures/entity/xenoform_" + id + ".png");
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

	/** Bulwark: thick-set, a heavy brow, pauldrons, and a second pair of arms under the first. */
	static final class Bulwark extends FormModel {
		private final ModelPart rightLowerArm, leftLowerArm;

		Bulwark() {
			super(bake(mesh()), "bulwark");
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

	/** Wisp: a hooded thing with no legs, trailing off into tatters that sway. */
	static final class Wisp extends FormModel {
		private final ModelPart tatters;

		Wisp() {
			super(bake(mesh()), "wisp");
			this.tatters = this.body.getChild("tatters");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			root.getChild("head").addOrReplaceChild("hood", CubeListBuilder.create().texOffs(0, 64).addBox(-5.0F, -9.0F, -5.0F, 10.0F, 10.0F, 10.0F), PartPose.ZERO);
			root.getChild("body").addOrReplaceChild("tatters", CubeListBuilder.create().texOffs(0, 86).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 7.0F, 4.0F),
				PartPose.offset(0.0F, 12.0F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.rightLeg.visible = false;
			this.leftLeg.visible = false;
			XenoMotion.Read m = XenoMotion.read(state);
			// the tatters drag behind it, billow up as it drops and swing out of a turn
			this.tatters.xRot = Mth.sin(state.ageInTicks * 0.12F) * 0.15F + state.walkAnimationSpeed * 0.3F + Math.abs(m.fall()) * 0.6F * m.air()
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

	/** Duplex: stocky and riveted, a copper belt, and a lightning rod with a ball tip on its head that sways as it walks. */
	static final class Duplex extends FormModel {
		private final ModelPart rod;

		Duplex() {
			super(bake(mesh()), "duplex");
			this.rod = this.head.getChild("rod");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.4F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition rod = root.getChild("head").addOrReplaceChild("rod", CubeListBuilder.create().texOffs(0, 64).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
				PartPose.offset(0.0F, -8.4F, 0.0F));
			rod.addOrReplaceChild("knob", CubeListBuilder.create().texOffs(8, 64).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offset(0.0F, -4.0F, 0.0F));
			root.getChild("body").addOrReplaceChild("belt", CubeListBuilder.create().texOffs(0, 72).addBox(-4.5F, 9.0F, -2.5F, 9.0F, 2.0F, 5.0F, new CubeDeformation(0.4F)),
				PartPose.ZERO);
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
