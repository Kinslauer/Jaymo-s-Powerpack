package com.jaymospowers.client.aether.mob;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The burrukai: a hulking, armoured beast with a shoulder hump, plates on its back, a broad head with a pair of flat
 * antlers and thick legs. It lowers its head and paws the ground when it winds up a ram.
 */
final class BurrukaiModel extends AetherAnimalModel {
	private final ModelPart head, antlerL, antlerR, tail, legFr, legFl, legBr, legBl;

	private BurrukaiModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.antlerL = this.head.getChild("antler_l");
		this.antlerR = this.head.getChild("antler_r");
		this.tail = root.getChild("tail");
		this.legFr = root.getChild("leg_fr");
		this.legFl = root.getChild("leg_fl");
		this.legBr = root.getChild("leg_br");
		this.legBl = root.getChild("leg_bl");
	}

	static BurrukaiModel create() {
		return new BurrukaiModel(layer().bakeRoot());
	}

	private static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		part(r, "body", 0, 0, -7F, -6F, -9F, 14, 12, 18, 0F, 9F, 0F);
		part(r, "hump", 64, 0, -6F, -5F, -5F, 12, 5, 10, 0F, 3F, -3F);
		part(r, "plate", 64, 15, -5F, -4F, -6F, 10, 4, 12, 0F, 3F, 5F);
		PartDefinition head = part(r, "head", 0, 31, -3F, -4F, -8F, 6, 8, 8, 0F, 6F, -9F);
		part(head, "antler_l", 28, 31, 2F, -11F, -4F, 1, 10, 10, 0F, 0F, 0F);
		part(head, "antler_r", 50, 31, -3F, -11F, -4F, 1, 10, 10, 0F, 0F, 0F);
		part(r, "tail", 72, 31, -1F, 0F, 0F, 2, 9, 2, 0F, 5F, 9F);
		part(r, "leg_fr", 0, 51, -2.5F, 0F, -2.5F, 5, 12, 5, -5F, 12F, -6F);
		part(r, "leg_fl", 20, 51, -2.5F, 0F, -2.5F, 5, 12, 5, 5F, 12F, -6F);
		part(r, "leg_br", 40, 51, -2.5F, 0F, -2.5F, 5, 12, 5, -5F, 12F, 6F);
		part(r, "leg_bl", 60, 51, -2.5F, 0F, -2.5F, 5, 12, 5, 5F, 12F, 6F);
		return LayerDefinition.create(mesh, 128, 80);
	}

	@Override
	public void setupAnim(AetherAnimalRenderer.State state) {
		super.setupAnim(state);
		look(this.head, state);
		swing(this.legFr, this.legBl, state, 0.9F);
		swing(this.legFl, this.legBr, state, 0.9F);
		if (state.windup) {
			// head down, front hoof scraping
			this.head.xRot = 0.55F;
			this.legFr.xRot = Mth.sin(state.ageInTicks * 0.9F) * 0.9F - 0.3F;
		}
		this.antlerL.zRot = 0.3F;
		this.antlerR.zRot = -0.3F;
		this.tail.xRot = 0.35F + (state.angry ? Mth.sin(state.ageInTicks * 0.6F) * 0.2F : 0.0F);
	}
}
