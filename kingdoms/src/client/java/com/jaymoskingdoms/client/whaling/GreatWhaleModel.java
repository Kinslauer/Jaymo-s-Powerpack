package com.jaymoskingdoms.client.whaling;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * (0.7.206) The great whale, our own model: a long body, a blunt head with its jaw, swept-back flippers, and a tail
 * stock, tail and flukes that beat up and down as it swims. Built at a quarter of its size and drawn
 * {@link GreatWhaleRenderer#SCALE}x; {@code tools/gen_whaling.py} paints its skins to these boxes' UVs.
 */
public final class GreatWhaleModel extends EntityModel<GreatWhaleRenderer.State> {
	private final ModelPart body, stock, tail, flukes, leftFin, rightFin, jaw;

	public GreatWhaleModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.stock = this.body.getChild("stock");
		this.tail = this.stock.getChild("tail");
		this.flukes = this.tail.getChild("flukes");
		this.leftFin = this.body.getChild("left_fin");
		this.rightFin = this.body.getChild("right_fin");
		this.jaw = this.body.getChild("head").getChild("jaw");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -6.0F, -11.0F, 12.0F, 12.0F, 22.0F),
			PartPose.offset(0.0F, 18.0F, 0.0F));
		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 34).addBox(-5.0F, -5.0F, -15.0F, 10.0F, 8.0F, 15.0F),
			PartPose.offset(0.0F, 0.0F, -11.0F));
		head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 60).addBox(-4.5F, 0.0F, -14.0F, 9.0F, 3.0F, 14.0F),
			PartPose.offset(0.0F, 3.0F, -0.5F));
		body.addOrReplaceChild("left_fin", CubeListBuilder.create().texOffs(56, 46).addBox(0.0F, -1.0F, -2.5F, 10.0F, 2.0F, 5.0F),
			PartPose.offsetAndRotation(5.5F, 4.0F, -6.0F, 0.0F, -0.35F, 0.55F));
		body.addOrReplaceChild("right_fin", CubeListBuilder.create().texOffs(56, 46).mirror().addBox(-10.0F, -1.0F, -2.5F, 10.0F, 2.0F, 5.0F),
			PartPose.offsetAndRotation(-5.5F, 4.0F, -6.0F, 0.0F, 0.35F, -0.55F));
		PartDefinition stock = body.addOrReplaceChild("stock", CubeListBuilder.create().texOffs(68, 0).addBox(-4.0F, -4.0F, 0.0F, 8.0F, 8.0F, 12.0F),
			PartPose.offset(0.0F, -1.0F, 11.0F));
		stock.addOrReplaceChild("dorsal", CubeListBuilder.create().texOffs(90, 46).addBox(-0.5F, -3.0F, 0.0F, 1.0F, 3.0F, 4.0F),
			PartPose.offsetAndRotation(0.0F, -4.0F, 3.0F, -0.3F, 0.0F, 0.0F));
		PartDefinition tail = stock.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(68, 20).addBox(-2.5F, -2.5F, 0.0F, 5.0F, 5.0F, 10.0F),
			PartPose.offset(0.0F, 0.5F, 11.5F));
		tail.addOrReplaceChild("flukes", CubeListBuilder.create().texOffs(56, 36).addBox(-10.0F, -1.0F, 0.0F, 20.0F, 2.0F, 7.0F),
			PartPose.offset(0.0F, 0.0F, 9.0F));
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(GreatWhaleRenderer.State state) {
		super.setupAnim(state);
		float t = state.ageInTicks * 0.1F;
		float beat = state.carcass ? 0.0F : 0.12F + Math.min(1.0F, state.walkAnimationSpeed * 4.0F) * 0.2F;
		this.body.xRot = state.carcass ? 0.0F : state.xRot * Mth.DEG_TO_RAD;
		this.stock.xRot = Mth.sin(t) * beat * 0.6F;
		this.tail.xRot = Mth.sin(t - 0.8F) * beat;
		this.flukes.xRot = Mth.sin(t - 1.6F) * beat * 1.4F;
		float flap = state.carcass ? 0.35F : Mth.sin(t * 0.5F) * 0.08F;
		this.leftFin.zRot = 0.55F + flap;
		this.rightFin.zRot = -0.55F - flap;
		this.jaw.xRot = state.carcass ? 0.25F : 0.0F;
	}
}
