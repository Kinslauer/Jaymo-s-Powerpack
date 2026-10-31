package com.jaymospowers.client.freerun;

import com.jaymospowers.core.Ledge;
import com.jaymospowers.freerun.AirFlip;
import com.jaymospowers.power.web.WebPose;
import com.jaymospowers.stamina.Feathers;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;

/**
 * (Motion patch 0) The air flip on this side ({@link AirFlip}): one tap of Sneak in the air, high enough off the ground,
 * flips you. A second tap within {@link #DOUBLE_TAP_TICKS} makes it the dodge or landing roll instead
 * ({@code DodgeClient}), so the flip waits out that window before it goes; a held Sneak is never a tap.
 */
public final class AirFlipClient {
	/** The same double-tap window and tap length as {@code DodgeClient}'s. */
	static final int DOUBLE_TAP_TICKS = 6, TAP_HOLD_TICKS = 3;

	private static boolean sneakWasDown;
	/** Ticks the current Sneak press has been held. */
	private static int held;
	/** Ticks left before a lone tap becomes a flip (0: none waiting). */
	private static int waiting;

	private AirFlipClient() {
	}

	/** Every client tick, from {@link FreerunClient}. */
	static void tick(Minecraft minecraft, LocalPlayer player) {
		boolean sneak = minecraft.options.keyShift.isDown();
		if (minecraft.gui.screen() != null || player.onGround() || !AirFlip.allowed(player) || !Ledge.canHang(player)
			|| FreerunClient.active()) {
			waiting = 0;
			held = sneak ? held + 1 : 0;
			sneakWasDown = sneak;
			return;
		}
		boolean pressed = sneak && !sneakWasDown;
		sneakWasDown = sneak;
		held = sneak ? held + 1 : 0;
		if (pressed) {
			// a second tap inside the window is the dodge's, not ours
			waiting = waiting > 0 ? 0 : DOUBLE_TAP_TICKS + 1;
		}
		if (held > TAP_HOLD_TICKS) {
			waiting = 0;
		}
		if (waiting > 0 && --waiting == 0 && !sneak) {
			flip(player);
		}
	}

	private static void flip(LocalPlayer player) {
		if (!player.level().noCollision(player, player.getBoundingBox().expandTowards(0.0, -AirFlip.MIN_HEIGHT, 0.0))
			|| !Feathers.canSpend(player, AirFlip.COST) || !ClientPlayNetworking.canSend(AirFlip.Flip.TYPE)) {
			return;
		}
		Input input = player.input.keyPresses;
		int kind, side = 1;
		if (input.left() != input.right() && !input.forward() && !input.backward()) {
			kind = WebPose.CARTWHEEL;
			side = input.left() ? -1 : 1;
		} else if (input.backward() && !input.forward()) {
			kind = WebPose.BACK_FLIP;
		} else {
			kind = WebPose.FRONT_FLIP;
		}
		ClientPlayNetworking.send(new AirFlip.Flip(kind, side));
	}
}
