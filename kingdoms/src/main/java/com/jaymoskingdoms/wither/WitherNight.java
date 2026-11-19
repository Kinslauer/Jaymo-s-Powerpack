package com.jaymoskingdoms.wither;

import com.jaymoskingdoms.JaymosKingdoms;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Wither's coming (lore: the sky goes dark over the whole land and stays dark while it lives). The server tells every
 * player when a Wither stands anywhere (and anyone who joins); the client's DarkNights then holds the lightmap at its
 * darkest, day or night, until it dies.
 */
public final class WitherNight {
	/** Server to player: whether a Wither lives. */
	public record Sync(boolean on) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(JaymosKingdoms.id("wither_night"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.on), buf -> new Sync(buf.readBoolean()));

		@Override
		public Type<Sync> type() {
			return TYPE;
		}
	}

	/** The client's side: a Wither lives, as last told. */
	public static volatile boolean clientOn;

	private static boolean sent;

	private WitherNight() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (sent) {
				ServerPlayNetworking.send(handler.player, new Sync(true));
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> sent = false);
	}

	/** Called every tick with whether any Wither lives: sends the change. */
	static void update(MinecraftServer server, boolean lives) {
		if (lives == sent) {
			return;
		}
		sent = lives;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, new Sync(lives));
		}
	}
}
