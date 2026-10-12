package com.jaymoskingdoms;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.golem.IronGolem;

/**
 * Words spoken aloud in the world (a ruler's temper, a witness crying thief): they float above the speaker's head for
 * the players nearby and fade, instead of going to chat. The client half draws them ({@code SpeechBubbles}), and MCA
 * villagers' own talk goes the same way there.
 */
public final class Speech {
	/** Bubble colours (RGB): plain talk, a warning, an angry shout. */
	public static final int WHITE = 0xFFFFFF, GOLD = 0xFFD966, YELLOW = 0xFFFF66, RED = 0xFF6655;

	/** Server → nearby players: {@code entityId} says {@code text}, drawn in {@code color}. */
	public record Said(int entityId, Component text, int color) implements CustomPacketPayload {
		public static final Type<Said> TYPE = new Type<>(JaymosKingdoms.id("said"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Said> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.entityId);
				ComponentSerialization.STREAM_CODEC.encode(buf, p.text);
				buf.writeInt(p.color);
			},
			buf -> new Said(buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf), buf.readInt()));

		@Override
		public Type<Said> type() {
			return TYPE;
		}
	}

	private Speech() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Said.TYPE, Said.CODEC);
	}

	/** {@code speaker} says {@code words} aloud: every player within {@code range} sees it over their head. */
	public static void say(ServerLevel level, Entity speaker, Component words, int color, double range) {
		if (speaker instanceof IronGolem) {
			// (0.5.42) golems never speak
			return;
		}
		Said said = new Said(speaker.getId(), words, color);
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, speaker.getBoundingBox().inflate(range))) {
			if (ServerPlayNetworking.canSend(p, Said.TYPE)) {
				ServerPlayNetworking.send(p, said);
			} else {
				// (a client without the mod: chat, as before)
				p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.says", speaker.getDisplayName(), words)
					.withColor(color));
			}
		}
	}
}
