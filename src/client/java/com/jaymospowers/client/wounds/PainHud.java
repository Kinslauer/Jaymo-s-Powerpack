package com.jaymospowers.client.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.core.wounds.Pain;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * (0.14.5) Wounds phase 5 on your own screen: the edges darken and slowly throb while your head or body is low and the
 * pain isn't dulled ({@link Pain#dulled}); a red edge flashes as any part drops ({@link WoundsHud} spots it); and with
 * head or body nearly gone, while still on your feet, your heart pounds (faster the worse it gets) with a faint red
 * pulse in time. Nothing in creative or spectator.
 */
public final class PainHud {
	/** Head or body below this fraction hurts: the dark edges. */
	static final float PAIN_FROM = 0.35F;
	/** Head or body below this fraction is critical: the heartbeat. */
	static final float CRITICAL_FROM = 0.15F;
	/** The pain's edges: colour (RGB), how thick at most (0-255, at nothing left), how wide (a fraction of the screen's height), and how fast they throb. */
	static final int PAIN = 0x000000, PAIN_ALPHA = 0xB0;
	static final float PAIN_EDGE = 0.22F, PAIN_THROB = 0.07F;
	/** A hit's red edge: how long (ticks), its colour, how thick at most, and its strength (a small drop, plus this per whole part lost). */
	static final int HIT_FLASH_TICKS = 6, HIT = 0xB01010, HIT_ALPHA = 0xC0;
	static final float HIT_MIN = 0.35F, HIT_PER_DROP = 2.0F;
	/** Critical: a heartbeat every this many ticks (at the threshold, and at nothing left), how loud and how deep. */
	static final int BEAT_SLOW = 18, BEAT_FAST = 11;
	static final float BEAT_VOLUME = 0.6F, BEAT_PITCH = 0.85F;
	/** Critical: the red pulse with each beat, its thickness and how long it fades (ticks). */
	static final int BEAT_ALPHA = 0x50, BEAT_FADE_TICKS = 7;
	/** Just joined (ticks): a drop from "whole" to what was saved isn't a hit. */
	static final int JOIN_GRACE = 20;
	/** The sides are drawn in this many fading bands (the top and bottom are smooth gradients). */
	private static final int BANDS = 8;

	private static int ticks;
	private static int hitTicks;
	private static float hitStrength;
	/** Ticks since the last heartbeat (counting up), and until the next. */
	private static int sinceBeat = Integer.MAX_VALUE / 2, toBeat;

	private PainHud() {
	}

	/** From {@link WoundsHud#init}. */
	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(PainHud::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("pain_hud"), PainHud::render);
	}

	/** From {@link WoundsHud}: a part dropped by {@code drop} (a fraction of the part) this tick. */
	static void hit(float drop) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.tickCount < JOIN_GRACE) {
			return;
		}
		float strength = Mth.clamp(HIT_MIN + drop * HIT_PER_DROP, 0.0F, 1.0F);
		hitStrength = hitTicks > 0 ? Math.max(hitStrength, strength) : strength;
		hitTicks = HIT_FLASH_TICKS;
	}

	/** Whether {@code player}'s screen shows any of this (alive, and not in creative or spectator). */
	private static boolean shown(LocalPlayer player) {
		return player.isAlive() && !player.isCreative() && !player.isSpectator();
	}

	/** The lower of head and body (0-1). */
	private static float vital() {
		return Math.min(WoundsHud.part(0), WoundsHud.part(1));
	}

	private static void tick(Minecraft minecraft) {
		ticks++;
		if (hitTicks > 0) {
			hitTicks--;
		}
		if (sinceBeat < Integer.MAX_VALUE / 2) {
			sinceBeat++;
		}
		LocalPlayer player = minecraft.player;
		float vital = vital();
		if (player == null || !shown(player) || vital >= CRITICAL_FROM || Downed.isDowned(player)) {
			// (downed: DownedHud's own slow heartbeat takes over once passed out)
			toBeat = 0;
			return;
		}
		if (--toBeat > 0) {
			return;
		}
		player.level().playLocalSound(player, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, BEAT_VOLUME, BEAT_PITCH);
		sinceBeat = 0;
		float worse = Mth.clamp(1.0F - vital / CRITICAL_FROM, 0.0F, 1.0F);
		toBeat = Math.round(Mth.lerp(worse, BEAT_SLOW, BEAT_FAST));
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !shown(player)) {
			return;
		}
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		int edge = Math.max(24, (int) (h * PAIN_EDGE));
		float vital = vital();
		if (vital < PAIN_FROM && !Pain.dulled(player)) {
			float how = Mth.clamp(1.0F - vital / PAIN_FROM, 0.0F, 1.0F);
			float throb = 0.7F + 0.3F * Mth.sin(ticks * PAIN_THROB);
			edges(graphics, w, h, (int) (edge * (0.6F + 0.4F * how)), (int) (PAIN_ALPHA * how * throb), PAIN);
		}
		if (vital < CRITICAL_FROM && !Downed.isDowned(player) && sinceBeat < BEAT_FADE_TICKS) {
			edges(graphics, w, h, edge, BEAT_ALPHA * (BEAT_FADE_TICKS - sinceBeat) / BEAT_FADE_TICKS, HIT);
		}
		if (hitTicks > 0) {
			edges(graphics, w, h, edge, (int) (HIT_ALPHA * hitStrength * hitTicks / (float) HIT_FLASH_TICKS), HIT);
		}
	}

	/** A soft frame of {@code colour} closing in from the edges, {@code alpha} thick at the very edge (as Resonance's hush draws its own). */
	private static void edges(GuiGraphicsExtractor graphics, int w, int h, int edge, int alpha, int colour) {
		alpha = Mth.clamp(alpha, 0, 255);
		if (alpha <= 0) {
			return;
		}
		int solid = alpha << 24 | colour;
		graphics.fillGradient(0, 0, w, edge, solid, colour);
		graphics.fillGradient(0, h - edge, w, h, colour, solid);
		int band = Mth.ceil(edge / (float) BANDS);
		for (int i = 0; i < BANDS; i++) {
			int a = (int) (alpha * (1.0F - i / (float) BANDS) * 0.8F);
			graphics.fill(i * band, 0, i * band + band, h, a << 24 | colour);
			graphics.fill(w - i * band - band, 0, w - i * band, h, a << 24 | colour);
		}
	}
}
