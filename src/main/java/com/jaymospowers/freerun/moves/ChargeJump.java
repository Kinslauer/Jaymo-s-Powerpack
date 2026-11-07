package com.jaymospowers.freerun.moves;

import com.jaymospowers.core.Ledge;
import com.jaymospowers.core.Powers;
import com.jaymospowers.freerun.Freerun;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.stamina.Feathers;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * The charge jump, for everyone (after ParCool's): standing still on the ground, hold Sneak to wind up (a bar fills
 * over the Feathers row), then press Jump to leap up to twice as high as a jump. Moving lets the charge go. It starts
 * only after {@link #START_TICKS} of a held Sneak, so a tap (the air flip's, the zoom's double tap) never charges.
 * A web slinger keeps Web's own charged leap ({@code WebSwing#tickChargeJump}) and never gets this one.
 * <p>The client jumps ({@code RunMovesClient}); here it pays: {@link #COST_MIN} feathers for a short wind-up, up to
 * {@link #COST_MAX} for a full one.
 */
public final class ChargeJump {
	// ===== the knobs ================================================================================================
	/** Sneak held this long (ticks) before the charge starts, and the charge that is full (ticks after that). */
	public static final int START_TICKS = 8, FULL_TICKS = 30;
	/** A charge shorter than this (ticks) is no charge: Jump is a plain jump. */
	public static final int MIN_TICKS = 6;
	/** The leap straight up (blocks a tick) at a full charge (a plain jump is 0.42; 0.6 clears about twice its height). */
	public static final double FULL_UP = 0.6;
	/** Moving faster than this (blocks a tick, along the ground) lets the charge go. */
	public static final double MOVE_CANCEL = 0.03;
	/** Feathers: a minimal charge costs {@link #COST_MIN}, a full one {@link #COST_MAX}. */
	public static final int COST_MIN = 1, COST_MAX = 4;
	// ================================================================================================================

	private ChargeJump() {
	}

	/** Whether {@code player} may charge a jump at all (both sides). */
	public static boolean allowed(Player player) {
		return !Powers.has(player, WebPower.INSTANCE) && Freerun.canMove(player) && Ledge.canHang(player);
	}

	/** The upward speed of a jump wound up for {@code ticks}. */
	public static double up(int ticks, double plain) {
		float share = Math.min(1.0F, ticks / (float)FULL_TICKS);
		return plain + (FULL_UP - plain) * share;
	}

	static void jumped(ServerPlayer player, int ticks) {
		if (ticks < MIN_TICKS || !allowed(player)) {
			return;
		}
		int cost = COST_MIN + Math.round((COST_MAX - COST_MIN) * Math.min(1.0F, ticks / (float)FULL_TICKS));
		if (!Feathers.spend(player, cost)) {
			return;
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.4F, 1.4F);
		player.level().sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.05, player.getZ(), 6, 0.3, 0.02, 0.3, 0.02);
		player.addTag(Freerun.XP_TAG);
	}
}
