package com.jaymoskingdoms.client.disasters;

import com.jaymoskingdoms.sailing.WindPush;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The wind on your own body: a player moves themselves, so the airborne drift ({@link WindPush#airDrift}: a jump, a fall,
 * a glide, flight) is added here on the client, from the wind the server already syncs. Grounded, swimming, riding,
 * creative and spectating players feel nothing.
 */
public final class WindFeel {
	private WindFeel() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(WindFeel::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || minecraft.isPaused() || player.onGround() || player.isInWater() || player.isPassenger()
			|| player.isCreative() || player.isSpectator()) {
			return;
		}
		Vec3 drift = WindPush.airDrift(player);
		if (drift.lengthSqr() > 0.0) {
			player.setDeltaMovement(player.getDeltaMovement().add(drift));
		}
	}
}
