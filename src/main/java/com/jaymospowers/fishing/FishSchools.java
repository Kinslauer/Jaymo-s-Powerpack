package com.jaymospowers.fishing;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Track C: schools and hunters (True Fishing's idea, our own code). Fish come in schools ({@link Species#schoolMin} to
 * {@link Species#schoolMax}, swimming together as vanilla's schooling fish do), and hungry predators hunt them
 * ({@link WildFish.Hunt}). Where a predator is hunting, the shoal scatters at the top and every fish round there is on the
 * feed: bait is noticed {@link #FRENZY_KEEN} times as often within {@link #FRENZY_RANGE} for {@link #FRENZY_TICKS}. So
 * an angler who watches for the boil fishes where the fishing is best.
 */
public final class FishSchools {
	static final float FRENZY_KEEN = 1.8F;
	static final double FRENZY_RANGE = 12.0;
	static final long FRENZY_TICKS = 20L * 60;

	private record Spot(ResourceKey<Level> level, BlockPos pos, long until) {
	}

	private static final List<Spot> SPOTS = new ArrayList<>();

	private FishSchools() {
	}

	/** A predator is hunting at {@code pos}. */
	static void frenzy(ServerLevel level, BlockPos pos) {
		long now = level.getGameTime();
		SPOTS.removeIf(s -> s.until < now || s.level == level.dimension() && s.pos.closerThan(pos, FRENZY_RANGE / 3));
		SPOTS.add(new Spot(level.dimension(), pos.immutable(), now + FRENZY_TICKS));
	}

	/** How much keener fish at {@code pos} are for predators hunting near. */
	static float keen(ServerLevel level, BlockPos pos) {
		long now = level.getGameTime();
		for (Spot s : SPOTS) {
			if (s.until >= now && s.level == level.dimension() && s.pos.closerThan(pos, FRENZY_RANGE)) {
				return FRENZY_KEEN;
			}
		}
		return 1.0F;
	}

	static void clear() {
		SPOTS.clear();
	}
}
