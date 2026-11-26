package com.jaymospowers.core;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Player;

/**
 * Andy's skill-tree locks, as Jaymo's Powers sees them: moves and gear every player used to have from the start are learned
 * in Jaymo's Kingdoms' skill tree now (its {@code TreeLocks}, by reflection, so Powers never compiles against Kingdoms).
 * Without Kingdoms nothing here is locked. Both sides may ask: the tree's ranks are synced to their own player.
 * <p>
 * The names are Kingdoms' {@code TreeLocks.Lock} keys, kept here as constants so a typo can't quietly open a lock.
 */
public final class TreeLocks {
	public static final String SLIDE = "slide", LONG_JUMP = "long_jump", CHARGE_JUMP = "charge_jump", UP_RUN = "up_run",
		TRICK_JUMP = "trick_jump", SWIM_BURST = "swim_burst", BREACH = "breach", SHIELD_VAULT = "shield_vault",
		PARAGLIDER = "paraglider", BALLOON = "balloon", AIM_PARTS = "aim_parts", SHIELD_THROW = "shield_throw",
		SHIELD_MOVES = "shield_moves", SWAT = "swat", TWO_BLADES = "two_blades", THROWER = "thrower", SWEEP_KICK = "sweep_kick",
		AIR_COMBO = "air_combo", BATTLE_RHYTHM = "battle_rhythm",
		SECOND_WIND = "second_wind", BATTLE_CRY = "battle_cry", BOXING = "boxing", HAUL_BOUND = "haul_bound", MEDITATE = "meditate",
		ANGLER = "angler", ALCHEMIST = "alchemist", COYOTE_TIME = "coyote_time";
	/**
	 * Blood Awakening, learned (Andy, 2026-11-23): a blood's own moves are everyone's from the start, and the perk upgrades
	 * them. Every wait on one is this share as long (Kingdoms' {@code TreeLocks.AWAKENED_WAIT}).
	 */
	public static final float AWAKENED_WAIT = 0.5F;

	private static final Method HAS, REFUSE, AWAKENED;

	static {
		Method has = null, refuse = null, awakened = null;
		if (FabricLoader.getInstance().isModLoaded("jaymoskingdoms")) {
			try {
				Class<?> locks = Class.forName("com.jaymoskingdoms.TreeLocks");
				has = locks.getMethod("has", Player.class, String.class);
				refuse = locks.getMethod("refuse", Player.class, String.class);
				awakened = locks.getMethod("awakened", Player.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				has = null;
				refuse = null;
				awakened = null;
			}
		}
		HAS = has;
		REFUSE = refuse;
		AWAKENED = awakened;
	}

	private TreeLocks() {
	}

	/** Whether {@code player} has learned what {@code lock} opens (true without Kingdoms). */
	public static boolean has(Player player, String lock) {
		if (HAS == null) {
			return true;
		}
		try {
			return (boolean)HAS.invoke(null, player, lock);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return true;
		}
	}

	/** Tells {@code player} {@code lock} isn't learned: a line on the action bar naming the perk, a dull sound. */
	public static void refuse(Player player, String lock) {
		if (REFUSE == null) {
			return;
		}
		try {
			REFUSE.invoke(null, player, lock);
		} catch (ReflectiveOperationException | RuntimeException e) {
			// nothing to tell
		}
	}

	/** Whether {@code player} has learned Blood Awakening (false without Kingdoms). */
	public static boolean awakened(Player player) {
		if (AWAKENED == null) {
			return false;
		}
		try {
			return (boolean)AWAKENED.invoke(null, player);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** {@code ticks} as {@code player} waits them on a blood's move: {@link #AWAKENED_WAIT} of it once they're {@link #awakened}. */
	public static int wait(Player player, int ticks) {
		return awakened(player) ? Math.max(1, Math.round(ticks * AWAKENED_WAIT)) : ticks;
	}

	/** {@link #has}, and if not, {@link #refuse}: whether {@code player} may go on. */
	public static boolean check(Player player, String lock) {
		if (has(player, lock)) {
			return true;
		}
		refuse(player, lock);
		return false;
	}
}
