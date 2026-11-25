package com.jaymoskingdoms.client.monsters;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The big golem, from {@code MoCModelGolem} of DrZhark's Mo' Creatures (GPL-3, see {@code licenses/mocreatures}), ported to
 * 26.3: a head and a chest (calm or angry) and twenty-three places for a cube of earth (two on each side of the chest, the
 * back, the shoulders, arms and hands, the thighs, knees and feet, the groin and the rump), each cube one of twenty-eight
 * block faces in one texture. Every place has all twenty-eight cubes ready; the one that is there is shown. Its arms swing,
 * its legs walk, its chest turns while it gathers and opens when it takes in earth, and its arms go up to throw.
 */
final class GolemModel extends EntityModel<BeastState> {
	static final int SLOTS = 23, TEXTURES = 28, NONE = 30;
	private static final float RAD = 57.29578F;
	/** Per slot: the cube's corner (x, y, z), the point it turns on (x, y, z) and how it is turned at rest (x, y, z). */
	private static final float[][] SLOT = {
		{-4, 3, -4, 0, -3, 0, -97 / RAD, -40 / RAD, 0},
		{-4, 3, -4, 0, -3, 0, -55 / RAD, -41 / RAD, 0},
		{-4, 3, -4, 0, -3, 0, -97 / RAD, 40 / RAD, 0},
		{-4, 3, -4, 0, -3, 0, -55 / RAD, 41 / RAD, 0},
		{-7, -14, -1, 0, 6, 3, 0, 0.7853982F, 0},
		{-4, 3, -4, 0, -3, 0, 1.919862F, 0.6981317F, 0},
		{-4, 3, -4, 0, -3, 0, 1.183003F, 0.6981317F, 0},
		{-4, 3, -4, 0, -3, 0, 1.919862F, -0.6981317F, 0},
		{-4, 3, -4, 0, -3, 0, 1.183003F, -0.6981317F, 0},
		{0, -2, -4, 8, -3, 0, 0, 0, -0.6981317F},
		{2, 4, -4, 8, -3, 0, 0, 0, -0.2094395F},
		{4.5F, 11, -4, 8, -3, 0, 0, 0, 0},
		{-8, -2, -4, -8, -3, 0, 0, 0, 0.6981317F},
		{-10, 4, -4, -8, -3, 0, 0, 0, 0.2094395F},
		{-12.5F, 11, -4, -8, -3, 0, 0, 0, 0},
		{-3.5F, 0, -4, 5, 4, 0, -0.3490659F, 0, 0},
		{-4, 6, -7, 5, 4, 0, 0, 0, 0},
		{-3.5F, 12, -5, 5, 4, 0, 0, 0, 0},
		{-4.5F, 0, -4, -5, 4, 0, -0.3490659F, 0, 0},
		{-4, 6, -7, -5, 4, 0, 0, 0, 0},
		{-4.5F, 12, -5, -5, 4, 0, 0, 0, 0},
		{0, -4, -8, 0, 6, 3, 0, 0.7853982F, 0},
		{-4, -4, -4, 0, 6, 3, -0.7435722F, 0, 0}};

	private final ModelPart head, headAngry, chest, chestAngry;
	private final ModelPart[][] blocks = new ModelPart[SLOTS][TEXTURES];
	private final ModelPart body;

	GolemModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.head = this.body.getChild("head");
		this.headAngry = this.body.getChild("head_angry");
		this.chest = this.body.getChild("chest");
		this.chestAngry = this.body.getChild("chest_angry");
		for (int i = 0; i < SLOTS; i++) {
			for (int t = 0; t < TEXTURES; t++) {
				this.blocks[i][t] = this.body.getChild("cube_" + i + "_" + t);
			}
		}
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(96, 64).addBox(-4F, -4F, -4F, 8F, 8F, 8F), PartPose.offsetAndRotation(0F, -10F, 0F, 0F, 0.7853982F, 0F));
		body.addOrReplaceChild("head_angry", CubeListBuilder.create().texOffs(96, 80).addBox(-4F, -4F, -4F, 8F, 8F, 8F), PartPose.offsetAndRotation(0F, -10F, 0F, 0F, 0.7853982F, 0F));
		body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(96, 96).addBox(-4F, -4F, -4F, 8F, 8F, 8F), PartPose.offsetAndRotation(0F, -3F, -7F, 0F, 0.7853982F, 0F));
		body.addOrReplaceChild("chest_angry", CubeListBuilder.create().texOffs(96, 112).addBox(-4F, -4F, -4F, 8F, 8F, 8F), PartPose.offsetAndRotation(0F, -3F, -7F, 0F, 0.7853982F, 0F));
		for (int i = 0; i < SLOTS; i++) {
			float[] s = SLOT[i];
			for (int t = 0; t < TEXTURES; t++) {
				body.addOrReplaceChild("cube_" + i + "_" + t, CubeListBuilder.create().texOffs(t / 8 * 32, t % 8 * 16).addBox(s[0], s[1], s[2], 8F, 8F, 8F),
					PartPose.offsetAndRotation(s[3], s[4], s[5], s[6], s[7], s[8]));
			}
		}
		return LayerDefinition.create(mesh, 128, 128);
	}

	/** How far it is let down (blocks) when its legs are missing: it squats on what it has. */
	static float sink(int[] cubes) {
		if (cubes[17] != NONE || cubes[20] != NONE) {
			return 0.0F;
		}
		if (cubes[16] != NONE || cubes[19] != NONE) {
			return 0.4F;
		}
		if (cubes[15] != NONE || cubes[18] != NONE) {
			return 0.7F;
		}
		return cubes[1] != NONE || cubes[3] != NONE ? 0.8F : 1.45F;
	}

	@Override
	public void setupAnim(BeastState state) {
		super.setupAnim(state);
		boolean angry = state.golemState > 1;
		this.head.visible = this.chest.visible = !angry;
		this.headAngry.visible = this.chestAngry.visible = angry;
		this.body.y = sink(state.cubes) * 16.0F;
		float f = state.walkAnimationPos, f1 = state.walkAnimationSpeed, f2 = state.ageInTicks;
		float rLeg = Mth.cos(f * 0.6662F + 3.141593F) * 1.2F * f1;
		float lLeg = Mth.cos(f * 0.6662F) * 1.2F * f1;
		float rArmZ = -(Mth.cos(f2 * 0.09F) * 0.05F) + 0.05F;
		float lArmZ = Mth.cos(f2 * 0.09F) * 0.05F - 0.05F;
		float headY = (45.0F + state.yRot) / RAD;
		this.head.yRot = this.headAngry.yRot = headY;
		float chestY = state.golemState == 1 ? 45.0F / RAD + f2 / 2.0F : 45.0F / RAD;
		this.chest.yRot = this.chestAngry.yRot = chestY;
		this.chest.z = this.chestAngry.z = state.open ? -7.0F : -4.0F;
		for (int i = 0; i < SLOTS; i++) {
			for (int t = 0; t < TEXTURES; t++) {
				this.blocks[i][t].visible = state.cubes[i] == t;
			}
		}
		float[] open = {-60 / RAD, -55 / RAD, 60 / RAD, 55 / RAD}, shut = {-40 / RAD, -41 / RAD, 40 / RAD, 41 / RAD};
		for (int i = 0; i < 4; i++) {
			this.set(i, ((state.open ? open : shut)[i]), Axis.Y);
		}
		this.set(15, -20 / RAD + lLeg, Axis.X);
		this.set(16, lLeg, Axis.X);
		this.set(17, lLeg, Axis.X);
		this.set(18, -20 / RAD + rLeg, Axis.X);
		this.set(19, rLeg, Axis.X);
		this.set(20, rLeg, Axis.X);
		if (state.raised) {
			lLeg = rLeg = -90.0F / RAD;
			rArmZ = lArmZ = 0.0F;
		}
		this.set(12, 40 / RAD + rArmZ, Axis.Z);
		this.set(12, lLeg, Axis.X);
		this.set(13, 12 / RAD + rArmZ, Axis.Z);
		this.set(13, lLeg, Axis.X);
		this.set(14, rArmZ, Axis.Z);
		this.set(14, lLeg, Axis.X);
		this.set(9, -40 / RAD + lArmZ, Axis.Z);
		this.set(9, rLeg, Axis.X);
		this.set(10, -12 / RAD + lArmZ, Axis.Z);
		this.set(10, rLeg, Axis.X);
		this.set(11, lArmZ, Axis.Z);
		this.set(11, rLeg, Axis.X);
	}

	private enum Axis { X, Y, Z }

	/** Turns the cube in {@code slot} (all of its twenty-eight faces, the one shown being whichever is there). */
	private void set(int slot, float angle, Axis axis) {
		for (ModelPart part : this.blocks[slot]) {
			switch (axis) {
				case X -> part.xRot = angle;
				case Y -> part.yRot = angle;
				case Z -> part.zRot = angle;
			}
		}
	}
}
