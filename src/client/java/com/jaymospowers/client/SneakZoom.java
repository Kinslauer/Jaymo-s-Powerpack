package com.jaymospowers.client;

import com.jaymospowers.client.freerun.FreerunClient;
import com.jaymospowers.freerun.Slide;
import com.jaymospowers.power.web.WebParkour;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.65) Andy: "double tap and hold shift should be default zoom, and alt should be roll". Tap Sneak, then press it
 * again within {@link #DOUBLE_TAP_TICKS} and keep holding: OK Zoomer zooms in until you let go. The pack's zoom stays OK
 * Zoomer's (its smooth zoom, scroll steps and settings): its Zoom key reads {@code isDown()} at the start of each client
 * tick, so this holds that key down while the gesture is held. Its own key comes off Left Alt ({@code KeyConflicts} v7),
 * which is now only the dodge's; anyone who binds OK Zoomer's key again keeps it working as well.
 * <p>The double tap used to be the dodge's (Combat Roll's old gesture, in {@code DodgeClient}); the dodge is the Alt key
 * only now. Kept from that gesture: no zoom from Sneak while clinging to a wall or hanging from a bar (Sneak lets go
 * there) nor until {@link #CLING_SETTLE} ticks after, none from Web's double-Sneak yank, and none in a slide (a held
 * Sneak in a fast run is the slide's).
 * <p>(0.20.74) Andy: "shift + left alt should make you zoom". Holding Sneak and the Dodge key (Left Alt) together zooms
 * as well, until Alt is let go (0.20.98: letting go of Shift first keeps it); {@code DodgeClient} doesn't dodge on Alt while Sneak is down. Alt alone still rolls.
 */
public final class SneakZoom {
	/** The most ticks between the first Sneak press and the second for a double tap. */
	private static final int DOUBLE_TAP_TICKS = 6;
	/** A first press held longer than this (ticks) is a hold, not a tap (a freerunner's slide is a held Sneak). */
	private static final int TAP_HOLD_TICKS = 3;
	/** After a cling or bar hang, ticks on the ground before Sneak taps zoom again. */
	private static final int CLING_SETTLE = 10;
	private static final String OK_ZOOMER_KEY = "key.ok_zoomer.zoom";

	private static boolean sneakWasDown;
	private static int sinceLastTap = Integer.MAX_VALUE;
	private static int held;
	private static boolean afterCling;
	private static int groundedAfterCling;
	/** The second press is still held: zooming. */
	private static boolean zooming;
	/** This zoom is Shift + Alt's: it ends when either is let go. */
	private static boolean byAlt;
	private static @Nullable KeyMapping zoomKey;
	private static boolean looked;

	private SneakZoom() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(SneakZoom::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		boolean sneak = minecraft.options.keyShift.isDown();
		if (player == null || minecraft.gui.screen() != null) {
			sneakWasDown = false;
			sinceLastTap = Integer.MAX_VALUE;
			held = 0;
			setZoom(minecraft, false);
			return;
		}
		if (sinceLastTap < Integer.MAX_VALUE) {
			sinceLastTap++;
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
			sinceLastTap = Integer.MAX_VALUE;
			sneakWasDown = sneak;
			held = sneak ? held + 1 : 0;
			setZoom(minecraft, false);
			return;
		}
		held = sneak ? held + 1 : 0;
		boolean alt = com.jaymospowers.client.stamina.DodgeClient.keyDown();
		if (zooming) {
			// (0.20.98) Andy: "releasing shift shouldnt stop your zoom but alt does": Shift + Alt's zoom lasts while Alt is
			// held, Shift or not; the double tap's while Sneak is
			if (byAlt ? !alt : !sneak) {
				setZoom(minecraft, false);
			}
		} else if (sneak && alt) {
			// (0.20.74) Shift + Left Alt zooms too, for as long as both are held (Alt with Shift down doesn't dodge)
			sinceLastTap = Integer.MAX_VALUE;
			setZoom(minecraft, true);
			byAlt = true;
		} else if (sneak && !sneakWasDown) {
			if (sinceLastTap <= DOUBLE_TAP_TICKS) {
				sinceLastTap = Integer.MAX_VALUE;
				setZoom(minecraft, true);
			} else {
				sinceLastTap = 0;
			}
		} else if (held > TAP_HOLD_TICKS) {
			// held, not tapped: it doesn't start a double tap
			sinceLastTap = Integer.MAX_VALUE;
		}
		if (zooming) {
			// held down again every tick: vanilla lets go of every key when a screen opens or the window loses focus
			KeyMapping key = zoomKey(minecraft);
			if (key != null) {
				key.setDown(true);
			}
		}
		sneakWasDown = sneak;
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

	/** OK Zoomer's Zoom key, found by name (null without OK Zoomer). */
	private static @Nullable KeyMapping zoomKey(Minecraft minecraft) {
		if (!looked) {
			looked = true;
			for (KeyMapping mapping : minecraft.options.keyMappings) {
				if (OK_ZOOMER_KEY.equals(mapping.getName())) {
					zoomKey = mapping;
				}
			}
		}
		return zoomKey;
	}
}
