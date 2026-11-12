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
 * The muskox ({@code wild/Muskox}), our own model built from {@code docs/Muskox-Model-Spec.md}: a barrel of a body with
 * the hump over the shoulders its highest point, a skirt of long hair hanging to the knees round short stout legs, the
 * head carried low in front of the chest, and the horns: a broad boss across the brow, each horn dropping beside the
 * cheek, hooking forward and turning up at the tip. A calf has no horns yet and no skirt, and a bigger head for its
 * size. Its poses: the walk, a gallop when it runs (fore pair and hind pair together), the head down to graze, and
 * the head dropped and thrust forward when it butts. {@code tools/gen_muskox.py} mirrors these boxes (keep both the
 * same) and paints the coats.
 */
final class MuskoxModel extends EntityModel<MuskoxRenderer.State> {
	/** The walk's swing (radians at full stride), and the limb speed its gallop begins at and is in full by. */
	static final float STRIDE = 1.1F, GALLOP_FROM = 0.5F, GALLOP_FULL = 0.75F;
	/** Grazing: the head's turn down (radians) and drop (texels), and its chewing nod. */
	static final float GRAZE_TURN = 1.0F, GRAZE_DROP = 3.0F, CHEW = 0.07F;
	/** Butting: the head's turn down (radians) and thrust forward (texels). */
	static final float BUTT_TURN = 0.55F, BUTT_THRUST = 3.0F;
	/** How far the head turns to look (radians at most), and a calf's head for its size. */
	static final float LOOK = 0.6F, CALF_HEAD = 1.25F;

	private final ModelPart body, skirt, head, horns, rightFrontLeg, leftFrontLeg, rightHindLeg, leftHindLeg;

	MuskoxModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.skirt = this.body.getChild("skirt");
		this.head = root.getChild("head");
		this.horns = this.head.getChild("horns");
		this.rightFrontLeg = root.getChild("right_front_leg");
		this.leftFrontLeg = root.getChild("left_front_leg");
		this.rightHindLeg = root.getChild("right_hind_leg");
		this.leftHindLeg = root.getChild("left_hind_leg");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// the barrel, and the hump over the shoulders standing a little forward of the chest (F3, F4)
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-6.0F, 5.0F, -10.0F, 12.0F, 11.0F, 20.0F, CubeDeformation.NONE)
				.texOffs(64, 0).addBox(-5.0F, 2.0F, -11.0F, 10.0F, 4.0F, 10.0F, CubeDeformation.NONE),
			PartPose.ZERO);
		// the skirt of guard hair, a shell round the barrel down to the knees (F5)
		body.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(0, 31).addBox(-7.0F, 0.0F, -11.0F, 14.0F, 11.0F, 22.0F, CubeDeformation.NONE),
			PartPose.offset(0.0F, 9.0F, 0.0F));
		// the head, low in front of the chest: skull and the broad muzzle (F8)
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
				.texOffs(72, 31).addBox(-4.0F, -4.0F, -7.0F, 8.0F, 8.0F, 7.0F, CubeDeformation.NONE)
				.texOffs(104, 0).addBox(-3.0F, 0.0F, -10.0F, 6.0F, 5.0F, 3.0F, CubeDeformation.NONE),
			PartPose.offset(0.0F, 9.0F, -10.0F));
		// the horns (F7): the boss across the brow, then each side down beside the cheek, forward, and up at the tip
		CubeListBuilder horns = CubeListBuilder.create().texOffs(104, 8).addBox(-4.5F, -5.0F, -6.0F, 9.0F, 2.0F, 3.0F, CubeDeformation.NONE);
		for (int side = -1; side <= 1; side += 2) {
			horns.texOffs(64, 14).addBox(side > 0 ? 4.0F : -6.0F, -4.0F, -5.5F, 2.0F, 8.0F, 2.0F, CubeDeformation.NONE)
				.texOffs(72, 14).addBox(side > 0 ? 4.0F : -6.0F, 3.0F, -8.5F, 2.0F, 2.0F, 3.0F, CubeDeformation.NONE)
				.texOffs(82, 14).addBox(side > 0 ? 5.0F : -6.0F, 0.0F, -8.5F, 1.0F, 3.0F, 1.0F, CubeDeformation.NONE);
		}
		head.addOrReplaceChild("horns", horns, PartPose.ZERO);
		// short stout legs, mostly under the skirt (F9)
		for (int side = -1; side <= 1; side += 2) {
			root.addOrReplaceChild(side > 0 ? "right_front_leg" : "left_front_leg", CubeListBuilder.create()
					.texOffs(72, 46).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 9.0F, 4.0F, CubeDeformation.NONE),
				PartPose.offset(3.5F * side, 15.0F, -7.0F));
			root.addOrReplaceChild(side > 0 ? "right_hind_leg" : "left_hind_leg", CubeListBuilder.create()
					.texOffs(88, 46).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 9.0F, 4.0F, CubeDeformation.NONE),
				PartPose.offset(3.5F * side, 15.0F, 7.0F));
		}
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(MuskoxRenderer.State state) {
		super.setupAnim(state);
		// a calf: no horns yet, a short woolly coat with no skirt, a big head (F10)
		this.horns.visible = !state.isBaby;
		this.skirt.visible = !state.isBaby;
		float headSize = state.isBaby ? CALF_HEAD : 1.0F;
		this.head.xScale = headSize;
		this.head.yScale = headSize;
		this.head.zScale = headSize;

		// walking in diagonal pairs; running, the fore pair and the hind pair each together
		float swing = state.walkAnimationPos * 0.6662F, amount = STRIDE * Math.min(1.0F, state.walkAnimationSpeed);
		float gallop = Mth.clamp((state.walkAnimationSpeed - GALLOP_FROM) / (GALLOP_FULL - GALLOP_FROM), 0.0F, 1.0F);
		this.leftFrontLeg.xRot = Mth.cos(swing) * amount;
		this.rightFrontLeg.xRot = Mth.cos(swing + Mth.PI * (1.0F - gallop)) * amount;
		this.leftHindLeg.xRot = Mth.cos(swing + Mth.PI) * amount;
		this.rightHindLeg.xRot = Mth.cos(swing + Mth.PI * gallop) * amount;
		// the skirt swings with the stride, and the whole body rocks a little at the gallop
		this.skirt.zRot = Mth.sin(swing) * 0.04F * amount;
		this.skirt.xRot = Mth.cos(swing) * 0.08F * amount * gallop;
		this.body.xRot = Mth.sin(swing) * 0.05F * amount * gallop;

		// the head where it looks (it can't turn far in all that hair), lower when it runs
		this.head.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -LOOK, LOOK);
		this.head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.5F + 0.3F * gallop;
		this.head.y += gallop;
		// a slow breath
		this.body.y += Mth.sin(state.ageInTicks * 0.07F) * 0.15F;
		if (state.graze > 0.0F) {
			// the head down to the ground, nodding as it chews
			this.head.xRot = Mth.lerp(state.graze, this.head.xRot, GRAZE_TURN + Mth.sin(state.ageInTicks * 0.7F) * CHEW);
			this.head.yRot *= 1.0F - state.graze;
			this.head.y += GRAZE_DROP * state.graze;
		}
		if (state.butt > 0.0F) {
			// the boss dropped and driven forward
			this.head.xRot += BUTT_TURN * state.butt;
			this.head.z -= BUTT_THRUST * state.butt;
		}
	}
}
