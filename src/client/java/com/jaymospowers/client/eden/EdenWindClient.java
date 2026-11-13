package com.jaymospowers.client.eden;

import com.jaymospowers.core.Powers;
import com.jaymospowers.eden.EdenWind;
import com.jaymospowers.power.wind.WindPower;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.player.LocalPlayer;

/** The wind valley's push on your own character ({@link EdenWind}): steady on the ground, stronger off it. Sneaking braces against it. */
public final class EdenWindClient {
	private EdenWindClient() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			LocalPlayer player = minecraft.player;
			if (player == null || minecraft.isPaused() || player.isSpectator() || player.getAbilities().flying || player.isPassenger()
				|| player.isShiftKeyDown() || player.isInWater() || !EdenWind.in(player)) {
				return;
			}
			double push = player.onGround() ? EdenWind.PUSH_GROUND : EdenWind.PUSH_AIR;
			if (Powers.has(player, WindPower.INSTANCE)) {
				push *= EdenWind.WIND_KIN;
			}
			player.setDeltaMovement(player.getDeltaMovement().add(EdenWind.blowing(player.level()).scale(push)));
		});
	}
}
