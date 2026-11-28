package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.freerun.FreerunClient;
import com.jaymospowers.freerun.Slide;
import com.jaymospowers.power.web.WebParkour;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.74) Andy: "shift + left alt should make you zoom". Holding Sneak and the Dodge key (Left Alt) together zooms
 * as well, until Alt is let go (0.20.98: letting go of Shift first keeps it); {@code DodgeClient} doesn't dodge on Alt while Sneak is down. Alt alone still rolls,
 * except with a fishing line out ({@link #lineOut}): then Alt alone zooms.
 * <p>Kept from the old double-tap gesture: no zoom from Sneak while clinging to a wall or hanging from a bar (Sneak lets go
 * there) nor until {@link #CLING_SETTLE} ticks after, none from Web's double-Sneak yank, and none in a slide (a held
 * Sneak in a fast run is the slide's).
 * <p>The zoom is our own, not OK Zoomer's. Andy's clip (2026-11-28) showed OK Zoomer turned on by its Zoom key (the HUD
 * blown up past the screen edges, its "zoomed interface") while the world's field of view never narrowed: something in
 * the pack after OK Zoomer's own change to {@code Camera#calculateFov} put the plain field of view back. So the zoom
 * here is applied last of all ({@code ZoomFovMixin}, a late priority): it eases in over {@link #EASE_TICKS} to
 * {@link #ZOOM} times and out again the same way, and the mouse turns slower by the same amount while it's on
 * ({@code ZoomMouseMixin}), as OK Zoomer's did. OK Zoomer's own key (unbound by {@code KeyConflicts}) is left alone.
 * Combo Clash's dash, on the same Left Alt, stays still for the zoom ({@link #ownsAlt}).
 */
public final class SneakZoom {
	/** After a cling or bar hang, ticks on the ground before Sneak taps zoom again. */
	private static final int CLING_SETTLE = 10;
	/** How far the view zooms in (the field of view is divided by this), OK Zoomer's default. */
	static final float ZOOM = 4.0F;
	/** Ticks the zoom takes to ease in, and out (OK Zoomer's default transition length). */
	static final float EASE_TICKS = 8.0F;

	private static int held;
	private static boolean afterCling;
	private static int groundedAfterCling;
	/** The zoom is Shift + Alt's: it ends when Alt is let go. */
	private static boolean zooming;
	/** How far in the zoom is (0 out, 1 all the way in), now and a tick ago, before easing. */
	private static float progress, progressOld;
	/** Why the last held Shift + Alt didn't zoom, already put in the log (null: nothing to say). */
	private static @Nullable String told;

	private SneakZoom() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(SneakZoom::tick);
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

	/**
	 * What the field of view is multiplied by for the zoom this frame (1 with no zoom): eased in and out, so the view
	 * glides rather than jumps.
	 */
	public static float fovFactor(float partialTicks) {
		float shown = Mth.lerp(partialTicks, progressOld, progress);
		if (shown <= 0.0F) {
			return 1.0F;
		}
		// smoothstep: slow at both ends
		float eased = shown * shown * (3.0F - 2.0F * shown);
		return 1.0F / Mth.lerp(eased, 1.0F, ZOOM);
	}

	/** What the mouse's turning is multiplied by while zoomed (1 with no zoom): slower the further in the view is. */
	public static float turnFactor() {
		return fovFactor(1.0F);
	}

	private static void tick(Minecraft minecraft) {
		tickZoom(minecraft);
		progressOld = progress;
		progress = zooming ? Math.min(1.0F, progress + 1.0F / EASE_TICKS) : Math.max(0.0F, progress - 1.0F / EASE_TICKS);
	}

	private static void tickZoom(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		boolean sneak = sneakDown(minecraft);
		if (player == null || minecraft.gui.screen() != null || UltimateCinematic.filming()) {
			held = 0;
			zooming = false;
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
			held = sneak ? held + 1 : 0;
			zooming = false;
			return;
		}
		held = sneak ? held + 1 : 0;
		boolean alt = com.jaymospowers.client.stamina.DodgeClient.keyDown();
		if (zooming) {
			// (0.20.98) Andy: "releasing shift shouldnt stop your zoom but alt does": Shift + Alt's zoom lasts while Alt is
			// held, Shift or not
			if (!alt) {
				zooming = false;
			}
		} else if (sneak && alt || alt && lineOut(player)) {
			// (0.20.74) Shift + Left Alt zooms too, for as long as both are held (Alt with Shift down doesn't dodge);
			// (Andy, 2026-11-26) with a fishing line out, Alt alone zooms, and DodgeClient throws no dash or roll
			zooming = true;
		}
		if (zooming || !(sneak && alt)) {
			told = null;
		}
	}

	/**
	 * (Andy, 2026-11-26: "make alt zoom alone when the line is out, so you dont do the dash or cancel the cast") A fishing
	 * line is out: Alt alone is the zoom then, not a dodge ({@code DodgeClient} skips the press).
	 */
	public static boolean lineOut(LocalPlayer player) {
		return player.fishing != null && !player.fishing.isRemoved();
	}

	/** Shift + Alt is held and no zoom comes of it: the log says why, once a hold. */
	private static void tell(String why) {
		if (!why.equals(told)) {
			told = why;
			JaymosPowers.LOGGER.info("Zoom: Shift + Alt held but no zoom ({})", why);
		}
	}
}
