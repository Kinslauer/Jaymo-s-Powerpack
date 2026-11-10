package com.jaymospowers.client.compat;

import com.jaymospowers.client.BeamCinematic;
import com.jaymospowers.client.UltimateCinematic;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Freecam (MIT, hashalite and Matt Sturgeon; shipped unchanged) is for lining up shots, in creative only. Andy (2026-11-10):
 * "Only for creative". Out of creative or spectator, or while one of our films is playing (an ultimate's film, a beam
 * clash's), a Freecam left on is turned off at once, with a line on the action bar saying why. Freecam is client-only, so
 * the rule is kept on each player's own game. Reached by reflection ({@code net.xolt.freecam.Freecam}'s public
 * {@code isEnabled} and {@code toggle}), so nothing breaks when Freecam isn't installed.
 */
public final class FreecamCompat {
	private static Method isEnabled, toggle;

	private FreecamCompat() {
	}

	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded("freecam")) {
			return;
		}
		try {
			Class<?> freecam = Class.forName("net.xolt.freecam.Freecam");
			isEnabled = freecam.getMethod("isEnabled");
			toggle = freecam.getMethod("toggle");
		} catch (ReflectiveOperationException | LinkageError e) {
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(FreecamCompat::tick);
	}

	private static void tick(Minecraft minecraft) {
		if (isEnabled == null || minecraft.player == null) {
			return;
		}
		boolean film = UltimateCinematic.filming() || BeamCinematic.running();
		if (!film && (minecraft.player.isCreative() || minecraft.player.isSpectator())) {
			return;
		}
		try {
			if ((boolean) isEnabled.invoke(null)) {
				toggle.invoke(null);
				minecraft.player.sendOverlayMessage(Component.translatable(film ? "jaymospowers.freecam.film" : "jaymospowers.freecam.creative_only"));
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			// a Freecam we can't read: leave it be
			isEnabled = null;
		}
	}
}
