package com.jaymoskingdoms.forms;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The bout bracket screen's packets ({@link Bouts}): the bracket ({@link Bracket}: where, who, how far you've come, and
 * whether the next round waits on you), and your answer ({@link Ready}: ready, or withdraw).
 */
public final class BoutNet {
	/** Server → player: {@code where}'s bracket; {@code fighters} in order (the last the bout master); rounds won so far; whether the next waits on you. */
	public record Bracket(Component where, Component you, List<Component> fighters, int won, boolean asking, boolean tavern) implements CustomPacketPayload {
		public static final Type<Bracket> TYPE = new Type<>(JaymosKingdoms.id("bout_bracket"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Bracket> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				ComponentSerialization.STREAM_CODEC.encode(buf, p.where);
				ComponentSerialization.STREAM_CODEC.encode(buf, p.you);
				buf.writeVarInt(p.fighters.size());
				for (Component c : p.fighters) {
					ComponentSerialization.STREAM_CODEC.encode(buf, c);
				}
				buf.writeVarInt(p.won);
				buf.writeBoolean(p.asking);
				buf.writeBoolean(p.tavern);
			},
			buf -> {
				Component where = ComponentSerialization.STREAM_CODEC.decode(buf);
				Component you = ComponentSerialization.STREAM_CODEC.decode(buf);
				int n = buf.readVarInt();
				List<Component> fighters = new ArrayList<>();
				for (int i = 0; i < n; i++) {
					fighters.add(ComponentSerialization.STREAM_CODEC.decode(buf));
				}
				return new Bracket(where, you, fighters, buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
			});

		@Override
		public Type<Bracket> type() {
			return TYPE;
		}
	}

	/** Player → server: ready for the next round (true), or withdrawing from the bracket (false). */
	public record Ready(boolean ready) implements CustomPacketPayload {
		public static final Type<Ready> TYPE = new Type<>(JaymosKingdoms.id("bout_ready"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Ready> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.ready), buf -> new Ready(buf.readBoolean()));

		@Override
		public Type<Ready> type() {
			return TYPE;
		}
	}

	private BoutNet() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Bracket.TYPE, Bracket.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Ready.TYPE, Ready.CODEC);
	}
}
