package com.jaymospowers.client.water;

import com.jaymospowers.water.Currents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The current ({@link Currents}) on what this client moves itself: the player, and the boat or mount they steer.
 */
public final class CurrentsClient {
	private CurrentsClient() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			LocalPlayer player = minecraft.player;
			if (player == null || minecraft.level == null || minecraft.isPaused() || player.getAbilities().flying) {
				return;
			}
			Entity vehicle = player.getVehicle();
			if (vehicle != null) {
				if (vehicle.getControllingPassenger() == player) {
					Currents.carry(minecraft.level, vehicle, 1);
				}
			} else if (player.isInWater()) {
				Currents.carry(minecraft.level, player, 1);
			}
		});
	}
}
