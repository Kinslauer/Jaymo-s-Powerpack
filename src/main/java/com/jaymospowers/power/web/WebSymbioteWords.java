package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * The symbiote's words on a plate: its whispers go to the client in this packet and are drawn low and centred on a dark
 * plate (WebSymbioteHud), not on the action bar. A client without the mod's packet (or a server without the client
 * mod) gets the action bar as before.
 */
public final class WebSymbioteWords {
	/** Server to client: a line it says, already in its voice's colour and italics. */
	public record Words(Component text) implements CustomPacketPayload {
		public static final Type<Words> TYPE = new Type<>(JaymosPowers.id("web_symbiote_words"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Words> CODEC = ComponentSerialization.STREAM_CODEC.map(Words::new, Words::text);

		@Override
		public Type<Words> type() {
			return TYPE;
		}
	}

	private WebSymbioteWords() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Words.TYPE, Words.CODEC);
	}

	/** Shows {@code line} to {@code player} on the plate, or on the action bar if their client can't draw it. */
	static void say(ServerPlayer player, Component line) {
		if (ServerPlayNetworking.canSend(player, Words.TYPE)) {
			ServerPlayNetworking.send(player, new Words(line));
		} else {
			player.sendOverlayMessage(line);
		}
	}
}
