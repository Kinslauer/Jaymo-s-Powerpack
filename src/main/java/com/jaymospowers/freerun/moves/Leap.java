package com.jaymospowers.freerun.moves;

import com.jaymospowers.freerun.FastRun;
import com.jaymospowers.freerun.Freerun;
import com.jaymospowers.freerun.Slide;
import com.jaymospowers.stamina.Feathers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * The leap into prone (after Moves Like Mafuyu's idea; nothing of its code taken): just after a sprint jump, press the
 * Crawl key in the air (within {@link #WINDOW_TICKS} of leaving the ground) and you dive forward onto your belly with a burst
 * of speed, low and flat enough for a gap only a block and a half high, and land prone. Sneak at the jump is already the
 * long jump ({@link LongJump}), so this has its own key. The client throws the body and goes prone
 * ({@code RunMovesClient}, {@code CrawlTaps}); here the server checks the run, charges {@link #COST} feathers and shows the
 * dive ({@link RunMoves#pose}, drawn by {@code RunMovesPose}).
 */
public final class Leap {
	// ===== the knobs ================================================================================================
	/** Feathers a leap costs. */
	public static final int COST = 3;
	/** Client: the Crawl key counts this many ticks after a sprint jump leaves the ground. */
	public static final int WINDOW_TICKS = 12;
	/** The dive: the run's speed times {@link #BOOST}, at least {@link #MIN_SPEED} and at most {@link #MAX_SPEED} (blocks a tick), and the most it keeps of the jump's rise (blocks a tick; a jump is 0.42). */
	public static final double BOOST = 1.5, MIN_SPEED = 0.55, MAX_SPEED = 0.85, UP_CAP = 0.12;
	/** How long the body shows the dive in the air (ticks), at most; landing ends it sooner. */
	public static final int POSE_TICKS = 30;
	/** Server: a sprint this recent (ticks) still counts for the jump; at most one leap this often (ticks). */
	static final int SPRINT_GRACE_TICKS = 20, GAP_TICKS = 15;
	// ================================================================================================================

	private static final Map<UUID, Long> LAST = new HashMap<>();
	/** Client only: the local player's entity id and the game time of their own leap, so their own dive shows (and skips the going-prone animation) at once. */
	public static volatile int localId = -1;
	public static volatile long localAt = Long.MIN_VALUE / 2;

	private Leap() {
	}

	/** Whether {@code player} is in a leap's dive in the air (any side, from the synced pose). */
	public static boolean diving(Player player) {
		if (player.level().isClientSide() && player.getId() == localId && player.level().getGameTime() - localAt < POSE_TICKS && !player.onGround()) {
			return true;
		}
		long[] pose = RunMoves.pose(player);
		return pose != null && pose[0] == RunMoves.LEAP && player.level().getGameTime() - pose[1] < POSE_TICKS && !player.onGround();
	}

	static void jumped(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long last = LAST.get(player.getUUID());
		boolean running = player.isSprinting() || FastRun.active(player) || Slide.sprintedWithin(player, SPRINT_GRACE_TICKS);
		if (last != null && now - last < GAP_TICKS || !running || !Freerun.canMove(player)) {
			return;
		}
		if (!Feathers.spend(player, COST)) {
			return;
		}
		LAST.put(player.getUUID(), now);
		if (LAST.size() > 256) {
			LAST.entrySet().removeIf(e -> now - e.getValue() > 1200L);
		}
		RunMoves.pose(player, RunMoves.LEAP);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.4F, 0.9F);
		player.addTag(Freerun.XP_TAG);
	}
}
