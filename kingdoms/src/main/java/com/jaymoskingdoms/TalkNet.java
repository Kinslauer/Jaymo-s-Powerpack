package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * (0.5.39) The packets of {@link Approaches}: someone who came over to ask the player something sends the question and
 * its answers ({@link Ask}, some greyed out: no emeralds for the fine, no food to give); the player's pick comes back
 * ({@link Answer}; -1: they shut the screen without answering).
 */
public final class TalkNet {
	/** Server → player: {@code entityId} ({@code speaker}) asks {@code words}; the answers, and which can be picked. */
	public record Ask(int entityId, Component speaker, Component words, List<Component> options, List<Boolean> enabled) implements CustomPacketPayload {
		public static final Type<Ask> TYPE = new Type<>(JaymosKingdoms.id("talk_ask"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Ask> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.entityId);
				ComponentSerialization.STREAM_CODEC.encode(buf, p.speaker);
				ComponentSerialization.STREAM_CODEC.encode(buf, p.words);
				buf.writeVarInt(p.options.size());
				for (int i = 0; i < p.options.size(); i++) {
					ComponentSerialization.STREAM_CODEC.encode(buf, p.options.get(i));
					buf.writeBoolean(p.enabled.get(i));
				}
			},
			buf -> {
				int entity = buf.readVarInt();
				Component speaker = ComponentSerialization.STREAM_CODEC.decode(buf);
				Component words = ComponentSerialization.STREAM_CODEC.decode(buf);
				int n = buf.readVarInt();
				List<Component> options = new ArrayList<>();
				List<Boolean> enabled = new ArrayList<>();
				for (int i = 0; i < n; i++) {
					options.add(ComponentSerialization.STREAM_CODEC.decode(buf));
					enabled.add(buf.readBoolean());
				}
				return new Ask(entity, speaker, words, options, enabled);
			});

		@Override
		public Type<Ask> type() {
			return TYPE;
		}
	}

	/** Player → server: they picked answer {@code option} (-1: none) to {@code entityId}'s question. */
	public record Answer(int entityId, int option) implements CustomPacketPayload {
		public static final Type<Answer> TYPE = new Type<>(JaymosKingdoms.id("talk_answer"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Answer> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.entityId);
				buf.writeVarInt(p.option + 1);
			},
			buf -> new Answer(buf.readVarInt(), buf.readVarInt() - 1));

		@Override
		public Type<Answer> type() {
			return TYPE;
		}
	}

	private TalkNet() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Ask.TYPE, Ask.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Answer.TYPE, Answer.CODEC);
	}
}
