package com.jaymospowers.core;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.net.PowerPayloads;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Wires the core into Fabric's events. */
public final class PowerEvents {
	private PowerEvents() {
	}

	public static void register() {
		PowerPayloads.register();
		ServerPlayNetworking.registerGlobalReceiver(PowerPayloads.KeyInput.TYPE, (payload, ctx) -> {
			PowerKey key = PowerKey.byIndex(payload.key());
			// (0.14.1) a downed player's powers are out of reach (a key let go still goes through)
			if (key != null && !(payload.down() && Downed.isDowned(ctx.player()))) {
				Powers.key(ctx.player(), key, payload.down());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(PowerPayloads.ModeKey.TYPE,
			(payload, ctx) -> {
				if (!(payload.down() && Downed.isDowned(ctx.player()))) {
					Powers.mode(ctx.player(), payload.down());
				}
			});
		ServerPlayNetworking.registerGlobalReceiver(PowerPayloads.UltimateKey.TYPE,
			(payload, ctx) -> {
				if (!(payload.down() && Downed.isDowned(ctx.player()))) {
					Powers.ultimate(ctx.player(), payload.down());
				}
			});
		ServerPlayNetworking.registerGlobalReceiver(PowerPayloads.ChooseAffinity.TYPE,
			(payload, ctx) -> Affinity.choose(ctx.player(), payload.affinity()));
		ServerPlayNetworking.registerGlobalReceiver(PowerPayloads.ChoosePower.TYPE,
			(payload, ctx) -> Powers.choose(ctx.player(), payload.power()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			PowerInput.clear(handler.player);
			Mastery.forget(handler.player);
			Affinity.forget(handler.player);
		});
		Callings.register();
		ServerTickEvents.END_SERVER_TICK.register(Powers::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> PowerCommand.register(dispatcher));
	}
}
