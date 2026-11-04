package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.483) Every settlement keeps a butcher and a healer (a cleric). Andy: "too many times i get to a kingdom and they
 * have no butcher or healer". Each kingdom, fiefdom and village (never a barbarian camp) is looked at every
 * {@link #EVERY} ticks while its seat is loaded, so the ones already in a world get theirs too. When it has none of a
 * trade, someone takes it up, in this order:
 * <ol>
 * <li>a grown townsperson without a trade (as {@link Meals} takes on a cook);</li>
 * <li>a nitwit, given a use at last;</li>
 * <li>the greenest of a trade the town has {@link #SPARE_OF} or more of (a third farmer turns butcher);</li>
 * <li>nobody: a newcomer in the trade moves in, at most once a day for each trade.</li>
 * </ol>
 * Never a ruler, a guard, a realm worker, a cook, a visitor, a player's family or follower, nor one leaving town. A
 * new butcher or cleric gets a little experience so they keep the trade without a smoker or brewing stand nearby.
 */
public final class TownTrades {
	/** How often each settlement is looked at (ticks). */
	static final int EVERY = 600;
	/** A trade the town has this many of can spare one. */
	static final int SPARE_OF = 3;
	/** How long (ticks) before another newcomer comes for the same trade, if the last didn't stay. */
	static final long NEWCOMER_EVERY = 24000L;
	static final int LINES = 3;
	static final double SPEECH_RANGE = 10.0;

	/** The trades every settlement keeps: by name (for the speech lines) and profession. */
	private static final List<ResourceKey<VillagerProfession>> NEEDED = List.of(VillagerProfession.BUTCHER, VillagerProfession.CLERIC);

	/** When a newcomer last came, by settlement and trade (game time). */
	private static final Map<Long, Long> NEWCOMERS = new HashMap<>();

	private TownTrades() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TownTrades::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> NEWCOMERS.clear());
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			staff(level, f, now);
		}
	}

	/** {@code f} gets whichever of its needed trades it lacks (one change per trade per look). */
	static void staff(ServerLevel level, Faction f, long now) {
		List<Villager> town = level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(Economy.POPULATION_RANGE),
			v -> v.isAlive() && !Visitors.is(v));
		for (int i = 0; i < NEEDED.size(); i++) {
			ResourceKey<VillagerProfession> trade = NEEDED.get(i);
			if (town.stream().anyMatch(v -> v.getVillagerData().profession().is(trade))) {
				continue;
			}
			Villager v = pick(level, f, town);
			if (v != null) {
				take(level, v, trade);
				continue;
			}
			long key = f.id() * 31L + i;
			if (now - NEWCOMERS.getOrDefault(key, Long.MIN_VALUE / 2) >= NEWCOMER_EVERY) {
				NEWCOMERS.put(key, now);
				newcomer(level, f, trade);
			}
		}
	}

	/** Who in {@code town} takes up a missing trade: one without a trade, then a nitwit, then the greenest of a common one; null if nobody. */
	private static @Nullable Villager pick(ServerLevel level, Faction f, List<Villager> town) {
		List<Villager> free = town.stream().filter(v -> free(f, v)).toList();
		RandomSource random = level.getRandom();
		List<Villager> idle = free.stream().filter(v -> v.getVillagerData().profession().is(VillagerProfession.NONE)).toList();
		if (!idle.isEmpty()) {
			return idle.get(random.nextInt(idle.size()));
		}
		List<Villager> nitwits = free.stream().filter(v -> v.getVillagerData().profession().is(VillagerProfession.NITWIT)).toList();
		if (!nitwits.isEmpty()) {
			return nitwits.get(random.nextInt(nitwits.size()));
		}
		// a trade the town has plenty of spares its least practised
		Map<VillagerProfession, List<Villager>> byTrade = new HashMap<>();
		for (Villager v : town) {
			Holder<VillagerProfession> p = v.getVillagerData().profession();
			if (vanilla(p) && !p.is(VillagerProfession.NONE) && !p.is(VillagerProfession.NITWIT)) {
				byTrade.computeIfAbsent(p.value(), k -> new ArrayList<>()).add(v);
			}
		}
		Villager best = null;
		for (List<Villager> same : byTrade.values()) {
			if (same.size() < SPARE_OF) {
				continue;
			}
			for (Villager v : same) {
				if (free(f, v) && (best == null || v.getVillagerXp() < best.getVillagerXp())) {
					best = v;
				}
			}
		}
		return best;
	}

	/** {@code v} could take up a trade: grown, and nobody's ruler, guard, worker, cook, family, follower or leaver. */
	private static boolean free(Faction f, Villager v) {
		Holder<VillagerProfession> p = v.getVillagerData().profession();
		return !v.isBaby() && vanilla(p) && !(v instanceof Worker w && w.role() >= 0) && !Rulers.is(v)
			&& (f.leader().isEmpty() || !f.leader().get().equals(v.getUUID())) && !McaCompat.playerFamily(v) && McaCompat.following(v) == null
			&& !v.hasAttached(Emigrants.MOVE) && !Downed.isDowned(v) && v.getTradingPlayer() == null && !v.isSleeping()
			&& v.getAttached(com.jaymoskingdoms.dragons.Disguises.DISGUISE) == null;
	}

	/** One of the game's own trades (not a guard's, a cook's or any other mod's office). */
	private static boolean vanilla(Holder<VillagerProfession> p) {
		return p.unwrapKey().map(k -> k.identifier().getNamespace().equals("minecraft")).orElse(false);
	}

	/** {@code v} takes up {@code trade}, says so, and keeps it. */
	private static void take(ServerLevel level, Villager v, ResourceKey<VillagerProfession> trade) {
		v.setVillagerData(v.getVillagerData().withProfession(BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(trade)));
		// (a little experience, so the game doesn't take the trade off them for want of a job site)
		v.setVillagerXp(Math.max(1, v.getVillagerXp()));
		v.setPersistenceRequired();
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 2.1, v.getZ(), 5, 0.3, 0.2, 0.3, 0.0);
		say(level, v, trade, "took_up");
	}

	/** A newcomer in {@code trade} moves in by {@code f}'s seat (MCA's villager with MCA, so they find a home there). */
	private static void newcomer(ServerLevel level, Faction f, ResourceKey<VillagerProfession> trade) {
		BlockPos at = ShutIn.openGround(level, f.seat());
		if (at == null || !level.isPositionEntityTicking(at)) {
			return;
		}
		Villager v = McaCompat.visitor(level, Vec3.atBottomCenterOf(at), BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(trade).value(), false);
		if (v == null) {
			return;
		}
		v.setVillagerXp(Math.max(1, v.getVillagerXp()));
		v.setPersistenceRequired();
		say(level, v, trade, "moved_in");
		JaymosKingdoms.LOGGER.debug("A {} moved in to {}", trade.identifier().getPath(), f.name());
	}

	private static void say(ServerLevel level, Villager v, ResourceKey<VillagerProfession> trade, String kind) {
		Speech.say(level, v, Component.translatable("trades.jaymoskingdoms." + trade.identifier().getPath() + "." + kind + "."
			+ level.getRandom().nextInt(LINES)), Speech.WHITE, SPEECH_RANGE);
	}
}
