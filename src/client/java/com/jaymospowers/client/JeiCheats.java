package com.jaymospowers.client;

import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * JEI (optional): its cheat mode (click an item to take it instead of seeing its recipe) stays off.
 *
 * <p>(0.11.31) It used to switch on by itself in creative or opped. The server now allows nobody to cheat items out of
 * JEI ({@code jei-server.ini}), and with cheat mode on a left click asks for the item, is refused and does nothing, where
 * it should show the item's recipe. So whenever it's on (JEI's own switch, Ctrl + left-click on the wrench, included) it's
 * switched off again, and a left click always shows the recipe.
 */
final class JeiCheats {
	private static @Nullable Object toggleState;
	private static @Nullable Method isCheats, setCheats;

	private JeiCheats() {
	}

	static void init() {
		if (!FabricLoader.getInstance().isModLoaded("jei")) {
			return;
		}
		try {
			// JEI has no public API for this; its own toggle lives in Internal.getClientToggleState()
			toggleState = Class.forName("mezz.jei.common.Internal").getMethod("getClientToggleState").invoke(null);
			Class<?> state = Class.forName("mezz.jei.common.config.IClientToggleState");
			isCheats = state.getMethod("isCheatItemsEnabled");
			setCheats = state.getMethod("setCheatItemsEnabled", boolean.class);
			ClientTickEvents.END_CLIENT_TICK.register(JeiCheats::tick);
		} catch (ReflectiveOperationException | LinkageError e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("JEI is loaded but its cheat toggle wasn't found; cheat mode won't be kept off", e);
		}
	}

	private static void tick(Minecraft minecraft) {
		if (isCheats == null || setCheats == null) {
			return;
		}
		try {
			if (Boolean.TRUE.equals(isCheats.invoke(toggleState))) {
				setCheats.invoke(toggleState, false);
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			// (once: it won't work any better next tick)
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't switch JEI cheat mode off", e);
			isCheats = null;
		}
	}
}
