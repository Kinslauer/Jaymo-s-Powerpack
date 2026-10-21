package com.jaymospowers.client.aether.monster;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** The zephyr: a long soft oval of a body, puffed cheeks, a hinged mouth and thin wisp wings; it bobs and beats them, and opens wide to blow. */
final class ZephyrModel extends AetherMonsterModel {
	private final ModelPart body, cheekL, cheekR, jawBottom, wingL, wingR, tailA, tailB;

	private ZephyrModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.cheekL = root.getChild("cheek_l");
		this.cheekR = root.getChild("cheek_r");
		this.jawBottom = root.getChild("jaw_bottom");
		this.wingL = root.getChild("wing_l");
		this.wingR = root.getChild("wing_r");
		this.tailA = root.getChild("tail_a");
		this.tailB = this.tailA.getChild("tail_b");
	}

	static ZephyrModel create() {
		return new ZephyrModel(layer().bakeRoot());
	}

	private static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		part(r, "body", 0, 0, -7.0F, -6.0F, -9.0F, 14, 12, 18, 0.0F, 10.0F, 0.0F);
		part(r, "crest", 64, 0, -3.0F, -3.0F, -5.0F, 6, 3, 10, 0.0F, 4.0F, 0.0F);
		part(r, "cheek_l", 96, 0, 0.0F, -2.0F, -2.0F, 4, 5, 4, 7.0F, 11.0F, -7.0F);
		part(r, "cheek_r", 112, 0, -4.0F, -2.0F, -2.0F, 4, 5, 4, -7.0F, 11.0F, -7.0F);
		part(r, "jaw_top", 0, 30, -3.0F, -2.0F, -4.0F, 6, 2, 4, 0.0F, 10.0F, -9.0F);
		part(r, "jaw_bottom", 20, 30, -3.0F, 0.0F, -4.0F, 6, 2, 4, 0.0F, 10.0F, -9.0F);
		part(r, "wing_l", 40, 30, 0.0F, -0.5F, -5.0F, 14, 1, 10, 7.0F, 6.0F, -1.0F);
		part(r, "wing_r", 0, 41, -14.0F, -0.5F, -5.0F, 14, 1, 10, -7.0F, 6.0F, -1.0F);
		PartDefinition tail_a = part(r, "tail_a", 48, 41, -3.0F, -2.5F, 0.0F, 6, 5, 8, 0.0F, 11.0F, 9.0F);
		part(tail_a, "tail_b", 76, 41, -2.0F, -1.5F, 0.0F, 4, 3, 8, 0.0F, 0.0F, 8.0F);
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(AetherMonsterRenderer.State state) {
		super.setupAnim(state);
		float t = state.ageInTicks;
		this.root.y += Mth.sin(t * 0.1F) * 1.2F;
		// the wings beat slowly and a little unevenly
		float flap = Mth.sin(t * 0.3F) * 0.45F;
		this.wingL.zRot = 0.1F + flap;
		this.wingR.zRot = -0.1F - flap;
		this.tailA.yRot = Mth.sin(t * 0.09F) * 0.2F;
		this.tailB.yRot = Mth.sin(t * 0.09F - 1.0F) * 0.3F;
		look(this.body, state);
		this.body.xRot *= 0.4F;
		this.body.yRot *= 0.4F;
		// blowing (1): mouth wide and cheeks swelled; spitting (2): mouth a little open
		float open = state.action == 1 ? 0.7F : state.action == 2 ? 0.35F : 0.05F;
		this.jawBottom.xRot = open;
		float puff = state.action == 1 ? 1.35F : 1.0F;
		this.cheekL.xScale = puff;
		this.cheekL.yScale = puff;
		this.cheekL.zScale = puff;
		this.cheekR.xScale = puff;
		this.cheekR.yScale = puff;
		this.cheekR.zScale = puff;
	}
}
