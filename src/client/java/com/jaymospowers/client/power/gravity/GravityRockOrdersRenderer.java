package com.jaymospowers.client.power.gravity;

import com.jaymospowers.power.arsenal.ArsenalStorm;
import com.jaymospowers.power.gravity.GravityRockOrders;
import com.jaymospowers.power.gravity.GravityRocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * A gravity user's rocks away on an order ({@link GravityRockOrders}), seen by everyone: steered to where their holder
 * looks, ringing what they hold, or looping through the holder's aim in a stone storm. Each rock is the item the ball
 * is made of, tumbling, lit by where it is.
 */
final class GravityRockOrdersRenderer {
	private static final double VIEW_DISTANCE = 96.0;
	private static final float ROCK_SCALE = 0.45F;
	private static final ItemStackRenderState ITEM = new ItemStackRenderState();

	private GravityRockOrdersRenderer() {
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
			GravityRockOrders.Order order = player.getAttached(GravityRockOrders.ORDER);
			GravityRocks.Rocks rocks = player.getAttached(GravityRocks.ROCKS);
			if (order == null || rocks == null || player.position().distanceToSqr(camera) > VIEW_DISTANCE * VIEW_DISTANCE) {
				continue;
			}
			ItemStack stone = rocks.stack();
			if (stone.isEmpty()) {
				continue;
			}
			if (order.kind() == GravityRockOrders.STORM) {
				ArsenalStorm.Storm storm = order.storm();
				int count = storm.count(now);
				for (int i = Math.max(0, count - 1 - (int) (ArsenalStorm.LOOP_TICKS / GravityRockOrders.STORM_GAP) - 1); i < count; i++) {
					double along = storm.along(i, now);
					if (along >= 0.0 && along <= 1.0) {
						rock(minecraft, context, poseStack, level, stone, ArsenalStorm.place(player, storm, i, along, partialTick), camera, now, player.getId() + i);
					}
				}
				continue;
			}
			int n = Math.min(rocks.count(), GravityRockOrders.DRAWN);
			for (int i = 0; i < n; i++) {
				Vec3 at = GravityRockOrders.place(player, order, i, n, now, partialTick);
				if (at != null) {
					rock(minecraft, context, poseStack, level, stone, at, camera, now, player.getId() + i);
				}
			}
		}
	}

	private static void rock(Minecraft minecraft, LevelRenderContext context, PoseStack poseStack, ClientLevel level, ItemStack stone, Vec3 at, Vec3 camera,
		double now, int seed) {
		Vec3 rel = at.subtract(camera);
		poseStack.pushPose();
		poseStack.translate(rel.x, rel.y, rel.z);
		poseStack.rotateDegrees(Axis.YP, (float) (now * 9.0 + seed * 41.0));
		poseStack.rotateDegrees(Axis.XP, (float) (now * 6.0 + seed * 67.0));
		poseStack.scale(ROCK_SCALE, ROCK_SCALE, ROCK_SCALE);
		minecraft.getItemModelResolver().updateForTopItem(ITEM, stone, ItemDisplayContext.NONE, level, null, seed);
		ITEM.submit(poseStack, context.submitNodeCollector(), LightCoordsUtil.getLightCoords(level, BlockPos.containing(at)), OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
