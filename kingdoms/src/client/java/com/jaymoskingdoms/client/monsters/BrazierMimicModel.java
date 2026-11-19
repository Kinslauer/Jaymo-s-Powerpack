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
 * The living brazier (our own): a stone-footed iron bowl of fire on a pillar, shut and still. Awake, its rim bares teeth,
 * two legs show under its foot, it hops along and its fire flares. Texture 64x64 (tools/gen_labyrinth_craft.py).
 */
final class BrazierMimicModel extends EntityModel<LegendState> {
	/** How high it hops awake (pixels), and how far its feet lift it. */
	private static final float HOP = 4.0F, STAND = 4.0F;

	private final ModelPart body, bowl, fire, teeth, legs;

	BrazierMimicModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.bowl = this.body.getChild("bowl");
		this.fire = this.bowl.getChild("fire");
		this.teeth = this.bowl.getChild("teeth");
		this.legs = this.body.getChild("legs");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-6, -3, -6, 12, 3, 12)
			.texOffs(0, 16).addBox(-3, -11, -3, 6, 8, 6), PartPose.offset(0, 24, 0));
		PartDefinition bowl = body.addOrReplaceChild("bowl", CubeListBuilder.create().texOffs(0, 32).addBox(-7, -5, -7, 14, 5, 14), PartPose.offset(0, -11, 0));
		bowl.addOrReplaceChild("fire", CubeListBuilder.create().texOffs(24, 16).addBox(-4, -6, -4, 8, 6, 8), PartPose.offset(0, -5, 0));
		bowl.addOrReplaceChild("teeth", CubeListBuilder.create()
			.texOffs(0, 52).addBox(-7, -8, -7, 2, 3, 2).texOffs(0, 52).addBox(5, -8, -7, 2, 3, 2)
			.texOffs(0, 52).addBox(-7, -8, 5, 2, 3, 2).texOffs(0, 52).addBox(5, -8, 5, 2, 3, 2)
			.texOffs(0, 52).addBox(-1, -8, -7, 2, 3, 2).texOffs(0, 52).addBox(-1, -8, 5, 2, 3, 2)
			.texOffs(0, 52).addBox(-7, -8, -1, 2, 3, 2).texOffs(0, 52).addBox(5, -8, -1, 2, 3, 2), PartPose.ZERO);
		body.addOrReplaceChild("legs", CubeListBuilder.create().texOffs(10, 52).addBox(-5, 0, -1.5F, 3, 4, 3).texOffs(10, 52).addBox(2, 0, -1.5F, 3, 4, 3),
			PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(LegendState state) {
		super.setupAnim(state);
		boolean open = !state.shut;
		this.teeth.visible = this.legs.visible = open;
		float flicker = Mth.sin(state.ageInTicks * 0.4F) * 0.08F;
		this.fire.yScale = open ? 1.3F + flicker * 2.0F : 1.0F + flicker;
		if (!open) {
			return;
		}
		float hop = Math.abs(Mth.sin(state.walkAnimationPos * 0.6F)) * state.walkAnimationSpeed;
		this.body.y -= STAND + hop * HOP;
		this.body.xRot = -hop * 0.15F;
		this.bowl.xRot = -0.15F + Mth.sin(state.ageInTicks * 0.15F) * 0.05F;
	}
}
