package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Word of what a town has built. Andy (2026-11-27), of "a butchershop was built" turning up in chat: "these should stop
 * being shown as text. it is another thing you should notice from walking, asking the way, or hearing from the crier."
 *
 * <p>Nobody is told in chat when the builders finish something. Instead:
 * <ul>
 * <li>the town's crier cries it by the seat, with where it stands ({@link TownCrier#town});</li>
 * <li>walk by it in its first days ({@link #FRESH}) and one of the townsfolk near you remarks on it, in a speech bubble for
 * you alone (once each);</li>
 * <li>Ask the Way has "What's New?": they point you to the newest thing the town has put up, or what's going up now
 * ({@link Directions}).</li>
 * </ul>
 * A building's finishing time is kept on it ({@link Works.Built#finished}); who has had their remark is not (a restart
 * lets them say it again).
 */
public final class BuildNews {
	/** A building is new for this long after it's finished (ticks: three days); Ask the Way tells of one this old at most (a week). */
	static final long FRESH = 24000L * 3, NEWS_AGE = 24000L * 7;
	/** You notice one this near its walls; a townsperson this near you remarks on it (blocks). */
	static final double NOTICE = 8.0, REMARK_REACH = 16.0;
	/** How far from a town's seat its buildings stand, for finding whose they are (as {@link Directions#FIND_RANGE}). */
	static final double TOWN_REACH = 128.0;
	/** How often players are checked (ticks). */
	static final int CHECK_TICKS = 40;
	/** Lines each (lang {@code buildnews.jaymoskingdoms.<kind>.<n>}). */
	static final int LINES = 3;
	/** The most remarks remembered per player before the oldest are let go. */
	static final int MOST_HEARD = 128;

	/** Player → the buildings they've had remarked on ({@link #key}). */
	private static final Map<UUID, Set<String>> HEARD = new HashMap<>();

	private BuildNews() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BuildNews::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> HEARD.clear());
	}

	/** {@code f}'s builders have finished {@code entry} at {@code min}..{@code max}: the crier has it to cry. */
	static void finished(ServerLevel level, Faction f, String entry, BlockPos min, BlockPos max) {
		Vec3 seat = Vec3.atCenterOf(f.seat()), at = middle(min, max);
		double far = seat.distanceTo(at);
		TownCrier.town(level, f, "built", name(entry), Directions.distance(far), Directions.heading(seat, at));
	}

	/** A building's name, as its blueprint has it. */
	static Component name(String entry) {
		return Component.translatableWithFallback("building.jaymoskingdoms." + entry, entry);
	}

	/** The newest thing {@code f}'s builders have finished in the last week (and that still stands), or null. */
	static Works.@Nullable Built newest(MinecraftServer server, Faction f, long now) {
		Works.Built best = null;
		for (Works.Built b : Works.of(server, f.id()).built()) {
			if (b.finished() > 0L && b.ruined() == 0L && now - b.finished() <= NEWS_AGE && (best == null || b.finished() > best.finished())) {
				best = b;
			}
		}
		return best;
	}

	/** What {@code f}'s builders are putting up now (the first of their works), or null. */
	static Works.@Nullable Project underway(MinecraftServer server, Faction f) {
		List<Works.Project> projects = Works.of(server, f.id()).projects();
		return projects.isEmpty() ? null : projects.getFirst();
	}

	/** "today", "yesterday", "a few days back". */
	static Component when(long now, long finished) {
		long days = now / 24000L - finished / 24000L;
		return Component.translatable("buildnews.jaymoskingdoms.when." + (days <= 0 ? "today" : days == 1 ? "yesterday" : "days"));
	}

	static Vec3 middle(BlockPos min, BlockPos max) {
		return new Vec3((min.getX() + max.getX()) / 2.0 + 0.5, min.getY() + 1.0, (min.getZ() + max.getZ()) / 2.0 + 0.5);
	}

	// ---- walking by ---------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % CHECK_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player.isAlive() && !player.isSpectator()) {
				look(level, player, now);
			}
		}
	}

	/** {@code player} is by a building their nearest town put up these last days: someone near says so, once. */
	private static void look(ServerLevel level, ServerPlayer player, long now) {
		Faction f = Factions.around(level.getServer(), level, player.blockPosition(), TOWN_REACH);
		if (f == null) {
			return;
		}
		for (Works.Built b : Works.of(level.getServer(), f.id()).built()) {
			if (b.finished() <= 0L || b.ruined() != 0L || now - b.finished() > FRESH) {
				continue;
			}
			AABB walls = new AABB(Vec3.atLowerCornerOf(b.min()), Vec3.atLowerCornerOf(b.top()).add(1.0, 1.0, 1.0)).inflate(NOTICE);
			if (!walls.contains(player.position())) {
				continue;
			}
			String key = key(f, b);
			Set<String> heard = HEARD.computeIfAbsent(player.getUUID(), id -> new HashSet<>());
			if (heard.contains(key)) {
				continue;
			}
			Villager by = remarker(level, f, player);
			if (by == null) {
				// nobody about to say so: the building is there to be seen all the same
				continue;
			}
			if (heard.size() >= MOST_HEARD) {
				heard.clear();
			}
			heard.add(key);
			RandomSource random = level.getRandom();
			String kind = by.isBaby() ? "child" : now - b.finished() < 24000L / 2 ? "fresh" : "walk";
			Component words = Component.translatable("buildnews.jaymoskingdoms." + kind + "." + random.nextInt(LINES), name(b.entry()), when(now, b.finished()));
			Vec3 at = middle(b.min(), b.max());
			by.getLookControl().setLookAt(at.x, by.getEyeY(), at.z, 30.0F, 30.0F);
			Speech.sayTo(level, by, player, words, Speech.WHITE);
			// one remark a look
			return;
		}
	}

	/** The townsperson nearest {@code player} who's free to say a word: awake, up, not fighting, not tied up in talk. */
	private static @Nullable Villager remarker(ServerLevel level, Faction f, ServerPlayer player) {
		Villager best = null;
		for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(REMARK_REACH), v -> free(level, f, v, player))) {
			if (best == null || v.distanceToSqr(player) < best.distanceToSqr(player)) {
				best = v;
			}
		}
		return best;
	}

	private static boolean free(ServerLevel level, Faction f, Villager v, ServerPlayer player) {
		if (!v.isAlive() || v.isSleeping() || Downed.isDowned(v) || Arrests.held(v) || v.getTarget() != null || v.getTradingPlayer() != null
			|| Deafened.unheard(player, v) || !v.hasLineOfSight(player)) {
			return false;
		}
		Faction theirs = Factions.around(level.getServer(), level, v.blockPosition(), TOWN_REACH);
		return theirs != null && theirs.id() == f.id();
	}

	private static String key(Faction f, Works.Built b) {
		return f.id() + ":" + b.entry() + ":" + b.min().asLong();
	}
}
