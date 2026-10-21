package com.jaymospowers.client.aether.monster;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** The cockatrice: a tall bird-lizard on long legs, a spined back, a long tail and a crested head. The head rears back to spit and snaps forward to bite. */
final class CockatriceModel extends AetherMonsterModel {
	private final ModelPart neck, head, legL, legR, tail, tailTip, wingL, wingR;

	private CockatriceModel(ModelPart root) {
		super(root);
		this.neck = root.getChild("neck");
		this.head = root.getChild("head");
		this.legL = root.getChild("leg_l");
		this.legR = root.getChild("leg_r");
		this.tail = root.getChild("tail");
		this.tailTip = this.tail.getChild("tail_tip");
		this.wingL = root.getChild("wing_l");
		this.wingR = root.getChild("wing_r");
	}

	static CockatriceModel create() {
		return new CockatriceModel(layer().bakeRoot());
	}

	private static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		part(r, "body", 0, 0, -5.0F, -5.0F, -7.0F, 10, 10, 15, 0.0F, 7.0F, 0.0F);
		part(r, "neck", 50, 0, -2.0F, -9.0F, -2.0F, 4, 10, 4, 0.0F, 4.0F, -6.0F);
		PartDefinition head = part(r, "head", 66, 0, -3.0F, -5.0F, -4.0F, 6, 5, 7, 0.0F, -5.0F, -6.0F);
		part(head, "beak", 92, 0, -1.5F, -1.0F, -4.0F, 3, 2, 4, 0.0F, -2.0F, -4.0F);
		part(head, "crest", 106, 0, -0.5F, -3.0F, -1.0F, 1, 3, 4, 0.0F, -5.0F, -1.0F);
		part(r, "spine_a", 116, 0, -0.5F, -3.0F, -1.0F, 1, 3, 2, 0.0F, 2.0F, -3.0F);
		part(r, "spine_b", 122, 0, -0.5F, -3.0F, -1.0F, 1, 3, 2, 0.0F, 2.0F, 1.0F);
		part(r, "spine_c", 0, 25, -0.5F, -3.0F, -1.0F, 1, 3, 2, 0.0F, 2.0F, 5.0F);
		PartDefinition tail = part(r, "tail", 6, 25, -2.0F, -2.0F, 0.0F, 4, 4, 10, 0.0F, 6.0F, 8.0F);
		part(tail, "tail_tip", 34, 25, -1.0F, -1.0F, 0.0F, 2, 2, 6, 0.0F, 0.0F, 10.0F);
		part(r, "wing_l", 50, 25, 0.0F, -1.0F, -3.0F, 1, 6, 7, 5.0F, 4.0F, -1.0F);
		part(r, "wing_r", 66, 25, -1.0F, -1.0F, -3.0F, 1, 6, 7, -5.0F, 4.0F, -1.0F);
		PartDefinition leg_l = part(r, "leg_l", 82, 25, -1.5F, 0.0F, -1.5F, 3, 12, 3, 3.0F, 12.0F, 1.0F);
		PartDefinition leg_r = part(r, "leg_r", 94, 25, -1.5F, 0.0F, -1.5F, 3, 12, 3, -3.0F, 12.0F, 1.0F);
		part(leg_l, "foot_l", 106, 25, -2.0F, -1.0F, -4.0F, 4, 1, 5, 0.0F, 12.0F, 0.0F);
		part(leg_r, "foot_r", 0, 40, -2.0F, -1.0F, -4.0F, 4, 1, 5, 0.0F, 12.0F, 0.0F);
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(AetherMonsterRenderer.State state) {
		super.setupAnim(state);
		float t = state.ageInTicks;
		swing(this.legL, this.legR, state, 1.0F);
		look(this.head, state);
		this.head.xRot *= 0.7F;
		this.head.yRot *= 0.7F;
		this.neck.xRot = 0.15F + Mth.sin(t * 0.08F) * 0.03F;
		// spitting: the head goes back and the little wings lift
		if (state.action == 1) {
			this.head.xRot -= 0.7F;
			this.neck.xRot -= 0.25F;
			float flap = Mth.sin(t * 0.8F) * 0.3F;
			this.wingL.zRot = 0.6F + flap;
			this.wingR.zRot = -0.6F - flap;
		}
		// the bite
		float bite = Mth.sin(state.attack * Mth.PI);
		this.head.xRot += bite * 0.9F;
		this.neck.xRot += bite * 0.3F;
		this.tail.xRot = -0.25F;
		this.tail.yRot = Mth.sin(t * 0.1F) * 0.15F;
		this.tailTip.yRot = Mth.sin(t * 0.1F - 0.8F) * 0.25F;
	}
}
