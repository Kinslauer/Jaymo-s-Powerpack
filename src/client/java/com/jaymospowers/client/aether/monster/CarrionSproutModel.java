package com.jaymospowers.client.aether.monster;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** The carrion sprout: a pale stalk with a bud and six curved leaves. By day the leaves hang loose; at night they spread flat, and they snap shut on what touches it. */
final class CarrionSproutModel extends AetherMonsterModel {
	private final ModelPart bud;
	private final ModelPart[] leaves = new ModelPart[6];
	private final ModelPart[] tips = new ModelPart[6];

	private CarrionSproutModel(ModelPart root) {
		super(root);
		this.bud = root.getChild("bud");
		for (int i = 0; i < 6; i++) {
			this.leaves[i] = root.getChild("leaf_" + i);
			this.tips[i] = this.leaves[i].getChild("tip_" + i);
		}
	}

	static CarrionSproutModel create() {
		return new CarrionSproutModel(layer().bakeRoot());
	}

	private static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		part(r, "stem", 0, 0, -1.5F, -8.0F, -1.5F, 3, 8, 3, 0.0F, 24.0F, 0.0F);
		part(r, "bud", 12, 0, -2.0F, -3.0F, -2.0F, 4, 3, 4, 0.0F, 16.0F, 0.0F);
		PartDefinition leaf_0 = part(r, "leaf_0", 28, 0, 0.0F, -0.5F, -1.5F, 6, 1, 3, 0.0F, 17.0F, 0.0F);
		part(leaf_0, "tip_0", 46, 0, 0.0F, -0.5F, -1.0F, 4, 1, 2, 6.0F, 0.0F, 0.0F);
		PartDefinition leaf_1 = part(r, "leaf_1", 0, 11, 0.0F, -0.5F, -1.5F, 6, 1, 3, 0.0F, 17.0F, 0.0F);
		part(leaf_1, "tip_1", 18, 11, 0.0F, -0.5F, -1.0F, 4, 1, 2, 6.0F, 0.0F, 0.0F);
		PartDefinition leaf_2 = part(r, "leaf_2", 0, 11, 0.0F, -0.5F, -1.5F, 6, 1, 3, 0.0F, 17.0F, 0.0F);
		part(leaf_2, "tip_2", 18, 11, 0.0F, -0.5F, -1.0F, 4, 1, 2, 6.0F, 0.0F, 0.0F);
		PartDefinition leaf_3 = part(r, "leaf_3", 0, 11, 0.0F, -0.5F, -1.5F, 6, 1, 3, 0.0F, 17.0F, 0.0F);
		part(leaf_3, "tip_3", 18, 11, 0.0F, -0.5F, -1.0F, 4, 1, 2, 6.0F, 0.0F, 0.0F);
		PartDefinition leaf_4 = part(r, "leaf_4", 0, 11, 0.0F, -0.5F, -1.5F, 6, 1, 3, 0.0F, 17.0F, 0.0F);
		part(leaf_4, "tip_4", 18, 11, 0.0F, -0.5F, -1.0F, 4, 1, 2, 6.0F, 0.0F, 0.0F);
		PartDefinition leaf_5 = part(r, "leaf_5", 0, 11, 0.0F, -0.5F, -1.5F, 6, 1, 3, 0.0F, 17.0F, 0.0F);
		part(leaf_5, "tip_5", 18, 11, 0.0F, -0.5F, -1.0F, 4, 1, 2, 6.0F, 0.0F, 0.0F);
		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public void setupAnim(AetherMonsterRenderer.State state) {
		super.setupAnim(state);
		float t = state.ageInTicks;
		// asleep: leaves droop; armed: spread nearly flat and trembling; snapping: whipped shut
		float spread = state.armed ? 0.1F + Mth.sin(t * 0.3F) * 0.04F : 0.9F;
		float tip = state.armed ? 0.25F : 0.5F;
		float snap = smooth(state.progress);
		spread = Mth.lerp(snap, spread, -1.2F);
		tip = Mth.lerp(snap, tip, -0.9F);
		for (int i = 0; i < 6; i++) {
			this.leaves[i].yRot = i * Mth.PI / 3.0F;
			this.leaves[i].zRot = spread;
			this.tips[i].zRot = tip;
		}
		this.bud.yRot = t * 0.02F;
		this.root.zRot = Mth.sin(t * 0.05F) * 0.03F;
	}
}
