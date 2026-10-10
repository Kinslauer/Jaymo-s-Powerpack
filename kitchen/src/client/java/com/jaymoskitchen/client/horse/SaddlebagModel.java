package com.jaymoskitchen.client.horse;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.horse.SaddlebagContainer;
import net.minecraft.client.model.animal.equine.AbstractEquineModel;
import net.minecraft.client.model.animal.equine.HorseModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.EquineRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Saddlebags on a horse: a leather pouch hanging on each flank behind the saddle, joined by a strap over the back.
 * Built on the horse's own skeleton so it follows the body (rearing and so on). The pouches swing and bounce with
 * the horse's gait, and bulge as they fill up.
 */
public class SaddlebagModel extends HorseModel {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(JaymosKitchen.id("saddlebag"), "main");

	private final ModelPart left;
	private final ModelPart right;

	public SaddlebagModel(ModelPart root) {
		super(root);
		ModelPart body = root.getChild("body");
		this.left = body.getChild("left_bag");
		this.right = body.getChild("right_bag");
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = AbstractEquineModel.createBodyMesh(CubeDeformation.NONE);
		PartDefinition body = mesh.getRoot().clearRecursively().getChild("body");
		// the horse's back is at y = -8, its flanks at x = +-5; the saddle sits forward of z = -4
		body.addOrReplaceChild("strap",
			CubeListBuilder.create().texOffs(32, 0).addBox(-5.5F, -8.5F, -1.0F, 11.0F, 1.0F, 3.0F),
			PartPose.ZERO);
		body.addOrReplaceChild("left_bag", pouch(false), PartPose.offset(5.0F, -7.0F, 0.5F));
		body.addOrReplaceChild("right_bag", pouch(true), PartPose.offset(-5.0F, -7.0F, 0.5F));
		return LayerDefinition.create(mesh, 64, 32);
	}

	/** One pouch, hanging from its top edge at the pivot, with a flap and a brass buckle on the outside. */
	private static CubeListBuilder pouch(boolean right) {
		float x0 = right ? -3.5F : 0.0F;
		return CubeListBuilder.create()
			.texOffs(0, 0).addBox(x0, 0.0F, -3.0F, 3.5F, 7.0F, 6.0F)
			.texOffs(0, 16).addBox(x0 - 0.25F, -0.5F, -3.25F, 4.0F, 3.0F, 6.5F)
			.texOffs(32, 8).addBox(right ? -4.25F : 3.75F, 2.0F, -0.5F, 0.5F, 1.0F, 1.0F);
	}

	@Override
	public void setupAnim(EquineRenderState state) {
		super.setupAnim(state);
		float speed = Math.min(state.walkAnimationSpeed, 1.0F);
		float pos = state.walkAnimationPos;
		// bounce out from the flanks on each stride, and swing fore and aft
		float bounce = 0.04F + Math.abs(Mth.sin(pos * 0.6662F)) * 0.35F * speed;
		float sway = Mth.sin(pos * 0.3331F) * 0.3F * speed;
		left.zRot = -bounce;
		right.zRot = bounce;
		left.xRot = sway;
		right.xRot = -sway;

		// fuller bags bulge
		ItemContainerContents contents = state.bodyArmorItem.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		int used = 0;
		for (var ignored : contents.nonEmptyItems()) {
			used++;
		}
		float fill = Math.min(1.0F, used / (float) SaddlebagContainer.SIZE);
		for (ModelPart bag : new ModelPart[] {left, right}) {
			bag.xScale = 0.6F + 0.4F * fill;
			bag.yScale = 0.75F + 0.25F * fill;
			bag.zScale = 0.85F + 0.15F * fill;
		}
	}
}
