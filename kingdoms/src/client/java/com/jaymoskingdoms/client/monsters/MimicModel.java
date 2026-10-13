package com.jaymoskingdoms.client.monsters;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * (0.5.56) The chest mimic (our own): a chest, lid and lock, shut and still. Awake, the lid gapes on rows of teeth over a
 * lolling tongue, snaps as it bites and flaps as it bounds along. Texture 128x64.
 */
final class MimicModel extends EntityModel<LegendState> {
	/** How wide it gapes awake (radians), how much wider to bite, how high it bounds (pixels). */
	private static final float GAPE = 0.7F, BITE = 0.5F, BOUND = 4.0F;
	private static final float BITE_TICKS = 10.0F;

	private final ModelPart body, lid, upperTeeth, lowerTeeth, tongue;

	MimicModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.lid = this.body.getChild("lid");
		this.upperTeeth = this.lid.getChild("upper_teeth");
		this.lowerTeeth = this.body.getChild("lower_teeth");
		this.tongue = this.body.getChild("tongue");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 19).addBox(-7, -10, -7, 14, 10, 14), PartPose.offset(0, 24, 0));
		PartDefinition lid = body.addOrReplaceChild("lid", CubeListBuilder.create().texOffs(0, 0).addBox(-7, -5, -14, 14, 5, 14)
			.texOffs(56, 0).addBox(-1, -2, -15, 2, 4, 1), PartPose.offset(0, -10, 7));
		lid.addOrReplaceChild("upper_teeth", CubeListBuilder.create().texOffs(64, 0).addBox(-6, 0, -13.5F, 12, 2, 1)
			.texOffs(64, 8).addBox(-6.5F, 0, -13, 1, 2, 11).texOffs(64, 8).mirror().addBox(5.5F, 0, -13, 1, 2, 11), PartPose.ZERO);
		body.addOrReplaceChild("lower_teeth", CubeListBuilder.create().texOffs(64, 4).addBox(-6, -12, -6.5F, 12, 2, 1)
			.texOffs(90, 8).addBox(-6.5F, -12, -6, 1, 2, 11).texOffs(90, 8).mirror().addBox(5.5F, -12, -6, 1, 2, 11), PartPose.ZERO);
		body.addOrReplaceChild("tongue", CubeListBuilder.create().texOffs(64, 24).addBox(-2, -1, -8, 4, 1, 8), PartPose.offset(0, -10, 3));
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(LegendState state) {
		super.setupAnim(state);
		boolean open = !state.shut;
		this.upperTeeth.visible = this.lowerTeeth.visible = this.tongue.visible = open;
		if (!open) {
			return;
		}
		float bite = Math.max(state.leftAttack, state.rightAttack);
		float snap = bite > 0.0F ? Mth.sin(Math.min(bite / BITE_TICKS, 1.0F) * Mth.PI) : 0.0F;
		float hop = Math.abs(Mth.sin(state.walkAnimationPos * 0.6F)) * state.walkAnimationSpeed;
		// gapes, flaps with each bound, opens wide and slams shut to bite
		this.lid.xRot = -(GAPE + hop * 0.4F + snap * BITE - snap * snap * (GAPE + BITE)) + Mth.sin(state.ageInTicks * 0.15F) * 0.05F;
		this.body.y -= hop * BOUND;
		this.body.xRot = -hop * 0.15F;
		this.tongue.xRot = -0.1F + Mth.sin(state.ageInTicks * 0.2F) * 0.15F;
		this.tongue.yRot = Mth.sin(state.ageInTicks * 0.13F) * 0.3F;
	}
}
