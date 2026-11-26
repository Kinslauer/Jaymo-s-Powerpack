package com.jaymospowers.core.wounds;

import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;

/**
 * The line a swing cuts across the body. Better Combat tells the server which attack animation a player starts
 * ({@code IncomingSwingMixin}); its name says how the blade travels (across, down, slanting, or a thrust). A cut made
 * soon after leaves a mark at that angle ({@link WoundMarks}); with no swing noted (a mob, a plain hit) the mark takes
 * a random angle as it always has.
 */
public final class SwingCuts {
	/** A swing's angle counts for this long after it starts (ticks). */
	public static final int LIFE = 40;
	/** A sideways cut tilts this far from level and a downward one from upright (of 255 for a half turn); each cut strays up to {@link #JITTER}. */
	public static final int TILT = 14, SLANT = 28, JITTER = 7;

	private record Swing(String animation, long tick) {
	}

	private static final Map<LivingEntity, Swing> SWINGS = new WeakHashMap<>();

	private SwingCuts() {
	}

	/** {@code attacker} started the attack animation {@code animation} at {@code now}. */
	public static synchronized void note(LivingEntity attacker, String animation, long now) {
		SWINGS.put(attacker, new Swing(animation.toLowerCase(Locale.ROOT), now));
	}

	/** The angle of a cut by {@code attacker} (0 to 255 for a half turn, 0 level, 128 upright), or -1 when it has no line (a thrust, or no swing noted). */
	public static synchronized int angle(LivingEntity attacker, long now) {
		Swing swing = SWINGS.get(attacker);
		if (swing == null || now - swing.tick() > LIFE) {
			return -1;
		}
		String n = swing.animation();
		if (n.contains("thrust") || n.contains("stab") || n.contains("pierce") || n.contains("punch") || n.contains("jab") || n.contains("poke")) {
			return -1;
		}
		boolean left = n.contains("left"), right = n.contains("right");
		int base;
		if (n.contains("vertical") || n.contains("overhead") || n.contains("slam") || n.contains("chop") || n.contains("uppercut") || n.contains("downward")) {
			base = 128 + (left ? -SLANT / 2 : right ? SLANT / 2 : 0);
		} else if (n.contains("diagonal") || n.contains("slant")) {
			base = left ? 64 : 192;
		} else {
			base = left ? TILT : right ? 255 - TILT : 0;
		}
		return (base + attacker.getRandom().nextInt(2 * JITTER + 1) - JITTER) & 0xFF;
	}
}
