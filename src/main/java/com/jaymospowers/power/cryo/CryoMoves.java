package com.jaymospowers.power.cryo;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.42) Which Cryokinesis move a player is making and since when, synced to everyone so every screen draws its pose
 * ({@code CryoPose}), as Smoke's {@code SmokeMoves}. Frost Breath is held and lasts while the breath does; the shards,
 * Flash Freeze and the Ice Wall are one-shots that play for {@link #length} ticks. Ice Slide and the Blizzard have synced
 * flags of their own ({@link CryoSlide#SLIDING}, {@link CryoBlizzard#STORM}).
 */
public final class CryoMoves {
	/** Held: Frost Breath (G). */
	public static final int BREATH = 1;
	/** One-shots: Ice Shards (V), Flash Freeze (B), Ice Wall (X). */
	public static final int SHARDS = 10, FREEZE = 11, WALL = 12;

	public record Move(int kind, long start) {
		public static final StreamCodec<ByteBuf, Move> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Move::kind, ByteBufCodecs.VAR_LONG,
			Move::start, Move::new);
	}

	/** On a Cryokinesis user making a move: synced to everyone, who draw its pose. */
	public static final AttachmentType<Move> MOVE = AttachmentRegistry.create(JaymosPowers.id("cryo_move"),
		builder -> builder.syncWith(Move.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private CryoMoves() {
	}

	/** How long a one-shot plays (ticks); 0 for a held move. */
	public static int length(int kind) {
		return switch (kind) {
			case SHARDS -> 14;
			case FREEZE -> 14;
			case WALL -> 16;
			default -> 0;
		};
	}

	public static @Nullable Move get(Player player) {
		return player.getAttached(MOVE);
	}

	static void begin(ServerPlayer player, int kind) {
		player.setAttached(MOVE, new Move(kind, player.level().getGameTime()));
	}

	/** Ends a held move, if it's still the one on. */
	static void end(ServerPlayer player, int kind) {
		Move move = player.getAttached(MOVE);
		if (move != null && move.kind() == kind) {
			player.removeAttached(MOVE);
		}
	}

	/** From the power's tick: a finished one-shot, or the breath once it has stopped, is dropped. */
	static void tick(ServerPlayer player) {
		Move move = player.getAttached(MOVE);
		if (move == null) {
			return;
		}
		int length = length(move.kind());
		boolean over = length > 0 ? player.level().getGameTime() - move.start() >= length
			: move.kind() != BREATH || !CryoBreath.breathing(player);
		if (over || !player.isAlive()) {
			player.removeAttached(MOVE);
		}
	}

	static void forget(ServerPlayer player) {
		if (player.getAttached(MOVE) != null) {
			player.removeAttached(MOVE);
		}
	}
}
