package com.jaymoskitchen.client.rats;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.rats.SnapTrapEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a snap trap: the Rats mod's rat trap (LGPL-3.0; its model {@code RatTrapModel} and texture, texture 64x32, our
 * own code): a board, a spring, and the bar that swings over and slams down when it goes off, with the bait on it.
 */
public class SnapTrapRenderer implements BlockEntityRenderer<SnapTrapEntity, SnapTrapRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(JaymosKitchen.id("snap_trap"), "main");
	static final Identifier TEXTURE = JaymosKitchen.id("textures/block/snap_trap.png");

	public static class State extends BlockEntityRenderState {
		final ItemStackRenderState bait = new ItemStackRenderState();
		boolean baited;
		float yaw;
		float shut;
	}

	private final ModelPart bottom;
	private final ModelPart hinge;
	private final ItemModelResolver itemModels;

	public SnapTrapRenderer(BlockEntityRendererProvider.Context context) {
		ModelPart root = context.bakeLayer(LAYER);
		this.bottom = root.getChild("bottom");
		this.hinge = root.getChild("hinge_main");
		this.itemModels = context.itemModelResolver();
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition bottom = root.addOrReplaceChild("bottom", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -2.0F, -7.0F, 8, 2, 14),
			PartPose.offset(0.0F, 24.0F, 0.0F));
		bottom.addOrReplaceChild("spring", CubeListBuilder.create().texOffs(0, 16).addBox(-4.0F, -1.0F, 0.0F, 8, 1, 2),
			PartPose.offset(0.0F, -2.0F, 0.0F));
		PartDefinition hinge = root.addOrReplaceChild("hinge_main", CubeListBuilder.create().texOffs(0, 19).addBox(-0.5F, -0.5F, 0.0F, 1, 1, 7),
			PartPose.offset(4.5F, 22.0F, 0.0F));
		hinge.addOrReplaceChild("hinge_top", CubeListBuilder.create().texOffs(0, 27).addBox(-9.0F, -0.5F, 0.0F, 10, 1, 1),
			PartPose.offset(-0.5F, 0.0F, 7.0F));
		hinge.addOrReplaceChild("hinge_2", CubeListBuilder.create().texOffs(0, 19).addBox(-0.5F, -0.5F, 0.0F, 1, 1, 7),
			PartPose.offset(-9.0F, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SnapTrapEntity trap, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(trap, state, partialTicks, camera, breakProgress);
		ItemStack bait = trap.bait();
		state.baited = !bait.isEmpty();
		if (state.baited) {
			itemModels.updateForTopItem(state.bait, bait, ItemDisplayContext.FIXED, trap.getLevel(), null, 0);
		}
		state.yaw = trap.getBlockState().getValue(HorizontalDirectionalBlock.FACING).toYRot();
		state.shut = Mth.lerp(partialTicks, trap.shutProgressO, trap.shutProgress);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		// as the Rats mod draws it: model space flipped upright, turned to face the way it was set
		poseStack.translate(0.5F, 1.5F, 0.5F);
		poseStack.rotateDegrees(Axis.XP, 180.0F);
		poseStack.rotateDegrees(Axis.YP, state.yaw);
		var look = RenderTypes.entityCutout(TEXTURE);
		collector.submitModelPart(bottom, poseStack, look, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
		// the bar swings about its pivot (turned here, not on the shared part: parts are drawn after every trap is submitted)
		poseStack.pushPose();
		poseStack.translate(hinge.x / 16.0F, hinge.y / 16.0F, hinge.z / 16.0F);
		poseStack.rotate(Axis.XP, state.shut * Mth.PI / SnapTrapEntity.SHUT_FULL);
		poseStack.translate(-hinge.x / 16.0F, -hinge.y / 16.0F, -hinge.z / 16.0F);
		collector.submitModelPart(hinge, poseStack, look, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
		poseStack.popPose();
		if (state.baited) {
			poseStack.scale(0.4F, 0.4F, 0.4F);
			poseStack.translate(0.0F, 3.4F, -0.5F);
			poseStack.rotateDegrees(Axis.XP, 90.0F);
			poseStack.rotateDegrees(Axis.YP, 180.0F);
			state.bait.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		}
		poseStack.popPose();
	}
}
