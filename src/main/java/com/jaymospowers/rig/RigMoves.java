package com.jaymospowers.rig;

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
 * (0.11.107) What the Rig is doing, synced to everyone so they see it (client {@code RigPose}, {@code RigSuit}): the
 * move under way ({@link #MOVE}: a palm blast, a launch, the Unibeam) and the thrusters ({@link #THRUST}: hovering or
 * in full flight).
 */
public final class RigMoves {
	/** {@link Move} kinds. */
	public static final int PALM = 1, LAUNCHER = 2, UNIBEAM = 3;
	/** {@link #THRUST} values. */
	public static final int THRUST_NONE = 0, THRUST_HOVER = 1, THRUST_FLIGHT = 2;
	/** How long a palm blast's and a launch's poses play (ticks); the Unibeam's is its charge and beam and the let-down. */
	public static final int PALM_TICKS = 10, LAUNCHER_TICKS = 14, UNIBEAM_SETTLE = 6;

	public record Move(int kind, long start) {
		public static final StreamCodec<ByteBuf, Move> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Move::kind, ByteBufCodecs.VAR_LONG,
			Move::start, Move::new);
	}

	public static final AttachmentType<Move> MOVE = AttachmentRegistry.create(JaymosPowers.id("rig_move"),
		builder -> builder.syncWith(Move.STREAM_CODEC, AttachmentSyncPredicate.all()));
	public static final AttachmentType<Integer> THRUST = AttachmentRegistry.create(JaymosPowers.id("rig_thrust"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	private RigMoves() {
	}

	/** How long a move's pose plays (ticks). */
	public static int length(int kind) {
		return switch (kind) {
			case PALM -> PALM_TICKS;
			case LAUNCHER -> LAUNCHER_TICKS;
			case UNIBEAM -> RigUnibeam.CHARGE_TICKS + RigUnibeam.BEAM_TICKS + UNIBEAM_SETTLE;
			default -> 0;
		};
	}

	public static @Nullable Move get(Player player) {
		return player.getAttached(MOVE);
	}

	public static int thrust(Player player) {
		Integer thrust = player.getAttached(THRUST);
		return thrust == null ? THRUST_NONE : thrust;
	}

	static void start(ServerPlayer player, int kind) {
		player.setAttached(MOVE, new Move(kind, player.level().getGameTime()));
	}

	/** Every tick: a finished move comes off, and the thrusters' state goes out when it changes. */
	static void tick(ServerPlayer player, int thrust) {
		Move move = player.getAttached(MOVE);
		if (move != null && player.level().getGameTime() - move.start() > length(move.kind())) {
			player.removeAttached(MOVE);
		}
		if (thrust == THRUST_NONE) {
			if (player.getAttached(THRUST) != null) {
				player.removeAttached(THRUST);
			}
		} else if (thrust(player) != thrust) {
			player.setAttached(THRUST, thrust);
		}
	}
}
