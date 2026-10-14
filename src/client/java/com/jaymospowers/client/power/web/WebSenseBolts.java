package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebCounter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Spider-Sense tingle: three faint lightning bolts that flash over a web slinger's head for as long as a dodge's
 * counter window is open ({@link WebCounter#sensing}). Everyone sees them over the head; in first person, your own
 * flash at the top of the screen instead.
 */
final class WebSenseBolts {
	/** Pale gold, and how faint it is at its brightest (alpha 0-255). */
	private static final int RGB = 0xFFF0B0;
	private static final int ALPHA = 150;
	/** The fan of bolts: tilt of the outer two from upright (degrees), how far over the head they start, how long they are. */
	private static final float SPREAD = 38.0F;
	private static final double LIFT = 0.25, LENGTH = 0.45, JAG = 0.07;
	/** Each bolt's kinks along it. */
	private static final int KINKS = 4;
	/** A flash every {@code FLASH_TICKS}: lit for the first part of it, dark for the rest. */
	private static final float FLASH_TICKS = 6.0F, FLASH_LIT = 0.55F;
	/** The screen version: how big a bolt is, and how far down from the top it sits (gui pixels). */
	private static final int HUD_SIZE = 10, HUD_TOP = 6, HUD_GAP = 14;

	private WebSenseBolts() {
	}

	/** How lit the bolts are {@code t} ticks into the window (0 between flashes), fading as the window runs out. */
	private static float flash(float t) {
		float phase = (t % FLASH_TICKS) / FLASH_TICKS;
		if (phase > FLASH_LIT) {
			return 0.0F;
		}
		float fade = 1.0F - t / WebCounter.WINDOW * 0.6F;
		return Mth.sin(phase / FLASH_LIT * Mth.PI) * fade;
	}

	/** Level render: the bolts over every tingling slinger's head (not your own in first person). */
	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float width = minecraft.gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth * 1.2F;
		for (Player player : level.players()) {
			float t = WebCounter.sensing(player, partialTick);
			if (t < 0.0F || player.isInvisible() || player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
				continue;
			}
			float lit = flash(t);
			if (lit <= 0.02F) {
				continue;
			}
			Vec3 head = player.getPosition(partialTick).add(0.0, player.getBbHeight() + LIFT, 0.0);
			Vec3 toCamera = camera.subtract(head);
			Vec3 flat = new Vec3(toCamera.x, 0.0, toCamera.z);
			Vec3 right = flat.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(-flat.z, 0.0, flat.x).normalize();
			int color = ARGB.color(Math.round(ALPHA * lit), RGB);
			Vec3 base = head.subtract(camera);
			PoseStack poseStack = context.poseStack();
			context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
				for (int b = -1; b <= 1; b++) {
					double angle = b * SPREAD * Mth.DEG_TO_RAD;
					Vec3 along = right.scale(Math.sin(angle)).add(0.0, Math.cos(angle), 0.0);
					Vec3 across = right.scale(Math.cos(angle)).add(0.0, -Math.sin(angle), 0.0);
					Vec3 prev = base.add(along.scale(0.05 + Math.abs(b) * 0.04));
					for (int k = 1; k <= KINKS; k++) {
						double side = k == KINKS ? 0.0 : (k % 2 == 1 ? JAG : -JAG);
						Vec3 next = base.add(along.scale(0.05 + Math.abs(b) * 0.04 + LENGTH * k / KINKS)).add(across.scale(side));
						segment(buffer, pose, prev, next, color, width);
						prev = next;
					}
				}
			});
		}
	}

	private static void segment(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, int color, float width) {
		Vec3 n = b.subtract(a).normalize();
		buffer.addVertex(pose, (float)a.x, (float)a.y, (float)a.z).setColor(color).setNormal(pose, (float)n.x, (float)n.y, (float)n.z).setLineWidth(width);
		buffer.addVertex(pose, (float)b.x, (float)b.y, (float)b.z).setColor(color).setNormal(pose, (float)n.x, (float)n.y, (float)n.z).setLineWidth(width);
	}

	/** First person: your own bolts, at the top of the screen. */
	static void renderHud(GuiGraphicsExtractor graphics, Player player) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!minecraft.options.getCameraType().isFirstPerson()) {
			return;
		}
		float t = WebCounter.sensing(player, minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		float lit = t < 0.0F ? 0.0F : flash(t);
		if (lit <= 0.02F) {
			return;
		}
		int color = ARGB.color(Math.round(ALPHA * lit), RGB);
		int cx = graphics.guiWidth() / 2;
		for (int b = -1; b <= 1; b++) {
			// a zigzag of pixels: down and to the side, kinking back, the outer two leaning out
			int x = cx + b * HUD_GAP;
			int y = HUD_TOP + Math.abs(b) * 3;
			for (int i = 0; i < HUD_SIZE; i++) {
				int kink = (i / 3) % 2 == 0 ? i % 3 : 2 - i % 3;
				int px = x + kink - 1 + b * i / 3;
				graphics.fill(px, y + i, px + 1, y + i + 1, color);
			}
		}
	}
}
