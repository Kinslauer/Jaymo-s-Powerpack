package com.jaymospowers.client.aether.monster;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** The tempest: a small beetle body, two long wing-case plates that spread open as it charges a thunderball, and short legs. */
final class TempestModel extends AetherMonsterModel {
	private final ModelPart head, plateL, plateR, legFl, legFr, legBl, legBr;

	private TempestModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.plateL = root.getChild("plate_l");
		this.plateR = root.getChild("plate_r");
		this.legFl = root.getChild("leg_fl");
		this.legFr = root.getChild("leg_fr");
		this.legBl = root.getChild("leg_bl");
		this.legBr = root.getChild("leg_br");
	}

	static TempestModel create() {
		return new TempestModel(layer().bakeRoot());
	}

	private static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		part(r, "body", 0, 0, -3.5F, -3.5F, -5.0F, 7, 7, 10, 0.0F, 13.0F, 0.0F);
		part(r, "head", 34, 0, -2.5F, -2.5F, -4.0F, 5, 5, 4, 0.0F, 12.0F, -5.0F);
		part(r, "plate_l", 0, 17, 0.0F, -1.0F, -6.0F, 3, 2, 16, 3.5F, 10.0F, 0.0F);
		part(r, "plate_r", 0, 35, -3.0F, -1.0F, -6.0F, 3, 2, 16, -3.5F, 10.0F, 0.0F);
		part(r, "leg_fl", 38, 35, -0.5F, 0.0F, -0.5F, 1, 5, 1, 2.5F, 16.0F, -3.0F);
		part(r, "leg_fr", 42, 35, -0.5F, 0.0F, -0.5F, 1, 5, 1, -2.5F, 16.0F, -3.0F);
		part(r, "leg_bl", 46, 35, -0.5F, 0.0F, -0.5F, 1, 5, 1, 2.5F, 16.0F, 3.0F);
		part(r, "leg_br", 50, 35, -0.5F, 0.0F, -0.5F, 1, 5, 1, -2.5F, 16.0F, 3.0F);
		part(r, "stinger", 0, 53, -1.0F, -1.0F, 0.0F, 2, 2, 4, 0.0F, 13.0F, 5.0F);
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(AetherMonsterRenderer.State state) {
		super.setupAnim(state);
		float t = state.ageInTicks;
		this.root.y += Mth.sin(t * 0.12F) * 1.0F;
		look(this.head, state);
		// the plates hum at rest and fling wide when it charges
		float buzz = Mth.sin(t * 1.6F) * 0.05F;
		float open = state.charging ? -0.85F : 0.12F;
		this.plateL.zRot = open + buzz;
		this.plateR.zRot = -open - buzz;
		float dangle = Mth.sin(t * 0.2F) * 0.15F;
		this.legFl.xRot = dangle;
		this.legFr.xRot = -dangle;
		this.legBl.xRot = -dangle;
		this.legBr.xRot = dangle;
	}
}
