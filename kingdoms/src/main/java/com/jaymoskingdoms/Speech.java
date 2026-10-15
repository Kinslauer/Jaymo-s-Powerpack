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
import org.jspecify.annotations.Nullable;

/**
 * Words spoken aloud in the world (a ruler's temper, a witness crying thief): they float above the speaker's head for
 * the players nearby and fade, instead of going to chat. The client half draws them ({@code SpeechBubbles}), and MCA
 * villagers' own talk goes the same way there. (0.7.20) Townsfolk speaking a tongue a player doesn't know
 * ({@link Languages}) come out as gibberish for that player.
 */
public final class Speech {
	/** Bubble colours (RGB): plain talk, a warning, an angry shout. */
	public static final int WHITE = 0xFFFFFF, GOLD = 0xFFD966, YELLOW = 0xFFFF66, RED = 0xFF6655;
	/** (0.7.51) How near (blocks) others see words said to one player. */
	static final double OVERHEARD_RANGE = 16.0;

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

	/** (0.7.20) {@code speaker} says {@code words} to {@code player} alone, slowly enough to follow in any tongue. */
	public static void sayTo(ServerLevel level, Entity speaker, ServerPlayer player, Component words, int color) {
		if (speaker instanceof IronGolem) {
			return;
		}
		if (ServerPlayNetworking.canSend(player, Said.TYPE)) {
			ServerPlayNetworking.send(player, new Said(speaker.getId(), words, color));
		} else {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.says", speaker.getDisplayName(), words).withColor(color));
		}
		overheard(level, speaker, player, words, color);
	}

	/**
	 * (0.7.51) Words said to {@code player} are seen by everyone near too (a bubble over the speaker's head, in the
	 * speaker's tongue for each of them): anyone can watch a conversation, as with {@link #say}. Bubbles only, no chat.
	 */
	public static void overheard(ServerLevel level, Entity speaker, ServerPlayer player, Component words, int color) {
		if (speaker instanceof IronGolem) {
			return;
		}
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, speaker.getBoundingBox().inflate(OVERHEARD_RANGE))) {
			if (p != player && ServerPlayNetworking.canSend(p, Said.TYPE)) {
				ServerPlayNetworking.send(p, new Said(speaker.getId(), Languages.heard(p, speaker, words), color));
			}
		}
	}

	/**
	 * (0.7.51) {@code speaker} says {@code words} to {@code player}, in a conversation of theirs (an approach, a quest, the
	 * wizard), in the speaker's tongue; everyone near sees it too ({@link #overheard}).
	 */
	public static void tell(ServerLevel level, Entity speaker, @Nullable ServerPlayer player, Component words, int color) {
		if (player == null || speaker instanceof IronGolem) {
			return;
		}
		if (ServerPlayNetworking.canSend(player, Said.TYPE)) {
			ServerPlayNetworking.send(player, new Said(speaker.getId(), Languages.heard(player, speaker, words), color));
		}
		overheard(level, speaker, player, words, color);
	}

	/** {@code speaker} says {@code words} aloud: every player within {@code range} sees it over their head. */
	public static void say(ServerLevel level, Entity speaker, Component words, int color, double range) {
		if (speaker instanceof IronGolem) {
			// (0.5.42) golems never speak
			return;
		}
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, speaker.getBoundingBox().inflate(range))) {
			// (0.7.20) in a tongue they don't speak, it's words they don't know
			Component heard = Languages.heard(p, speaker, words);
			if (ServerPlayNetworking.canSend(p, Said.TYPE)) {
				ServerPlayNetworking.send(p, new Said(speaker.getId(), heard, color));
			} else {
				// (a client without the mod: chat, as before)
				p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.says", speaker.getDisplayName(), heard)
					.withColor(color));
			}
		}
	}
}
