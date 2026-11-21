package com.jaymospowers.client.combat;

import com.jaymospowers.client.ScreenShake;
import com.jaymospowers.client.power.zfighter.ZFighterClientConfig;
import com.jaymospowers.combat.CritCue;
import com.mojang.brigadier.Command;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;

/**
 * The camera punch of a crit on this side ({@link CritCue.Punch}): a short shake of your own screen when you land a
 * special hit. Yours to turn off: {@code /critpunch off} (or {@code on}), kept in the client settings file.
 */
public final class CritPunchClient {
	static final String KEY = "crit.punch";

	private CritPunchClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(CritCue.Punch.TYPE, (payload, context) -> {
			if (on()) {
				ScreenShake.add(payload.degrees(), payload.ticks(), payload.from());
			}
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(ClientCommands.literal("critpunch")
			.executes(c -> {
				c.getSource().sendFeedback(Component.translatable(on() ? "message.jaymospowers.critpunch.on" : "message.jaymospowers.critpunch.off"));
				return Command.SINGLE_SUCCESS;
			})
			.then(ClientCommands.literal("on").executes(c -> set(c.getSource(), true)))
			.then(ClientCommands.literal("off").executes(c -> set(c.getSource(), false)))));
	}

	private static boolean on() {
		return !"off".equals(ZFighterClientConfig.get(KEY, "on"));
	}

	private static int set(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, boolean on) {
		ZFighterClientConfig.set(KEY, on ? "on" : "off");
		source.sendFeedback(Component.translatable(on ? "message.jaymospowers.critpunch.on" : "message.jaymospowers.critpunch.off"));
		return Command.SINGLE_SUCCESS;
	}
}
