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
			MODELS.put("blur", new Blur());
			MODELS.put("prism", new Prism());
			MODELS.put("wisp", new Wisp());
			MODELS.put("gremlin", new Gremlin());
			MODELS.put("knell", new Knell());
			MODELS.put("wyrm", new Wyrm());
			MODELS.put("nadir", new Nadir());
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

	/** Pyre: a lean body with plumes of flame on its head and shoulders that sway. */
	static final class Pyre extends FormModel {
		private final ModelPart crest, rightPlume, leftPlume;

		Pyre() {
			super(bake(mesh()), "pyre");
			this.crest = this.head.getChild("crest");
			this.rightPlume = this.body.getChild("right_plume");
			this.leftPlume = this.body.getChild("left_plume");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.3F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(0, 64).addBox(-1.5F, -15.0F, -2.0F, 3.0F, 7.0F, 4.0F)
				.texOffs(14, 64).addBox(-4.0F, -11.0F, 0.0F, 8.0F, 3.0F, 2.0F), PartPose.ZERO);
			PartDefinition body = root.getChild("body");
			body.addOrReplaceChild("right_plume", CubeListBuilder.create().texOffs(0, 76).addBox(-2.0F, -5.0F, -1.5F, 3.0F, 6.0F, 3.0F),
				PartPose.offset(-6.0F, 0.0F, 0.0F));
			body.addOrReplaceChild("left_plume", CubeListBuilder.create().texOffs(0, 76).mirror().addBox(-1.0F, -5.0F, -1.5F, 3.0F, 6.0F, 3.0F),
				PartPose.offset(6.0F, 0.0F, 0.0F));
			body.addOrReplaceChild("core", CubeListBuilder.create().texOffs(12, 76).addBox(-2.5F, 2.0F, -3.0F, 5.0F, 5.0F, 1.0F), PartPose.ZERO);
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
			this.crest.zRot = Mth.sin(flicker * 0.35F) * 0.12F - m.trail() * 0.25F;
			this.crest.xRot = Mth.cos(flicker * 0.27F) * 0.08F - 0.15F - stream;
			this.rightPlume.zRot = 0.3F + Mth.sin(flicker * 0.4F) * 0.15F - m.trail() * 0.2F;
			this.leftPlume.zRot = -0.3F - Mth.sin(flicker * 0.4F + 1.0F) * 0.15F - m.trail() * 0.2F;
			this.rightPlume.xRot = -stream;
			this.leftPlume.xRot = -stream;
		}
	}

	/** Blur: slight, a fin along the skull, pricked ears, and a long tail that swings as it runs. */
	static final class Blur extends FormModel {
		private final ModelPart tail, tailTip;

		Blur() {
			super(bake(mesh()), "blur");
			this.tail = this.body.getChild("tail");
			this.tailTip = this.tail.getChild("tip");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(-0.15F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			head.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(0, 64).addBox(-1.0F, -12.0F, -4.0F, 2.0F, 4.0F, 9.0F), PartPose.ZERO);
			head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(22, 64).addBox(-2.0F, -4.0F, -7.0F, 4.0F, 3.0F, 3.0F), PartPose.ZERO);
			head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(36, 64).addBox(-0.5F, -3.0F, -1.0F, 1.0F, 3.0F, 2.0F),
				PartPose.offsetAndRotation(-3.0F, -8.0F, 1.0F, 0.0F, 0.0F, -0.35F));
			head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(36, 64).mirror().addBox(-0.5F, -3.0F, -1.0F, 1.0F, 3.0F, 2.0F),
				PartPose.offsetAndRotation(3.0F, -8.0F, 1.0F, 0.0F, 0.0F, 0.35F));
			PartDefinition body = root.getChild("body");
			PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 78).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 8.0F),
				PartPose.offsetAndRotation(0.0F, 10.5F, 1.5F, 0.5F, 0.0F, 0.0F));
			tail.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(22, 78).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 8.0F),
				PartPose.offsetAndRotation(0.0F, 0.0F, 7.5F, 0.3F, 0.0F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float run = state.walkAnimationSpeed;
			XenoMotion.Read m = XenoMotion.read(state);
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
		private final ModelPart rightWing, leftWing, rightOuter, leftOuter, tail, tailMid, tailTip;

		Wyrm() {
			super(bake(mesh()), "wyrm");
			this.rightWing = this.body.getChild("right_wing");
			this.leftWing = this.body.getChild("left_wing");
			this.rightOuter = this.rightWing.getChild("outer");
			this.leftOuter = this.leftWing.getChild("outer");
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
			PartDefinition right = body.addOrReplaceChild("right_wing",
				CubeListBuilder.create().texOffs(0, 90).addBox(-WING_HALF, -0.5F, 0.0F, WING_HALF, 1.0F, WING_DEPTH), PartPose.offset(-2.0F, 2.0F, 2.5F));
			right.addOrReplaceChild("outer", CubeListBuilder.create().texOffs(0, 90).addBox(-WING_HALF, -0.5F, 0.0F, WING_HALF, 1.0F, WING_DEPTH),
				PartPose.offset(-WING_HALF + 0.5F, 0.0F, 0.0F));
			PartDefinition left = body.addOrReplaceChild("left_wing",
				CubeListBuilder.create().texOffs(0, 90).mirror().addBox(0.0F, -0.5F, 0.0F, WING_HALF, 1.0F, WING_DEPTH), PartPose.offset(2.0F, 2.0F, 2.5F));
			left.addOrReplaceChild("outer", CubeListBuilder.create().texOffs(0, 90).mirror().addBox(0.0F, -0.5F, 0.0F, WING_HALF, 1.0F, WING_DEPTH),
				PartPose.offset(WING_HALF - 0.5F, 0.0F, 0.0F));
			return mesh;
		}

		/** Each wing half's length and depth (was one 14x8 sheet a side). */
		static final float WING_HALF = 10.0F, WING_DEPTH = 14.0F;
		/** In flight: the beat's speed and depth; folded, the wings sweep back to this yaw and lift this little. */
		static final float FLAP_RATE = 0.55F, FLAP_DEPTH = 0.95F, FOLDED_YAW = 1.35F, FOLDED_LIFT = 0.15F;
		/** In flight, how far the outer halves bend behind the beat. */
		static final float OUTER_FLEX = 0.45F;
		/** Falling without flying, how far the wings flare open; how hard a landing slams them down. */
		static final float FALL_FLARE = 0.8F, LAND_SLAM = 0.6F;
		/**
		 * Wrapped on the ground: the inner halves hang down (pitch) and swing forward round the sides past the arms
		 * (yaw), the outer halves fold in across the chest; how fast the wrap closes and opens (per tick of air).
		 */
		static final float WRAP_PITCH = -Mth.HALF_PI, WRAP_YAW = -0.42F, WRAP_FOLD = -2.25F;
		/** The tail's resting pitch at each joint (+ is up): down from the back, levelling out near the ground. */
		static final float[] TAIL_REST = {-0.35F, -0.12F, 0.32F};

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float t = state.ageInTicks;
			XenoformFlightPose.Flight flight = state.getData(XenoformFlightPose.KEY);
			float fly = flight == null ? 0.0F : XenoformFlightPose.smooth(flight.wings());
			float fold = flight == null ? 0.0F : XenoformFlightPose.smooth(flight.fold());
			XenoMotion.Read m = XenoMotion.read(state);
			float walk = state.walkAnimationSpeed;
			// on the ground and not flying, the wings wrap round it; in the air they open
			float wrap = XenoformFlightPose.smooth(Mth.clamp((1.0F - m.air()) * (1.0F - fly), 0.0F, 1.0F));

			// open (in the air): a slow stir at rest, deep strokes flying that die away as the wings fold back at speed
			float idle = m.breath() * 0.08F + Mth.sin(t * 0.25F) * 0.05F;
			float flap = Mth.sin(t * FLAP_RATE) * FLAP_DEPTH * (1.0F - fold);
			float beat = Mth.lerp(fly, idle, flap);
			float lift = fold * FOLDED_LIFT * fly;
			// dropping without flying, they flare open to catch the air; landing slams them down
			float flare = Math.max(0.0F, m.fall()) * m.air() * (1.0F - fly) * FALL_FLARE;
			float slam = m.land() * LAND_SLAM;
			// both tip together out of a turn, the outer wing up
			float bank = m.turn() * 0.3F;
			float openZ = -0.3F + beat + lift - flare + slam + bank;
			float openY = Mth.lerp(fold * fly, 0.6F + Mth.cos(t * 0.25F) * 0.1F - flare * 0.5F, FOLDED_YAW);
			float openOuter = -Mth.sin(t * FLAP_RATE - 0.9F) * OUTER_FLEX * fly * (1.0F - fold) + flare * 0.3F;

			// wrapped (on the ground): the cloak breathes, sways with each step and swings out behind a turn
			float step = Mth.sin(state.walkAnimationPos * 0.6662F) * walk;
			float wrapX = WRAP_PITCH + m.breath() * 0.03F + walk * 0.15F + m.land() * 0.25F;
			float wrapY = WRAP_YAW + m.breath() * 0.03F + Mth.abs(step) * 0.06F;
			float wrapFold = WRAP_FOLD + walk * 0.25F - m.breath() * 0.04F;

			this.rightWing.xRot = Mth.lerp(wrap, 0.0F, wrapX);
			this.leftWing.xRot = Mth.lerp(wrap, 0.0F, wrapX);
			this.rightWing.yRot = Mth.lerp(wrap, openY, wrapY + step * 0.08F - m.trail() * 0.2F);
			this.leftWing.yRot = Mth.lerp(wrap, -openY, -wrapY + step * 0.08F - m.trail() * 0.2F);
			this.rightWing.zRot = Mth.lerp(wrap, openZ, 0.0F);
			this.leftWing.zRot = Mth.lerp(wrap, -openZ + 2.0F * bank, 0.0F);
			// the outer halves: open, they flex behind the stroke (about the wing's length axis it's zRot); wrapped,
			// the same axis now stands upright, so zRot folds them forward across the chest
			this.rightOuter.zRot = Mth.lerp(wrap, openOuter, wrapFold);
			this.leftOuter.zRot = Mth.lerp(wrap, -openOuter, -wrapFold);

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
