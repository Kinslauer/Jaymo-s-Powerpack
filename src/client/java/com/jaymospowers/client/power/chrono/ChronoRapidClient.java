package com.jaymospowers.client.power.chrono;

import com.jaymospowers.power.chrono.ChronoRapid;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Tells the server each press of use while the local player is a speedster at speed: the server then catches a shot in the
 * sight line, hurls the one it holds, or snatches a weapon from an armed mob ({@link ChronoRapid}). Use itself is not
 * taken, so a held bow still draws.
 */
public final class ChronoRapidClient {
	private static boolean wasDown;

	private ChronoRapidClient() {
	}

	public static void tick(LocalPlayer player, boolean overclocked) {
		Minecraft minecraft = Minecraft.getInstance();
		boolean down = overclocked && minecraft.options.keyUse.isDown() && minecraft.gui.screen() == null && !player.isSpectator();
		if (down && !wasDown && ClientPlayNetworking.canSend(ChronoRapid.Use.TYPE)) {
			double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
			if (player.isSprinting() || Math.sqrt(dx * dx + dz * dz) >= 0.15) {
				ClientPlayNetworking.send(new ChronoRapid.Use());
			}
		}
		wasDown = down;
	}
}
