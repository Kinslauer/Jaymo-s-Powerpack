package com.jaymospowers.client.power.chrono;

import com.jaymospowers.power.chrono.ChronoFlow;
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
 * (0.11.37) Time Flow's Space cue: a blow passed through an intangible Chrono. A red space bar flashes over their head
 * for everyone to see while it's open ({@link ChronoFlow#DODGE}), and their own screen's edges flash red, then glow in
 * time with the cue. The Space counter itself ({@link ChronoFlow#SPIN}) flashes the edges red again.
 */
final class ChronoSpaceCue {
	/** Red, and how strong the cue is at its brightest (alpha 0-255). */
	private static final int RGB = 0xFF3030;
	private static final int ALPHA = 220;
	/** The bar over the head: how far over it sits, its half width and half height (blocks), and the key's inner line. */
	private static final double LIFT = 0.45, HALF_WIDTH = 0.32, HALF_HEIGHT = 0.07, INNER = 0.18;
	/** It blinks every {@code BLINK_TICKS}, lit for the first part. */
	private static final float BLINK_TICKS = 5.0F, BLINK_LIT = 0.6F;
	/** The red edges: strongest alpha, the opening flare's length, what glows after, how deep, in how many steps. */
	private static final int EDGE_ALPHA = 190;
	private static final float EDGE_FLARE_TICKS = 7.0F, EDGE_GLOW = 0.3F, EDGE_DEPTH = 0.24F;
	private static final int EDGE_STEPS = 10;

	private ChronoSpaceCue() {
	}

	/** Ticks since the cue opened on {@code player}, or -1 with none open. */
	static float open(Player player, float partialTick) {
		Long until = player.getAttached(ChronoFlow.DODGE);
		if (until == null) {
			return -1.0F;
		}
		float t = player.level().getGameTime() - (until - ChronoFlow.DODGE_TICKS) + partialTick;
		return t < 0.0F || t > ChronoFlow.DODGE_TICKS ? -1.0F : t;
	}

	private static float blink(float t) {
		float phase = (t % BLINK_TICKS) / BLINK_TICKS;
		return phase > BLINK_LIT ? 0.35F : 1.0F;
	}

	/** Level render: the red space bar over every cued Chrono's head (not your own in first person). */
	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float width = minecraft.gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth * 2.0F;
		for (Player player : level.players()) {
			float t = open(player, partialTick);
			if (t < 0.0F || player.isInvisible() || player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
				continue;
			}
			int color = ARGB.color(Math.round(ALPHA * blink(t)), RGB);
			Vec3 head = player.getPosition(partialTick).add(0.0, player.getBbHeight() + LIFT, 0.0);
			Vec3 toCamera = camera.subtract(head);
			Vec3 flat = new Vec3(toCamera.x, 0.0, toCamera.z);
			Vec3 right = flat.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(-flat.z, 0.0, flat.x).normalize();
			Vec3 up = new Vec3(0.0, 1.0, 0.0);
			Vec3 c = head.subtract(camera);
			// a pop as it opens
			double grow = Math.min(1.0, 0.6 + t * 0.2);
			Vec3 w = right.scale(HALF_WIDTH * grow), h = up.scale(HALF_HEIGHT * grow);
			Vec3 a = c.subtract(w).subtract(h), b = c.add(w).subtract(h), d = c.add(w).add(h), e = c.subtract(w).add(h);
			Vec3 i0 = c.subtract(right.scale(INNER * grow)).subtract(h.scale(0.3)), i1 = c.add(right.scale(INNER * grow)).subtract(h.scale(0.3));
			PoseStack poseStack = context.poseStack();
			context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
				segment(buffer, pose, a, b, color, width);
				segment(buffer, pose, b, d, color, width);
				segment(buffer, pose, d, e, color, width);
				segment(buffer, pose, e, a, color, width);
				segment(buffer, pose, i0, i1, color, width);
			});
		}
	}

	private static void segment(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, int color, float width) {
		Vec3 n = b.subtract(a).normalize();
		buffer.addVertex(pose, (float)a.x, (float)a.y, (float)a.z).setColor(color).setNormal(pose, (float)n.x, (float)n.y, (float)n.z).setLineWidth(width);
		buffer.addVertex(pose, (float)b.x, (float)b.y, (float)b.z).setColor(color).setNormal(pose, (float)n.x, (float)n.y, (float)n.z).setLineWidth(width);
	}

	/** Your own screen: red edges as the cue opens (then a glow while it's open), and again as the Space counter goes. */
	static void renderHud(GuiGraphicsExtractor graphics, Player player) {
		float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float strength = 0.0F;
		float t = open(player, partialTick);
		if (t >= 0.0F) {
			float flare = t < EDGE_FLARE_TICKS ? 1.0F - t / EDGE_FLARE_TICKS : 0.0F;
			strength = Math.max(flare, EDGE_GLOW * blink(t));
		}
		ChronoFlow.Move move = player.getAttached(ChronoFlow.MOVE);
		ChronoFlow.Move mine = ChronoFlowClient.local;
		if (mine != null && mine.style() == ChronoFlow.SPIN && (move == null || mine.start() >= move.start())) {
			move = mine;
		}
		if (move != null && move.style() == ChronoFlow.SPIN) {
			float s = player.level().getGameTime() - move.start() + partialTick;
			if (s >= 0.0F && s < EDGE_FLARE_TICKS * 1.5F) {
				strength = Math.max(strength, 1.0F - s / (EDGE_FLARE_TICKS * 1.5F));
			}
		}
		edges(graphics, strength);
	}

	/** A red frame round the screen's edges, soft towards the middle, {@code strength} 0-1. */
	private static void edges(GuiGraphicsExtractor graphics, float strength) {
		int alpha = Math.round(EDGE_ALPHA * Mth.clamp(strength, 0.0F, 1.0F));
		if (alpha <= 2) {
			return;
		}
		int width = graphics.guiWidth(), height = graphics.guiHeight();
		int step = Math.max(1, (int)(Math.min(width, height) * EDGE_DEPTH) / EDGE_STEPS);
		for (int i = 0; i < EDGE_STEPS; i++) {
			int a = alpha * (EDGE_STEPS - i) * (EDGE_STEPS - i) / (EDGE_STEPS * EDGE_STEPS);
			if (a <= 0) {
				break;
			}
			int c = ARGB.color(a, RGB);
			int o = i * step;
			graphics.fill(o, o, width - o, o + step, c);
			graphics.fill(o, height - o - step, width - o, height - o, c);
			graphics.fill(o, o + step, o + step, height - o - step, c);
			graphics.fill(width - o - step, o + step, width - o, height - o - step, c);
		}
	}
}
