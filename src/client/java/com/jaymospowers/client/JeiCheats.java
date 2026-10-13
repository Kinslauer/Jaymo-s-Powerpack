package com.jaymospowers.client;

import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.server.permissions.Permissions;
import org.jspecify.annotations.Nullable;

/**
 * JEI (optional): its cheat mode (click an item to take it instead of seeing its recipes) starts off every session, and
 * its only default switch is Ctrl + left-click on JEI's wrench button. So cheat mode turns on by itself whenever you
 * may cheat: in creative, or opped (in any game mode; joining that way counts), and off again when you no longer may.
 * In between, your own toggle is left alone. The server still decides who may cheat (JEI allows creative players and
 * ops by default).
 */
final class JeiCheats {
	private static @Nullable Object toggleState;
	private static @Nullable Method setCheats;
	/** Whether the player could cheat last tick (null: not in a world). */
	private static @Nullable Boolean couldCheat;

	private JeiCheats() {
	}

	static void init() {
		if (!FabricLoader.getInstance().isModLoaded("jei")) {
			return;
		}
		try {
			// JEI has no public API for this; its own toggle lives in Internal.getClientToggleState()
			toggleState = Class.forName("mezz.jei.common.Internal").getMethod("getClientToggleState").invoke(null);
			setCheats = Class.forName("mezz.jei.common.config.IClientToggleState").getMethod("setCheatItemsEnabled", boolean.class);
			ClientTickEvents.END_CLIENT_TICK.register(JeiCheats::tick);
		} catch (ReflectiveOperationException | LinkageError e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("JEI is loaded but its cheat toggle wasn't found; creative won't switch cheat mode on", e);
		}
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.gameMode == null) {
			couldCheat = null;
			return;
		}
		boolean may = minecraft.player.isCreative() || minecraft.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
		Boolean could = couldCheat;
		couldCheat = may;
		if (could == null ? !may : could == may) {
			return;
		}
		try {
			setCheats.invoke(toggleState, may);
		} catch (ReflectiveOperationException | RuntimeException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't switch JEI cheat mode", e);
		}
	}
}
