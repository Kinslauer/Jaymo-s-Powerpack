package com.jaymospowers.client.aether.mob;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The sheepuff: a sheep whose wool is a close coat when it is plain and a great cloud when it is puffed (a second,
 * fatter shell of wool shown in its place). Bare, it shows the skin. Its head goes down when it grazes.
 */
final class SheepuffModel extends AetherAnimalModel {
	private final ModelPart head, headWool, wool, woolPuff, legFr, legFl, legBr, legBl;

	private SheepuffModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.headWool = this.head.getChild("head_wool");
		this.wool = root.getChild("wool");
		this.woolPuff = root.getChild("wool_puff");
		this.legFr = root.getChild("leg_fr");
		this.legFl = root.getChild("leg_fl");
		this.legBr = root.getChild("leg_br");
		this.legBl = root.getChild("leg_bl");
	}

	static SheepuffModel create() {
		return new SheepuffModel(layer().bakeRoot());
	}

	private static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		PartDefinition head = part(r, "head", 0, 0, -3F, -3F, -7F, 6, 6, 7, 0F, 12F, -6F);
		part(head, "ear_l", 26, 0, 3F, -2F, -3F, 3, 2, 1, 0F, 0F, 0F);
		part(head, "ear_r", 34, 0, -6F, -2F, -3F, 3, 2, 1, 0F, 0F, 0F);
		part(head, "head_wool", 42, 0, -3F, -6F, -4F, 6, 3, 6, 0F, 0F, 0F);
		part(r, "body", 0, 13, -4F, -4F, -6F, 8, 7, 12, 0F, 15F, 0F);
		part(r, "wool", 0, 32, -4F, -4F, -6F, 8, 7, 12, 0F, 15F, 0F, 1.5F);
		part(r, "wool_puff", 0, 32, -4F, -4F, -6F, 8, 7, 12, 0F, 15F, 0F, 4F);
		part(r, "leg_fr", 64, 13, -2F, 0F, -2F, 4, 6, 4, -2.5F, 18F, -4F);
		part(r, "leg_fl", 80, 13, -2F, 0F, -2F, 4, 6, 4, 2.5F, 18F, -4F);
		part(r, "leg_br", 96, 13, -2F, 0F, -2F, 4, 6, 4, -2.5F, 18F, 4F);
		part(r, "leg_bl", 112, 13, -2F, 0F, -2F, 4, 6, 4, 2.5F, 18F, 4F);
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(AetherAnimalRenderer.State state) {
		super.setupAnim(state);
		look(this.head, state);
		// grazing: the head goes down and chews
		if (state.eating > 0.0F) {
			this.head.xRot = Mth.lerp(state.eating, this.head.xRot, 1.0F + Mth.sin(state.ageInTicks * 0.9F) * 0.12F);
		}
		swing(this.legFr, this.legBl, state, 1.4F);
		swing(this.legFl, this.legBr, state, 1.4F);
		this.headWool.visible = !state.sheared;
		this.wool.visible = !state.sheared && !state.puffed;
		this.woolPuff.visible = state.puffed;
	}
}
