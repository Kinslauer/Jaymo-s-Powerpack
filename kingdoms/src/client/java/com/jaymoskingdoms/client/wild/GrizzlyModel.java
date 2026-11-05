package com.jaymoskingdoms.client.wild;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * (0.7.610) The grizzly ({@code wild/Grizzly}): Bear Minimum's brown bear model (Apache-2.0, by Startraveler; see
 * {@code textures/entity/wild/CREDITS.txt}) with a shoulder hump, and its own poses for each of its moves: reared up on
 * its hind legs (the warning, and the forepaws' slam), a paw raked left or right, a bounding charge with its head low,
 * pinning and biting in a maul, its enraged roar, and staggered.
 */
final class GrizzlyModel extends EntityModel<GrizzlyRenderer.State> {
	/** The grizzly's moves' timings, as the server's Grizzly (ticks). */
	static final float REAR_SLAM = 26.0F, SWIPE_HIT = 7.0F, SWIPE_TICKS = 14.0F, ROAR_TICKS = 40.0F, MAUL_TICKS = 46.0F;
	static final float[] MAUL_BITES = {10.0F, 22.0F, 34.0F};

	private final ModelPart head, body, rightHindLeg, leftHindLeg, rightFrontLeg, leftFrontLeg;

	GrizzlyModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.body = root.getChild("body");
		this.rightHindLeg = root.getChild("right_hind_leg");
		this.leftHindLeg = root.getChild("left_hind_leg");
		this.rightFrontLeg = root.getChild("right_front_leg");
		this.leftFrontLeg = root.getChild("left_front_leg");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-4.5F, -5.0F, -7.0F, 9.0F, 8.0F, 9.0F, CubeDeformation.NONE)
				.texOffs(0, 56).addBox(-2.5F, -2.0F, -11.0F, 5.0F, 4.0F, 4.0F, CubeDeformation.NONE)
				.texOffs(21, 61).addBox(3.5F, -6.0F, -4.0F, 2.0F, 2.0F, 1.0F, CubeDeformation.NONE)
				.texOffs(21, 61).addBox(-5.5F, -6.0F, -4.0F, 2.0F, 2.0F, 1.0F, CubeDeformation.NONE),
			PartPose.offset(0.0F, 11.0F, -13.0F));
		// the body lies on its front (rotated a quarter turn): rear, shoulders, and (ours) the hump over the shoulders
		root.addOrReplaceChild("body", CubeListBuilder.create()
				.texOffs(0, 30).addBox(-5.0F, -13.0F, -7.0F, 14.0F, 14.0F, 11.0F, CubeDeformation.NONE)
				.texOffs(37, 0).addBox(-4.0F, -25.0F, -7.0F, 12.0F, 12.0F, 12.0F, CubeDeformation.NONE)
				.texOffs(88, 0).addBox(-2.0F, -23.0F, 4.0F, 8.0F, 9.0F, 3.0F, CubeDeformation.NONE),
			PartPose.offsetAndRotation(-2.0F, 9.0F, 12.0F, Mth.HALF_PI, 0.0F, 0.0F));
		root.addOrReplaceChild("right_hind_leg", CubeListBuilder.create().texOffs(50, 30).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 10.0F, 8.0F, CubeDeformation.NONE),
			PartPose.offset(4.5F, 14.0F, 6.0F));
		root.addOrReplaceChild("left_hind_leg", CubeListBuilder.create().texOffs(50, 30).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 10.0F, 8.0F, CubeDeformation.NONE),
			PartPose.offset(-4.5F, 14.0F, 6.0F));
		root.addOrReplaceChild("right_front_leg", CubeListBuilder.create().texOffs(50, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 10.0F, 6.0F, CubeDeformation.NONE),
			PartPose.offset(3.5F, 14.0F, -8.0F));
		root.addOrReplaceChild("left_front_leg", CubeListBuilder.create().texOffs(50, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 10.0F, 6.0F, CubeDeformation.NONE),
			PartPose.offset(-3.5F, 14.0F, -8.0F));
		return LayerDefinition.create(mesh, 128, 64).apply(MeshTransformer.scaling(1.2F));
	}

	@Override
	public void setupAnim(GrizzlyRenderer.State state) {
		super.setupAnim(state);
		String action = state.action;
		float t = state.actionAge;
		if ("charge".equals(action)) {
			// a bounding gallop: forelegs together, hind legs together, head low
			float bound = Mth.cos(state.ageInTicks * 0.9F) * 1.1F;
			this.rightFrontLeg.xRot = bound;
			this.leftFrontLeg.xRot = bound;
			this.rightHindLeg.xRot = -bound;
			this.leftHindLeg.xRot = -bound;
			this.head.xRot = 0.35F;
			this.body.xRot += Mth.sin(state.ageInTicks * 0.9F) * 0.06F;
			return;
		}
		// walking, and its head where it looks
		float swing = state.walkAnimationPos * 0.6662F, amount = 1.4F * state.walkAnimationSpeed;
		this.rightHindLeg.xRot = Mth.cos(swing) * amount;
		this.leftHindLeg.xRot = Mth.cos(swing + Mth.PI) * amount;
		this.rightFrontLeg.xRot = Mth.cos(swing + Mth.PI) * amount;
		this.leftFrontLeg.xRot = Mth.cos(swing) * amount;
		this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
		this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
		if (action == null) {
			return;
		}
		switch (action) {
			case "rear" -> {
				// up on its hind legs, then both forepaws brought down on the slam
				float up = t < 8.0F ? t / 8.0F : t < REAR_SLAM - 4.0F ? 1.0F : Math.max(0.0F, (REAR_SLAM - t) / 4.0F);
				this.stand(up, state.ageScale);
				this.head.xRot -= 0.35F * up;
			}
			case "roar" -> {
				float up = t < 8.0F ? t / 8.0F : t < ROAR_TICKS - 6.0F ? 1.0F : Math.max(0.0F, (ROAR_TICKS - t) / 6.0F);
				this.stand(up, state.ageScale);
				this.head.xRot -= 0.5F * up;
				this.head.yRot += Mth.sin(t * 1.3F) * 0.18F * up;
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
				paw.y -= -raise * 2.0F;
				this.head.yRot += (left ? 0.3F : -0.3F);
				this.body.yRot += (left ? -0.08F : 0.08F);
			}
			case "maul" -> {
				// pinning: forepaws forward on its victim, head down and biting
				float in = Math.min(1.0F, t / 4.0F);
				float bite = 0.0F;
				for (float at : MAUL_BITES) {
					float d = t - at;
					if (d > -3.0F && d < 4.0F) {
						bite = Math.max(bite, 1.0F - Math.abs(d + 0.5F) / 3.5F);
					}
				}
				this.rightFrontLeg.xRot = -0.7F * in;
				this.leftFrontLeg.xRot = -0.7F * in;
				this.body.xRot += 0.12F * in;
				this.head.xRot = (0.5F + 0.5F * bite) * in;
				this.head.y += 2.0F * in + 1.5F * bite;
				this.head.yRot = Mth.sin(t * 0.7F) * 0.25F * in;
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

	/** Up on its hind legs by {@code up} (0 on all fours, 1 standing), as the brown bear's (and the polar bear's) stand. */
	private void stand(float up, float ageScale) {
		float s = up * up;
		this.body.xRot -= s * Mth.PI * 0.35F;
		this.body.y += s * ageScale * 2.0F;
		this.rightFrontLeg.y -= s * ageScale * 20.0F;
		this.rightFrontLeg.z += s * ageScale * 4.0F;
		this.rightFrontLeg.xRot -= s * Mth.PI * 0.45F;
		this.leftFrontLeg.y = this.rightFrontLeg.y;
		this.leftFrontLeg.z = this.rightFrontLeg.z;
		this.leftFrontLeg.xRot -= s * Mth.PI * 0.45F;
		this.head.y -= s * 24.0F;
		this.head.z += s * 11.0F;
		this.head.xRot += s * Mth.PI * 0.15F;
	}
}
