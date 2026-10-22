package com.jaymospowers.client.aether.mob;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The taegore: a long, low digger with a ridge down its back, plates on its flanks, a long snout and a tall crest. Its head
 * goes to the ground and its forepaws scrape when it digs.
 */
final class TaegoreModel extends AetherAnimalModel {
	private final ModelPart head, legFr, legFl, legBr, legBl, tail;

	private TaegoreModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.legFr = root.getChild("leg_fr");
		this.legFl = root.getChild("leg_fl");
		this.legBr = root.getChild("leg_br");
		this.legBl = root.getChild("leg_bl");
		this.tail = root.getChild("tail");
	}

	static TaegoreModel create() {
		return new TaegoreModel(layer().bakeRoot());
	}

	private static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		part(r, "body", 0, 0, -6F, -6F, -10F, 12, 12, 20, 0F, 10F, 0F);
		part(r, "spine", 0, 52, -0.5F, -4F, -9F, 1, 4, 18, 0F, 4F, 0F);
		part(r, "plate_l", 64, 32, 6F, -5F, -6F, 1, 8, 12, 0F, 10F, 0F);
		part(r, "plate_r", 90, 32, -7F, -5F, -6F, 1, 8, 12, 0F, 10F, 0F);
		PartDefinition head = part(r, "head", 64, 0, -5F, -4F, -8F, 10, 7, 8, 0F, 9F, -10F);
		part(head, "snout", 100, 0, -2.5F, -2F, -13F, 5, 5, 5, 0F, 0F, 0F);
		part(head, "ear_l", 100, 10, 3F, -7F, -3F, 2, 3, 1, 0F, 0F, 0F);
		part(head, "ear_r", 106, 10, -5F, -7F, -3F, 2, 3, 1, 0F, 0F, 0F);
		part(head, "crest", 64, 15, -1F, -7F, -6F, 2, 3, 8, 0F, 0F, 0F);
		part(r, "tail", 84, 15, -4F, -2F, 0F, 8, 10, 1, 0F, 8F, 10F);
		part(r, "leg_fr", 0, 32, -2F, 0F, -2F, 4, 8, 4, -4.5F, 16F, -7F);
		part(r, "leg_fl", 16, 32, -2F, 0F, -2F, 4, 8, 4, 4.5F, 16F, -7F);
		part(r, "leg_br", 32, 32, -2F, 0F, -2F, 4, 8, 4, -4.5F, 16F, 7F);
		part(r, "leg_bl", 48, 32, -2F, 0F, -2F, 4, 8, 4, 4.5F, 16F, 7F);
		return LayerDefinition.create(mesh, 128, 80);
	}

	@Override
	public void setupAnim(AetherAnimalRenderer.State state) {
		super.setupAnim(state);
		look(this.head, state);
		swing(this.legFr, this.legBl, state, 0.8F);
		swing(this.legFl, this.legBr, state, 0.8F);
		if (state.digging) {
			// nose down, forepaws scraping in turn
			this.head.xRot = 0.9F + Mth.sin(state.ageInTicks * 0.7F) * 0.08F;
			this.legFr.xRot = Mth.sin(state.ageInTicks * 0.9F) * 1.1F;
			this.legFl.xRot = Mth.sin(state.ageInTicks * 0.9F + Mth.PI) * 1.1F;
		}
		this.tail.xRot = 0.25F;
	}
}
