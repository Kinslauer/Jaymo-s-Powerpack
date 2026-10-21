package com.jaymospowers.client.aether.monster;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** The aechor plant: a stem with a squat bud, six petals that droop at rest and flare when it is about to shoot, and two leaves. */
final class AechorPlantModel extends AetherMonsterModel {
	private final ModelPart bud, leafL, leafR;
	private final ModelPart[] petals = new ModelPart[6];

	private AechorPlantModel(ModelPart root) {
		super(root);
		this.bud = root.getChild("bud");
		this.leafL = root.getChild("leaf_l");
		this.leafR = root.getChild("leaf_r");
		for (int i = 0; i < 6; i++) {
			this.petals[i] = this.bud.getChild("petal_" + i);
		}
	}

	static AechorPlantModel create() {
		return new AechorPlantModel(layer().bakeRoot());
	}

	private static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		part(r, "stem", 0, 0, -1.0F, -6.0F, -1.0F, 2, 6, 2, 0.0F, 24.0F, 0.0F);
		PartDefinition bud = part(r, "bud", 8, 0, -3.0F, -5.0F, -3.0F, 6, 5, 6, 0.0F, 18.0F, 0.0F);
		part(bud, "petal_0", 32, 0, 0.0F, -0.5F, -2.0F, 5, 1, 4, 0.0F, -1.0F, 0.0F);
		part(bud, "petal_1", 0, 11, 0.0F, -0.5F, -2.0F, 5, 1, 4, 0.0F, -1.0F, 0.0F);
		part(bud, "petal_2", 0, 11, 0.0F, -0.5F, -2.0F, 5, 1, 4, 0.0F, -1.0F, 0.0F);
		part(bud, "petal_3", 0, 11, 0.0F, -0.5F, -2.0F, 5, 1, 4, 0.0F, -1.0F, 0.0F);
		part(bud, "petal_4", 0, 11, 0.0F, -0.5F, -2.0F, 5, 1, 4, 0.0F, -1.0F, 0.0F);
		part(bud, "petal_5", 0, 11, 0.0F, -0.5F, -2.0F, 5, 1, 4, 0.0F, -1.0F, 0.0F);
		part(r, "leaf_l", 18, 11, 0.0F, -0.5F, -1.5F, 4, 1, 3, 1.0F, 21.0F, 0.0F);
		part(r, "leaf_r", 32, 11, -4.0F, -0.5F, -1.5F, 4, 1, 3, -1.0F, 20.0F, 0.0F);
		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public void setupAnim(AetherMonsterRenderer.State state) {
		super.setupAnim(state);
		float t = state.ageInTicks;
		// it sways a little; the bud swells and the petals fling out before a shot
		this.root.zRot = Mth.sin(t * 0.07F) * 0.04F;
		float swell = state.charging ? 1.0F + 0.25F * Mth.sin(t * 1.2F) : 1.0F;
		this.bud.xScale = swell;
		this.bud.yScale = swell;
		this.bud.zScale = swell;
		float droop = state.charging ? -0.5F : 0.65F + Mth.sin(t * 0.09F) * 0.05F;
		for (int i = 0; i < 6; i++) {
			this.petals[i].yRot = i * Mth.PI / 3.0F;
			this.petals[i].zRot = droop;
		}
		this.leafL.zRot = 0.35F + Mth.sin(t * 0.1F) * 0.05F;
		this.leafR.zRot = -0.35F - Mth.sin(t * 0.1F + 1.0F) * 0.05F;
	}
}
