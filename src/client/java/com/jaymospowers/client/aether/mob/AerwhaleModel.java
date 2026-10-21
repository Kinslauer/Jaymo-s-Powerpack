package com.jaymospowers.client.aether.mob;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** The aerwhale: a huge blunt head over a flat jaw plate, a deep body, a dorsal fin, a tail ending in a wide fluke and two long flippers; all of it sways slowly as it drifts. */
final class AerwhaleModel extends AetherAnimalModel {
	private final ModelPart tail, fluke, finL, finR, head;

	private AerwhaleModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.tail = root.getChild("tail");
		this.fluke = this.tail.getChild("fluke");
		this.finL = root.getChild("fin_l");
		this.finR = root.getChild("fin_r");
	}

	static AerwhaleModel create() {
		return new AerwhaleModel(layer().bakeRoot());
	}

	private static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		part(r, "body", 0, 45, -9.5F, -6F, -9F, 19, 12, 18, 0F, 0F, 0F);
		PartDefinition head = part(r, "head", 0, 0, -12.5F, -6.5F, -32F, 25, 13, 32, 0F, 0F, -9F);
		part(head, "jaw", 114, 0, -13.5F, 2.5F, -30F, 27, 9, 23, 0F, 0F, 0F);
		part(r, "dorsal", 74, 45, -1.5F, -14F, -3F, 3, 14, 6, 0F, -6F, 2F);
		PartDefinition tail = part(r, "tail", 92, 45, -1.5F, -7F, 0F, 3, 14, 6, 0F, 0F, 9F);
		part(tail, "fluke", 0, 75, -19.5F, -0.5F, 0F, 39, 1, 20, 0F, 0F, 6F);
		part(r, "fin_l", 118, 75, 0F, -0.5F, -8F, 27, 1, 17, 8F, 3F, -3F);
		part(r, "fin_r", 118, 93, -27F, -0.5F, -8F, 27, 1, 17, -8F, 3F, -3F);
		return LayerDefinition.create(mesh, 256, 128);
	}

	@Override
	public void setupAnim(AetherAnimalRenderer.State state) {
		super.setupAnim(state);
		float t = state.ageInTicks * 0.06F;
		this.head.xRot = state.xRot * DEG * 0.4F;
		this.head.yRot = state.yRot * DEG * 0.4F;
		this.tail.yRot = Mth.sin(t) * 0.2F;
		this.fluke.xRot = Mth.sin(t * 1.3F) * 0.15F;
		this.finL.zRot = 0.35F + Mth.sin(t * 1.5F) * 0.2F;
		this.finR.zRot = -0.35F - Mth.sin(t * 1.5F) * 0.2F;
	}
}
