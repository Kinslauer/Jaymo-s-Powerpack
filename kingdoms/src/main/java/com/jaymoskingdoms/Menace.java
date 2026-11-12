package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * A drawn, crouched sword stance reads as a threat (Andy's pick from Star Wars: Galaxy in a Cube, ideas only; Powers
 * draws the pose, {@link PowersCompat#threatening} says who is in it). Within {@link #SEEN} blocks, in sight:
 * <ul>
 * <li>a grown villager grows wary: a muttered line now and then, and it backs away from you, eyes on the blade;</li>
 * <li>a child runs and hides ({@link Frights#scare});</li>
 * <li>a guard or archer warns you first, polearm levelled; if you hold the stance within {@link #WARN_RANGE} blocks of
 * them for {@link #WARN_TICKS} more, they take you for hostile and set on you.</li>
 * </ul>
 * Barbarians and chiefs have no fear of it, and a villager already running from something carries on.
 */
public final class Menace {
	/** How often it looks (ticks); how far a stance is seen; how close a guard's patience is tried. */
	static final int EVERY = 10;
	static final double SEEN = 12.0, WARN_RANGE = 8.0;
	/** A guard's warning lasts this long before they act (ticks); how long a warning is remembered once the stance is dropped. */
	static final long WARN_TICKS = 100L, FORGET_TICKS = 100L;
	/** A villager mutters at most this often (ticks). */
	static final long MUTTER_GAP = 160L;
	/** How far an adult backs off, and how fast. */
	static final double BACK_RANGE = 5.0;
	static final float BACK_SPEED = 0.55F;
	/** How many lines of each kind there are in the language file. */
	static final int WARY_LINES = 4, CHILD_LINES = 3, WARN_LINES = 3, SET_ON_LINES = 3;

	/** When a guard first warned this player and when the stance last still held. */
	private record Warning(long since, long last) {
	}

	private static final Map<String, Warning> WARNINGS = new HashMap<>();
	private static final Map<UUID, Long> MUTTERED = new HashMap<>();

	private Menace() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Menace::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WARNINGS.clear();
			MUTTERED.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isSpectator() || !PowersCompat.threatening(player)) {
				continue;
			}
			ServerLevel level = player.level();
			long now = level.getGameTime();
			for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(SEEN), Villager::isAlive)) {
				if (v.distanceToSqr(player) > SEEN * SEEN || !v.hasLineOfSight(player)) {
					continue;
				}
				if (v instanceof Worker w && (w.role() == Folk.BARBARIAN || w.role() == Folk.CHIEF)) {
					continue;
				}
				if (guard(v)) {
					guardWatches(level, v, player, now);
				} else if (v.isBaby()) {
					child(level, v, player, now);
				} else {
					wary(level, v, player, now);
				}
			}
		}
		long now = server.overworld().getGameTime();
		WARNINGS.values().removeIf(w -> now - w.last() > FORGET_TICKS);
		for (Iterator<Map.Entry<UUID, Long>> it = MUTTERED.entrySet().iterator(); it.hasNext(); ) {
			if (now - it.next().getValue() > MUTTER_GAP) {
				it.remove();
			}
		}
	}

	private static boolean guard(Villager v) {
		return v instanceof Worker w && (w.role() == Folk.GUARD || w.role() == Folk.ARCHER) || McaCompat.isGuard(v);
	}

	private static boolean muttersNow(Villager v, long now) {
		Long last = MUTTERED.get(v.getUUID());
		if (last != null && now - last < MUTTER_GAP) {
			return false;
		}
		MUTTERED.put(v.getUUID(), now);
		return true;
	}

	private static Component line(String kind, int count, ServerLevel level) {
		return Component.translatable("talk.jaymoskingdoms.menace." + kind + "." + (1 + level.getRandom().nextInt(count)));
	}

	/** A grown villager: a wary word now and then, and steps back with its eyes on the blade. */
	private static void wary(ServerLevel level, Villager v, ServerPlayer player, long now) {
		if (Frights.afraid(v.getUUID())) {
			return;
		}
		if (muttersNow(v, now)) {
			Speech.tell(level, v, player, line("wary", WARY_LINES, level), Speech.YELLOW);
		}
		v.getLookControl().setLookAt(player);
		if (v.distanceToSqr(player) < BACK_RANGE * BACK_RANGE) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, Frights.away(level, v, player.position()), BACK_SPEED, 1);
		}
	}

	/** A child runs and hides, as from a monster. */
	private static void child(ServerLevel level, Villager v, ServerPlayer player, long now) {
		if (Frights.afraid(v.getUUID())) {
			return;
		}
		if (muttersNow(v, now)) {
			Speech.tell(level, v, player, line("child", CHILD_LINES, level), Speech.YELLOW);
		}
		Frights.scare(level, v, player.position());
	}

	/** A guard warns first; if the stance is held near them past the warning, they set on the player. */
	private static void guardWatches(ServerLevel level, Villager v, ServerPlayer player, long now) {
		v.getLookControl().setLookAt(player);
		String key = v.getUUID() + "/" + player.getUUID();
		Warning had = WARNINGS.get(key);
		if (v.getTarget() == player) {
			WARNINGS.put(key, new Warning(had != null ? had.since() : now, now));
			return;
		}
		if (had == null) {
			WARNINGS.put(key, new Warning(now, now));
			Levelled.level(v, (int)WARN_TICKS + 20);
			Speech.tell(level, v, player, line("warn", WARN_LINES, level), Speech.YELLOW);
			return;
		}
		WARNINGS.put(key, new Warning(had.since(), now));
		if (v.distanceToSqr(player) > WARN_RANGE * WARN_RANGE || now - had.since() < WARN_TICKS) {
			return;
		}
		v.setTarget(player);
		Speech.tell(level, v, player, line("set_on", SET_ON_LINES, level), Speech.RED);
	}
}
