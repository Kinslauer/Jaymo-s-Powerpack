package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.UiStyle;
import com.jaymospowers.power.zfighter.ZFighterSpace;
import com.jaymospowers.power.zfighter.pod.PodFlight;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * (Patch 9, tr_cockpit) The client's side of flying the pod: what the server last said ({@link PodFlight.State}), the stick it
 * sends back, the canopy cockpit drawn over the climb through the sky bands and under the crossing's view ({@link PodFlightScreen}).
 * The client decides nothing: it draws, and reports the keys. In the climb the game's own keys fly the pod (look to steer, forward,
 * sprint to boost, back to brake) and only the autopilot key ({@link #AUTO}) is read here.
 */
public final class FlightClient {
	/** The autopilot key, in the climb (in the crossing the screen reads it with the rest). */
	static final int AUTO = InputConstants.KEY_T;
	/** Ticks without word from the server after which the flight is taken to be over. */
	static final int SILENCE = 40;
	/** How often the stick is repeated to the server though it has not changed (ticks). */
	static final int REPEAT = 10;
	static final int FRAME = 0xFF14161C, FRAME_LIGHT = 0xFF2C313C, FRAME_DARK = 0xFF08090C, LIT = 0xFF9FB8FF, WARN = 0xFFFF7060, FUEL = 0xFFFF9A30;

	static PodFlight.@Nullable State last, prev;
	/** Ticks since the last state came. */
	static int age;
	private static boolean autoWas;
	private static int sentAt;
	private static PodFlight.@Nullable Input sent;
	/** Whether the world flown to has been told to the night sky this trip. */
	private static boolean told;

	private FlightClient() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(PodFlight.State.TYPE, (payload, ctx) -> receive(ctx.client(), payload));
		ClientTickEvents.END_CLIENT_TICK.register(FlightClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosPowers.id("pod_cockpit"), FlightClient::hud);
	}

	private static void receive(Minecraft minecraft, PodFlight.State state) {
		prev = last;
		last = state;
		age = 0;
		if (state.phase() == PodFlight.PHASE_END) {
			last = null;
			prev = null;
			told = false;
			if (minecraft.gui.screen() instanceof PodFlightScreen) {
				minecraft.gui.setScreen(null);
			}
			return;
		}
		if (state.phase() == PodFlight.PHASE_SPACE && !(minecraft.gui.screen() instanceof PodFlightScreen)) {
			if (!told) {
				told = true;
				// (0.18.53) the world flown to (or from, on the way home) shines brighter in the night sky for a while
				com.jaymospowers.client.power.zfighter.sky.SkyPlanets.glow(state.to() == ZFighterSpace.HOME_INDEX ? state.from() : state.to());
			}
			minecraft.gui.setScreen(new PodFlightScreen());
		}
	}

	private static void tick(Minecraft minecraft) {
		if (last == null) {
			return;
		}
		if (minecraft.player == null || ++age > SILENCE) {
			last = null;
			prev = null;
			told = false;
			if (minecraft.gui.screen() instanceof PodFlightScreen) {
				minecraft.gui.setScreen(null);
			}
			return;
		}
		if (last.phase() == PodFlight.PHASE_CLIMB && minecraft.gui.screen() == null) {
			boolean down = InputConstants.isKeyDown(AUTO);
			if (down && !autoWas) {
				ClientPlayNetworking.send(new PodFlight.Input(0, 0, PodFlight.AUTO_KEY));
			}
			autoWas = down;
		}
	}

	/** Sends the stick if it changed (or is due again). */
	static void send(PodFlight.Input input) {
		PodFlight.Input was = sent;
		sentAt++;
		if (was == null || !was.equals(input) || sentAt >= REPEAT || input.has(PodFlight.AUTO_KEY)) {
			ClientPlayNetworking.send(input);
			sent = input;
			sentAt = 0;
		}
	}

	private static void hud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		PodFlight.State state = last;
		if (state == null || state.phase() != PodFlight.PHASE_CLIMB || minecraft.gui.screen() != null || minecraft.player == null) {
			return;
		}
		int w = minecraft.getWindow().getGuiScaledWidth(), h = minecraft.getWindow().getGuiScaledHeight();
		canopy(graphics, w, h);
		Font font = minecraft.font;
		int baseY = h - Math.round(h * 0.13F) + 4;
		int left = Math.round(w * 0.30F), width = Math.round(w * 0.40F);
		gauge(graphics, font, left, baseY, width, Component.translatable("screen.jaymospowers.pod_flight.climb"), state.progress(), LIT);
		float top = (float)(PodFlight.CLIMB_BOOST * 1.2);
		gauge(graphics, font, left, baseY + 12, width / 2 - 2, Component.translatable("screen.jaymospowers.pod_flight.speed"), Mth.clamp(state.speed() / top, 0.0F, 1.0F),
			(state.flags() & PodFlight.F_BOOST) != 0 ? FUEL : LIT);
		Component mode = Component.translatable((state.flags() & PodFlight.F_AUTO) != 0 ? "screen.jaymospowers.pod_flight.auto_on" : "screen.jaymospowers.pod_flight.auto_off",
			InputConstants.Type.KEYBOARD.getOrCreate(AUTO).getDisplayName());
		UiStyle.lineCentred(graphics, font, mode, w / 2 + width / 4 + 2, baseY + 12, width / 2, (state.flags() & PodFlight.F_AUTO) != 0 ? 0xFF7CFC9A : UiStyle.DIM);
		UiStyle.lineCentred(graphics, font, Component.translatable("screen.jaymospowers.pod_flight.eta", Math.max(0, state.eta() / 20)), w / 2, baseY + 24, width, UiStyle.DIM);
	}

	/** A bar with its name over its left end. */
	static void gauge(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, Component label, float share, int colour) {
		graphics.text(font, label, x, y - 9, 0xFFC0C8D8, true);
		graphics.fill(x - 1, y - 1, x + w + 1, y + 5, FRAME_DARK);
		UiStyle.bar(graphics, x, y, w, 4, Mth.clamp(share, 0.0F, 1.0F), colour);
	}

	/**
	 * The pod's canopy seen from inside: a dark frame round the edge, a strut over the middle of the glass, a rivet along each
	 * rail, and the sill under the gauges. Drawn over the world (the climb) and over the crossing's view.
	 */
	static void canopy(GuiGraphicsExtractor graphics, int w, int h) {
		int top = Math.max(6, Math.round(h * 0.07F)), side = Math.max(5, Math.round(w * 0.04F)), sill = Math.max(26, Math.round(h * 0.13F));
		// the rails
		graphics.fill(0, 0, w, top, FRAME);
		graphics.fill(0, 0, side, h, FRAME);
		graphics.fill(w - side, 0, w, h, FRAME);
		graphics.fill(0, h - sill, w, h, FRAME);
		graphics.fill(0, top, w, top + 1, FRAME_LIGHT);
		graphics.fill(side, 0, side + 1, h - sill, FRAME_LIGHT);
		graphics.fill(w - side - 1, 0, w - side, h - sill, FRAME_LIGHT);
		graphics.fill(0, h - sill, w, h - sill + 1, FRAME_LIGHT);
		// the corners of the glass are rounded: steps of dark
		for (int i = 0; i < 12; i++) {
			int run = 12 - i;
			graphics.fill(side, top + i, side + run, top + i + 1, FRAME);
			graphics.fill(w - side - run, top + i, w - side, top + i + 1, FRAME);
			graphics.fill(side, h - sill - i - 1, side + run / 2, h - sill - i, FRAME);
			graphics.fill(w - side - run / 2, h - sill - i - 1, w - side, h - sill - i, FRAME);
		}
		// the strut down the middle of the glass, leaning in toward the top
		int mid = w / 2;
		for (int y = top; y < top + Math.round(h * 0.14F); y++) {
			int half = 1 + (y - top) / 14;
			graphics.fill(mid - half, y, mid + half + 1, y + 1, FRAME);
		}
		// rivets
		for (int x = side * 2; x < w - side * 2; x += 18) {
			graphics.fill(x, top / 2, x + 2, top / 2 + 2, FRAME_LIGHT);
			graphics.fill(x, h - sill / 2 + 6, x + 2, h - sill / 2 + 8, FRAME_LIGHT);
		}
	}
}
