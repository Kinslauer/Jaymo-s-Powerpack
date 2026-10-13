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
 * ({@code textures/entity/xenoform_<id>.png}, drawn by {@code tools/gen_xenoform.py}). The base limbs use the vanilla
 * skin layout; the extras use the free rows below it. {@link #of} finds a form's model by its id.
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
		}
		return MODELS.get(form);
	}

	private static ModelPart bake(MeshDefinition mesh) {
		return LayerDefinition.create(mesh, 64, 64).bakeRoot();
	}

	/** Bulwark: thick-set, a heavy brow, and a second pair of arms under the first. */
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
			head.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(0, 32).addBox(-5.0F, -10.0F, -6.0F, 10.0F, 3.0F, 3.0F), PartPose.ZERO);
			PartDefinition body = root.getChild("body");
			body.addOrReplaceChild("plates", CubeListBuilder.create().texOffs(0, 38).addBox(-6.0F, -1.0F, -3.0F, 12.0F, 4.0F, 7.0F, new CubeDeformation(0.9F)),
				PartPose.ZERO);
			body.addOrReplaceChild("right_lower_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.6F)),
				PartPose.offset(-5.5F, 8.0F, 0.0F));
			body.addOrReplaceChild("left_lower_arm", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.6F)),
				PartPose.offset(5.5F, 8.0F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			// the lower arms swing the opposite way, half as far, and hang a little out
			this.rightLowerArm.xRot = -this.rightArm.xRot * 0.5F;
			this.leftLowerArm.xRot = -this.leftArm.xRot * 0.5F;
			this.rightLowerArm.zRot = 0.25F;
			this.leftLowerArm.zRot = -0.25F;
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
			head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(0, 32).addBox(-1.5F, -15.0F, -2.0F, 3.0F, 7.0F, 4.0F)
				.texOffs(14, 32).addBox(-4.0F, -11.0F, 0.0F, 8.0F, 3.0F, 2.0F), PartPose.ZERO);
			PartDefinition body = root.getChild("body");
			body.addOrReplaceChild("right_plume", CubeListBuilder.create().texOffs(0, 44).addBox(-2.0F, -5.0F, -1.5F, 3.0F, 6.0F, 3.0F),
				PartPose.offset(-6.0F, 0.0F, 0.0F));
			body.addOrReplaceChild("left_plume", CubeListBuilder.create().texOffs(0, 44).mirror().addBox(-1.0F, -5.0F, -1.5F, 3.0F, 6.0F, 3.0F),
				PartPose.offset(6.0F, 0.0F, 0.0F));
			body.addOrReplaceChild("core", CubeListBuilder.create().texOffs(12, 44).addBox(-2.5F, 2.0F, -3.0F, 5.0F, 5.0F, 1.0F), PartPose.ZERO);
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float t = state.ageInTicks;
			this.crest.zRot = Mth.sin(t * 0.35F) * 0.12F;
			this.crest.xRot = Mth.cos(t * 0.27F) * 0.08F - 0.15F;
			this.rightPlume.zRot = 0.3F + Mth.sin(t * 0.4F) * 0.15F;
			this.leftPlume.zRot = -0.3F - Mth.sin(t * 0.4F + 1.0F) * 0.15F;
		}
	}

	/** Blur: slight, a fin along the skull, and a long tail that swings as it runs. */
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
			head.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(0, 32).addBox(-1.0F, -12.0F, -4.0F, 2.0F, 4.0F, 9.0F), PartPose.ZERO);
			head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(22, 32).addBox(-2.0F, -4.0F, -7.0F, 4.0F, 3.0F, 3.0F), PartPose.ZERO);
			PartDefinition body = root.getChild("body");
			PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 45).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 8.0F),
				PartPose.offsetAndRotation(0.0F, 10.5F, 1.5F, 0.5F, 0.0F, 0.0F));
			tail.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(22, 45).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 8.0F),
				PartPose.offsetAndRotation(0.0F, 0.0F, 7.5F, 0.3F, 0.0F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float run = state.walkAnimationSpeed;
			float sway = Mth.cos(state.walkAnimationPos * 0.6662F) * 0.4F * run + Mth.sin(state.ageInTicks * 0.1F) * 0.08F;
			this.tail.yRot = sway;
			this.tail.xRot = 0.5F + run * 0.5F;
			this.tailTip.yRot = sway * 1.3F;
			this.tailTip.xRot = 0.3F - run * 0.2F;
		}
	}

	/** Prism: angular, a crown of crystal and crystals jutting from its back. */
	static final class Prism extends FormModel {
		Prism() {
			super(bake(mesh()), "prism");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.4F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			head.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(0, 32).addBox(-1.0F, -14.0F, -1.0F, 2.0F, 6.0F, 2.0F), PartPose.ZERO);
			head.addOrReplaceChild("right_crystal", CubeListBuilder.create().texOffs(8, 32).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F),
				PartPose.offsetAndRotation(-3.5F, -8.0F, 0.0F, 0.0F, 0.0F, -0.5F));
			head.addOrReplaceChild("left_crystal", CubeListBuilder.create().texOffs(8, 32).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F),
				PartPose.offsetAndRotation(3.5F, -8.0F, 0.0F, 0.0F, 0.0F, 0.5F));
			PartDefinition body = root.getChild("body");
			body.addOrReplaceChild("back", CubeListBuilder.create().texOffs(16, 32).addBox(-3.0F, 1.0F, 2.0F, 6.0F, 6.0F, 3.0F), PartPose.ZERO);
			body.addOrReplaceChild("spine", CubeListBuilder.create().texOffs(34, 32).addBox(-1.0F, -2.0F, 3.0F, 2.0F, 5.0F, 2.0F),
				PartPose.offsetAndRotation(0.0F, 2.0F, 2.0F, -0.4F, 0.0F, 0.0F));
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
			root.getChild("head").addOrReplaceChild("hood", CubeListBuilder.create().texOffs(24, 32).addBox(-5.0F, -9.0F, -5.0F, 10.0F, 10.0F, 10.0F), PartPose.ZERO);
			root.getChild("body").addOrReplaceChild("tatters", CubeListBuilder.create().texOffs(0, 32).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 7.0F, 4.0F),
				PartPose.offset(0.0F, 12.0F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.rightLeg.visible = false;
			this.leftLeg.visible = false;
			this.tatters.xRot = Mth.sin(state.ageInTicks * 0.12F) * 0.15F + state.walkAnimationSpeed * 0.3F;
			this.tatters.zRot = Mth.cos(state.ageInTicks * 0.09F) * 0.1F;
		}
	}

	/** Gremlin: a big-headed imp with long ears that twitch. */
	static final class Gremlin extends FormModel {
		private final ModelPart rightEar, leftEar;

		Gremlin() {
			super(bake(mesh()), "gremlin");
			this.rightEar = this.head.getChild("right_ear");
			this.leftEar = this.head.getChild("left_ear");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition head = mesh.getRoot().getChild("head");
			head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 32).addBox(-5.0F, -10.0F, -5.0F, 10.0F, 10.0F, 10.0F), PartPose.ZERO);
			head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(40, 32).addBox(-4.0F, -5.0F, -0.5F, 4.0F, 5.0F, 1.0F),
				PartPose.offsetAndRotation(-5.0F, -4.0F, 0.0F, 0.0F, 0.0F, 0.6F));
			head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(40, 32).mirror().addBox(0.0F, -5.0F, -0.5F, 4.0F, 5.0F, 1.0F),
				PartPose.offsetAndRotation(5.0F, -4.0F, 0.0F, 0.0F, 0.0F, -0.6F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float twitch = Mth.sin(state.ageInTicks * 0.3F) * 0.1F + state.walkAnimationSpeed * 0.2F;
			this.rightEar.zRot = 0.6F + twitch;
			this.leftEar.zRot = -0.6F - twitch;
		}
	}

	/** Knell: hulking, horned, with a glowing ribcage and heavy shoulders. */
	static final class Knell extends FormModel {
		Knell() {
			super(bake(mesh()), "knell");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(1.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			head.addOrReplaceChild("right_horn", CubeListBuilder.create().texOffs(0, 32).addBox(-1.0F, -7.0F, -1.0F, 2.0F, 7.0F, 2.0F),
				PartPose.offsetAndRotation(-4.0F, -8.0F, 0.0F, 0.0F, 0.0F, -0.45F));
			head.addOrReplaceChild("left_horn", CubeListBuilder.create().texOffs(0, 32).addBox(-1.0F, -7.0F, -1.0F, 2.0F, 7.0F, 2.0F),
				PartPose.offsetAndRotation(4.0F, -8.0F, 0.0F, 0.0F, 0.0F, 0.45F));
			PartDefinition body = root.getChild("body");
			body.addOrReplaceChild("ribs", CubeListBuilder.create().texOffs(8, 32).addBox(-5.0F, 1.0F, -4.0F, 10.0F, 8.0F, 2.0F), PartPose.ZERO);
			body.addOrReplaceChild("shoulders", CubeListBuilder.create().texOffs(0, 42).addBox(-6.0F, -3.0F, -2.5F, 12.0F, 3.0F, 5.0F, new CubeDeformation(1.0F)),
				PartPose.ZERO);
			return mesh;
		}
	}

	/** Wyrm: horned, winged and tailed; the wings beat as it moves. */
	static final class Wyrm extends FormModel {
		private final ModelPart rightWing, leftWing, tail, tailTip;

		Wyrm() {
			super(bake(mesh()), "wyrm");
			this.rightWing = this.body.getChild("right_wing");
			this.leftWing = this.body.getChild("left_wing");
			this.tail = this.body.getChild("tail");
			this.tailTip = this.tail.getChild("tip");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.5F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			head.addOrReplaceChild("right_horn", CubeListBuilder.create().texOffs(0, 32).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 7.0F),
				PartPose.offsetAndRotation(-3.0F, -7.5F, 2.0F, -0.6F, 0.0F, 0.0F));
			head.addOrReplaceChild("left_horn", CubeListBuilder.create().texOffs(0, 32).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 7.0F),
				PartPose.offsetAndRotation(3.0F, -7.5F, 2.0F, -0.6F, 0.0F, 0.0F));
			PartDefinition body = root.getChild("body");
			PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(18, 32).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 10.0F),
				PartPose.offsetAndRotation(0.0F, 11.0F, 2.0F, 0.5F, 0.0F, 0.0F));
			tail.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(18, 45).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 8.0F),
				PartPose.offsetAndRotation(0.0F, 0.0F, 9.5F, 0.3F, 0.0F, 0.0F));
			body.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(0, 50).addBox(-14.0F, -0.5F, 0.0F, 14.0F, 1.0F, 8.0F),
				PartPose.offsetAndRotation(-2.0F, 2.0F, 2.5F, 0.0F, 0.6F, 0.0F));
			body.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(0, 50).mirror().addBox(0.0F, -0.5F, 0.0F, 14.0F, 1.0F, 8.0F),
				PartPose.offsetAndRotation(2.0F, 2.0F, 2.5F, 0.0F, -0.6F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float t = state.ageInTicks;
			float beat = Mth.sin(t * 0.25F) * (0.15F + state.walkAnimationSpeed * 0.6F);
			this.rightWing.zRot = -0.3F + beat;
			this.leftWing.zRot = 0.3F - beat;
			this.rightWing.yRot = 0.6F + Mth.cos(t * 0.25F) * 0.1F;
			this.leftWing.yRot = -0.6F - Mth.cos(t * 0.25F) * 0.1F;
			float sway = Mth.cos(state.walkAnimationPos * 0.6662F) * 0.3F * state.walkAnimationSpeed + Mth.sin(t * 0.08F) * 0.1F;
			this.tail.yRot = sway;
			this.tailTip.yRot = sway * 1.4F;
		}
	}
}
