package com.jaymospowers.client.aether.monster;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** The swet: a 16-pixel gel cube round a 12-pixel core with a face on it. The skin is see-through; the core wobbles as it hops. */
final class SwetModel extends AetherMonsterModel {
	private final ModelPart core;

	private SwetModel(ModelPart root) {
		super(root);
		this.core = root.getChild("core");
	}

	static SwetModel create() {
		return new SwetModel(layer().bakeRoot());
	}

	private static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		part(r, "shell", 0, 0, -8.0F, -16.0F, -8.0F, 16, 16, 16, 0.0F, 24.0F, 0.0F);
		part(r, "core", 0, 32, -6.0F, -14.0F, -6.0F, 12, 12, 12, 0.0F, 24.0F, 0.0F);
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(AetherMonsterRenderer.State state) {
		super.setupAnim(state);
		float t = state.ageInTicks;
		this.core.yRot = Mth.sin(t * 0.17F) * 0.12F + state.yRot * DEG * 0.2F;
		this.core.xRot = Mth.sin(t * 0.13F) * 0.05F;
	}
}
