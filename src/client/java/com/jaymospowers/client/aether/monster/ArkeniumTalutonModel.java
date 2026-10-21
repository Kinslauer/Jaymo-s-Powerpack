package com.jaymospowers.client.aether.monster;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** The arkenium taluton: a hunched, steel-plated brute with spines on its back and heavy fists; it drags its knuckles and slams down with both arms. */
final class ArkeniumTalutonModel extends AetherMonsterModel {
	private final ModelPart torso, head, legL, legR, armL, armR;

	private ArkeniumTalutonModel(ModelPart root) {
		super(root);
		this.torso = root.getChild("torso");
		this.head = root.getChild("head");
		this.legL = root.getChild("leg_l");
		this.legR = root.getChild("leg_r");
		this.armL = root.getChild("arm_l");
		this.armR = root.getChild("arm_r");
	}

	static ArkeniumTalutonModel create() {
		return new ArkeniumTalutonModel(layer().bakeRoot());
	}

	private static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		part(r, "leg_l", 0, 0, -2.5F, 0.0F, -2.5F, 5, 9, 5, 3.5F, 15.0F, 0.0F);
		part(r, "leg_r", 20, 0, -2.5F, 0.0F, -2.5F, 5, 9, 5, -3.5F, 15.0F, 0.0F);
		PartDefinition torso = part(r, "torso", 40, 0, -6.0F, -11.0F, -4.5F, 12, 11, 9, 0.0F, 15.0F, 0.0F);
		part(torso, "chest", 82, 0, -5.0F, -9.0F, -1.0F, 10, 7, 1, 0.0F, 0.0F, -5.0F);
		part(torso, "shoulder_l", 0, 20, -3.0F, -2.0F, -4.0F, 6, 3, 8, 7.0F, -10.0F, 0.0F);
		part(torso, "shoulder_r", 28, 20, -3.0F, -2.0F, -4.0F, 6, 3, 8, -7.0F, -10.0F, 0.0F);
		part(torso, "spine_a", 56, 20, -1.0F, -5.0F, -1.0F, 2, 5, 2, 0.0F, -11.0F, -2.5F);
		part(torso, "spine_b", 64, 20, -1.0F, -5.0F, -1.0F, 2, 5, 2, 0.0F, -11.0F, 1.0F);
		part(torso, "spine_c", 72, 20, -1.0F, -5.0F, -1.0F, 2, 5, 2, 0.0F, -11.0F, 4.0F);
		part(r, "head", 80, 20, -3.0F, -6.0F, -3.0F, 6, 6, 6, 0.0F, 4.0F, -2.0F);
		PartDefinition arm_l = part(r, "arm_l", 104, 20, -2.0F, 0.0F, -2.0F, 4, 10, 4, 8.0F, 5.0F, 0.0F);
		PartDefinition arm_r = part(r, "arm_r", 0, 34, -2.0F, 0.0F, -2.0F, 4, 10, 4, -8.0F, 5.0F, 0.0F);
		part(arm_l, "fist_l", 16, 34, -2.5F, 0.0F, -2.5F, 5, 4, 5, 0.0F, 10.0F, 0.0F);
		part(arm_r, "fist_r", 36, 34, -2.5F, 0.0F, -2.5F, 5, 4, 5, 0.0F, 10.0F, 0.0F);
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(AetherMonsterRenderer.State state) {
		super.setupAnim(state);
		float t = state.ageInTicks;
		swing(this.legL, this.legR, state, 0.9F);
		this.torso.xRot = 0.35F + Mth.sin(t * 0.07F) * 0.02F;
		look(this.head, state);
		this.head.xRot = this.head.xRot * 0.6F + 0.15F;
		// the arms hang forward and swing against the legs; a blow raises both and slams them down
		float walk = Math.min(state.walkAnimationSpeed, 1.0F);
		this.armL.xRot = 0.3F - Mth.cos(state.walkAnimationPos * 0.6662F) * 0.7F * walk;
		this.armR.xRot = 0.3F + Mth.cos(state.walkAnimationPos * 0.6662F) * 0.7F * walk;
		float blow = Mth.sin(state.attack * Mth.PI);
		this.armL.xRot -= blow * 2.4F;
		this.armR.xRot -= blow * 2.4F;
		this.armL.zRot = -0.08F;
		this.armR.zRot = 0.08F;
	}
}
