package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.freerun.FreerunClient;
import com.jaymospowers.freerun.Slide;
import com.jaymospowers.power.web.WebParkour;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.74) Andy: "shift + left alt should make you zoom". Holding Sneak and the Dodge key (Left Alt) together zooms
 * as well, until Alt is let go (0.20.98: letting go of Shift first keeps it); {@code DodgeClient} doesn't dodge on Alt while Sneak is down. Alt alone still rolls,
 * except with a fishing line out ({@link #lineOut}): then Alt alone zooms.
 * <p>Kept from the old double-tap gesture: no zoom from Sneak while clinging to a wall or hanging from a bar (Sneak lets go
 * there) nor until {@link #CLING_SETTLE} ticks after, none from Web's double-Sneak yank, and none in a slide (a held
 * Sneak in a fast run is the slide's).
 * <p>The zoom is OK Zoomer's, driven through its own Zoom key (left unbound by {@code KeyConflicts}). OK Zoomer reads that
 * key at the start of each tick, so it's held down again then too ({@link #BEFORE_ZOOMER}), not only at the end of the last
 * one: a key event or focus change in between that lets go of it can't drop the zoom. Sneak and Alt are both read off the
 * keyboard as well as their mappings. Combo Clash's dash, on the same Left Alt, stays still for the zoom ({@link #ownsAlt}).
 * If the gesture is held and no zoom comes of it, the log says why once ("Zoom:"), for the next time it fails in play.
 */
public final class SneakZoom {
	/** A first press held longer than this (ticks) is a hold, not a tap (a freerunner's slide is a held Sneak). */
	private static final int TAP_HOLD_TICKS = 3;
	/** After a cling or bar hang, ticks on the ground before Sneak taps zoom again. */
	private static final int CLING_SETTLE = 10;
	private static final String OK_ZOOMER_KEY = "key.ok_zoomer.zoom";
	/** A phase of the client's tick start that runs before OK Zoomer's (in the default phase), which reads its Zoom key. */
	private static final net.minecraft.resources.Identifier BEFORE_ZOOMER = JaymosPowers.id("before_ok_zoomer");

	private static boolean sneakWasDown;
	private static int held;
	private static boolean afterCling;
	private static int groundedAfterCling;
	/** The zoom is Shift + Alt's: it ends when either is let go. */
	private static boolean zooming;
	private static boolean byAlt;
	private static @Nullable KeyMapping zoomKey;
	private static boolean looked;
	/** Why the last held Shift + Alt didn't zoom, already put in the log (null: nothing to say). */
	private static @Nullable String told;

	private SneakZoom() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(SneakZoom::tick);
		ClientTickEvents.START_CLIENT_TICK.addPhaseOrdering(BEFORE_ZOOMER, Event.DEFAULT_PHASE);
		ClientTickEvents.START_CLIENT_TICK.register(BEFORE_ZOOMER, minecraft -> {
			if (zooming && minecraft.player != null && minecraft.gui.screen() == null) {
				KeyMapping key = zoomKey(minecraft);
				if (key != null) {
					key.setDown(true);
				}
			}
		});
	}

	/**
	 * Sneak is held: its mapping, or its key read off the keyboard itself (the way {@code DodgeClient#keyDown} reads Alt),
	 * so a mapping something has let go of between key events still counts.
	 */
	public static boolean sneakDown(Minecraft minecraft) {
		KeyMapping sneak = minecraft.options.keyShift;
		if (sneak.isDown()) {
			return true;
		}
		InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(sneak);
		return bound.getType() == InputConstants.Type.KEYBOARD && bound.getValue() > 0 && InputConstants.isKeyDown(bound.getValue());
	}

	/**
	 * Alt is the zoom's just now, not a dash or a roll: Sneak is held with it, or a fishing line is out (Combo Clash's dash,
	 * on the same key, holds off then; {@code ClientCombatMixin}).
	 */
	public static boolean ownsAlt() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		return player != null && (sneakDown(minecraft) || lineOut(player));
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		boolean sneak = sneakDown(minecraft);
		if (player == null || minecraft.gui.screen() != null) {
			sneakWasDown = false;
			held = 0;
			setZoom(minecraft, false);
			return;
		}
		Integer move = player.getAttached(WebParkour.MOVE);
		if (move != null && WebParkour.kind(move) == WebParkour.CLING || FreerunClient.holding()) {
			afterCling = true;
			groundedAfterCling = 0;
		} else if (afterCling && (player.onGround() || player.isInWater()) && ++groundedAfterCling >= CLING_SETTLE) {
			afterCling = false;
		}
		boolean blocked = afterCling || player.hasAttached(Slide.SLIDING) || FreerunClient.active()
			// double Sneak with two webs on small things is Web's yank, not a zoom
			|| player.tickCount - com.jaymospowers.client.power.web.WebSwing.reelReadyTick <= 1;
		if (blocked) {
			if (!sneak || !com.jaymospowers.client.stamina.DodgeClient.keyDown()) {
				told = null;
			} else {
				tell(afterCling ? "just off a wall or bar" : player.hasAttached(Slide.SLIDING) || FreerunClient.active() ? "in a freerun move"
					: "a web yank");
			}
			sneakWasDown = sneak;
			held = sneak ? held + 1 : 0;
			setZoom(minecraft, false);
			return;
		}
		held = sneak ? held + 1 : 0;
		boolean alt = com.jaymospowers.client.stamina.DodgeClient.keyDown();
		if (zooming) {
			// (0.20.98) Andy: "releasing shift shouldnt stop your zoom but alt does": Shift + Alt's zoom lasts while Alt is
			// held, Shift or not
			if (!alt) {
				setZoom(minecraft, false);
			}
		} else if (sneak && alt || alt && lineOut(player)) {
			// (0.20.74) Shift + Left Alt zooms too, for as long as both are held (Alt with Shift down doesn't dodge);
			// (Andy, 2026-11-26) with a fishing line out, Alt alone zooms, and DodgeClient throws no dash or roll
			setZoom(minecraft, true);
			byAlt = true;
		}
		if (zooming) {
			// held down again every tick: vanilla lets go of every key when a screen opens or the window loses focus
			KeyMapping key = zoomKey(minecraft);
			if (key != null) {
				key.setDown(true);
				told = null;
			} else {
				tell("OK Zoomer's Zoom key isn't there");
			}
		} else if (!(sneak && alt)) {
			told = null;
		}
		sneakWasDown = sneak;
	}

	/**
	 * (Andy, 2026-11-26: "make alt zoom alone when the line is out, so you dont do the dash or cancel the cast") A fishing
	 * line is out: Alt alone is the zoom then, not a dodge ({@code DodgeClient} skips the press).
	 */
	public static boolean lineOut(LocalPlayer player) {
		return player.fishing != null && !player.fishing.isRemoved();
	}

	private static void setZoom(Minecraft minecraft, boolean on) {
		if (zooming == on) {
			return;
		}
		zooming = on;
		byAlt = false;
		KeyMapping key = zoomKey(minecraft);
		if (key != null) {
			key.setDown(on);
		}
	}

	/** Shift + Alt is held and no zoom comes of it: the log says why, once a hold. */
	private static void tell(String why) {
		if (!why.equals(told)) {
			told = why;
			JaymosPowers.LOGGER.info("Zoom: Shift + Alt held but no zoom ({})", why);
		}
	}

	/** OK Zoomer's Zoom key, found by name (null without OK Zoomer). */
	private static @Nullable KeyMapping zoomKey(Minecraft minecraft) {
		if (!looked) {
			// looked for until found: a look made before OK Zoomer's keys were in the list would otherwise stay empty
			for (KeyMapping mapping : minecraft.options.keyMappings) {
				if (OK_ZOOMER_KEY.equals(mapping.getName())) {
					zoomKey = mapping;
					looked = true;
				}
			}
		}
		return zoomKey;
	}
}
