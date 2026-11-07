package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.power.xenoform.XenoDouble;
import com.jaymospowers.power.xenoform.XenoformState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * (0.18.96) Duplex's sneak + V: while the server says you look through a double's eyes ({@link XenoformState#EYES}),
 * this client's camera is that double; when it ends (or the double goes), the camera comes home.
 */
final class XenoEyes {
	private static boolean borrowed;

	private XenoEyes() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(XenoEyes::tick);
	}

	private static void tick(Minecraft minecraft) {
		var player = minecraft.player;
		if (player == null || minecraft.level == null) {
			borrowed = false;
			return;
		}
		Integer id = player.getAttached(XenoformState.EYES);
		Entity twin = id == null ? null : minecraft.level.getEntity(id);
		if (twin instanceof XenoDouble && twin.isAlive()) {
			if (minecraft.getCameraEntity() != twin) {
				minecraft.setCameraEntity(twin);
				borrowed = true;
			}
			return;
		}
		if (borrowed) {
			borrowed = false;
			if (minecraft.getCameraEntity() != player) {
				minecraft.setCameraEntity(player);
			}
		}
	}
}
