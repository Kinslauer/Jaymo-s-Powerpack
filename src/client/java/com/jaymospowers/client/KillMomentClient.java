package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.KillMoment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.TickRateManager;

/**
 * The kill moment on this screen ({@link KillMoment}): the player's own game slows to {@link #SLOW_RATE} ticks a
 * second for {@link #SLOW_MS} ms (wall clock) with a thin letterbox sliding in over it, then both go back. Only this
 * client slows; the server and everyone else run on. Not with Reduced motion ({@link MotionSettings}), and not if the
 * tick rate is already down for some other reason.
 */
public final class KillMomentClient {
	/** The slowed tick rate, and how long it lasts (ms). */
	static final float SLOW_RATE = 6.0F;
	static final long SLOW_MS = 600L;
	/** The letterbox's bar height (share of the screen), and how long it slides in and out (ms). */
	static final float BAR_SHARE = 0.055F;
	static final long BAR_IN_MS = 120L, BAR_OUT_MS = 250L;

	private static long start;
	private static boolean slowed;
	private static float rateBefore;

	private KillMomentClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(KillMoment.Cue.TYPE, (payload, context) -> begin(context.client()));
		ClientTickEvents.END_CLIENT_TICK.register(KillMomentClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("kill_moment"), KillMomentClient::draw);
	}

	private static void begin(Minecraft minecraft) {
		if (MotionSettings.reduced() || minecraft.level == null || minecraft.player == null || start != 0L) {
			return;
		}
		TickRateManager rate = minecraft.level.tickRateManager();
		start = Util.getMillis();
		if (rate.tickrate() > SLOW_RATE) {
			rateBefore = rate.tickrate();
			rate.setTickRate(SLOW_RATE);
			slowed = true;
		}
	}

	private static void tick(Minecraft minecraft) {
		if (start == 0L) {
			return;
		}
		if (minecraft.level == null || MotionSettings.reduced() || Util.getMillis() - start >= SLOW_MS) {
			restore(minecraft);
		}
	}

	private static void restore(Minecraft minecraft) {
		if (slowed && minecraft.level != null && minecraft.level.tickRateManager().tickrate() == SLOW_RATE) {
			minecraft.level.tickRateManager().setTickRate(rateBefore);
		}
		slowed = false;
		start = 0L;
	}

	/** The letterbox: in over {@link #BAR_IN_MS}, out over the last {@link #BAR_OUT_MS} of the moment. */
	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (start == 0L || MotionSettings.reduced()) {
			return;
		}
		long age = Util.getMillis() - start;
		float shown = Mth.clamp(Math.min(age / (float)BAR_IN_MS, (SLOW_MS - age) / (float)BAR_OUT_MS), 0.0F, 1.0F);
		int bar = Math.round(graphics.guiHeight() * BAR_SHARE * shown);
		if (bar <= 0) {
			return;
		}
		graphics.fill(0, 0, graphics.guiWidth(), bar, 0xFF000000);
		graphics.fill(0, graphics.guiHeight() - bar, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
	}
}
