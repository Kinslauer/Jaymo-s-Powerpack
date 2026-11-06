package com.jaymoskingdoms.client.wild;

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
 * (0.7.611) The grizzly ({@code wild/Grizzly}), our own model built from {@code docs/Grizzly-Model-Spec.md} (Andy:
 * "Can you use rea for models"): the shoulder hump its highest point, the rump lower, a dished face with a long muzzle
 * and a jaw that drops, small round ears, long pale claws on the forepaws, a stub of a tail. The body pivots at the
 * hips with the head and forelegs riding it, so rearing up is one turn. Its poses: reared (the warning, and the
 * forepaws' slam), the enraged roar with the jaw wide, a paw raked left or right, a bounding charge, a maul's bites,
 * staggered. {@code tools/gen_grizzly.py} mirrors these boxes (keep both the same) and paints the coat.
 */
final class GrizzlyModel extends EntityModel<GrizzlyRenderer.State> {
	/** The grizzly's moves' timings, as the server's Grizzly (ticks). */
	static final float REAR_SLAM = 26.0F, SWIPE_HIT = 7.0F, SWIPE_TICKS = 14.0F, ROAR_TICKS = 40.0F;
	static final float[] MAUL_BITES = {10.0F, 22.0F, 34.0F};
	/** Reared up: the body's turn back from the hips, the forelegs reaching out, the head kept level (radians). */
	static final float REAR_BODY = -1.15F, REAR_FORELEGS = 0.55F, REAR_HEAD = 0.75F;
	/** The jaw dropped wide (radians). */
	static final float JAW_OPEN = 0.75F;

	private final ModelPart head, jaw, body, rightHindLeg, leftHindLeg, rightFrontLeg, leftFrontLeg;

	GrizzlyModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.head = this.body.getChild("head");
		this.jaw = this.head.getChild("jaw");
		this.rightFrontLeg = this.body.getChild("right_front_leg");
		this.leftFrontLeg = this.body.getChild("left_front_leg");
		this.rightHindLeg = root.getChild("right_hind_leg");
		this.leftHindLeg = root.getChild("left_hind_leg");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// the body, pivoting at the hips: chest, the hump over the shoulders (F1), the lower rump (F2), the tail (F7)
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-7.5F, -11.0F, -20.0F, 15.0F, 14.0F, 12.0F, CubeDeformation.NONE)
				.texOffs(54, 0).addBox(-5.0F, -14.0F, -19.0F, 10.0F, 4.0F, 9.0F, CubeDeformation.NONE)
				.texOffs(0, 26).addBox(-6.5F, -8.0F, -8.0F, 13.0F, 12.0F, 12.0F, CubeDeformation.NONE)
				.texOffs(114, 4).addBox(-1.5F, -5.0F, 4.0F, 3.0F, 3.0F, 2.0F, CubeDeformation.NONE),
			PartPose.offset(0.0F, 14.0F, 8.0F));
		// the head: skull, the dished muzzle set a step below the brow (F3), small wide ears (F4)
		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
				.texOffs(54, 13).addBox(-5.0F, -5.0F, -8.0F, 10.0F, 9.0F, 8.0F, CubeDeformation.NONE)
				.texOffs(92, 0).addBox(-3.0F, -1.5F, -13.0F, 6.0F, 4.0F, 5.0F, CubeDeformation.NONE)
				.texOffs(114, 0).addBox(-5.5F, -7.0F, -4.0F, 3.0F, 3.0F, 1.0F, CubeDeformation.NONE)
				.texOffs(114, 0).addBox(2.5F, -7.0F, -4.0F, 3.0F, 3.0F, 1.0F, CubeDeformation.NONE),
			PartPose.offset(0.0F, -5.0F, -20.0F));
		// the jaw, which drops for the roar and the bites (F9)
		head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(92, 9).addBox(-2.5F, 0.0F, -5.0F, 5.0F, 2.0F, 5.0F, CubeDeformation.NONE),
			PartPose.offset(0.0F, 2.5F, -8.0F));
		// the forelegs ride the body, with the long claws (F5)
		for (int side = -1; side <= 1; side += 2) {
			body.addOrReplaceChild(side > 0 ? "right_front_leg" : "left_front_leg", CubeListBuilder.create()
					.texOffs(54, 30).addBox(-2.5F, 0.0F, -3.0F, 5.0F, 10.0F, 6.0F, CubeDeformation.NONE)
					.texOffs(100, 30).addBox(-2.5F, 9.0F, -5.0F, 5.0F, 1.0F, 2.0F, CubeDeformation.NONE),
				PartPose.offset(4.5F * side, 0.0F, -14.0F));
			root.addOrReplaceChild(side > 0 ? "right_hind_leg" : "left_hind_leg", CubeListBuilder.create()
					.texOffs(76, 30).addBox(-2.5F, 0.0F, -3.0F, 5.0F, 10.0F, 7.0F, CubeDeformation.NONE),
				PartPose.offset(4.5F * side, 14.0F, 8.0F));
		}
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(GrizzlyRenderer.State state) {
		super.setupAnim(state);
		String action = state.action;
		float t = state.actionAge;
		if ("charge".equals(action)) {
			// a bounding gallop: forelegs together, hind legs together, head low
			float bound = Mth.cos(state.ageInTicks * 0.9F) * 0.9F;
			this.rightFrontLeg.xRot = -bound;
			this.leftFrontLeg.xRot = -bound;
			this.rightHindLeg.xRot = bound;
			this.leftHindLeg.xRot = bound;
			this.head.xRot = 0.3F;
			this.body.xRot = Mth.sin(state.ageInTicks * 0.9F) * 0.06F;
			return;
		}
		// walking, and its head where it looks
		float swing = state.walkAnimationPos * 0.6662F, amount = 1.2F * state.walkAnimationSpeed;
		this.rightHindLeg.xRot = Mth.cos(swing) * amount;
		this.leftHindLeg.xRot = Mth.cos(swing + Mth.PI) * amount;
		this.rightFrontLeg.xRot = Mth.cos(swing + Mth.PI) * amount;
		this.leftFrontLeg.xRot = Mth.cos(swing) * amount;
		this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
		this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
		// a slow breath, the head bobbing a little with the stride
		this.body.y += Mth.sin(state.ageInTicks * 0.08F) * 0.2F;
		this.head.y += Mth.cos(swing * 2.0F) * 0.4F * state.walkAnimationSpeed;
		if (action == null) {
			return;
		}
		switch (action) {
			case "rear" -> {
				// up on its hind legs, then both forepaws brought down on the slam
				this.rear(t < 8.0F ? t / 8.0F : t < REAR_SLAM - 4.0F ? 1.0F : Math.max(0.0F, (REAR_SLAM - t) / 4.0F));
			}
			case "roar" -> {
				float up = t < 8.0F ? t / 8.0F : t < ROAR_TICKS - 6.0F ? 1.0F : Math.max(0.0F, (ROAR_TICKS - t) / 6.0F);
				this.rear(up);
				this.head.xRot -= 0.4F * up;
				this.head.yRot += Mth.sin(t * 1.3F) * 0.18F * up;
				this.jaw.xRot = JAW_OPEN * up;
			}
			case "swipe_left", "swipe_right" -> {
				// the paw drawn up and back, raked across, then set down
				boolean left = "swipe_left".equals(action);
				ModelPart paw = left ? this.leftFrontLeg : this.rightFrontLeg;
				float raise, sweep;
				if (t < SWIPE_HIT - 2.0F) {
					float k = t / (SWIPE_HIT - 2.0F);
					raise = -1.5F * k;
					sweep = 0.5F * k;
				} else if (t < SWIPE_HIT + 1.0F) {
					float k = (t - (SWIPE_HIT - 2.0F)) / 3.0F;
					raise = Mth.lerp(k, -1.5F, -0.3F);
					sweep = Mth.lerp(k, 0.5F, -0.7F);
				} else {
					float k = Math.min(1.0F, (t - (SWIPE_HIT + 1.0F)) / (SWIPE_TICKS - SWIPE_HIT - 1.0F));
					raise = Mth.lerp(k, -0.3F, 0.0F);
					sweep = Mth.lerp(k, -0.7F, 0.0F);
				}
				paw.xRot = raise;
				paw.zRot = left ? -sweep : sweep;
				paw.y += raise * 1.5F;
				this.head.yRot += left ? 0.3F : -0.3F;
				this.body.yRot += left ? -0.08F : 0.08F;
				this.jaw.xRot = 0.25F * -raise / 1.5F;
			}
			case "maul" -> {
				// pinning: chest down on its victim, forepaws forward, head low, the jaw snapping on each bite
				float in = Math.min(1.0F, t / 4.0F);
				float bite = 0.0F;
				for (float at : MAUL_BITES) {
					float d = t - at;
					if (d > -4.0F && d < 2.0F) {
						// open on the way in, shut on the bite
						bite = Math.max(bite, d < 0.0F ? 1.0F + d / 4.0F : 1.0F - d / 2.0F);
					}
				}
				this.body.xRot += 0.18F * in;
				this.rightFrontLeg.xRot = -0.75F * in;
				this.leftFrontLeg.xRot = -0.75F * in;
				this.head.xRot = (0.5F + 0.25F * bite) * in;
				this.head.yRot = Mth.sin(t * 0.7F) * 0.25F * in;
				this.jaw.xRot = JAW_OPEN * bite * in;
			}
			case "stagger" -> {
				float k = Math.max(0.0F, 1.0F - t / 24.0F);
				this.head.yRot += Mth.sin(t * 0.9F) * 0.45F * k;
				this.head.xRot += 0.3F * k;
				this.body.zRot += Mth.sin(t * 0.5F) * 0.08F * k;
			}
			default -> {
			}
		}
	}

	/** Up on its hind legs by {@code up} (0 on all fours, 1 standing): the body turned back at the hips (F8). */
	private void rear(float up) {
		float s = up * up * (3.0F - 2.0F * up);
		this.body.xRot += REAR_BODY * s;
		this.rightFrontLeg.xRot = Mth.lerp(s, this.rightFrontLeg.xRot, REAR_FORELEGS);
		this.leftFrontLeg.xRot = Mth.lerp(s, this.leftFrontLeg.xRot, REAR_FORELEGS);
		this.head.xRot += REAR_HEAD * s;
		this.rightHindLeg.xRot *= 1.0F - s;
		this.leftHindLeg.xRot *= 1.0F - s;
	}
}
