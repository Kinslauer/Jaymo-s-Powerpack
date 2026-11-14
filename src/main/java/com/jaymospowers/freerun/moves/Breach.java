package com.jaymospowers.freerun.moves;

import com.jaymospowers.freerun.Freerun;
import com.jaymospowers.stamina.Feathers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The breach leap (Andy's pick from Moves Like Mafuyu's swim push; the code is ours): swimming flat out at the surface
 * (a sprint swim, head above the water), Jump throws you out of the water along your look, head first with your arms
 * ahead ({@code DivePose}'s clean dive, drawn by {@code RunMovesPose}). You come down on the ground into a slide
 * ({@code Slide}, a head-first one) if you're still sprinting, or onto the water again, where a shallow enough landing
 * skips off the surface ({@link #skipped}). Costs {@link #COST} feathers; the client throws the body ({@code RunMovesClient}).
 */
public final class Breach {
	// ===== the knobs ================================================================================================
	/** Feathers (half-feathers) a breach costs. */
	public static final int COST = 3;
	/** The leap: its speed (blocks a tick), the least it looks up (so a level look still clears the water: sine of the angle). */
	public static final double SPEED = 0.85, MIN_LOOK_Y = 0.35;
	/** The ticks the dive pose shows from the leap. */
	public static final int POSE_TICKS = 14;
	/** Ticks before another breach, and the most ticks one leap may last before it's called over. */
	public static final int COOLDOWN = 20, FLIGHT_TICKS = 60;
	/**
	 * A skip off the water: coming down no faster than {@link #SKIP_MAX_DOWN} (blocks a tick) and no slower along it than
	 * {@link #SKIP_MIN_SPEED}, you bounce, keeping {@link #SKIP_KEEP} of the speed along and {@link #SKIP_BOUNCE} of the fall
	 * back up, at most {@link #SKIPS} times a leap.
	 */
	public static final double SKIP_MAX_DOWN = 0.55, SKIP_MIN_SPEED = 0.45, SKIP_KEEP = 0.85, SKIP_BOUNCE = 0.6;
	public static final int SKIPS = 2;
	/** A skip's least rise (blocks a tick) however shallow it came down, and the least speed along the ground (blocks a tick) a landing slides from. */
	public static final double SKIP_MIN_UP = 0.22, SLIDE_MIN_SPEED = 0.3;
	/** A slide this soon (ticks) after a breach is the head-first one. */
	public static final int SLIDE_WINDOW = 60;
	static final int GAP_TICKS = 14;
	// ================================================================================================================

	private static final Map<UUID, Long> LAST = new HashMap<>();

	private Breach() {
	}

	/** Whether {@code player} breached within the last {@code ticks} ticks (server). */
	public static boolean recent(ServerPlayer player, int ticks) {
		Long last = LAST.get(player.getUUID());
		return last != null && player.level().getGameTime() - last <= ticks;
	}

	static void leap(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long last = LAST.get(player.getUUID());
		if (last != null && now - last < GAP_TICKS || !player.isInWater() || player.isUnderWater() || player.isPassenger() || !Freerun.canMove(player)) {
			return;
		}
		if (!Feathers.spend(player, COST)) {
			return;
		}
		LAST.put(player.getUUID(), now);
		if (LAST.size() > 256) {
			LAST.entrySet().removeIf(e -> now - e.getValue() > 1200L);
		}
		RunMoves.pose(player, RunMoves.BREACH);
		splash(player, 24);
		player.addTag(Freerun.XP_TAG);
	}

	/** The client skipped off the water: shown again, a small splash (free; the leap already paid). */
	static void skipped(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long last = LAST.get(player.getUUID());
		if (last == null || now - last > FLIGHT_TICKS) {
			return;
		}
		RunMoves.pose(player, RunMoves.BREACH);
		splash(player, 12);
	}

	private static void splash(ServerPlayer player, int drops) {
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 0.2, player.getZ(), drops, 0.4, 0.1, 0.4, 0.2);
		level.sendParticles(ParticleTypes.BUBBLE, player.getX(), player.getY(), player.getZ(), drops / 2, 0.3, 0.1, 0.3, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.8F, 1.2F);
	}
}
