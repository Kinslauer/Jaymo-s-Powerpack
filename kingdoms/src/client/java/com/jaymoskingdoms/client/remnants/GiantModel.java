package com.jaymoskingdoms.client.remnants;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * (0.7.97) An Aether Giant (our own). (0.7.182) Andy's titan build (drawn with Gemini): a towering slab of a torso
 * with heavy back plating, a square skull under a brow crest and a hinged jaw, long arms on wide shoulders that flare
 * out as it marches (they never clip the torso), and columnar legs. It breathes, flinches when hit (jaw dropping) and
 * sprawls when it dies. A blow brings one arm up and down; a stamp lifts both arms and drops its weight onto them.
 * Texture 256x128 ({@code kingdoms/tools/gen_giants.py} paints its UV layout).
 */
final class GiantModel extends EntityModel<GiantState> {
	/** How far an arm lifts for a blow, how long a blow takes (ticks), and how far the body pitches into a stamp. */
	private static final float BLOW_LIFT = 2.4F, BLOW_TICKS = 12.0F, STAMP_PITCH = 0.35F;
	/** Its stride, how far its legs and arms swing, and how far the arms flare out from the torso. */
	private static final float STRIDE = 0.35F, LEG_SWING = 1.0F, ARM_SWING = 0.85F, ARM_FLARE = 0.32F;
	/** How far it leans forward, and how it flinches when hit (body, head, jaw, arms). */
	private static final float LEAN = 0.05F, FLINCH_BODY = 0.42F, FLINCH_HEAD = 0.6F, FLINCH_JAW = 0.5F, FLINCH_ARM = 0.6F;
	/** How long (ticks) it takes to sprawl when it dies. */
	private static final float SPRAWL_TICKS = 20.0F;

	private final ModelPart body, head, jaw, rightArm, leftArm, rightLeg, leftLeg;

	GiantModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.head = this.body.getChild("head");
		this.jaw = this.head.getChild("jaw");
		this.rightArm = this.body.getChild("right_arm");
		this.leftArm = this.body.getChild("left_arm");
		this.rightLeg = root.getChild("right_leg");
		this.leftLeg = root.getChild("left_leg");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// the torso (28x30x16) and the plating on its back (26x24x4)
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-14, -28, -8, 28, 30, 16)
			.texOffs(88, 28).addBox(-13, -26, 8, 26, 24, 4), PartPose.ZERO);
		// the skull (14x14x14) under its brow crest (16x4x6), and the jaw (12x6x10) hinged inside it
		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
			.texOffs(88, 0).addBox(-7, -14, -7, 14, 14, 14)
			.texOffs(144, 0).addBox(-8, -16, -8, 16, 4, 6), PartPose.offset(0, -28, -2));
		head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(144, 10).addBox(-6, 0, -6, 12, 6, 10), PartPose.offset(0, -4, 0));
		// the arms (10x32x10), on shoulders set wide of the torso
		body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0, 46).addBox(-5.5F, -3, -5, 10, 32, 10),
			PartPose.offset(-22, -24, 0));
		body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(0, 46).mirror().addBox(-4.5F, -3, -5, 10, 32, 10),
			PartPose.offset(22, -24, 0));
		// columnar legs (10x22x10)
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(40, 46).addBox(-5, 0, -5, 10, 22, 10), PartPose.offset(-8, 2, 0));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(40, 46).mirror().addBox(-5, 0, -5, 10, 22, 10),
			PartPose.offset(8, 2, 0));
		return LayerDefinition.create(mesh, 256, 128);
	}

	@Override
	public void setupAnim(GiantState state) {
		super.setupAnim(state);
		this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
		this.head.yRot = Mth.clamp(state.yRot, -50.0F, 50.0F) * Mth.DEG_TO_RAD;
		if (state.deathTime > 0.0F) {
			sprawl(Math.min(state.deathTime / SPRAWL_TICKS, 1.0F));
			return;
		}
		float walk = Mth.cos(state.walkAnimationPos * STRIDE), amount = Math.min(state.walkAnimationSpeed, 1.0F);
		this.rightLeg.xRot = walk * LEG_SWING * amount;
		this.leftLeg.xRot = -walk * LEG_SWING * amount;
		// arms swing against the legs and flare outward, more at the height of each swing
		this.rightArm.xRot = (-walk * ARM_SWING - 0.2F) * amount;
		this.rightArm.yRot = walk * 0.22F * amount;
		this.rightArm.zRot = ARM_FLARE + Math.abs(walk) * 0.22F * amount;
		this.leftArm.xRot = walk * ARM_SWING * amount;
		this.leftArm.yRot = -walk * 0.22F * amount;
		this.leftArm.zRot = -ARM_FLARE - Math.abs(walk) * 0.22F * amount;
		// a slow breath
		this.body.xRot = LEAN + Mth.sin(state.ageInTicks * 0.09F) * 0.025F;
		this.jaw.xRot = 0.0F;
		if (state.aggressive && state.leftAttack <= 0.0F && state.rightAttack <= 0.0F) {
			// arms half raised when it's after someone
			this.rightArm.xRot -= 0.35F;
			this.leftArm.xRot -= 0.35F;
		}
		if (state.rightAttack > 0.0F) {
			this.rightArm.xRot = -blow(state.rightAttack);
		}
		if (state.leftAttack > 0.0F) {
			this.leftArm.xRot = -blow(state.leftAttack);
		}
		if (state.stamp) {
			// both arms up, then its whole weight thrown down onto them
			float t = Math.min(Math.max(state.leftAttack, state.rightAttack) / BLOW_TICKS, 1.0F);
			this.body.xRot += STAMP_PITCH * Mth.sin(t * Mth.PI);
		}
		if (state.flinch > 0.0F) {
			// rocked back by a hit, jaw dropping
			this.body.xRot -= state.flinch * FLINCH_BODY;
			this.head.xRot -= state.flinch * FLINCH_HEAD;
			this.jaw.xRot = state.flinch * FLINCH_JAW;
			this.rightArm.xRot -= state.flinch * FLINCH_ARM;
			this.rightArm.zRot += state.flinch * 0.5F;
			this.leftArm.xRot -= state.flinch * FLINCH_ARM;
			this.leftArm.zRot -= state.flinch * 0.5F;
		}
	}

	/** Its death: it folds forward over its legs, head lolling and arms thrown wide ({@code t} 0 to 1). */
	private void sprawl(float t) {
		this.body.xRot = 1.48F * t;
		this.body.zRot = 0.45F * t;
		this.head.xRot = Mth.lerp(t, this.head.xRot, 0.45F);
		this.head.yRot = Mth.lerp(t, this.head.yRot, 0.75F);
		this.jaw.xRot = 0.65F * t;
		this.leftArm.xRot = 0.45F * t;
		this.leftArm.zRot = -1.35F * t;
		this.rightArm.xRot = -0.55F * t;
		this.rightArm.zRot = 1.25F * t;
		this.leftLeg.xRot = 0.45F * t;
		this.rightLeg.xRot = 0.25F * t;
	}

	/** How high an arm is raised {@code ticks} into a blow: up over the head, then down hard. */
	private static float blow(float ticks) {
		return BLOW_LIFT * Mth.sin(Math.min(ticks / BLOW_TICKS, 1.0F) * Mth.PI);
	}
}
