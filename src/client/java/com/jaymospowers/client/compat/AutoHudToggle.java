package com.jaymospowers.client.compat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.power.zfighter.ZFighterClientConfig;
import com.mojang.brigadier.Command;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-27): "can you make disabling autohud a command". {@code /autohud off} stops Auto HUD (LGPL-3.0, in the
 * pack) hiding the HUD, so everything stays on screen; {@code /autohud on} lets it fade parts out again, and a bare
 * {@code /autohud} says which it is. The choice is kept on this computer and put back each time the game starts. Until
 * someone uses the command, Auto HUD's own "Enable on game load" setting decides, as before. Its toggle key still flips
 * it for the session. Auto HUD is looked up by name, so without it the command only says it isn't installed.
 */
public final class AutoHudToggle {
	/** The setting, in {@code config/jaymospowers-client.properties}: "true" off, "false" on, missing = Auto HUD decides. */
	static final String OFF = "autohud.off";

	private static @Nullable MethodHandle setDynamic, actDynamic;
	private static boolean looked;

	private AutoHudToggle() {
	}

	public static void init() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(ClientCommands.literal("autohud")
			.executes(c -> now(c.getSource()))
			.then(ClientCommands.literal("on").executes(c -> set(c.getSource(), true)))
			.then(ClientCommands.literal("off").executes(c -> set(c.getSource(), false)))));
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			String saved = ZFighterClientConfig.get(OFF, "");
			if (!saved.isEmpty()) {
				apply(!"true".equals(saved));
			}
		});
	}

	private static int set(FabricClientCommandSource source, boolean on) {
		if (!apply(on)) {
			source.sendError(Component.translatable("message.jaymospowers.autohud.missing"));
			return 0;
		}
		ZFighterClientConfig.set(OFF, on ? "false" : "true");
		return said(source, on);
	}

	/** Says whether Auto HUD is hiding the HUD right now (its key may have flipped it since the command). */
	private static int now(FabricClientCommandSource source) {
		look();
		if (actDynamic == null) {
			source.sendError(Component.translatable("message.jaymospowers.autohud.missing"));
			return 0;
		}
		try {
			return said(source, (boolean)actDynamic.invoke());
		} catch (Throwable e) {
			return said(source, !"true".equals(ZFighterClientConfig.get(OFF, "false")));
		}
	}

	private static int said(FabricClientCommandSource source, boolean on) {
		source.sendFeedback(Component.translatable(on ? "message.jaymospowers.autohud.on" : "message.jaymospowers.autohud.off"));
		return Command.SINGLE_SUCCESS;
	}

	/** Turns Auto HUD's hiding on or off now. False when Auto HUD isn't there (or its switch wasn't found). */
	private static boolean apply(boolean on) {
		look();
		if (setDynamic == null) {
			return false;
		}
		try {
			setDynamic.invoke(on);
			return true;
		} catch (Throwable e) {
			JaymosPowers.LOGGER.warn("Auto HUD didn't take /autohud {}", on ? "on" : "off", e);
			return false;
		}
	}

	private static void look() {
		if (looked) {
			return;
		}
		looked = true;
		if (FabricLoader.getInstance().isModLoaded("autohud")) {
			try {
				Class<?> hud = Class.forName("mod.crend.autohud.hud.Hud");
				MethodHandles.Lookup lookup = MethodHandles.publicLookup();
				setDynamic = lookup.findStatic(hud, "setDynamic", MethodType.methodType(void.class, boolean.class));
				actDynamic = lookup.findStatic(hud, "actDynamic", MethodType.methodType(boolean.class));
			} catch (ReflectiveOperationException | RuntimeException e) {
				JaymosPowers.LOGGER.warn("Auto HUD's on/off switch wasn't found; /autohud can't change it", e);
			}
		}
	}
}
