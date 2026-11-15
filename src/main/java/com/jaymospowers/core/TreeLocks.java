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
		SECOND_WIND = "second_wind", BATTLE_CRY = "battle_cry", BOXING = "boxing", HAUL_BOUND = "haul_bound", MEDITATE = "meditate",
		BLOOD = "blood", ANGLER = "angler", ALCHEMIST = "alchemist";

	private static final Method HAS, REFUSE;

	static {
		Method has = null, refuse = null;
		if (FabricLoader.getInstance().isModLoaded("jaymoskingdoms")) {
			try {
				Class<?> locks = Class.forName("com.jaymoskingdoms.TreeLocks");
				has = locks.getMethod("has", Player.class, String.class);
				refuse = locks.getMethod("refuse", Player.class, String.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				has = null;
				refuse = null;
			}
		}
		HAS = has;
		REFUSE = refuse;
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

	/** {@link #has}, and if not, {@link #refuse}: whether {@code player} may go on. */
	public static boolean check(Player player, String lock) {
		if (has(player, lock)) {
			return true;
		}
		refuse(player, lock);
		return false;
	}
}
