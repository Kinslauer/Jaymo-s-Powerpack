package com.jaymospowers.client.aether.monster;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** The bladeshroom hunter: a low body on four short legs, bladed jaws, and two mushroom caps on stalks along its back. It rears back and cuts. */
final class BladeshroomHunterModel extends AetherMonsterModel {
	private final ModelPart head, legFl, legFr, legBl, legBr, stemA, stemB;

	private BladeshroomHunterModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.legFl = root.getChild("leg_fl");
		this.legFr = root.getChild("leg_fr");
		this.legBl = root.getChild("leg_bl");
		this.legBr = root.getChild("leg_br");
		this.stemA = root.getChild("stem_a");
		this.stemB = root.getChild("stem_b");
	}

	static BladeshroomHunterModel create() {
		return new BladeshroomHunterModel(layer().bakeRoot());
	}

	private static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		part(r, "body", 0, 0, -4.0F, -7.0F, -6.0F, 8, 7, 12, 0.0F, 18.0F, 0.0F);
		part(r, "head", 40, 0, -3.0F, -3.0F, -4.0F, 6, 6, 4, 0.0F, 14.0F, -6.0F);
		part(r, "blade_l", 60, 0, -0.5F, -1.0F, -5.0F, 1, 2, 5, 2.0F, 15.0F, -9.0F);
		part(r, "blade_r", 72, 0, -0.5F, -1.0F, -5.0F, 1, 2, 5, -2.0F, 15.0F, -9.0F);
		part(r, "leg_fl", 84, 0, -1.0F, 0.0F, -1.0F, 2, 6, 2, 3.0F, 18.0F, -3.5F);
		part(r, "leg_fr", 92, 0, -1.0F, 0.0F, -1.0F, 2, 6, 2, -3.0F, 18.0F, -3.5F);
		part(r, "leg_bl", 100, 0, -1.0F, 0.0F, -1.0F, 2, 6, 2, 3.0F, 18.0F, 3.5F);
		part(r, "leg_br", 108, 0, -1.0F, 0.0F, -1.0F, 2, 6, 2, -3.0F, 18.0F, 3.5F);
		PartDefinition stem_a = part(r, "stem_a", 116, 0, -1.5F, -5.0F, -1.5F, 3, 5, 3, 0.0F, 11.0F, 2.0F);
		part(stem_a, "cap_a", 0, 19, -4.0F, -2.0F, -4.0F, 8, 2, 8, 0.0F, -5.0F, 0.0F);
		PartDefinition stem_b = part(r, "stem_b", 32, 19, -1.0F, -3.0F, -1.0F, 2, 3, 2, 0.0F, 11.0F, -2.0F);
		part(stem_b, "cap_b", 40, 19, -3.0F, -2.0F, -3.0F, 6, 2, 6, 0.0F, -3.0F, 0.0F);
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(AetherMonsterRenderer.State state) {
		super.setupAnim(state);
		float t = state.ageInTicks;
		look(this.head, state);
		this.head.xRot *= 0.7F;
		this.head.yRot *= 0.7F;
		swing(this.legFl, this.legBr, state, 0.9F);
		swing(this.legFr, this.legBl, state, 0.9F);
		// the caps nod as it runs
		this.stemA.xRot = Mth.sin(t * 0.11F) * 0.05F + Math.min(state.walkAnimationSpeed, 1.0F) * 0.12F;
		this.stemB.xRot = Mth.sin(t * 0.13F + 1.0F) * 0.05F;
		// the cut: rear back until tick 18, then slash down
		if (state.strike >= 0.0F) {
			float s = state.strike;
			float windup = s < 18.0F ? smooth(s / 18.0F) : 1.0F - smooth((s - 18.0F) / 6.0F);
			float slash = s >= 18.0F ? smooth((s - 18.0F) / 3.0F) * (1.0F - smooth((s - 24.0F) / 14.0F)) : 0.0F;
			this.head.xRot += -0.7F * windup + 1.1F * slash;
		}
	}
}
