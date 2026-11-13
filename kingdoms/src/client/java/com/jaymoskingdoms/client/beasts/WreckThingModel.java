package com.jaymoskingdoms.client.beasts;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The thing under the wreck ({@code beasts/WreckThing}), our own: a squat body with a beak on top and {@link #ARMS} arms
 * standing up from its rim, each of {@link #JOINTS} lengths riding the one under it so a curl runs up the whole arm.
 * At rest the arms stand up and sway outward; for its lash they open wide and clap shut over it; for its grab they
 * open, close and stay curled in over what it holds; behind its ink they trail straight up as it darts.
 * {@code kingdoms/tools/gen_wreck_thing.py} paints the hide over these boxes (keep both the same).
 */
final class WreckThingModel extends EntityModel<WreckThingRenderer.State> {
	static final int ARMS = 8, JOINTS = 5;
	/** The body's height, an arm length's, and how far out from the middle the arms stand (pixels). */
	static final float BODY = 20.0F, LENGTH = 12.0F, RIM = 9.0F;
	/** The server's timings (ticks). */
	static final float LASH_TELL = 14.0F, LASH_TICKS = 26.0F, GRAB_TELL = 14.0F;
	/** A joint's lean (radians; outward above 0): at rest, opened for a blow, shut over it, curled round what it holds. */
	static final float REST = 0.07F, OPEN = 0.3F, SHUT = -0.2F, CURL = -0.3F;

	private final ModelPart body;
	private final ModelPart[][] arms = new ModelPart[ARMS][JOINTS];

	WreckThingModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		for (int arm = 0; arm < ARMS; arm++) {
			ModelPart part = this.body;
			for (int joint = 0; joint < JOINTS; joint++) {
				part = part.getChild(joint == 0 ? "arm" + arm : "joint" + joint);
				this.arms[arm][joint] = part;
			}
		}
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-12.0F, -BODY, -12.0F, 24.0F, BODY, 24.0F,
			CubeDeformation.NONE), PartPose.offset(0.0F, 24.0F, 0.0F));
		body.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(0, 44).addBox(-3.0F, -4.0F, -3.0F, 6.0F, 4.0F, 6.0F, CubeDeformation.NONE),
			PartPose.offset(0.0F, -BODY, 0.0F));
		for (int arm = 0; arm < ARMS; arm++) {
			float a = arm * Mth.TWO_PI / ARMS;
			PartDefinition part = body;
			for (int joint = 0; joint < JOINTS; joint++) {
				// each length a little thinner than the one under it
				float thin = joint * 0.35F;
				CubeListBuilder cube = CubeListBuilder.create().texOffs(96, 0).addBox(-2.5F, -LENGTH, -2.5F, 5.0F, LENGTH, 5.0F, new CubeDeformation(-thin, 0.0F, -thin));
				// (a length standing up leans toward its own -Z as its xRot grows: turned so that is outward from the body)
				part = part.addOrReplaceChild(joint == 0 ? "arm" + arm : "joint" + joint, cube, joint == 0
					? PartPose.offsetAndRotation(Mth.sin(a) * RIM, -BODY + 2.0F, Mth.cos(a) * RIM, 0.0F, a + Mth.PI, 0.0F) : PartPose.offset(0.0F, -LENGTH, 0.0F));
			}
		}
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(WreckThingRenderer.State state) {
		super.setupAnim(state);
		String action = state.action;
		float t = state.actionAge, lean = REST, sway = 1.0F;
		if ("lash".equals(action)) {
			lean = t < LASH_TELL - 3.0F ? Mth.lerp(Mth.clamp(t / (LASH_TELL - 3.0F), 0.0F, 1.0F), REST, OPEN)
				: t < LASH_TELL ? Mth.lerp((t - (LASH_TELL - 3.0F)) / 3.0F, OPEN, SHUT)
				: Mth.lerp(Mth.clamp((t - LASH_TELL - 3.0F) / (LASH_TICKS - LASH_TELL - 3.0F), 0.0F, 1.0F), SHUT, REST);
			sway = 0.3F;
		} else if ("grab".equals(action)) {
			lean = t < GRAB_TELL - 3.0F ? Mth.lerp(Mth.clamp(t / (GRAB_TELL - 3.0F), 0.0F, 1.0F), REST, OPEN)
				: Mth.lerp(Mth.clamp((t - (GRAB_TELL - 3.0F)) / 4.0F, 0.0F, 1.0F), OPEN, CURL);
			sway = 0.4F;
		} else if ("ink".equals(action)) {
			lean = 0.0F;
			sway = 0.5F;
		}
		this.body.y = 24.0F + Mth.sin(state.ageInTicks * 0.05F) * 1.5F;
		this.body.yRot = "ink".equals(action) ? 0.0F : Mth.sin(state.ageInTicks * 0.02F) * 0.1F;
		for (int arm = 0; arm < ARMS; arm++) {
			for (int joint = 0; joint < JOINTS; joint++) {
				ModelPart part = this.arms[arm][joint];
				float wave = state.ageInTicks * 0.09F + arm * 1.7F - joint * 0.8F;
				// the foot of an arm bends least, so a shut arm closes over the body and not through it
				part.xRot = lean * (joint == 0 ? 0.5F : 1.0F) + Mth.sin(wave) * 0.09F * sway;
				part.zRot = Mth.cos(wave * 0.8F) * 0.07F * sway;
			}
		}
	}
}
