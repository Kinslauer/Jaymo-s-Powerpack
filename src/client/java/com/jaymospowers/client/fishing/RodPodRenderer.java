package com.jaymospowers.client.fishing;

import com.jaymospowers.fishing.FishingBlocks;
import com.jaymospowers.fishing.RodPodEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a rod pod's rods, each resting on its rest and pointing out over the water, its line running from the tip to
 * where its bait lies; and the alarm's head lit red while it rings.
 */
public class RodPodRenderer implements BlockEntityRenderer<RodPodEntity, RodPodRenderer.State> {
	/** Where each rod rests across the pod (block units, facing north) and how steeply it points up. */
	private static final float[] REST_X = {0.22F, 0.5F, 0.78F};
	private static final float TILT = 35.0F, ROD_SCALE = 0.9F;
	private static final int LINE = 0xFFE8E8E8, ALARM = 0xFFFF3020;

	public static class State extends BlockEntityRenderState {
		final ItemStackRenderState[] rods = new ItemStackRenderState[RodPodEntity.RODS];
		final boolean[] has = new boolean[RodPodEntity.RODS];
		final @Nullable Vec3[] bait = new Vec3[RodPodEntity.RODS];
		Direction facing = Direction.NORTH;
		int ringing = -1;

		State() {
			for (int i = 0; i < rods.length; i++) {
				rods[i] = new ItemStackRenderState();
			}
		}
	}

	private final ItemModelResolver itemModels;

	public RodPodRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModels = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(RodPodEntity pod, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(pod, state, partialTicks, camera, breakProgress);
		state.facing = pod.getBlockState().getValue(FishingBlocks.FACING);
		state.ringing = pod.ringingSlot();
		for (int i = 0; i < RodPodEntity.RODS; i++) {
			ItemStack rod = pod.rod(i);
			state.has[i] = !rod.isEmpty();
			if (state.has[i]) {
				itemModels.updateForTopItem(state.rods[i], rod, ItemDisplayContext.FIXED, pod.getLevel(), null, i);
			}
			Vec3 at = pod.baitAt(i);
			state.bait[i] = at == null ? null : at.subtract(Vec3.atLowerCornerOf(pod.getBlockPos()));
		}
	}

	/** {@code x, z} in the pod's own frame (facing north) turned to where it faces. */
	private static Vec3 turn(double x, double y, double z, Direction facing) {
		return switch (facing) {
			case EAST -> new Vec3(1.0 - z, y, x);
			case SOUTH -> new Vec3(1.0 - x, y, 1.0 - z);
			case WEST -> new Vec3(z, y, 1.0 - x);
			default -> new Vec3(x, y, z);
		};
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		float yaw = -state.facing.toYRot() + 180.0F;
		for (int i = 0; i < RodPodEntity.RODS; i++) {
			if (!state.has[i]) {
				continue;
			}
			Vec3 rest = turn(REST_X[i], 0.7, 0.55, state.facing);
			poseStack.pushPose();
			poseStack.translate(rest.x, rest.y, rest.z);
			poseStack.rotateDegrees(Axis.YP, yaw);
			poseStack.rotateDegrees(Axis.XP, -TILT);
			poseStack.rotateDegrees(Axis.ZP, -45.0F);
			poseStack.scale(ROD_SCALE, ROD_SCALE, ROD_SCALE);
			state.rods[i].submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
			Vec3 bait = state.bait[i];
			if (bait != null) {
				// the tip: up and out along the rod
				Vec3 tip = turn(REST_X[i], 0.7 + Math.sin(Math.toRadians(TILT)) * 0.75, 0.55 - Math.cos(Math.toRadians(TILT)) * 0.75, state.facing);
				Vec3 dir = bait.subtract(tip).normalize();
				int color = state.ringing == i ? ALARM : LINE;
				collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
					int steps = 8;
					Vec3 last = tip;
					for (int s = 1; s <= steps; s++) {
						double t = s / (double) steps;
						// a little sag in the middle
						Vec3 p = tip.lerp(bait, t).add(0.0, -0.35 * Math.sin(Math.PI * t), 0.0);
						buffer.addVertex(pose, (float) last.x, (float) last.y, (float) last.z).setColor(color)
							.setNormal(pose, (float) dir.x, (float) dir.y, (float) dir.z).setLineWidth(1.5F);
						buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(color)
							.setNormal(pose, (float) dir.x, (float) dir.y, (float) dir.z).setLineWidth(1.5F);
						last = p;
					}
				});
			}
		}
		if (state.ringing >= 0) {
			// the alarm head under the ringing rod, lit
			Vec3 head = turn(REST_X[state.ringing], 0.72, 0.55, state.facing);
			collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
				for (int k = 0; k < 6; k++) {
					double a = k * Math.PI / 3.0;
					Vec3 d = new Vec3(Math.cos(a), 0.6, Math.sin(a)).scale(0.12);
					buffer.addVertex(pose, (float) head.x, (float) head.y, (float) head.z).setColor(ALARM).setNormal(pose, 0, 1, 0).setLineWidth(3.0F);
					buffer.addVertex(pose, (float) (head.x + d.x), (float) (head.y + d.y), (float) (head.z + d.z)).setColor(ALARM)
						.setNormal(pose, 0, 1, 0).setLineWidth(3.0F);
				}
			});
		}
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 64;
	}
}
