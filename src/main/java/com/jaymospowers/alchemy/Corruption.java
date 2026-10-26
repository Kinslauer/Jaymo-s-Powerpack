package com.jaymospowers.alchemy;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * A player's alchemical corruption: what flux does to a person. Three kinds, like layers:
 * <ul>
 * <li>{@link Kind#FLEETING}: from standing in flux or a spill; fades by itself within minutes.</li>
 * <li>{@link Kind#LINGERING}: from working with flux-heavy things (infusions, forbidden gear, essence enchanting);
 * fades very slowly, faster with the remedies in {@link com.jaymospowers.alchemy.flux.FluxEffects}.</li>
 * <li>{@link Kind#DEEP}: from forbidden research and the worst accidents; only circumstances in the world lift it.</li>
 * </ul>
 * Only the numbers live here; the effects, decay and cures are {@link com.jaymospowers.alchemy.flux.FluxEffects}.
 * Messages about it stay vague: the player discovers what it is in play.
 */
public final class Corruption {
	public enum Kind {
		FLEETING, LINGERING, DEEP
	}

	public record State(int fleeting, int lingering, int deep) {
		public static final State NONE = new State(0, 0, 0);
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("fleeting").forGetter(State::fleeting),
			Codec.INT.fieldOf("lingering").forGetter(State::lingering),
			Codec.INT.fieldOf("deep").forGetter(State::deep)).apply(i, State::new));
		public static final StreamCodec<io.netty.buffer.ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, State::fleeting, ByteBufCodecs.VAR_INT, State::lingering, ByteBufCodecs.VAR_INT, State::deep, State::new);

		public int total() {
			return fleeting + lingering + deep;
		}

		public int get(Kind kind) {
			return switch (kind) {
				case FLEETING -> fleeting;
				case LINGERING -> lingering;
				case DEEP -> deep;
			};
		}

		public State with(Kind kind, int value) {
			int v = Math.max(0, value);
			return switch (kind) {
				case FLEETING -> new State(v, lingering, deep);
				case LINGERING -> new State(fleeting, v, deep);
				case DEEP -> new State(fleeting, lingering, v);
			};
		}
	}

	/** Kept through death (the fleeting part is cleared on respawn by FluxEffects). Synced to its own player. */
	public static final AttachmentType<State> CORRUPTION = AttachmentRegistry.create(JaymosPowers.id("alchemy_corruption"),
		builder -> builder.persistent(State.CODEC).copyOnDeath().initializer(() -> State.NONE)
			.syncWith(State.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	private Corruption() {
	}

	public static State of(Player player) {
		State s = player.getAttached(CORRUPTION);
		return s == null ? State.NONE : s;
	}

	public static int total(Player player) {
		return of(player).total();
	}

	/** Adds (or, with a negative amount, takes away) corruption of one kind. Creative and spectators take none. */
	public static void add(ServerPlayer player, Kind kind, int amount) {
		if (amount > 0 && (player.isCreative() || player.isSpectator())) {
			return;
		}
		State s = of(player);
		player.setAttached(CORRUPTION, s.with(kind, s.get(kind) + amount));
	}
}
