package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * (0.6.9) Settlements have little ones. MCA only lets its couples have babies while a village has beds to spare, so a
 * full one never does, and nobody ever saw a toddler. Now, while a realm's settlement has fewer than one young one
 * (toddler, child or teen, or one on the way) for every {@link #GROWN_PER_YOUNG} grown, one of its couples has a baby
 * now and then; born a toddler, as MCA's are. A settlement with none at all has its first due within a few minutes,
 * and one without a couple to have it takes in a toddler (once a day at most).
 */
final class Families {
	/** How often each settlement is looked at (ticks), and the chance then that a couple has a baby. */
	static final int EVERY = 1200;
	static final float BABY_CHANCE = 0.25F;
	/** Wanted: a young one for this many grown; a settlement smaller than this many grown is left to MCA. */
	static final int GROWN_PER_YOUNG = 4, LEAST_GROWN = 4;
	/** A settlement's first baby is due this soon (seconds; MCA's pregnancies count 60 a second). */
	static final int FIRST_DUE_SECONDS = 150;
	/** A toddler is taken in at most this often per settlement (ticks). */
	static final long TAKE_IN_TICKS = 24000L;

	/** Per faction: when it last took in a toddler. */
	private static final Map<Integer, Long> TAKEN_IN = new HashMap<>();

	private Families() {
	}

	static void register() {
		if (!McaCompat.LOADED) {
			return;
		}
		ServerTickEvents.END_SERVER_TICK.register(Families::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TAKEN_IN.clear());
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || !f.type().isRealm() || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			Long last = TAKEN_IN.get(f.id());
			boolean takeIn = last == null || now - last >= TAKE_IN_TICKS;
			AABB box = new AABB(f.seat()).inflate(Economy.POPULATION_RANGE);
			// (always try when there are none at all; else now and then)
			int done = McaCompat.families(level, box, GROWN_PER_YOUNG, LEAST_GROWN, BABY_CHANCE, FIRST_DUE_SECONDS * 60, takeIn);
			if (done == 2) {
				TAKEN_IN.put(f.id(), now);
				JaymosKingdoms.LOGGER.debug("{} took in a toddler", f.name());
			} else if (done == 1) {
				JaymosKingdoms.LOGGER.debug("a baby is on the way in {}", f.name());
			}
		}
	}
}
