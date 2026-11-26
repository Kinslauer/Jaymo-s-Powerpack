package com.jaymospowers.client.blood;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

/**
 * Blood on your own screen: a big blow (a blade's or a heavy blunt one, {@code BladeBlood}) within {@link #RANGE} blocks of
 * you and in your view puts a few spatters on the screen, each running down it and fading over {@link #LIFE} milliseconds.
 * None when the blood setting is Off. More at a higher gore level; grey when streamer safe.
 */
public final class BloodScreen {
	/** How near the blow must be (blocks), and how wide of your view it can be (cosine of the angle from where you look). */
	static final double RANGE = 2.5, IN_VIEW = 0.35;
	/** How long a spatter lasts (milliseconds, at least this, up to this many more), its last share fading. */
	static final int LIFE = 1200, LIFE_SPREAD = 800;
	static final float FADE_SHARE = 0.45F;
	/** A blow's spatters: this many, plus this many per unit of its weight, at most this many (times the gore level's share). */
	static final int SPATTERS = 2, SPATTERS_MAX = 6;
	static final float PER_WEIGHT = 1.5F;
	/** A spatter's size (share of the screen's height), its opacity, how far its run goes (times its size) and how wide it is. */
	static final float MIN_SIZE = 0.03F, MAX_SIZE = 0.09F, ALPHA = 0.78F, RUN = 4.0F, RUN_WIDE = 0.28F;
	/** Spatters kept at most. */
	static final int MOST = 24;

	private record Spatter(float x, float y, float radius, int color, long born, int life, float run) {
	}

	private static final List<Spatter> LIVE = new ArrayList<>();

	private BloodScreen() {
	}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("blood_screen"), BloodScreen::draw);
	}

	/** A blow of {@code size} weight, spilling {@code color}, landed at {@code at}: spatters the screen if it is near and in view. */
	static void hit(Vec3 at, int color, float size) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || BloodSettings.off() || player.isSpectator()) {
			return;
		}
		Vec3 to = at.subtract(player.getEyePosition());
		double distance = to.length();
		if (distance > RANGE || distance > 0.3 && to.scale(1.0 / distance).dot(player.getLookAngle()) < IN_VIEW) {
			return;
		}
		var random = player.getRandom();
		int count = Math.min(SPATTERS_MAX, Math.round((SPATTERS + size * PER_WEIGHT) * BloodSettings.gore().share));
		long now = Util.getMillis();
		int rgb = BloodSettings.tint(color);
		for (int i = 0; i < count; i++) {
			float radius = Mth.lerp(random.nextFloat(), MIN_SIZE, MAX_SIZE) * (0.8F + 0.4F * Math.min(size, 2.0F));
			LIVE.add(new Spatter(0.1F + random.nextFloat() * 0.8F, 0.1F + random.nextFloat() * 0.6F, radius, rgb, now,
				LIFE + random.nextInt(LIFE_SPREAD), radius * RUN * (0.5F + random.nextFloat())));
		}
		while (LIVE.size() > MOST) {
			LIVE.remove(0);
		}
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (LIVE.isEmpty()) {
			return;
		}
		if (BloodSettings.off()) {
			LIVE.clear();
			return;
		}
		long now = Util.getMillis();
		LIVE.removeIf(s -> now - s.born >= s.life);
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		for (Spatter s : LIVE) {
			float t = (now - s.born) / (float) s.life;
			float alpha = ALPHA * (t < 1.0F - FADE_SHARE ? 1.0F : 1.0F - (t - (1.0F - FADE_SHARE)) / FADE_SHARE);
			if (alpha <= 0.02F) {
				continue;
			}
			int argb = ARGB.color(Math.round(alpha * 255.0F), s.color);
			int cx = Math.round(s.x * w), cy = Math.round(s.y * h), r = Math.max(2, Math.round(s.radius * h));
			// the blob: rows of a circle
			for (int dy = -r; dy <= r; dy++) {
				int half = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
				graphics.fill(cx - half, cy + dy, cx + half, cy + dy + 1, argb);
			}
			// the run: a thin streak down from it, growing the first part of its life
			int run = Math.round(s.run * h * Math.min(1.0F, t * 2.5F));
			int wide = Math.max(1, Math.round(r * RUN_WIDE));
			if (run > 0) {
				graphics.fill(cx - wide, cy, cx + wide, cy + r + run, argb);
			}
		}
	}
}
