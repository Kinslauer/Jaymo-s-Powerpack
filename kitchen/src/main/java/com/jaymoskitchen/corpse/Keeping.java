package com.jaymoskitchen.corpse;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * A body kept fresh: a priest's rite or a salt-and-herb wrap (Jaymo's Kingdoms puts the mark on) stops it rotting, and stops a
 * dead player or a zombie or skeleton's body getting back up, until the day on the mark. The mark is a tag on the corpse,
 * {@code jaymoskitchen_kept_<last day>} (days as {@link com.jaymoskitchen.fresh.Freshness#today} counts them), so other mods
 * read and write it without touching this class. A kept corpse's clock stands still (see {@code CorpseEntity#serverTick}):
 * the time does not count against it. Fire and butchering still work on it.
 */
public final class Keeping {
	public static final String TAG = "jaymoskitchen_kept_";

	private Keeping() {
	}

	/** Whether {@code body} is kept just now. */
	public static boolean kept(Entity body, Level level) {
		long today = com.jaymoskitchen.fresh.Freshness.today(level);
		for (String tag : body.entityTags()) {
			if (tag.startsWith(TAG)) {
				try {
					if (today <= Long.parseLong(tag.substring(TAG.length()))) {
						return true;
					}
				} catch (NumberFormatException e) {
					// (a mark nobody can read keeps nothing)
				}
			}
		}
		return false;
	}

	/** Keeps {@code body} until the end of day {@code lastDay}, in place of any earlier mark. */
	public static void keep(Entity body, long lastDay) {
		for (String tag : java.util.List.copyOf(body.entityTags())) {
			if (tag.startsWith(TAG)) {
				body.removeTag(tag);
			}
		}
		body.addTag(TAG + lastDay);
	}
}
