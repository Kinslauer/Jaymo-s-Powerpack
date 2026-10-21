package com.jaymospowers.client.aether.mob;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** The skyroot lizard: a flat little thing with wide frills at its sides, a long neck, a flat head and a three-part tail that sways as it scurries. */
final class SkyrootLizardModel extends AetherAnimalModel {
	private final ModelPart neck, head, frillL, frillR, tail1, tail2, tail3;

	private SkyrootLizardModel(ModelPart root) {
		super(root);
		this.neck = root.getChild("neck");
		this.head = this.neck.getChild("head");
		this.frillL = root.getChild("frill_l");
		this.frillR = root.getChild("frill_r");
		this.tail1 = root.getChild("tail_1");
		this.tail2 = this.tail1.getChild("tail_2");
		this.tail3 = this.tail2.getChild("tail_3");
	}

	static SkyrootLizardModel create() {
		return new SkyrootLizardModel(layer().bakeRoot());
	}

	private static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		part(r, "body", 0, 0, -1.5F, -1F, -2.5F, 3, 2, 5, 0F, 22F, 0F);
		part(r, "frill_l", 16, 0, 1.5F, -0.5F, -2.5F, 5, 1, 5, 0F, 22F, 0F);
		part(r, "frill_r", 36, 0, -6.5F, -0.5F, -2.5F, 5, 1, 5, 0F, 22F, 0F);
		PartDefinition neck = part(r, "neck", 0, 8, -1F, -1F, -6F, 2, 2, 6, 0F, 22F, -2.5F);
		PartDefinition head = part(neck, "head", 16, 8, -2.5F, -0.5F, -5F, 5, 1, 5, 0F, 0F, -6F);
		part(head, "snout", 36, 8, -2F, -1F, -4F, 4, 2, 4, 0F, 0F, -5F);
		PartDefinition tail1 = part(r, "tail_1", 0, 16, -1F, -1F, 0F, 2, 2, 6, 0F, 22F, 2.5F);
		PartDefinition tail2 = part(tail1, "tail_2", 16, 16, -0.5F, -0.5F, 0F, 1, 1, 5, 0F, 0F, 6F);
		part(tail2, "tail_3", 28, 16, -0.5F, -0.5F, 0F, 1, 1, 5, 0F, 0F, 5F);
		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public void setupAnim(AetherAnimalRenderer.State state) {
		super.setupAnim(state);
		float run = Math.min(state.walkAnimationSpeed, 1.0F);
		float t = state.walkAnimationPos * 1.2F;
		this.neck.yRot = Mth.sin(t) * 0.25F * run;
		this.head.yRot = state.yRot * DEG * 0.5F - this.neck.yRot * 0.5F;
		this.frillL.zRot = Mth.sin(t) * 0.25F * run;
		this.frillR.zRot = -Mth.sin(t) * 0.25F * run;
		this.tail1.yRot = -Mth.sin(t) * 0.35F * run;
		this.tail2.yRot = -Mth.sin(t - 0.8F) * 0.4F * run;
		this.tail3.yRot = -Mth.sin(t - 1.6F) * 0.4F * run;
	}
}
