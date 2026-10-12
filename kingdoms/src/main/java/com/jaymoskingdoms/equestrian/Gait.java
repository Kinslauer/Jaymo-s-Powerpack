package com.jaymoskingdoms.equestrian;

/**
 * (0.5.34) A horse's five gaits, shifted on the scroll wheel while riding: each a share of the breed's top speed and
 * what it does to stamina a second (refilling it walking, draining it galloping). Sprint is a burst on top of the
 * gallop ({@link Equestrian#SPRINT_SPEED}, {@link Equestrian#SPRINT_DRAIN}). Synced by ordinal: only add at the end.
 */
public enum Gait {
	HALT(0.0, 8.0),
	WALK(0.32, 5.0),
	TROT(0.55, 1.5),
	CANTER(0.78, -1.5),
	GALLOP(1.0, -4.0);

	/** Share of the breed's top speed (at its own top gait). */
	public final double speed;
	/** Stamina a second while moving at it: refilled (+) or spent (-). */
	public final double perSecond;

	Gait(double speed, double perSecond) {
		this.speed = speed;
		this.perSecond = perSecond;
	}

	public static Gait byId(int id) {
		Gait[] all = values();
		return all[Math.max(0, Math.min(all.length - 1, id))];
	}

	public String key() {
		return "gait.jaymoskingdoms." + this.name().toLowerCase(java.util.Locale.ROOT);
	}
}
