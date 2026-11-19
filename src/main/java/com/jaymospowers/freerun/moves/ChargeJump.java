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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * The charge jump, for everyone (after ParCool's): standing still on the ground with Sneak held (for {@link #START_TICKS},
 * so a tap never arms it), the XP bar turns into a horse's jump bar. Hold Jump to fill it and let go to leap at the charge
 * it shows, up to twice as high as a jump. Andy: "peak of the jump is orange. everyone can do it again, but the orange
 * stays consistent with the skill. randomized for everyone else." The bar's orange stretch is the full leap; let go
 * anywhere else and the leap tops out at {@link #MISS_SHARE} of it. With the Charge Jump perk the orange always sits at
 * {@link #PEAK_SKILLED}; without it, it lands somewhere new on every charge. Moving lets the charge go.
 * A web slinger keeps Web's own charged leap ({@code WebSwing#tickChargeJump}) and never gets this one.
 * <p>The client jumps ({@code RunMovesClient}); here it pays: {@link #COST_MIN} feathers for a short wind-up, up to
 * {@link #COST_MAX} for a full one.
 */
public final class ChargeJump {
	// ===== the knobs ================================================================================================
	/** Sneak held this long (ticks) before the bar shows, and the Jump held (ticks) that fills it. */
	public static final int START_TICKS = 8, FULL_TICKS = 30;
	/** A charge shorter than this (ticks) is no charge: letting go of Jump is a plain jump. */
	public static final int MIN_TICKS = 6;
	/** The leap straight up (blocks a tick) at a full charge (a plain jump is 0.42; 0.6 clears about twice its height). */
	public static final double FULL_UP = 0.6;
	/** Moving faster than this (blocks a tick, along the ground) lets the charge go. */
	public static final double MOVE_CANCEL = 0.03;
	/** Feathers: a minimal charge costs {@link #COST_MIN}, a full one {@link #COST_MAX}. */
	public static final int COST_MIN = 1, COST_MAX = 4;
	/** The orange peak: how much of the bar it covers, and where it starts with the perk (share of the bar). */
	public static final float PEAK_WIDTH = 0.15F, PEAK_SKILLED = 0.7F;
	/** Without the perk the peak starts anywhere from {@link #PEAK_RANDOM_MIN} to {@link #PEAK_RANDOM_MAX} of the bar. */
	public static final float PEAK_RANDOM_MIN = 0.3F, PEAK_RANDOM_MAX = 0.85F;
	/** The most a leap let go outside the peak gets (share of the full leap's extra height); short of the peak it builds up to this. */
	public static final float MISS_SHARE = 0.7F;
	// ================================================================================================================

	private ChargeJump() {
	}

	/** Whether {@code player} may charge a jump at all (both sides). Everyone may; the perk only steadies the peak. */
	public static boolean allowed(Player player) {
		return !Powers.has(player, WebPower.INSTANCE) && Freerun.canMove(player) && Ledge.canHang(player);
	}

	/** (Andy's skill-tree locks) Whether {@code player} has the Charge Jump perk (Stealth's Freerunning page): a steady peak (both sides). */
	public static boolean learned(Player player) {
		return com.jaymospowers.core.TreeLocks.has(player, com.jaymospowers.core.TreeLocks.CHARGE_JUMP);
	}

	/** Where this charge's orange peak starts (share of the bar): the same spot every time with the perk, anywhere without. */
	public static float peak(Player player, RandomSource random) {
		return learned(player) ? PEAK_SKILLED : PEAK_RANDOM_MIN + random.nextFloat() * (PEAK_RANDOM_MAX - PEAK_RANDOM_MIN);
	}

	/** How much of the full leap a bar filled to {@code fill} gives, with the peak starting at {@code peak}. */
	public static float share(float fill, float peak) {
		if (fill >= peak && fill <= peak + PEAK_WIDTH) {
			return 1.0F;
		}
		return MISS_SHARE * Math.min(1.0F, fill / peak);
	}

	/** The upward speed of a leap given {@code share} of the full leap's extra height over a {@code plain} jump. */
	public static double up(float share, double plain) {
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
