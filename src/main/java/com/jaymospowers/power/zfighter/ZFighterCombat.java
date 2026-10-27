package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * (0.17.45, Andy's NOEA picks) The combat feel of round two, wired in one place: lock-on ({@link ZFighterLock}), poise and
 * K.O. ({@link ZFighterPoise}), the rush attacks ({@link ZFighterRush}), grab and throw ({@link ZFighterGrab}), the
 * meditation dodge ({@link ZFighterSidestep}) and the ki weapons ({@link ZFighterKiWeapon}). {@link #init} is the one
 * call from {@link ZFighterPower#init}; {@link #quick} the one from G's handler.
 */
public final class ZFighterCombat {
	/** Client to server: the training screen's Shape button for the ki weapon. */
	public record Shape() implements CustomPacketPayload {
		public static final Type<Shape> TYPE = new Type<>(JaymosPowers.id("zfighter_ki_weapon_shape"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Shape> CODEC = StreamCodec.unit(new Shape());

		@Override
		public Type<Shape> type() {
			return TYPE;
		}
	}

	private ZFighterCombat() {
	}

	public static void init() {
		ZFighterLock.register();
		ZFighterPoise.init();
		ZFighterGrab.init();
		ZFighterSidestep.init();
		PayloadTypeRegistry.serverboundPlay().register(Shape.TYPE, Shape.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Shape.TYPE, (payload, ctx) -> ZFighterKiWeapon.cycle(ctx.player()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ZFighterPoise.tick();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (!Powers.has(player, ZFighterPower.INSTANCE) || !player.isAlive()) {
					drop(player);
					continue;
				}
				ZFighterPower.Live live = ZFighterPower.live(player);
				ZFighterLock.tick(player);
				ZFighterRush.tick(player, live);
				ZFighterGrab.tick(player);
				ZFighterKiWeapon.tick(player, live);
				ZFighterSidestep.tick(player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			drop(handler.player);
			ZFighterPoise.drop(handler.player);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> ZFighterPoise.clear());
	}

	/** He lost the power, died or left: everything of his that was running ends. */
	static void drop(ServerPlayer player) {
		ZFighterLock.drop(player);
		ZFighterRush.drop(player);
		ZFighterGrab.drop(player);
		if (ZFighterKiWeapon.out(player) >= 0) {
			ZFighterKiWeapon.sheathe(player, false);
		}
	}

	/**
	 * G, when a rush attack or the ki weapon is on it: does it. False if it is some other technique's turn. The caller has
	 * checked G is ready.
	 */
	static boolean quick(ServerPlayer player, ZFighterPower.Live live) {
		int technique = ZFighterTraining.of(player).quick();
		if (technique < ZFighterTraining.DRAGON_RUSH || technique > ZFighterTraining.KI_WEAPON) {
			return false;
		}
		if (ZFighterPoise.stunned(player)) {
			return true;
		}
		if (technique == ZFighterTraining.KI_WEAPON) {
			ZFighterKiWeapon.toggle(player, live);
			ZFighterPower.cooldown(player, ZFighterState.BLAST, ZFighterKiWeapon.TOGGLE_COOLDOWN);
			return true;
		}
		int cooldown = ZFighterRush.start(player, live, technique);
		if (cooldown > 0) {
			ZFighterPower.cooldown(player, ZFighterState.BLAST, cooldown);
		}
		return true;
	}
}
