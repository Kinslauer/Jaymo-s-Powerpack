package com.jaymospowers.client.hud;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

/**
 * The dread vignette: the edges of the screen darken when something far stronger than you is near. A "far stronger
 * thing" is a hostile living thing whose max health is at least {@link #MIN_HEALTH} and {@link #RATIO} times yours (a
 * great foe, an ogre, a boss), within {@link #RANGE} blocks. It comes in with how close the nearest is, and eases in
 * and out so it never snaps. A full-screen wash, so it keeps out of the HUD's zones but hides when a film hides the
 * HUD ({@link HudZones#hidden}).
 */
public final class DreadVignette {
	/** How far off it feels something (blocks), and the nearest it can be before it's at full strength. */
	static final double RANGE = 40.0, NEAR = 10.0;
	/** What counts as far stronger: at least this much max health, and this many times the player's. */
	static final float MIN_HEALTH = 80.0F, RATIO = 4.0F;
	/** The most the edges darken (alpha 0-255), how deep they reach (share of the screen's height) and the colour. */
	static final int MAX_ALPHA = 170;
	static final float EDGE_SHARE = 0.38F;
	static final int COLOUR = 0x0A0008;
	/** Frames between looks round the player, and how fast the strength eases (share of the gap closed per second). */
	static final int LOOK_EVERY = 20;
	static final float EASE_IN = 3.5F, EASE_OUT = 2.0F;
	/** Edge steps drawn (more is smoother). */
	static final int BANDS = 6;

	private static float target, strength;
	private static long last;
	private static int ticks;

	private DreadVignette() {
	}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("dread_vignette"), DreadVignette::draw);
	}

	/** How strong the dread is for the nearest far-stronger hostile round {@code player}, 0 to 1. */
	private static float look(Minecraft minecraft, LocalPlayer player) {
		float mine = player.getMaxHealth();
		double nearest = Double.MAX_VALUE;
		AABB box = player.getBoundingBox().inflate(RANGE);
		for (LivingEntity e : minecraft.level.getEntitiesOfClass(LivingEntity.class, box)) {
			if (e != player && e.isAlive() && e instanceof Enemy && e.getMaxHealth() >= MIN_HEALTH && e.getMaxHealth() >= RATIO * mine) {
				nearest = Math.min(nearest, e.distanceTo(player));
			}
		}
		return nearest > RANGE ? 0.0F : (float)Mth.clamp((RANGE - nearest) / (RANGE - NEAR), 0.0, 1.0);
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		long now = Util.getMillis();
		float dt = Math.min(0.1F, (now - last) / 1000.0F);
		last = now;
		if (player == null || minecraft.level == null || player.isSpectator() || HudZones.hidden()) {
			target = 0.0F;
			strength = 0.0F;
			return;
		}
		if (ticks++ % LOOK_EVERY == 0) {
			target = look(minecraft, player);
		}
		strength += (target - strength) * Math.min(1.0F, (target > strength ? EASE_IN : EASE_OUT) * dt);
		int alpha = Math.round(MAX_ALPHA * strength);
		if (alpha < 4) {
			return;
		}
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		int edge = Math.round(h * EDGE_SHARE * (0.5F + 0.5F * strength));
		int solid = alpha << 24 | COLOUR;
		graphics.fillGradient(0, 0, w, edge, solid, COLOUR);
		graphics.fillGradient(0, h - edge, w, h, COLOUR, solid);
		int band = Mth.ceil(edge / (float)BANDS);
		for (int i = 0; i < BANDS; i++) {
			int a = Math.round(alpha * (1.0F - i / (float)BANDS) * 0.8F);
			graphics.fill(i * band, 0, i * band + band, h, a << 24 | COLOUR);
			graphics.fill(w - i * band - band, 0, w - i * band, h, a << 24 | COLOUR);
		}
	}
}
