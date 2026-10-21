package com.jaymospowers.client.aether.monster;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** The gravitite taluton: a core of rock in thick plates on short stone legs, with four stone chunks circling it; the plates push out as it charges a shot. */
final class GravititeTalutonModel extends AetherMonsterModel {
	private final ModelPart core, plateFront, plateBack, head, legL, legR, armL, armR;
	private final ModelPart[] chunks = new ModelPart[4];

	private GravititeTalutonModel(ModelPart root) {
		super(root);
		this.core = root.getChild("core");
		this.plateFront = this.core.getChild("plate_front");
		this.plateBack = this.core.getChild("plate_back");
		this.head = root.getChild("head");
		this.legL = root.getChild("leg_l");
		this.legR = root.getChild("leg_r");
		this.armL = root.getChild("arm_l");
		this.armR = root.getChild("arm_r");
		for (int i = 0; i < 4; i++) {
			this.chunks[i] = root.getChild("chunk_" + i);
		}
	}

	static GravititeTalutonModel create() {
		return new GravititeTalutonModel(layer().bakeRoot());
	}

	private static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		part(r, "leg_l", 0, 0, -2.0F, 0.0F, -2.0F, 4, 7, 4, 3.0F, 17.0F, 0.0F);
		part(r, "leg_r", 16, 0, -2.0F, 0.0F, -2.0F, 4, 7, 4, -3.0F, 17.0F, 0.0F);
		PartDefinition core = part(r, "core", 32, 0, -5.0F, -7.0F, -4.0F, 10, 14, 8, 0.0F, 10.0F, 0.0F);
		part(core, "plate_front", 68, 0, -6.0F, -8.0F, -2.0F, 12, 16, 3, 0.0F, 0.0F, -5.0F);
		part(core, "plate_back", 98, 0, -6.0F, -8.0F, -1.0F, 12, 16, 3, 0.0F, 0.0F, 5.0F);
		part(core, "shoulder_l", 0, 22, -3.0F, -3.0F, -3.0F, 6, 6, 6, 8.0F, -5.0F, 0.0F);
		part(core, "shoulder_r", 24, 22, -3.0F, -3.0F, -3.0F, 6, 6, 6, -8.0F, -5.0F, 0.0F);
		part(r, "arm_l", 48, 22, -2.0F, 0.0F, -2.0F, 4, 10, 4, 8.0F, 4.0F, 0.0F);
		part(r, "arm_r", 64, 22, -2.0F, 0.0F, -2.0F, 4, 10, 4, -8.0F, 4.0F, 0.0F);
		part(r, "head", 80, 22, -3.5F, -7.0F, -3.5F, 7, 7, 7, 0.0F, 3.0F, 0.0F);
		part(r, "chunk_0", 108, 22, -1.5F, -1.5F, -1.5F, 3, 3, 3, 0.0F, 6.0F, 0.0F);
		part(r, "chunk_1", 0, 36, -1.5F, -1.5F, -1.5F, 3, 3, 3, 0.0F, 6.0F, 0.0F);
		part(r, "chunk_2", 0, 36, -1.5F, -1.5F, -1.5F, 3, 3, 3, 0.0F, 6.0F, 0.0F);
		part(r, "chunk_3", 0, 36, -1.5F, -1.5F, -1.5F, 3, 3, 3, 0.0F, 6.0F, 0.0F);
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(AetherMonsterRenderer.State state) {
		super.setupAnim(state);
		float t = state.ageInTicks;
		swing(this.legL, this.legR, state, 0.6F);
		look(this.head, state);
		this.head.xRot *= 0.5F;
		this.head.yRot *= 0.5F;
		this.armL.xRot = Mth.sin(t * 0.05F) * 0.1F - 0.1F;
		this.armR.xRot = -Mth.sin(t * 0.05F) * 0.1F - 0.1F;
		this.armL.zRot = -0.15F;
		this.armR.zRot = 0.15F;
		// the plates float apart a little, and wide when it charges
		float gap = state.glowing ? 2.5F + Mth.sin(t * 1.0F) * 0.5F : 0.6F + Mth.sin(t * 0.08F) * 0.4F;
		this.plateFront.z -= gap;
		this.plateBack.z += gap;
		// the chunks circle it, faster when it charges
		float spin = t * (state.glowing ? 0.18F : 0.06F);
		for (int i = 0; i < 4; i++) {
			float a = spin + i * Mth.HALF_PI;
			this.chunks[i].x = Mth.cos(a) * 11.0F;
			this.chunks[i].z = Mth.sin(a) * 11.0F;
			this.chunks[i].y = 6.0F + Mth.sin(t * 0.1F + i * 1.7F) * 3.0F;
			this.chunks[i].xRot = a;
			this.chunks[i].yRot = a * 0.7F;
		}
	}
}
