package com.jaymospowers.client.power.chrono;

import com.jaymospowers.power.chrono.ChronoGears;
import com.jaymospowers.power.chrono.ChronoSpeed;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * The mouse wheel shifts Overclock's (and Velocity's) gears while you sprint at speed; any other time it is the hotbar's.
 * Called from {@code ChronoScrollMixin}.
 */
public final class ChronoGearsClient {
	private ChronoGearsClient() {
	}

	/** True if the scroll was a gear shift (the hotbar must not see it). */
	public static boolean scroll(double yOffset) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || yOffset == 0.0 || minecraft.gui.screen() != null || player.isSpectator() || !ChronoSpeed.overclocked(player)
			|| !player.isSprinting() || player.isPassenger() || !ClientPlayNetworking.canSend(ChronoGears.Shift.TYPE)) {
			return false;
		}
		double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
		if (Math.sqrt(dx * dx + dz * dz) < ChronoGears.SHIFT_MIN_SPEED) {
			return false;
		}
		ClientPlayNetworking.send(new ChronoGears.Shift(yOffset > 0.0 ? 1 : -1));
		return true;
	}
}
