package com.jaymospowers.client.aether.mob;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** The aerbunny: a tiny round rabbit with long ears, puffed cheeks and a puff of a tail. Its ears stream back in the air. */
final class AerbunnyModel extends AetherAnimalModel {
	private final ModelPart head, earL, earR, legFl, legFr, legBl, legBr, tail;

	private AerbunnyModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.earL = this.head.getChild("ear_l");
		this.earR = this.head.getChild("ear_r");
		this.legFl = root.getChild("leg_fl");
		this.legFr = root.getChild("leg_fr");
		this.legBl = root.getChild("leg_bl");
		this.legBr = root.getChild("leg_br");
		this.tail = root.getChild("tail");
	}

	static AerbunnyModel create() {
		return new AerbunnyModel(layer().bakeRoot());
	}

	private static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		PartDefinition head = part(r, "head", 0, 0, -2F, -2F, -4F, 4, 4, 4, 0F, 19F, -3F);
		part(head, "ear_l", 16, 0, 0.5F, -6F, -2F, 1, 4, 1, 0F, 0F, 0F);
		part(head, "ear_r", 20, 0, -1.5F, -6F, -2F, 1, 4, 1, 0F, 0F, 0F);
		part(head, "cheek_l", 24, 0, 2F, 0F, -3F, 2, 2, 2, 0F, 0F, 0F);
		part(head, "cheek_r", 32, 0, -4F, 0F, -3F, 2, 2, 2, 0F, 0F, 0F);
		part(r, "body", 0, 8, -2F, -2F, -3F, 4, 4, 6, 0F, 20F, 0F);
		part(r, "tail", 40, 0, -1.5F, -1.5F, 0F, 3, 3, 3, 0F, 20F, 3F);
		part(r, "leg_bl", 0, 18, -1F, 0F, -1.5F, 2, 3, 3, 1.5F, 21F, 2F);
		part(r, "leg_br", 10, 18, -1F, 0F, -1.5F, 2, 3, 3, -1.5F, 21F, 2F);
		part(r, "leg_fl", 20, 18, -0.5F, 0F, -0.5F, 1, 2, 1, 1F, 22F, -2F);
		part(r, "leg_fr", 24, 18, -0.5F, 0F, -0.5F, 1, 2, 1, -1F, 22F, -2F);
		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public void setupAnim(AetherAnimalRenderer.State state) {
		super.setupAnim(state);
		look(this.head, state);
		// a hop: hind legs and fore legs swing together
		float hop = Mth.cos(state.walkAnimationPos * 0.6662F) * 1.2F * Math.min(state.walkAnimationSpeed, 1.0F);
		this.legBl.xRot = hop;
		this.legBr.xRot = hop;
		this.legFl.xRot = -hop * 0.6F;
		this.legFr.xRot = -hop * 0.6F;
		// the ears stream back when it is in the air, and twitch a little when it is still
		float back = state.air * 0.7F;
		float twitch = Mth.sin(state.ageInTicks * 0.15F) * 0.05F;
		this.earL.xRot = back + twitch;
		this.earR.xRot = back - twitch;
		this.earL.zRot = 0.1F;
		this.earR.zRot = -0.1F;
		this.tail.xRot = -state.air * 0.3F;
	}
}
