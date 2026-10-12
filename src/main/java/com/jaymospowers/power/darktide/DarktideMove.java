package com.jaymospowers.power.darktide;

import net.minecraft.world.entity.LivingEntity;

/**
 * The moves Darktide plays on bodies, by kind (the client's {@code DarktidePose} draws them). Set on the server, synced to
 * everyone through {@link DarktideState#MOVE}.
 */
public final class DarktideMove {
	/** Dark Strike's blow; variant bit 0: empowered, bit 1: a blade brought down from overhead (else a punch). */
	public static final int STRIKE = 1;
	/** Riposte: a slash from the shoulder across the body (freeze frame and flash on the clients). */
	public static final int RIPOSTE = 2;
	public static final int CRESCENT = 3;
	public static final int INK_CROSS = 4;
	/** Gedan stance held; the variant is the kamae (see {@link #GEDAN}..{@link #HASSO}). */
	public static final int STANCE = 5;
	/** The sunken slashes after a Gedan release. */
	public static final int STILLWATER = 6;
	public static final int POUNCE_AIM = 7;
	public static final int POUNCE_STAB = 8;
	public static final int BARRAGE = 9;
	public static final int DASH = 10;
	public static final int FLURRY = 11;
	/** The hand thrown out: Black Tide, Drown, Necromancy, Tide Arms. */
	public static final int CAST = 12;
	/** A creature reeling back (riposted, Gedan-parried). */
	public static final int REEL = 13;
	/** Sinking into water or shadow (Undertow). */
	public static final int SINK = 14;
	/** Staggered: the guard broke. */
	public static final int STAGGER = 15;
	/** Dark Strike charging (R held); variant {@link #CHARGE_BLADE} (blade raised overhead) or {@link #CHARGE_FIST} (fist drawn back). */
	public static final int CHARGE = 16;
	public static final int CHARGE_BLADE = 0, CHARGE_FIST = 1;

	/** The four kamae, in the order a Gedan parry steps through them. */
	public static final int GEDAN = 0, CHUDAN = 1, JODAN = 2, HASSO = 3;

	public static final int STRIKE_TICKS = 8;
	/** Dark Strike: ticks to full charge, and the longest it can be held before it strikes on its own. */
	public static final int CHARGE_FULL_TICKS = 20;
	public static final int CHARGE_HOLD_MAX = 600;
	public static final int RIPOSTE_TICKS = 10;
	public static final int CRESCENT_TICKS = 10;
	public static final int INK_CROSS_TICKS = 10;
	public static final int POUNCE_STAB_TICKS = 10;
	public static final int CAST_TICKS = 14;
	public static final int REEL_TICKS = 14;
	public static final int STAGGER_TICKS = 12;

	private DarktideMove() {
	}

	static void play(LivingEntity entity, int kind, int duration, int variant) {
		entity.setAttached(DarktideState.MOVE, new DarktideState.Move(kind, entity.level().getGameTime(), duration, variant));
	}

	static void clear(LivingEntity entity) {
		entity.removeAttached(DarktideState.MOVE);
	}

	/** The move playing on {@code entity} right now, or null. */
	public static DarktideState.Move current(LivingEntity entity) {
		DarktideState.Move move = entity.getAttached(DarktideState.MOVE);
		if (move == null) {
			return null;
		}
		long t = entity.level().getGameTime() - move.start();
		return t >= 0 && t < move.duration() ? move : null;
	}
}
