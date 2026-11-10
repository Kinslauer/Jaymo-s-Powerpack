package com.jaymoskingdoms.client.astronomy;

import com.jaymoskingdoms.astronomy.SunMoon;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.world.phys.HitResult;

/**
 * The client's side of {@link SunMoon}: a click on the Sun (a left click that hits nothing, aimed at it) is sent to the
 * server, which decides what it does. The click itself goes on as usual.
 */
public final class SunMoonClient {
	private SunMoonClient() {
	}

	public static void register() {
		ClientPreAttackCallback.EVENT.register((minecraft, player, clicks) -> {
			if (clicks > 0 && minecraft.level != null && (minecraft.hitResult == null || minecraft.hitResult.getType() == HitResult.Type.MISS)
				&& !SunMoon.blinded(player) && SunMoon.lookingAtSun(minecraft.level, player)) {
				ClientPlayNetworking.send(new SunMoon.Glance());
			}
			return false;
		});
	}
}
