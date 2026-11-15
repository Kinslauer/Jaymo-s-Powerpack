package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;

/**
 * (am-upgrades, medieval picks; Armed Mobs' fortification upgrades, an idea only) A lord who has been threatened (a
 * siege on his seat, broken or not; a raid on his town) spends on his defences: a gatehouse, barracks or towers out of
 * the treasury, put on his builders' list ({@link Commissions#give}) for them to build like any other. At most once in
 * {@link #GAP_TICKS}, and only what the treasury can pay for ({@link #COST}); his ruler says so to those about him.
 */
final class Defences {
	/** What he buys, in the order he wants it. */
	static final String[] WANTS = {"gatehouse", "tower", "barracks"};
	/** What each costs the treasury (silver), and the least a lord keeps back after paying. */
	static final long COST = 24, KEEP_BACK = 10;
	/** A lord spends on defences at most this often (ticks). */
	static final long GAP_TICKS = 24000L * 2;
	/** Those this near the ruler hear him say so. */
	static final double SAY_RANGE = 24.0;
	static final int LINES = 2;

	private static final Map<Integer, Long> LAST = new HashMap<>();

	private Defences() {
	}

	static void clear() {
		LAST.clear();
	}

	/** {@code f} was threatened: if it can spare it, a new defence for its builders. */
	static void afterThreat(ServerLevel level, Faction f) {
		MinecraftServer server = level.getServer();
		Faction now = Factions.get(server, f.id());
		long time = level.getGameTime();
		if (now == null || now.fallen() || !now.type().isRealm() || now.treasury() < COST + KEEP_BACK
			|| time - LAST.getOrDefault(now.id(), -GAP_TICKS) < GAP_TICKS) {
			return;
		}
		// what he has least of (built or waiting), first
		String[] wants = WANTS.clone();
		Works.Site works = Works.of(server, now.id());
		java.util.Arrays.sort(wants, java.util.Comparator.comparingInt(w -> works.count(w)
			+ (int)Commissions.of(server, now.id()).stream().filter(c -> c.design().equals(w)).count()));
		for (String want : wants) {
			if (Commissions.give(server, now, want)) {
				LAST.put(now.id(), time);
				Factions.update(server, now.id(), x -> x.withTreasury(Math.max(0L, x.treasury() - COST)));
				PathfinderMob ruler = Rulers.of(level, now);
				if (ruler != null) {
					Speech.say(level, ruler, Component.translatable("speech.jaymoskingdoms.defences." + want + "." + level.getRandom().nextInt(LINES)),
						Speech.GOLD, SAY_RANGE);
				}
				JaymosKingdoms.LOGGER.info("{} spends on a {} after a threat", now.name(), want);
				return;
			}
		}
	}
}
