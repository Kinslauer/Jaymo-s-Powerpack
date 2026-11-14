package com.jaymospowers.client.power.arsenal;

import com.jaymospowers.power.arsenal.ArsenalRide;
import com.jaymospowers.power.arsenal.ArsenalVoid;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/**
 * Blade riding, seen by everyone: the great blade lying flat under its rider's feet, point first the way they face,
 * out of a rift that closes behind it. It shudders as it is about to go home ({@link ArsenalRide.Ride#shake}).
 */
final class ArsenalRideRenderer {
	private static final double VIEW_DISTANCE = 96.0;
	private static final float BLADE_SCALE = 1.9F;
	/** Under the soles. */
	private static final double BELOW = 0.06;
	private static final float RIFT_TICKS = 8.0F;
	private static final double RIFT_RADIUS = 0.9;
	private static final float SHAKE_DEGREES = 9.0F;
	private static final ItemStackRenderState ITEM = new ItemStackRenderState();

	private ArsenalRideRenderer() {
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		double now = level.getGameTime() + (double) partialTick;
		PoseStack poseStack = context.poseStack();
		for (Player player : level.players()) {
			ArsenalRide.Ride ride = player.getAttached(ArsenalRide.RIDE);
			if (ride == null || player.isInvisible() || player.position().distanceToSqr(camera) > VIEW_DISTANCE * VIEW_DISTANCE) {
				continue;
			}
			boolean voidRift = ArsenalVoid.isVoid(player);
			Vec3 at = player.getPosition(partialTick).subtract(camera).subtract(0.0, BELOW, 0.0);
			float out = (float) Math.clamp((now - ride.start()) / RIFT_TICKS, 0.0, 1.0);
			if (out < 1.0F) {
				// the rift it came up through, closing
				double r = RIFT_RADIUS * (1.0F - out);
				RiftPortals.disc(context.submitNodeCollector(), poseStack, at.subtract(0.0, 0.1, 0.0), new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0),
					new Vec3(0.0, 1.0, 0.0), r, r, now * 0.15, voidRift);
			}
			float shake = ride.shake(now);
			poseStack.pushPose();
			poseStack.translate(at.x, at.y, at.z);
			poseStack.rotateDegrees(Axis.YP, -Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot));
			if (shake > 0.0F) {
				poseStack.rotateDegrees(Axis.ZP, Mth.sin((float) now * 2.3F) * SHAKE_DEGREES * shake);
			}
			// flat, and (item sprites have their tip in the top-right corner) point first
			poseStack.rotateDegrees(Axis.XP, 90.0F);
			poseStack.rotateDegrees(Axis.ZP, 45.0F);
			float scale = BLADE_SCALE * (0.3F + 0.7F * out);
			poseStack.scale(scale, scale, scale);
			minecraft.getItemModelResolver().updateForTopItem(ITEM, ride.item(), ItemDisplayContext.NONE, level, null, player.getId());
			ITEM.submit(poseStack, context.submitNodeCollector(), voidRift ? RiftPortals.VOID_LIGHT : RiftPortals.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}
}
