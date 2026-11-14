package com.jaymospowers.client.power.arsenal;

import com.jaymospowers.power.arsenal.ArsenalStorm;
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
 * Blade Storm, seen by everyone: the rift behind its holder's shoulder, and every blade on its loop
 * ({@link ArsenalStorm#place}), point first along the way it flies. Echo blades are drawn smaller and unlit.
 */
final class ArsenalStormRenderer {
	private static final double VIEW_DISTANCE = 96.0;
	private static final double RIFT_RADIUS = 0.6;
	private static final float RIFT_OPEN_TICKS = 5.0F;
	private static final float BLADE_SCALE = 1.3F;
	private static final int ECHO_LIGHT = 0x600060;
	private static final ItemStackRenderState ITEM = new ItemStackRenderState();

	private ArsenalStormRenderer() {
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
			ArsenalStorm.Storm storm = player.getAttached(ArsenalStorm.STORM);
			if (storm == null || storm.over(now) || player.position().distanceToSqr(camera) > VIEW_DISTANCE * VIEW_DISTANCE) {
				continue;
			}
			boolean voidRift = ArsenalVoid.isVoid(player);
			// the rift: open while blades are leaving or still out, facing the way its holder looks
			double open = Math.min(1.0, (now - storm.start()) / RIFT_OPEN_TICKS);
			if (storm.end() != Long.MAX_VALUE) {
				open = Math.min(open, 1.0 - (now - storm.end() - ArsenalStorm.LOOP_TICKS) / RIFT_OPEN_TICKS);
			}
			if (open > 0.01) {
				float yaw = player.getViewYRot(partialTick) * Mth.DEG_TO_RAD;
				Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
				Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
				double r = RIFT_RADIUS * Math.min(1.0, open);
				RiftPortals.disc(context.submitNodeCollector(), poseStack, ArsenalStorm.rift(player, partialTick).subtract(camera), right,
					new Vec3(0.0, 1.0, 0.0), forward.scale(-1.0), r * 0.85, r * 1.1, now * 0.15, voidRift);
			}
			int count = storm.count(now);
			for (int i = Math.max(0, count - 1 - (int) (ArsenalStorm.LOOP_TICKS / storm.gap()) - 1); i < count; i++) {
				double along = storm.along(i, now);
				if (along < 0.0 || along > 1.0) {
					continue;
				}
				Vec3 at = ArsenalStorm.place(player, storm, i, along, partialTick);
				Vec3 dir = ArsenalStorm.place(player, storm, i, Math.min(1.0, along + 0.02), partialTick)
					.subtract(ArsenalStorm.place(player, storm, i, Math.max(0.0, along - 0.02), partialTick));
				if (dir.lengthSqr() < 1.0E-6) {
					continue;
				}
				float yRot = (float) (Mth.atan2(dir.x, dir.z) * Mth.RAD_TO_DEG);
				float xRot = (float) (Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG);
				boolean echo = storm.echo(i);
				// small as it leaves the rift and as it goes back in
				float size = (float) Math.min(1.0, Math.min(along, 1.0 - along) * 8.0);
				float scale = BLADE_SCALE * (0.3F + 0.7F * size) * (echo ? 0.8F : 1.0F);
				Vec3 rel = at.subtract(camera);
				poseStack.pushPose();
				poseStack.translate(rel.x, rel.y, rel.z);
				// item sprites have their tip in the top-right corner (as RiftBladeRenderer turns its blades)
				poseStack.rotateDegrees(Axis.YP, yRot - 90.0F);
				poseStack.rotateDegrees(Axis.ZP, xRot - 45.0F);
				poseStack.scale(scale, scale, scale);
				minecraft.getItemModelResolver().updateForTopItem(ITEM, storm.weapon(i), ItemDisplayContext.NONE, level, null, player.getId() + i);
				ITEM.submit(poseStack, context.submitNodeCollector(), voidRift ? RiftPortals.VOID_LIGHT : echo ? ECHO_LIGHT : RiftPortals.FULL_BRIGHT,
					OverlayTexture.NO_OVERLAY, 0);
				poseStack.popPose();
			}
		}
	}
}
