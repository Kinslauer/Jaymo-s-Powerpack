package com.jaymospowers.client.power.arsenal;

import com.jaymospowers.power.arsenal.ArsenalVoid;
import com.jaymospowers.power.arsenal.ArsenalWing;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/**
 * Attendant Blades, seen by everyone: each blade in its place round its holder ({@link ArsenalWing#slot}), hanging point
 * down from a small rift laid flat over it (no rift while the wing is steered or whirling: the blades are loose then). A blade slides down out of its rift as it arrives, and is not drawn while it
 * is away striking (that one is a {@code RiftBlade}).
 */
final class ArsenalWingRenderer {
	private static final double VIEW_DISTANCE = 64.0;
	private static final double RIFT_RADIUS = 0.2;
	/** How far over the middle of the blade its rift sits. */
	private static final double RIFT_ABOVE = 0.5;
	private static final float BLADE_SCALE = 0.8F;
	private static final ItemStackRenderState ITEM = new ItemStackRenderState();

	private ArsenalWingRenderer() {
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
			ArsenalWing.Wing wing = player.getAttached(ArsenalWing.WING);
			if (wing == null || player.isInvisible() || player.position().distanceToSqr(camera) > VIEW_DISTANCE * VIEW_DISTANCE) {
				continue;
			}
			boolean voidRift = ArsenalVoid.isVoid(player);
			float yaw = player.getViewYRot(partialTick);
			for (int i = 0; i < wing.blades().size(); i++) {
				float out = wing.out(i, now);
				if (out <= 0.0F) {
					continue;
				}
				float eased = 1.0F - (1.0F - out) * (1.0F - out);
				Vec3 at = ArsenalWing.slot(player, wing, i, now, partialTick).subtract(camera);
				double r = RIFT_RADIUS * Math.min(1.0F, out * 3.0F);
				if (wing.mode() == ArsenalWing.DOCKED) {
					RiftPortals.disc(context.submitNodeCollector(), poseStack, at.add(0.0, RIFT_ABOVE, 0.0), new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0),
						new Vec3(0.0, -1.0, 0.0), r, r, now * 0.15 + i, voidRift);
				}
				poseStack.pushPose();
				// down out of the rift, growing as it comes into this world
				poseStack.translate(at.x, at.y + RIFT_ABOVE * (1.0F - eased), at.z);
				poseStack.rotateDegrees(Axis.YP, -yaw);
				if (wing.mode() == ArsenalWing.STEER) {
					// flying where its holder looks: point first
					poseStack.rotateDegrees(Axis.XP, player.getViewXRot(partialTick) - 90.0F);
				}
				// item sprites have their tip in the top-right corner: turned so it points at the ground
				poseStack.rotateDegrees(Axis.ZP, -135.0F);
				float scale = BLADE_SCALE * (0.3F + 0.7F * eased);
				poseStack.scale(scale, scale, scale);
				minecraft.getItemModelResolver().updateForTopItem(ITEM, wing.blades().get(i).item(), ItemDisplayContext.FIXED, level, null, player.getId() + i);
				ITEM.submit(poseStack, context.submitNodeCollector(), voidRift ? RiftPortals.VOID_LIGHT : RiftPortals.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
				poseStack.popPose();
			}
		}
	}
}
