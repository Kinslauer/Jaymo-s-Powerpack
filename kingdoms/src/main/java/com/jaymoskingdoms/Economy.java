package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
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
		return Storage.food(level, faction) >= TownProjects.foodReserve(level.getServer(), faction); // (0.7.530) a granary halves the reserve
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
				: Stables.wanted(server, grown, Folk.STABLEHAND) ? Folk.STABLEHAND : Stables.wanted(server, grown, Folk.FARRIER) ? Folk.FARRIER
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
			v -> v.isAlive() && !v.isBaby() && !Visitors.is(v) && !Envoys.is(v) && (f.leader().isEmpty() || !f.leader().get().equals(v.getUUID())));
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
			// (0.5.26) a rickshaw puller
			hired = Rickshaws.spawn(level, ready, from);
		} else if (Stables.isStableWorker(role)) {
			// (0.5.47) a stable's stablehand or farrier
			hired = Stables.spawn(level, ready, role, from);
		} else if (role == Folk.ARCHER) {
			hired = KingdomEvents.spawnArcher(level, ready);
		} else if (Sorcery.isCaster(role)) {
			// (0.5.37) a court's wizard (or necromancer) in its hall, a camp's shaman or necromancer by its fire
			hired = Sorcery.spawn(level, ready, role);
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
		for (int role : new int[] {Folk.FOREMAN, Folk.GUARD, Folk.ARCHER, Folk.WORKER, Folk.FARMER, Folk.MINER, Folk.WOODCUTTER, Folk.PULLER, Folk.STABLEHAND, Folk.FARRIER}) {
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
		WORTH.clear();
	}

	// ---- what things are worth (village mods patch 4, fair pay) ---------------------------------------------------

	/**
	 * What raw goods are worth, in milli (an emerald is {@link Money#UNIT}), about what a villager trades them at: what
	 * grows, is mined, shorn or hunted. An id, or {@code #tag} for every item in it. Everything made is worth what went
	 * into it ({@link #worth}).
	 */
	private static final Map<String, Integer> RAW_WORTH = Map.ofEntries(
		Map.entry("minecraft:dirt", 2), Map.entry("minecraft:cobblestone", 5), Map.entry("minecraft:cobbled_deepslate", 6),
		Map.entry("minecraft:sand", 5), Map.entry("minecraft:red_sand", 6), Map.entry("minecraft:gravel", 5), Map.entry("minecraft:mud", 6),
		Map.entry("minecraft:snowball", 2), Map.entry("minecraft:ice", 20), Map.entry("minecraft:clay_ball", 60), Map.entry("minecraft:flint", 30),
		Map.entry("#minecraft:logs", 60), Map.entry("minecraft:bamboo", 10), Map.entry("minecraft:cactus", 30), Map.entry("minecraft:sugar_cane", 40),
		Map.entry("minecraft:kelp", 10), Map.entry("minecraft:vine", 10), Map.entry("minecraft:lily_pad", 40), Map.entry("#minecraft:small_flowers", 20),
		Map.entry("minecraft:pink_petals", 15), Map.entry("minecraft:brown_mushroom", 40), Map.entry("minecraft:red_mushroom", 40),
		Map.entry("minecraft:resin_clump", 150), Map.entry("minecraft:mangrove_roots", 20),
		Map.entry("minecraft:wheat", 50), Map.entry("minecraft:wheat_seeds", 5), Map.entry("minecraft:carrot", 60), Map.entry("minecraft:potato", 50),
		Map.entry("minecraft:beetroot", 50), Map.entry("minecraft:melon_slice", 25), Map.entry("minecraft:pumpkin", 150),
		Map.entry("minecraft:sweet_berries", 30), Map.entry("minecraft:cocoa_beans", 60), Map.entry("minecraft:apple", 80), Map.entry("minecraft:egg", 60),
		Map.entry("minecraft:honeycomb", 150), Map.entry("minecraft:honey_bottle", 200),
		Map.entry("minecraft:cod", 120), Map.entry("minecraft:salmon", 150), Map.entry("minecraft:beef", 150), Map.entry("minecraft:porkchop", 150),
		Map.entry("minecraft:chicken", 100), Map.entry("minecraft:mutton", 120), Map.entry("minecraft:rabbit", 120),
		Map.entry("minecraft:leather", 200), Map.entry("minecraft:rabbit_hide", 60), Map.entry("#minecraft:wool", 100), Map.entry("minecraft:string", 70),
		Map.entry("minecraft:bone", 60), Map.entry("minecraft:feather", 40), Map.entry("minecraft:rotten_flesh", 20), Map.entry("minecraft:slime_ball", 250),
		Map.entry("minecraft:gunpowder", 200), Map.entry("minecraft:spider_eye", 100), Map.entry("minecraft:ink_sac", 80),
		Map.entry("minecraft:coal", 70), Map.entry("minecraft:raw_iron", 300), Map.entry("minecraft:raw_copper", 60), Map.entry("minecraft:raw_gold", 600),
		Map.entry("minecraft:redstone", 60), Map.entry("minecraft:lapis_lazuli", 150), Map.entry("minecraft:quartz", 120),
		Map.entry("minecraft:amethyst_shard", 150), Map.entry("minecraft:obsidian", 200), Map.entry("minecraft:glowstone_dust", 120),
		Map.entry("minecraft:emerald", 1000), Map.entry("minecraft:diamond", 8000), Map.entry("minecraft:netherite_scrap", 15000),
		Map.entry("minecraft:ender_pearl", 1500), Map.entry("minecraft:blaze_rod", 1500), Map.entry("minecraft:ghast_tear", 3000),
		Map.entry("minecraft:nether_wart", 60),
		Map.entry("minecraft:water_bucket", 1050), Map.entry("minecraft:lava_bucket", 1300), Map.entry("minecraft:milk_bucket", 1100));
	/** What a beast led in on a rope is worth (milli), by entity id; one not listed is {@link #BEAST_WORTH}. */
	private static final Map<String, Integer> BEASTS = Map.of("minecraft:sheep", 1500, "minecraft:cow", 2000, "minecraft:pig", 1200,
		"minecraft:chicken", 400, "minecraft:camel", 6000, "minecraft:llama", 3000, "minecraft:goat", 2500, "minecraft:frog", 1500,
		"minecraft:mooshroom", 4000);
	static final int BEAST_WORTH = 1500;
	/** A thing made is worth what went into it and this much over, for the work. */
	static final float MADE_SHARE = 1.1F;
	/** How many times the recipes are walked, each time pricing what's made of what the last walk priced. */
	static final int WORTH_WALKS = 8;
	/** What a thing nobody makes and nobody listed is worth (milli), by rarity; and what rarity multiplies a made thing's worth by. */
	static final int COMMON_WORTH = 50, UNCOMMON_WORTH = 500, RARE_WORTH = 2000, EPIC_WORTH = 6000;
	static final float UNCOMMON_SHARE = 1.5F, RARE_SHARE = 2.0F, EPIC_SHARE = 3.0F;

	/** The worth table, built the first time it's asked for after the server starts. */
	private static final Map<Item, Long> WORTH = new HashMap<>();

	/** What one {@code item} is worth (milli): its raw worth, or what it's made of, times its rarity. */
	static long worth(MinecraftServer server, Item item) {
		if (WORTH.isEmpty()) {
			buildWorth(server);
		}
		Long known = WORTH.get(item);
		if (known != null) {
			return known;
		}
		return switch (new ItemStack(item).getRarity()) {
			case COMMON -> COMMON_WORTH;
			case UNCOMMON -> UNCOMMON_WORTH;
			case RARE -> RARE_WORTH;
			case EPIC -> EPIC_WORTH;
		};
	}

	/** {@code target} as a quest asks for it (an item id, maybe with a {@code @grade}, or a {@code #tag}: its cheapest item), one of it. */
	static long worth(MinecraftServer server, String target) {
		String id = Quests.itemId(target);
		if (id.startsWith("#")) {
			Identifier tag = Identifier.tryParse(id.substring(1));
			long least = Long.MAX_VALUE;
			if (tag != null) {
				for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, tag))) {
					least = Math.min(least, worth(server, h.value()));
				}
			}
			return least == Long.MAX_VALUE ? COMMON_WORTH : least;
		}
		Identifier key = Identifier.tryParse(id);
		Item item = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
		return item == null ? COMMON_WORTH : worth(server, item);
	}

	/** What one beast of {@code type} (an entity id) led in is worth (milli). */
	static long beastWorth(String type) {
		return BEASTS.getOrDefault(type, BEAST_WORTH);
	}

	private static void buildWorth(MinecraftServer server) {
		Map<Item, Long> raw = new HashMap<>();
		for (Item item : BuiltInRegistries.ITEM) {
			ItemStack stack = new ItemStack(item);
			String id = BuiltInRegistries.ITEM.getKey(item).toString();
			Integer w = RAW_WORTH.get(id);
			if (w == null) {
				for (Map.Entry<String, Integer> e : RAW_WORTH.entrySet()) {
					Identifier tag = e.getKey().startsWith("#") ? Identifier.tryParse(e.getKey().substring(1)) : null;
					if (tag != null && stack.is(TagKey.create(Registries.ITEM, tag))) {
						w = e.getValue();
						break;
					}
				}
			}
			if (w != null) {
				raw.put(item, (long)w);
			}
		}
		Map<Item, Long> worth = new HashMap<>(raw);
		ContextMap context = SlotDisplayContext.fromLevel(server.overworld());
		record Made(Item result, int count, List<List<Item>> slots) {
		}
		List<Made> made = new ArrayList<>();
		for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
			try {
				Recipe<?> recipe = holder.value();
				var placement = recipe.placementInfo();
				if (placement.isImpossibleToPlace()) {
					continue;
				}
				List<List<Item>> slots = new ArrayList<>();
				for (Ingredient ingredient : placement.ingredients()) {
					slots.add(ingredient.items().map(Holder::value).toList());
				}
				for (RecipeDisplay display : recipe.display()) {
					for (ItemStack out : display.result().resolveForStacks(context)) {
						// (an item made from itself, a repair or a dye, says nothing of its worth)
						if (!raw.containsKey(out.getItem()) && slots.stream().noneMatch(s -> s.contains(out.getItem()))) {
							made.add(new Made(out.getItem(), Math.max(1, out.getCount()), slots));
						}
					}
				}
			} catch (RuntimeException e) {
				JaymosKingdoms.LOGGER.debug("Worth table skipped recipe {}: {}", holder.id(), e.toString());
			}
		}
		for (int walk = 0; walk < WORTH_WALKS; walk++) {
			boolean changed = false;
			for (Made m : made) {
				long cost = 0;
				for (List<Item> slot : m.slots()) {
					long least = Long.MAX_VALUE;
					for (Item i : slot) {
						Long w = worth.get(i);
						if (w != null) {
							least = Math.min(least, w);
						}
					}
					if (least == Long.MAX_VALUE) {
						cost = -1;
						break;
					}
					cost += least;
				}
				if (cost < 0) {
					continue;
				}
				long each = Math.max(1L, Math.round(cost * MADE_SHARE / m.count()));
				Long was = worth.get(m.result());
				// (the cheapest way of making it sets its worth)
				if (was == null || each < was) {
					worth.put(m.result(), each);
					changed = true;
				}
			}
			if (!changed) {
				break;
			}
		}
		for (Map.Entry<Item, Long> e : worth.entrySet()) {
			float share = switch (new ItemStack(e.getKey()).getRarity()) {
				case COMMON -> 1.0F;
				case UNCOMMON -> UNCOMMON_SHARE;
				case RARE -> RARE_SHARE;
				case EPIC -> EPIC_SHARE;
			};
			WORTH.put(e.getKey(), Math.round(e.getValue() * share));
		}
		JaymosKingdoms.LOGGER.info("Worth table: {} items priced ({} raw, {} recipes)", WORTH.size(), raw.size(), made.size());
	}

	/** {@code milli} as a quest's pay: whole emeralds' worth, and the rest in copper bits. */
	static Quest.Reward pay(long milli, List<String> goods, int hearts, int reputation) {
		long m = Math.max(0L, milli);
		return new Quest.Reward((int)(m / Money.UNIT), goods, java.util.Optional.empty(), java.util.Optional.empty(), hearts, reputation, 0,
			(int)(m % Money.UNIT / Currency.BIT.base));
	}
}
