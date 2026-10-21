package com.jaymospowers.client.aether.mob;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** The prismallard: a duck with a long neck, a broad bill, a fan of three bright tail feathers and folded wings that flap when it is afraid or falling. */
final class PrismallardModel extends AetherAnimalModel {
	private final ModelPart head, legL, legR, wingL, wingR, tail0, tail1, tail2;

	private PrismallardModel(ModelPart root) {
		super(root);
		this.head = root.getChild("neck").getChild("head");
		this.legL = root.getChild("leg_l");
		this.legR = root.getChild("leg_r");
		this.wingL = root.getChild("wing_l");
		this.wingR = root.getChild("wing_r");
		this.tail0 = root.getChild("tail_0");
		this.tail1 = root.getChild("tail_1");
		this.tail2 = root.getChild("tail_2");
	}

	static PrismallardModel create() {
		return new PrismallardModel(layer().bakeRoot());
	}

	private static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		part(r, "body", 0, 0, -2.5F, -2.5F, -3F, 5, 5, 6, 0F, 17F, 0F);
		PartDefinition neck = part(r, "neck", 22, 0, -1.5F, -4F, -1F, 3, 4, 2, 0F, 15F, -2.5F);
		PartDefinition head = part(neck, "head", 32, 0, -2.5F, -3F, -3F, 5, 4, 4, 0F, -4F, 0F);
		part(head, "bill", 0, 11, -2.5F, -1.5F, -6F, 5, 1, 3, 0F, 0F, 0F);
		part(head, "jaw", 16, 11, -1.5F, -0.5F, -5F, 3, 1, 3, 0F, 0F, 0F);
		PartDefinition legL = part(r, "leg_l", 28, 11, -1F, 0F, -1F, 2, 5, 2, 1.5F, 19F, 0F);
		PartDefinition legR = part(r, "leg_r", 36, 11, -1F, 0F, -1F, 2, 5, 2, -1.5F, 19F, 0F);
		part(legL, "foot_l", 0, 15, -1.5F, 4F, -3F, 3, 1, 3, 0F, 0F, 0F);
		part(legR, "foot_r", 12, 15, -1.5F, 4F, -3F, 3, 1, 3, 0F, 0F, 0F);
		part(r, "tail_0", 0, 20, -1F, -0.5F, 0F, 2, 1, 7, 0F, 16F, 3F);
		part(r, "tail_1", 18, 20, -1F, -0.5F, 0F, 2, 1, 7, 0F, 16F, 3F);
		part(r, "tail_2", 36, 20, -1F, -0.5F, 0F, 2, 1, 7, 0F, 16F, 3F);
		part(r, "wing_l", 44, 11, 0F, 0F, -2F, 1, 4, 5, 2.5F, 15F, -1F);
		part(r, "wing_r", 50, 0, -1F, 0F, -2F, 1, 4, 5, -2.5F, 15F, -1F);
		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public void setupAnim(AetherAnimalRenderer.State state) {
		super.setupAnim(state);
		look(this.head, state);
		swing(this.legL, this.legR, state, 1.0F);
		// the tail fans out
		this.tail0.xRot = 0.35F;
		this.tail1.xRot = 0.35F;
		this.tail2.xRot = 0.35F;
		this.tail0.yRot = -0.4F;
		this.tail2.yRot = 0.4F;
		// folded wings; they beat when it flaps
		float beat = Mth.sin(state.flap) * state.flapSpeed * 1.1F;
		this.wingL.zRot = -0.1F - beat;
		this.wingR.zRot = 0.1F + beat;
		if (state.flapSpeed > 0.3F) {
			this.legL.xRot += 0.6F;
			this.legR.xRot += 0.6F;
		}
	}
}
