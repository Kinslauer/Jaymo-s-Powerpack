package com.jaymospowers.client.power.arsenal;

import com.jaymospowers.power.arsenal.ArsenalDamocles;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The Sword of Damocles, seen by everyone: the great rift in the sky (flat, facing down, the void's starfield) and the
 * giant sword pushing out of it, falling, standing in the ground, and hauled back up into it. The sword is a netherite sword's item model blown
 * up to {@link ArsenalDamocles#LENGTH} blocks, point down.
 */
final class ArsenalDamoclesRenderer {
	private static final double VIEW_DISTANCE = 192.0;
	private static final Vec3 RIGHT = new Vec3(1.0, 0.0, 0.0);
	private static final Vec3 FORWARD = new Vec3(0.0, 0.0, 1.0);
	private static final Vec3 DOWN = new Vec3(0.0, -1.0, 0.0);
	/** A sword sprite runs corner to corner: its length is this many times the model's size. */
	private static final double SPRITE_LENGTH = 1.4;
	/** Lit as if in dim light, so it reads as void-dark but keeps its shape; its glint shows. */
	private static final int SWORD_LIGHT = 0xA000A0;
	private static final ItemStackRenderState ITEM = new ItemStackRenderState();
	private static ItemStack sword;

	private ArsenalDamoclesRenderer() {
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
			ArsenalDamocles.Fall fall = player.getAttached(ArsenalDamocles.FALL);
			if (fall == null) {
				continue;
			}
			float t = level.getGameTime() - fall.start() + partialTick;
			if (t < 0.0F || t > fall.gone()) {
				continue;
			}
			Vec3 portal = new Vec3(fall.x(), fall.portalY(), fall.z());
			if (portal.distanceToSqr(camera) > VIEW_DISTANCE * VIEW_DISTANCE) {
				continue;
			}
			float open = ArsenalDamocles.openness(fall, t);
			if (open > 0.01F) {
				double r = ArsenalDamocles.PORTAL_RADIUS * open;
				RiftPortals.disc(context.submitNodeCollector(), context.poseStack(), portal.subtract(camera), RIGHT, FORWARD, DOWN, r, r, t * 0.05, true);
			}
			double length = ArsenalDamocles.shownLength(fall, t);
			if (length > 0.01) {
				// the hilt end stays at the rift while it pushes out or is hauled back in
				double tip = ArsenalDamocles.tipY(fall, t);
				sword(context, minecraft, new Vec3(fall.x(), tip + length * 0.5, fall.z()).subtract(camera), fall.angle(), length, player.getId());
			}
		}
	}

	/** The sword, point down, its flat turned {@code angle} about the vertical (the cross-guard along it). */
	private static void sword(LevelRenderContext context, Minecraft minecraft, Vec3 at, float angle, double length, int seed) {
		if (sword == null) {
			sword = ArsenalVoid.voidTouched(new ItemStack(Items.NETHERITE_SWORD));
		}
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(at.x, at.y, at.z);
		poseStack.rotateDegrees(Axis.YP, angle * Mth.RAD_TO_DEG);
		// the sprite's point is its top-right corner: turn it to straight down
		poseStack.rotateDegrees(Axis.ZP, -135.0F);
		float scale = (float) (length / SPRITE_LENGTH);
		poseStack.scale(scale, scale, scale);
		minecraft.getItemModelResolver().updateForTopItem(ITEM, sword, ItemDisplayContext.NONE, minecraft.level, null, seed);
		ITEM.submit(poseStack, context.submitNodeCollector(), SWORD_LIGHT, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
