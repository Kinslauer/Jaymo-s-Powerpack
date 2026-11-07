package com.jaymospowers.freerun.moves;

import com.jaymospowers.freerun.FastRun;
import com.jaymospowers.freerun.Freerun;
import com.jaymospowers.stamina.Feathers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The long jump (after ParCool's): in a fast run ({@link FastRun}), tap Sneak as you jump (from {@link #TAP_BEFORE}
 * ticks before the jump to {@link #TAP_AFTER} after it) and you sail out long and flat, legs reaching ahead, over a gap
 * a plain jump wouldn't clear. A tap is a short press: a held Sneak stays the slide's, two taps the zoom's, and the air
 * flip waits its own tap out ({@code AirFlipClient} lets this one go). The client throws the body
 * ({@code RunMovesClient}); here it pays {@link #COST} feathers and is shown.
 */
public final class LongJump {
	// ===== the knobs ================================================================================================
	/** Feathers a long jump costs. */
	public static final int COST = 3;
	/** A Sneak tap this many ticks before the jump, or this many after it, makes it a long jump. */
	public static final int TAP_BEFORE = 4, TAP_AFTER = 3;
	/** The leap: the run's speed times {@link #BOOST}, at least {@link #MIN_SPEED} and at most {@link #MAX_SPEED} (blocks a tick), and lift ({@link #UP}; a jump is 0.42). */
	public static final double BOOST = 1.7, MIN_SPEED = 0.7, MAX_SPEED = 0.95, UP = 0.34;
	/** How long the body shows the leap (ticks). */
	public static final int POSE_TICKS = 16;
	/** Server: at most one this often (ticks). */
	static final int GAP_TICKS = 10;
	// ================================================================================================================

	private static final Map<UUID, Long> LAST = new HashMap<>();

	private LongJump() {
	}

	static void jumped(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long last = LAST.get(player.getUUID());
		if (last != null && now - last < GAP_TICKS || !FastRun.active(player) || !Freerun.canMove(player)) {
			return;
		}
		if (!Feathers.spend(player, COST)) {
			return;
		}
		LAST.put(player.getUUID(), now);
		if (LAST.size() > 256) {
			LAST.entrySet().removeIf(e -> now - e.getValue() > 1200L);
		}
		RunMoves.pose(player, RunMoves.LONG_JUMP);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.35F, 0.7F);
		player.addTag(Freerun.XP_TAG);
	}
}
