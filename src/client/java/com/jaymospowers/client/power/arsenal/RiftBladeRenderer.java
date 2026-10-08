package com.jaymospowers.client.power.arsenal;

import com.jaymospowers.power.arsenal.entity.RiftBlade;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a Barrage weapon as its item, pointing where it flies. Item sprites have their tip in the top-right corner, so
 * after turning to the flight direction (as arrows do: local +X forward) the sprite is rolled 45 degrees.
 */
public class RiftBladeRenderer extends EntityRenderer<RiftBlade, RiftBladeRenderer.State> {
	public static class State extends ThrownItemRenderState {
		float yRot;
		float xRot;
		boolean hovering;
		boolean voidBlade;
		boolean skyBlade;
	}

	/** A Barrage blade's rift: this big, this far behind the blade, open over this many ticks. */
	private static final double RIFT_RADIUS = 0.5;
	private static final double RIFT_BEHIND = 0.45;
	private static final float RIFT_OPEN_TICKS = 5.0F;

	private final ItemModelResolver itemModelResolver;

	public RiftBladeRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModelResolver = context.getItemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(RiftBlade entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yRot = entity.getYRot(partialTicks);
		state.xRot = entity.getXRot(partialTicks);
		state.hovering = entity.isHovering();
		state.voidBlade = entity.isVoid();
		state.skyBlade = entity.isSkyBlade();
		this.itemModelResolver.updateForNonLiving(state.item, entity.getItem(), ItemDisplayContext.NONE, entity);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.hovering && !state.skyBlade) {
			// the Barrage rift it hangs in (the sky rifts of Hundred Blades are drawn on their own)
			float yaw = state.yRot * Mth.DEG_TO_RAD;
			float pitch = state.xRot * Mth.DEG_TO_RAD;
			Vec3 dir = new Vec3(Mth.sin(yaw) * Mth.cos(pitch), Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
			Vec3 right = dir.cross(new Vec3(0.0, 1.0, 0.0));
			right = right.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
			Vec3 up = right.cross(dir).normalize();
			double open = Math.min(1.0, state.ageInTicks / RIFT_OPEN_TICKS);
			double r = RIFT_RADIUS * (1.0 - Math.pow(1.0 - open, 3.0));
			RiftPortals.disc(collector, poseStack, dir.scale(-RIFT_BEHIND), right, up, dir.scale(-1.0), r, r, state.ageInTicks * 0.15, state.voidBlade);
		}
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, state.yRot - 90.0F);
		poseStack.rotateDegrees(Axis.ZP, state.xRot - 45.0F);
		poseStack.scale(1.4F, 1.4F, 1.4F);
		state.item.submit(poseStack, collector, state.voidBlade ? RiftPortals.VOID_LIGHT : state.lightCoords, OverlayTexture.NO_OVERLAY,
			state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	protected int getBlockLightLevel(RiftBlade entity, BlockPos pos) {
		return Math.max(10, super.getBlockLightLevel(entity, pos)); // faintly lit by the rift
	}

	@Override
	protected AABB getBoundingBoxForCulling(RiftBlade entity, float partialTicks) {
		return super.getBoundingBoxForCulling(entity, partialTicks).inflate(1.0);
	}
}
