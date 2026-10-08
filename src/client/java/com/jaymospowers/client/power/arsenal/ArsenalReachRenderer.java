package com.jaymospowers.client.power.arsenal;

import com.jaymospowers.power.arsenal.ArsenalReach;
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
 * Draws Reach Through for everyone in view: a rift (see {@link RiftPortals}; swirling portal light, or the void's
 * starfield when corrupted) torn open in front of the weapon hand; and, while it's being drawn out, the weapon itself
 * sliding out of the portal into the hand.
 */
final class ArsenalReachRenderer {
	private static final ItemStackRenderState ITEM = new ItemStackRenderState();

	private ArsenalReachRenderer() {
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		for (Player player : level.players()) {
			ArsenalReach.Reach reach = player.getAttached(ArsenalReach.REACH);
			if (reach == null) {
				continue;
			}
			float t = level.getGameTime() - reach.start() + partialTick;
			if (t < 0.0F || t > ArsenalReach.CLOSE_END) {
				continue;
			}
			Vec3 center = ArsenalReach.portalCenter(player, partialTick);
			if (center.distanceToSqr(camera) > 64.0 * 64.0) {
				continue;
			}
			float bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot);
			boolean voidRift = ArsenalVoid.isVoid(player);
			portal(context, center.subtract(camera), bodyYaw, ArsenalReach.openness(t), t, voidRift);
			if (t > ArsenalReach.REACH_END && t < ArsenalReach.PULL_END && !reach.weapon().isEmpty()) {
				emerging(context, minecraft, player, reach, center, camera, bodyYaw, t, partialTick);
			}
		}
	}

	/** The portal: a turning disc facing the player (the void's starfield when the arsenal is corrupted). */
	private static void portal(LevelRenderContext context, Vec3 at, float bodyYaw, float open, float t, boolean voidRift) {
		if (open <= 0.01F) {
			return;
		}
		Vec3 right = ArsenalReach.right(bodyYaw);
		Vec3 up = new Vec3(0.0, 1.0, 0.0);
		Vec3 normal = ArsenalReach.forward(bodyYaw).scale(-1.0);
		double radius = ArsenalReach.PORTAL_RADIUS * open;
		// a little taller than wide, like a tear in the air
		RiftPortals.disc(context.submitNodeCollector(), context.poseStack(), at, right, up, normal, radius * 0.85, radius * 1.1, t * 0.12, voidRift);
	}

	/** The weapon sliding out of the portal toward the hand, growing as it comes into this world. */
	private static void emerging(LevelRenderContext context, Minecraft minecraft, Player player, ArsenalReach.Reach reach, Vec3 center,
		Vec3 camera, float bodyYaw, float t, float partialTick) {
		float x = (t - ArsenalReach.REACH_END) / (ArsenalReach.PULL_END - ArsenalReach.REACH_END);
		float p = x * x * (3.0F - 2.0F * x);
		Vec3 forward = ArsenalReach.forward(bodyYaw);
		Vec3 start = center.add(forward.scale(0.15));
		Vec3 end;
		if (player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
			// first person: into the bottom corner of the screen, where the hand is
			Vec3 look = player.getViewVector(partialTick);
			Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0));
			side = side.lengthSqr() < 1.0E-4 ? ArsenalReach.right(bodyYaw) : side.normalize();
			end = player.getEyePosition(partialTick).add(look.scale(0.6)).add(side.scale(ArsenalReach.side(player) * 0.35)).add(0.0, -0.3, 0.0);
		} else {
			end = ArsenalArmPose.handPos(player, partialTick);
		}
		Vec3 at = start.lerp(end, p).subtract(camera);
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(at.x, at.y, at.z);
		poseStack.rotateDegrees(Axis.YP, -bodyYaw);
		// tumbling out of the portal, settling as it reaches the hand
		poseStack.rotateDegrees(Axis.XP, (1.0F - p) * 160.0F);
		float scale = 0.35F + 0.4F * p;
		poseStack.scale(scale, scale, scale);
		minecraft.getItemModelResolver().updateForTopItem(ITEM, reach.weapon(), ItemDisplayContext.FIXED, minecraft.level, null, player.getId());
		ITEM.submit(poseStack, context.submitNodeCollector(), ArsenalVoid.isVoid(player) ? RiftPortals.VOID_LIGHT : RiftPortals.FULL_BRIGHT,
			OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
