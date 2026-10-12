package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * A faction's health, the three things that let it grow:
 * <ul>
 * <li><b>a healthy economy</b> ({@link #healthy}): enough of each building material in storage for its next project,
 * and food in stock;</li>
 * <li><b>adequate personnel</b> ({@link #adequatePersonnel}): enough folk for the settlement's size. The dead are
 * replaced slowly ({@link #HIRE_TICKS}) from its villagers, and it takes on more as it grows;</li>
 * <li><b>peace</b> ({@link #atPeace}): no war in its relations, and nobody attacking its folk, villagers, golems or
 * leader right now ({@link #noteAttack}).</li>
 * </ul>
 */
public final class Economy {
	/** Food (items) a healthy economy keeps in stock. */
	static final int FOOD_RESERVE = 32;
	/** A next project's needs when nobody has said what it is: about a house's worth of logs and stone. */
	static final int HOUSE_LOGS = 16, HOUSE_STONE = 32;
	/** The folk a settlement needs: a base by type, one more per this many villagers, up to a most by type. */
	static final int NEED_KINGDOM = 12, NEED_FIEFDOM = 10, NEED_VILLAGE = 2, NEED_CAMP = 4;
	static final int MOST_KINGDOM = 30, MOST_FIEFDOM = 24, MOST_CAMP = 20;
	static final int VILLAGERS_PER_FOLK = 6;
	/** Attacks are remembered this long (ticks): "under attack right now". */
	static final long ATTACK_MEMORY_TICKS = 600L;
	/** One of the dead (or a new hand) is taken on this often (ticks: a quarter of a day). */
	static final long HIRE_TICKS = 6000L;
	/** Villagers a settlement keeps: it won't take one into service below this. */
	static final int MIN_VILLAGERS_KEPT = 3;
	/** Villagers are counted (and taken on) this far round the seat. */
	static final double POPULATION_RANGE = 64.0;
	/** How often hiring is looked at (ticks). */
	static final int HIRE_CHECK_TICKS = 200;

	/**
	 * What {@code faction}'s next project needs (item → count): the construction code sets this; until then, a house's
	 * worth of its own logs and cobblestone.
	 */
	public static BiFunction<ServerLevel, Faction, Map<Item, Integer>> nextNeeds = Economy::standardNeeds;

	/** Faction id → game time of the last attack on its people; and who attacked (entity UUID → when). */
	private static final Map<Integer, Long> LAST_ATTACK = new HashMap<>();
	private static final Map<Integer, Map<UUID, Long>> ATTACKERS = new HashMap<>();

	private Economy() {
	}

	// ---- a healthy economy --------------------------------------------------------------------------------------

	static Map<Item, Integer> standardNeeds(ServerLevel level, Faction faction) {
		Map<Item, Integer> needs = new LinkedHashMap<>();
		needs.put(Production.logItem(level, faction), HOUSE_LOGS);
		needs.put(Items.COBBLESTONE, HOUSE_STONE);
		return needs;
	}

	/** Enough of {@code materials} in storage, and {@link #FOOD_RESERVE} food. */
	public static boolean healthy(ServerLevel level, Faction faction, Map<Item, Integer> materials) {
		return Storage.has(level, faction, materials) && hasFood(level, faction);
	}

	/** Healthy for its next project ({@link #nextNeeds}). */
	public static boolean healthy(ServerLevel level, Faction faction) {
		return healthy(level, faction, nextNeeds.apply(level, faction));
	}

	public static boolean hasFood(ServerLevel level, Faction faction) {
		return Storage.food(level, faction) >= FOOD_RESERVE;
	}

	// ---- adequate personnel --------------------------------------------------------------------------------------

	/** The folk {@code faction} needs for its size. */
	public static int needed(Faction faction) {
		int extra = faction.holdings().population() / VILLAGERS_PER_FOLK;
		return switch (faction.type()) {
			case KINGDOM -> Math.min(MOST_KINGDOM, NEED_KINGDOM + extra);
			case FIEFDOM -> Math.min(MOST_FIEFDOM, NEED_FIEFDOM + extra);
			case VILLAGE -> Math.min(Builders.CREW_MAX, NEED_VILLAGE + extra);
			case BARBARIAN -> Math.min(MOST_CAMP, NEED_CAMP + extra);
		};
	}

	/** The folk it has (its roster, leader excluded). */
	public static int staff(Faction faction) {
		return faction.holdings().staff();
	}

	public static boolean adequatePersonnel(Faction faction) {
		return staff(faction) >= needed(faction);
	}

	/** One of {@code faction}'s folk died: one fewer, and a place to fill. Leaders aren't on the roster. */
	public static void lost(MinecraftServer server, int faction, int role) {
		Folk.Role spec = Folk.role(role);
		if (spec != null && spec.leader()) {
			return;
		}
		Factions.update(server, faction, f -> f.withHoldings(f.holdings().adjust(role, -1).adjustVacancy(role, 1)));
	}

	/** Fills one vacancy (or takes on a new hand) for every faction whose time has come and whose seat is loaded. */
	static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % HIRE_CHECK_TICKS != 0) {
			return;
		}
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || now < f.holdings().nextHire() || !level.isPositionEntityTicking(f.seat()) || underAttack(server, f)) {
				continue;
			}
			hire(level, f);
		}
	}

	/** Takes one into {@code faction}'s service: a vacancy filled or, if it's short for its size, a new hand. Returns them. */
	public static @Nullable Worker hire(ServerLevel level, Faction faction) {
		MinecraftServer server = level.getServer();
		long now = level.getGameTime();
		Faction grown = faction;
		if (grown.holdings().vacant() == 0 && staff(grown) < needed(grown)) {
			// a builder while the crew is short; a farmer for a farm nobody keeps (0.5.1); a miner or woodcutter for a
			// mine or tree farm short of them (0.5.2); else a guard
			boolean crewShort = Builders.crewCount(grown) < Builders.crewTarget(grown);
			int picked = crewShort ? Folk.WORKER : Farmers.wanted(server, grown) ? Folk.FARMER : Gatherers.wanted(server, grown, Folk.MINER) ? Folk.MINER
				: Gatherers.wanted(server, grown, Folk.WOODCUTTER) ? Folk.WOODCUTTER : Rickshaws.wanted(server, grown) ? Folk.PULLER
				: grown.type().isRealm() ? Folk.GUARD : Folk.WORKER;
			if (picked == Folk.GUARD && Focus.enoughGuards(server, grown)) {
				// (0.5.14) a fiefdom with its few guards: another pair of hands for its fields or its mine instead
				picked = Focus.of(server, grown) == Focus.Kind.FARMING && !Farmers.farms(server, grown).isEmpty() ? Folk.FARMER
					: Mines.dug(server, grown) ? Folk.MINER : Builders.crewCount(grown) < Builders.CREW_MAX ? Folk.WORKER : Folk.GUARD;
			}
			int role = picked;
			if (grown.type() == FactionType.BARBARIAN || (role == Folk.WORKER && Builders.crewCount(grown) >= Builders.CREW_MAX)) {
				// (camps raise their own; a full crew takes no more)
				return null;
			}
			grown = Factions.update(server, grown.id(), x -> x.withHoldings(x.holdings().adjustVacancy(role, 1)));
		}
		if (grown == null || grown.holdings().vacant() == 0) {
			return null;
		}
		Faction f = grown;
		int role = nextVacancy(f);
		// from the settlement's own people, if it can spare one (0.5.21: with MCA, a worker is one of them promoted where
		// they stand, so nobody's taken away)
		List<Villager> villagers = Workers.inPlace(f, role) ? List.of() : level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(POPULATION_RANGE),
			v -> v.isAlive() && !v.isBaby() && (f.leader().isEmpty() || !f.leader().get().equals(v.getUUID())));
		BlockPos from = null;
		if (villagers.size() > MIN_VILLAGERS_KEPT) {
			Villager recruit = villagers.get(level.getRandom().nextInt(villagers.size()));
			from = recruit.blockPosition();
			level.sendParticles(ParticleTypes.POOF, recruit.getX(), recruit.getY() + 1.0, recruit.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
			level.playSound(null, from, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
			recruit.discard();
		} else if (!villagers.isEmpty()) {
			// too few to spare: wait
			Factions.update(server, f.id(), x -> x.withHoldings(x.holdings().withNextHire(now + HIRE_TICKS)));
			return null;
		}
		Faction ready = Factions.update(server, f.id(), x -> x.withHoldings(x.holdings().adjustVacancy(role, -1).withNextHire(now + HIRE_TICKS)));
		if (ready == null) {
			return null;
		}
		Worker hired;
		if (Folk.isBuilder(role)) {
			hired = Builders.spawn(level, ready, role, from);
		} else if (role == Folk.FARMER) {
			hired = Farmers.spawn(level, ready, from);
		} else if (Gatherers.isGatherer(role)) {
			hired = Gatherers.spawn(level, ready, role, from);
		} else if (role == Folk.PULLER) {
			// (0.5.27) a rickshaw puller
			hired = Rickshaws.spawn(level, ready, from);
		} else if (role == Folk.ARCHER) {
			hired = KingdomEvents.spawnArcher(level, ready);
		} else if (Workers.inPlace(ready, role) && (hired = McaCompat.enlist(level, ready, role, ready.seat(), ready.seat(), Folk.HOME_RADIUS)) != null) {
			// (0.5.21) one of its villagers made an MCA guard (or archer)
			Factions.update(server, ready.id(), x -> x.withHoldings(x.holdings().adjust(role, 1)));
		} else {
			hired = KingdomEvents.spawn(level, ready, role);
		}
		if (hired != null) {
			JaymosKingdoms.LOGGER.debug("{} took on a {}", ready.name(), Folk.roleName(role));
		}
		return hired;
	}

	/**
	 * One of the settlement's villagers taken into service (gone in a puff, with a nod) if it can spare one (it keeps
	 * {@link #MIN_VILLAGERS_KEPT}): where they stood, or null (then a new hand comes instead).
	 */
	static @Nullable BlockPos recruit(ServerLevel level, Faction f) {
		if (Workers.inPlace(f, Folk.WORKER)) {
			// (0.5.21: MCA's villagers are promoted where they stand)
			return null;
		}
		List<Villager> villagers = level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(POPULATION_RANGE),
			v -> v.isAlive() && !v.isBaby() && (f.leader().isEmpty() || !f.leader().get().equals(v.getUUID())));
		if (villagers.size() <= MIN_VILLAGERS_KEPT) {
			return null;
		}
		Villager recruit = villagers.get(level.getRandom().nextInt(villagers.size()));
		BlockPos from = recruit.blockPosition();
		level.sendParticles(ParticleTypes.POOF, recruit.getX(), recruit.getY() + 1.0, recruit.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		level.playSound(null, from, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
		recruit.discard();
		return from;
	}

	/** The vacancy filled first: a foreman, then guards, archers, workers, anything else. */
	private static int nextVacancy(Faction f) {
		for (int role : new int[] {Folk.FOREMAN, Folk.GUARD, Folk.ARCHER, Folk.WORKER, Folk.FARMER, Folk.MINER, Folk.WOODCUTTER, Folk.PULLER}) {
			if (f.holdings().vacancies().getOrDefault(Folk.roleName(role), 0) > 0) {
				return role;
			}
		}
		String name = f.holdings().vacancies().keySet().iterator().next();
		for (int id = 0; id < 64; id++) {
			if (Folk.roleName(id).equals(name)) {
				return id;
			}
		}
		return Folk.GUARD;
	}

	// ---- peace ---------------------------------------------------------------------------------------------------

	/** Not at war with anyone (by its relations), and not under attack right now. */
	public static boolean atPeace(MinecraftServer server, Faction faction) {
		return !atWar(server, faction) && !underAttack(server, faction);
	}

	/** It has a war on (a stored relation; barbarians' standing enmity doesn't count). */
	public static boolean atWar(MinecraftServer server, Faction faction) {
		return !Relations.enemies(server, faction).isEmpty();
	}

	/** Someone attacked its folk, villagers, golems or leader in the last {@link #ATTACK_MEMORY_TICKS}. */
	public static boolean underAttack(MinecraftServer server, Faction faction) {
		Long last = LAST_ATTACK.get(faction.id());
		return last != null && Factions.level(server).getGameTime() - last < ATTACK_MEMORY_TICKS;
	}

	/** Whoever attacked {@code faction}'s people lately (entity UUIDs), most recent attacks only. */
	public static List<UUID> recentAttackers(MinecraftServer server, Faction faction) {
		long now = Factions.level(server).getGameTime();
		Map<UUID, Long> map = ATTACKERS.getOrDefault(faction.id(), Map.of());
		return map.entrySet().stream().filter(e -> now - e.getValue() < ATTACK_MEMORY_TICKS).map(Map.Entry::getKey).toList();
	}

	/** {@code attacker} attacked one of {@code faction}'s people. */
	public static void noteAttack(MinecraftServer server, Faction faction, @Nullable Entity attacker) {
		long now = Factions.level(server).getGameTime();
		LAST_ATTACK.put(faction.id(), now);
		if (attacker != null) {
			Map<UUID, Long> map = ATTACKERS.computeIfAbsent(faction.id(), k -> new HashMap<>());
			map.put(attacker.getUUID(), now);
			map.values().removeIf(t -> now - t >= ATTACK_MEMORY_TICKS);
		}
	}

	/** A living thing was hurt: if it's one of a faction's people (folk, or a villager or golem of its settlement) and the attacker isn't on its side, note it. */
	static void hurt(LivingEntity victim, @Nullable Entity attacker) {
		if (!(victim.level() instanceof ServerLevel level) || attacker == null || attacker == victim) {
			return;
		}
		MinecraftServer server = level.getServer();
		Faction faction = null;
		if (victim instanceof Folk folk) {
			faction = Factions.get(server, folk.factionId());
		} else if (victim instanceof Villager || victim instanceof net.minecraft.world.entity.animal.golem.IronGolem) {
			faction = Factions.around(server, level, victim.blockPosition(), Villages.VILLAGE_RADIUS);
		}
		if (faction == null || faction.fallen()) {
			return;
		}
		if (attacker instanceof Folk other && (other.factionId() == faction.id() || Relations.between(server, other.factionId(), faction.id()) == Relation.ALLIED)) {
			return;
		}
		if (attacker instanceof net.minecraft.world.entity.animal.golem.IronGolem || attacker instanceof Villager) {
			return;
		}
		noteAttack(server, faction, attacker);
	}

	static void clear() {
		LAST_ATTACK.clear();
		ATTACKERS.clear();
	}
}
