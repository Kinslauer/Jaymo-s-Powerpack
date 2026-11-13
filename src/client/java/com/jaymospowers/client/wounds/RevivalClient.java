package com.jaymospowers.client.wounds;

import com.jaymospowers.core.wounds.Revival;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.DeathScreen;

/** Brought back (Revival): the death screen closes and they stand. */
public final class RevivalClient {
	private RevivalClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(Revival.Revived.TYPE, (payload, ctx) -> {
			if (ctx.client().player != null) {
				ctx.client().player.deathTime = 0;
			}
			if (ctx.client().gui.screen() instanceof DeathScreen) {
				ctx.client().gui.setScreen(null);
			}
		});
	}
}
