package com.jaymospowers.client.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.Adrenaline;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * (0.20.110) Adrenaline ({@link Adrenaline}) on your own screen. Andy (2026-11-05): "when it wears off you get the max
 * thirst effect screen effects, (not max thirst) and a lessened vein and slight letterbox while the adrenaline is
 * rushing".
 * <ul>
 * <li><b>Rushing:</b> thin black bars slide in at the top and bottom ({@link #BAR}), and the water-thirst veins show
 * lightly at the edges ({@link #RUSH_REACH}, {@link #RUSH_ALPHA}), throbbing with a racing heart ({@link #RUSH_BPM}).</li>
 * <li><b>Wearing off:</b> the bars slide away and the veins come in as far as Jaymo's Kitchen's at no water at all
 * ({@code DryVeins}: every layer, {@link #CRASH_ALPHA}), held {@link #CRASH_HOLD} then fading over {@link #CRASH_FADE}.
 * It's only the look: your water is untouched.</li>
 * </ul>
 * The vein art is our own (Kingdoms' {@code tools/gen_thirst_veins.py water}, as Kitchen's dry veins). Only you see it.
 */
public final class AdrenalineHud {
	/** The vein layers, outermost first. */
	static final int LAYERS = 3;
	/** Rushing: how many layers the veins reach, their opacity, and the heart they throb with (beats a minute). */
	static final float RUSH_REACH = 1.2F, RUSH_ALPHA = 0.35F, RUSH_BPM = 150.0F, RUSH_THROB = 0.35F;
	/** The letterbox: each bar's height (share of the screen's), and how fast it slides (share a tick). */
	static final float BAR = 0.06F, BAR_SLIDE = 0.12F;
	/** Wearing off: the veins' opacity (Kitchen's DryVeins at no water), held this long, then fading this long (ticks). */
	static final float CRASH_ALPHA = 0.65F;
	static final int CRASH_HOLD = 60, CRASH_FADE = 140;
	/** How fast the rush's veins ease in and out (share a tick). */
	static final float RUSH_EASE = 0.08F;

	private static final Identifier[] TEXTURES = new Identifier[LAYERS];

	static {
		for (int i = 0; i < LAYERS; i++) {
			TEXTURES[i] = JaymosPowers.id("textures/gui/adrenaline_veins_" + (i + 1) + ".png");
		}
	}

	/** The rush's look (0-1) and the bars' (0-1), eased; ticks into the wearing off (-1: none). */
	private static float rush, lastRush, bars, lastBars;
	private static int crash = -1;
	private static boolean wasActive;

	private AdrenalineHud() {
	}

	/** From {@link WoundsHud#init}. */
	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(AdrenalineHud::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("adrenaline_hud"), AdrenalineHud::render);
	}

	private static void tick(Minecraft minecraft) {
		lastRush = rush;
		lastBars = bars;
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || !player.isAlive() || player.isSpectator()) {
			rush = lastRush = bars = lastBars = 0.0F;
			crash = -1;
			wasActive = false;
			return;
		}
		boolean active = Adrenaline.active(player);
		if (wasActive && !active) {
			crash = 0;
		} else if (active) {
			crash = -1;
		} else if (crash >= 0 && ++crash > CRASH_HOLD + CRASH_FADE) {
			crash = -1;
		}
		wasActive = active;
		float target = active ? 1.0F : 0.0F;
		rush = target > rush ? Math.min(target, rush + RUSH_EASE) : Math.max(target, rush - RUSH_EASE);
		bars = target > bars ? Math.min(target, bars + BAR_SLIDE) : Math.max(target, bars - BAR_SLIDE);
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		float b = Mth.lerp(partial, lastBars, bars);
		letterbox(graphics, BAR * b);
		float crashLook = crash < 0 ? 0.0F : crash <= CRASH_HOLD ? 1.0F : 1.0F - (crash - CRASH_HOLD + partial) / CRASH_FADE;
		crashLook = Mth.clamp(crashLook, 0.0F, 1.0F);
		float r = Mth.lerp(partial, lastRush, rush);
		if (crashLook > 0.0F) {
			veins(graphics, w, h, LAYERS, CRASH_ALPHA * crashLook, 1.0F);
		} else if (r > 0.0F) {
			// the racing heart: a sharp swell on each beat, easing off between
			float t = (player.level().getGameTime() + partial) * RUSH_BPM / 1200.0F;
			float beat = (float) Math.pow(1.0F - (t - (float) Math.floor(t)), 3.0);
			veins(graphics, w, h, RUSH_REACH * r, RUSH_ALPHA * r * (1.0F - RUSH_THROB + RUSH_THROB * beat), 1.0F);
		}
	}

	/**
	 * The letterbox: a black bar at the top and the bottom, each {@code share} of the screen's height (0.5 meets in the
	 * middle: shut). (0.20.111) Fainting's ({@code FaintHud}) too.
	 */
	static void letterbox(GuiGraphicsExtractor graphics, float share) {
		if (share <= 0.0F) {
			return;
		}
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		int bar = share >= 0.5F ? (h + 1) / 2 + 1 : Math.round(h * share);
		graphics.fill(0, 0, w, bar, 0xFF000000);
		graphics.fill(0, h - bar, w, h, 0xFF000000);
	}

	/** The veins, reaching {@code reach} layers in, at {@code alpha}. */
	private static void veins(GuiGraphicsExtractor graphics, int w, int h, float reach, float alpha, float shade) {
		for (int i = 0; i < LAYERS; i++) {
			float part = Mth.clamp(reach - i, 0.0F, 1.0F);
			if (part <= 0.0F) {
				break;
			}
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURES[i], 0, 0, 0.0F, 0.0F, w, h, w, h, ARGB.colorFromFloat(alpha * part, shade, shade, shade));
		}
	}
}
