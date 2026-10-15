package com.jaymospowers.power.smoke;

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
 * Which Smoke move a player is making right now and since when, synced to everyone so every screen draws its pose
 * ({@code SmokeMovePose}). Held moves (Smokescreen, Smother, Pyroclasm's gather, Harness the Sky's holds) last while the
 * move does: {@link #tick} drops them as soon as the move itself has stopped. One-shots (a throw, a step, a release)
 * play for {@link #length} ticks and are then dropped. The Smoke Jet and Vapour Form have synced flags of their own.
 */
public final class SmokeMoves {
	/** Held: Smokescreen pouring (G), Smother (B), Pyroclasm gathering (C), Harness the Sky held (C), feeding a storm (sneak + C). */
	public static final int POUR = 1, SMOTHER = 2, GATHER = 3, SKY = 4, FEED = 5;
	/** One-shots: Smoke Bomb throw (V), Smoke Step's re-forming (sneak + X), Smoke Sight on (Z), Pyroclasm's release, the
	 * raincloud (tap C), Ash Storm (tap C, both fates), Vapour Form switched on (R). */
	public static final int BOMB = 10, STEP = 11, SIGHT = 12, PYRO = 13, RAIN = 14, ASH = 15, ARM = 16;
	/** A hard landing: the legs go to smoke for a second and spill out round you (no fall damage, {@code SmokeVapour}). */
	public static final int LAND = 17;

	public record Move(int kind, long start) {
		public static final StreamCodec<ByteBuf, Move> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Move::kind, ByteBufCodecs.VAR_LONG,
			Move::start, Move::new);
	}

	/** On a Smoke user making a move: synced to everyone, who draw its pose. */
	public static final AttachmentType<Move> MOVE = AttachmentRegistry.create(JaymosPowers.id("smoke_move"),
		builder -> builder.syncWith(Move.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private SmokeMoves() {
	}

	/** How long a one-shot plays (ticks); 0 for a held move. */
	public static int length(int kind) {
		return switch (kind) {
			case BOMB -> 12;
			case STEP -> 16;
			case SIGHT -> 18;
			case PYRO -> 16;
			case RAIN -> 18;
			case ASH -> 30;
			case ARM -> 12;
			case LAND -> 20;
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

	/** From the power's tick: a finished one-shot, or a held move whose move has stopped, is dropped. */
	static void tick(ServerPlayer player) {
		Move move = player.getAttached(MOVE);
		if (move == null) {
			return;
		}
		int length = length(move.kind());
		boolean over = length > 0 ? player.level().getGameTime() - move.start() >= length : !held(player, move.kind());
		if (over || !player.isAlive()) {
			player.removeAttached(MOVE);
		}
	}

	private static boolean held(ServerPlayer player, int kind) {
		return switch (kind) {
			case POUR -> SmokeScreen.pouring(player);
			case SMOTHER -> SmokeSmother.holding(player);
			case GATHER -> SmokePyroclasm.isGathering(player.getUUID());
			case SKY, FEED -> SmokeCloudshaper.holding(player);
			default -> false;
		};
	}

	static void forget(ServerPlayer player) {
		if (player.getAttached(MOVE) != null) {
			player.removeAttached(MOVE);
		}
	}
}
