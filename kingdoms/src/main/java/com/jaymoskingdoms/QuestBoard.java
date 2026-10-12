package com.jaymoskingdoms;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.26) What a giver asks: quests made up to suit who's asking. A villager's trade decides what they want (a farmer
 * frets over rabbits in the carrots and pays in hay, a smith wants iron and pays in arms, a librarian wants a lost tome
 * from a ruin and pays in enchanted books); every villager may want a letter carried. A king asks the deadly work: a
 * camp's chief slain, or a champion put down where it lairs in one of the world's own ruins.
 *
 * <p>Fetch and champion quests use the structures already in the world (vanilla's, and Grim Kingdoms' and ATi's when
 * they're in the pack), found as {@code /locate} finds them.
 */
final class QuestBoard {
	/** How far a villager's (and a king's) fetch or champion quest looks for its ruin (chunks). */
	static final int VILLAGER_SEARCH_CHUNKS = 32, KING_SEARCH_CHUNKS = 56;
	/** A ruin nearer any settlement's seat than this is lived in. */
	static final double RUIN_SETTLED = 72.0;
	/** A letter goes to another settlement this far (blocks) at most; this share of letters go elsewhere if there's one. */
	static final double MAIL_RANGE = 900.0, MAIL_ELSEWHERE = 0.5;
	/** A camp the king wants cleared lies this far at most. */
	static final double CAMP_RANGE = 1000.0;
	/** Villager rewards: MCA hearts, reputation and standing; a king's. */
	static final int HEARTS = 12, REPUTATION = 15, STANDING = 10;
	static final int ROYAL_REPUTATION = 150, ROYAL_STANDING = 100;
	/** Chance a villager's quest pays a relic: a smith's, a guard's or fletcher's, and anyone else's. */
	static final float SMITH_RELIC = 0.35F, ARMS_RELIC = 0.2F, OTHER_RELIC = 0.04F;

	/** Ruins any fetch may use (and the pack's mods' ruins, by namespace). */
	private static final List<String> RUINS = List.of("trail_ruins", "ruined_portal", "ruined_portal_desert", "ruined_portal_jungle", "ruined_portal_swamp",
		"ruined_portal_mountain", "abandoned_camp_forest", "abandoned_camp_meadow", "abandoned_camp_taiga", "abandoned_camp_savanna", "abandoned_camp_swamp",
		"abandoned_camp_birch_forest", "abandoned_camp_flower_forest", "abandoned_camp_snowy_taiga", "abandoned_camp_sparse_jungle",
		"abandoned_camp_wooded_badlands", "abandoned_camp_windswept_forest", "abandoned_camp_cherry_grove", "abandoned_camp_dappled_forest",
		"abandoned_camp_old_growth_birch_forest", "abandoned_camp_old_growth_pine_taiga", "abandoned_camp_old_growth_spruce_taiga",
		"abandoned_camp_pale_garden", "abandoned_camp_bamboo_jungle", "swamp_hut", "igloo", "desert_pyramid", "jungle_pyramid", "pillager_outpost");
	/** The pack's ruin mods (their structures count as ruins, villages aside). */
	private static final List<String> RUIN_MODS = List.of(GrimSites.NAMESPACE, "ati_structures");
	/** Where a champion may lair. */
	private static final List<String> LAIRS = List.of("pillager_outpost", "desert_pyramid", "jungle_pyramid", "stronghold", "trial_chambers", "ancient_city",
		"ruined_portal", "ruined_portal_desert", "ruined_portal_jungle", "ruined_portal_swamp", "ruined_portal_mountain", "trail_ruins", "igloo", "swamp_hut",
		"mansion", "mineshaft");

	/** A quest a trade can give: its words, kind, target, how many (min..max); for a fetch, the ruins it looks in and the keepsake. */
	private record T(String key, int kind, String target, int min, int max, List<String> where, String keepsake) {
		static T slay(String key, String target, int min, int max) {
			return new T(key, Quest.SLAY, target, min, max, List.of(), "");
		}

		static T gather(String key, String item, int min, int max) {
			return new T(key, Quest.GATHER, item, min, max, List.of(), "");
		}

		static T fetch(String key, String keepsake, List<String> where) {
			return new T(key, Quest.FETCH, "", 1, 1, where, keepsake);
		}
	}

	/** A trade's quests and what it pays in (items as {@code id*min-max}). */
	private record Trade(List<T> quests, List<String> pays) {
	}

	private static final List<String> SEA = List.of("shipwreck", "shipwreck_beached", "ocean_ruin_cold", "ocean_ruin_warm");
	private static final List<String> MINES = List.of("mineshaft", "mineshaft_mesa");
	private static final List<String> HOLY = List.of("ruined_portal", "ruined_portal_desert", "ruined_portal_jungle", "ruined_portal_mountain", "desert_pyramid", "jungle_pyramid");
	private static final List<String> LORE = List.of("stronghold", "trail_ruins", "igloo", "desert_pyramid", "jungle_pyramid");
	private static final List<String> WAR = List.of("pillager_outpost", "trail_ruins", "ruined_portal");
	private static final List<String> FIELDS = List.of("abandoned_camp_forest", "abandoned_camp_meadow", "abandoned_camp_taiga", "abandoned_camp_savanna",
		"abandoned_camp_birch_forest", "abandoned_camp_flower_forest", "abandoned_camp_swamp", "abandoned_camp_snowy_taiga", "abandoned_camp_sparse_jungle",
		"abandoned_camp_wooded_badlands", "abandoned_camp_windswept_forest", "abandoned_camp_cherry_grove", "trail_ruins", "swamp_hut", "igloo");

	private static final Trade FARMING = new Trade(List.of(
			T.slay("farmer.rabbits", "minecraft:rabbit", 4, 8),
			T.slay("farmer.zombies", "#minecraft:zombies", 6, 10),
			T.gather("farmer.bones", "minecraft:bone", 8, 16),
			T.fetch("farmer.seeds", "minecraft:bundle|seed_pouch", FIELDS)),
		List.of("minecraft:hay_block*8-24", "minecraft:wheat*16-32", "minecraft:bread*8-16", "minecraft:golden_carrot*4-8", "minecraft:pumpkin_pie*4-8"));

	/** Each trade (a profession's path: vanilla's, MCA's and the settlements' own) and its quests. */
	private static final Map<String, Trade> TRADES = Map.ofEntries(
		Map.entry("farmer", FARMING),
		Map.entry("butcher", new Trade(List.of(
				T.gather("butcher.beef", "minecraft:beef", 8, 16),
				T.gather("butcher.pork", "minecraft:porkchop", 8, 16),
				T.gather("butcher.mutton", "minecraft:mutton", 8, 16),
				T.gather("butcher.chicken", "minecraft:chicken", 8, 16),
				T.gather("butcher.rabbit", "minecraft:rabbit", 4, 8),
				T.fetch("butcher.cleaver", "minecraft:iron_axe|old_cleaver", FIELDS)),
			List.of("minecraft:cooked_beef*8-16", "minecraft:cooked_porkchop*8-16", "minecraft:leather*6-12", "minecraft:cooked_mutton*8-16",
				"jaymoskitchen:beef_jerky*6-12"))),
		Map.entry("shepherd", new Trade(List.of(
				T.slay("shepherd.wolves", "minecraft:wolf", 3, 5),
				T.slay("shepherd.spiders", "minecraft:spider", 6, 10),
				T.gather("shepherd.string", "minecraft:string", 12, 24),
				T.fetch("shepherd.shears", "minecraft:shears|golden_shears", FIELDS)),
			List.of("minecraft:white_wool*16-32", "minecraft:red_wool*8-16", "minecraft:blue_wool*8-16", "minecraft:white_bed*1-2", "minecraft:white_carpet*12-24"))),
		Map.entry("leatherworker", new Trade(List.of(
				T.gather("leatherworker.leather", "minecraft:leather", 10, 20),
				T.gather("leatherworker.hides", "minecraft:rabbit_hide", 6, 12),
				T.gather("leatherworker.feet", "minecraft:rabbit_foot", 2, 4),
				T.fetch("leatherworker.saddle", "minecraft:saddle|old_saddle", FIELDS)),
			List.of("minecraft:leather*8-16", "minecraft:saddle*1-1", "minecraft:bundle*1-1", "minecraft:leather_horse_armor*1-1", "minecraft:item_frame*4-8"))),
		Map.entry("fisherman", new Trade(List.of(
				T.gather("fisherman.cod", "minecraft:cod", 10, 20),
				T.gather("fisherman.salmon", "minecraft:salmon", 8, 16),
				T.gather("fisherman.ink", "minecraft:ink_sac", 6, 10),
				T.slay("fisherman.drowned", "minecraft:drowned", 5, 8),
				T.fetch("fisherman.compass", "minecraft:compass|lost_compass", SEA)),
			List.of("minecraft:cooked_cod*8-16", "minecraft:cooked_salmon*8-16", "minecraft:fishing_rod*1-1", "minecraft:nautilus_shell*1-2", "minecraft:prismarine_crystals*4-8"))),
		Map.entry("fletcher", new Trade(List.of(
				T.gather("fletcher.feathers", "minecraft:feather", 16, 32),
				T.gather("fletcher.flint", "minecraft:flint", 12, 24),
				T.slay("fletcher.skeletons", "#minecraft:skeletons", 6, 10),
				T.fetch("fletcher.bow", "minecraft:bow|grandsires_bow", WAR)),
			List.of("minecraft:arrow*32-64", "minecraft:spectral_arrow*12-24", "minecraft:target*1-2", "minecraft:crossbow*1-1"))),
		Map.entry("armorer", new Trade(List.of(
				T.gather("armorer.iron", "minecraft:iron_ingot", 10, 20),
				T.gather("armorer.coal", "minecraft:coal", 16, 32),
				T.slay("armorer.pillagers", "#minecraft:illager", 4, 8),
				T.fetch("armorer.helm", "minecraft:iron_helmet|ancestral_helm", WAR)),
			List.of("minecraft:iron_chestplate*1-1", "minecraft:iron_helmet*1-1", "minecraft:shield*1-1", "minecraft:iron_ingot*6-12", "minecraft:chainmail_leggings*1-1"))),
		Map.entry("weaponsmith", new Trade(List.of(
				T.slay("weaponsmith.zombies", "#minecraft:zombies", 8, 14),
				T.slay("weaponsmith.pillagers", "#minecraft:illager", 4, 8),
				T.gather("weaponsmith.iron", "minecraft:iron_ingot", 8, 16),
				T.fetch("weaponsmith.blade", "minecraft:iron_sword|broken_blade", WAR)),
			List.of("minecraft:iron_sword*1-1", "minecraft:iron_axe*1-1", "minecraft:diamond_sword*1-1", "minecraft:iron_ingot*6-12"))),
		Map.entry("toolsmith", new Trade(List.of(
				T.gather("toolsmith.iron", "minecraft:iron_ingot", 8, 16),
				T.gather("toolsmith.coal", "minecraft:coal", 16, 32),
				T.fetch("toolsmith.pick", "minecraft:iron_pickaxe|old_pick", MINES)),
			List.of("minecraft:iron_pickaxe*1-1", "minecraft:iron_shovel*1-1", "minecraft:diamond_pickaxe*1-1", "minecraft:iron_ingot*6-12", "minecraft:anvil*1-1"))),
		Map.entry("librarian", new Trade(List.of(
				T.fetch("librarian.tome", "minecraft:book|lost_tome", LORE),
				T.gather("librarian.paper", "minecraft:paper", 18, 32),
				T.gather("librarian.ink", "minecraft:ink_sac", 6, 12),
				T.gather("librarian.feathers", "minecraft:feather", 8, 16)),
			List.of("minecraft:book*4-8", "minecraft:bookshelf*2-4", "minecraft:lantern*2-4", "minecraft:writable_book*1-1"))),
		Map.entry("cleric", new Trade(List.of(
				T.slay("cleric.undead", "#minecraft:undead", 8, 14),
				T.gather("cleric.eyes", "minecraft:spider_eye", 6, 10),
				T.gather("cleric.flesh", "minecraft:rotten_flesh", 16, 32),
				T.fetch("cleric.relic", "minecraft:amethyst_shard|holy_relic", HOLY)),
			List.of("minecraft:experience_bottle*8-16", "minecraft:lapis_lazuli*12-24", "minecraft:golden_apple*1-2", "minecraft:glowstone_dust*8-16"))),
		Map.entry("cartographer", new Trade(List.of(
				T.fetch("cartographer.chart", "minecraft:paper|torn_chart", SEA),
				T.fetch("cartographer.map", "minecraft:paper|old_map", RUINS),
				T.slay("cartographer.phantoms", "minecraft:phantom", 3, 5)),
			List.of("minecraft:map*2-4", "minecraft:compass*1-1", "minecraft:spyglass*1-1", "minecraft:recovery_compass*1-1"))),
		Map.entry("mason", new Trade(List.of(
				T.gather("mason.clay", "minecraft:clay_ball", 16, 32),
				T.gather("mason.quartz", "minecraft:quartz", 8, 16),
				T.slay("mason.silverfish", "minecraft:silverfish", 6, 10),
				T.fetch("mason.chisel", "minecraft:flint|masters_chisel", RUINS)),
			List.of("minecraft:stone_bricks*32-64", "minecraft:stone*32-64", "minecraft:polished_andesite*24-48", "minecraft:smooth_stone*24-48"))),
		Map.entry("builder", new Trade(List.of(
				T.gather("builder.logs", "minecraft:oak_log", 16, 32),
				T.gather("builder.cobble", "minecraft:cobblestone", 32, 64),
				T.fetch("builder.plans", "minecraft:paper|lost_plans", RUINS)),
			List.of("minecraft:stone*32-64", "minecraft:oak_log*16-32", "minecraft:stone_bricks*24-48", "minecraft:spruce_log*16-32", "minecraft:glass*12-24"))),
		Map.entry("miner", new Trade(List.of(
				T.slay("miner.spiders", "minecraft:cave_spider", 5, 8),
				T.slay("miner.creepers", "minecraft:creeper", 4, 6),
				T.gather("miner.torches", "minecraft:torch", 24, 48),
				T.fetch("miner.lantern", "minecraft:lantern|lost_lantern", MINES)),
			List.of("minecraft:raw_iron*8-16", "minecraft:coal*16-32", "minecraft:stone*32-64", "minecraft:raw_copper*12-24", "minecraft:torch*16-32"))),
		Map.entry("woodcutter", new Trade(List.of(
				T.slay("woodcutter.spiders", "minecraft:spider", 6, 10),
				T.gather("woodcutter.saplings", "minecraft:oak_sapling", 6, 12),
				T.fetch("woodcutter.axe", "minecraft:iron_axe|felling_axe", FIELDS)),
			List.of("minecraft:oak_log*24-48", "minecraft:spruce_log*16-32", "minecraft:birch_log*16-32", "minecraft:oak_planks*32-64", "minecraft:stick*16-32"))),
		Map.entry("guard", new Trade(List.of(
				T.slay("guard.monsters", "*monster", 10, 16),
				T.slay("guard.skeletons", "#minecraft:skeletons", 8, 12),
				T.slay("guard.pillagers", "#minecraft:illager", 5, 8),
				T.fetch("guard.blade", "minecraft:iron_sword|comrades_blade", WAR)),
			List.of("minecraft:iron_sword*1-1", "minecraft:shield*1-1", "minecraft:arrow*16-32", "minecraft:iron_chestplate*1-1", "minecraft:cooked_beef*6-12"))),
		Map.entry("none", new Trade(List.of(
				T.gather("none.berries", "minecraft:sweet_berries", 12, 24),
				T.gather("none.flowers", "minecraft:poppy", 6, 12),
				T.fetch("none.heirloom", "minecraft:gold_nugget|family_heirloom", RUINS)),
			List.of("minecraft:bread*6-12", "minecraft:cookie*8-16", "minecraft:cake*1-1", "minecraft:apple*6-12"))));

	/** MCA's and others' trades that ask what another does (an archer as a guard; the jobless as anyone). */
	private static final Map<String, String> LIKE = Map.of("archer", "guard", "mercenary", "guard", "adventurer", "guard", "nitwit", "none");

	/** Enchanted books a villager may pay with (a librarian always does, a cleric often). */
	private static final List<String> BOOKS = List.of("minecraft:sharpness", "minecraft:protection", "minecraft:efficiency", "minecraft:unbreaking",
		"minecraft:power", "minecraft:feather_falling", "minecraft:looting", "minecraft:fortune", "minecraft:respiration", "minecraft:smite",
		"minecraft:projectile_protection", "minecraft:fire_aspect", "minecraft:knockback", "minecraft:silk_touch");
	/** A king's: the great ones, at their highest. */
	private static final List<String> ROYAL_BOOKS = List.of("minecraft:sharpness@5", "minecraft:protection@4", "minecraft:mending@1", "minecraft:efficiency@5",
		"minecraft:power@5", "minecraft:feather_falling@4", "minecraft:looting@3", "minecraft:fortune@3", "minecraft:unbreaking@3", "minecraft:silk_touch@1");
	/** A king's other gifts. */
	private static final List<String> ROYAL_PAYS = List.of("minecraft:gold_block*2-4", "minecraft:diamond*3-6", "minecraft:golden_apple*2-3",
		"minecraft:enchanted_golden_apple*1-1", "minecraft:netherite_scrap*1-2");

	private QuestBoard() {
	}

	/** The trade a giver asks as. */
	static String job(LivingEntity giver) {
		if (!(giver instanceof Villager v)) {
			return "king";
		}
		String path = BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
		path = LIKE.getOrDefault(path, path);
		return TRADES.containsKey(path) ? path : "none";
	}

	/** A villager's quest, or null if none could be made. */
	static @Nullable Quest villager(ServerLevel level, LivingEntity giver, Faction f, RandomSource random) {
		String job = job(giver);
		Trade trade = TRADES.getOrDefault(job, TRADES.get("none"));
		Quest.Giver who = new Quest.Giver(giver.getUUID(), giver.getName().getString(), f.id(), false, job);
		// a letter a third of the time (more for the jobless and cartographers), else one of the trade's own
		float mail = job.equals("none") || job.equals("cartographer") ? 0.45F : 0.25F;
		List<T> options = new ArrayList<>(trade.quests());
		if (random.nextFloat() < mail) {
			Quest q = mail(level, giver, f, who, random, trade);
			if (q != null) {
				return q;
			}
		}
		while (!options.isEmpty()) {
			T t = options.remove(random.nextInt(options.size()));
			Quest q = make(level, giver, f, who, t, random, trade);
			if (q != null) {
				return q;
			}
		}
		return mail(level, giver, f, who, random, trade);
	}

	private static @Nullable Quest make(ServerLevel level, LivingEntity giver, Faction f, Quest.Giver who, T t, RandomSource random, Trade trade) {
		int count = t.min() + random.nextInt(t.max() - t.min() + 1);
		Quest.Objective objective;
		if (t.kind() == Quest.FETCH) {
			Pair<BlockPos, Holder<Structure>> ruin = locate(level, giver.blockPosition(), t.where(), true, VILLAGER_SEARCH_CHUNKS);
			if (ruin == null) {
				return null;
			}
			String where = ruin.getSecond().unwrapKey().map(k -> k.identifier().toString()).orElse("");
			// (target: the ruin; otherName: what the keepsake is and is called)
			objective = new Quest.Objective(where, 1, 0, Optional.of(ruin.getFirst()), Optional.empty(), t.keepsake(), where, false);
		} else {
			if (t.kind() == Quest.GATHER && BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(t.target())).isEmpty()) {
				return null;
			}
			objective = new Quest.Objective(t.target(), count, 0, Optional.empty(), Optional.empty(), "", "", false);
		}
		int emeralds = switch (t.kind()) {
			case Quest.FETCH -> 8 + random.nextInt(7);
			case Quest.SLAY -> 2 + Math.round(count * 0.6F) + random.nextInt(3);
			default -> 3 + random.nextInt(6);
		};
		int hearts = HEARTS + (t.kind() == Quest.FETCH ? 8 : t.kind() == Quest.SLAY ? 4 : 0);
		return new Quest(UUID.randomUUID().toString(), t.kind(), t.key(), who, objective, reward(level, who.job(), t.kind(), emeralds, hearts, trade, random), 0L);
	}

	/** A letter to carry: to someone else in the settlement, or to another settlement's ruler. */
	private static @Nullable Quest mail(ServerLevel level, LivingEntity giver, Faction f, Quest.Giver who, RandomSource random, Trade trade) {
		MinecraftServer server = level.getServer();
		Quest.Objective objective = null;
		boolean far = false;
		if (random.nextFloat() < MAIL_ELSEWHERE) {
			List<Faction> elsewhere = new ArrayList<>();
			for (Faction other : Factions.all(server)) {
				double d = Math.sqrt(other.seat().distSqr(f.seat()));
				if (other.id() != f.id() && !other.fallen() && other.type() != FactionType.BARBARIAN && other.leader().isPresent() && d > 96.0 && d < MAIL_RANGE) {
					elsewhere.add(other);
				}
			}
			if (!elsewhere.isEmpty()) {
				Faction to = elsewhere.get(random.nextInt(elsewhere.size()));
				String ruler = to.type() == FactionType.KINGDOM ? "king" : to.type() == FactionType.FIEFDOM ? "lord" : "elder";
				objective = new Quest.Objective("leader:" + to.id() + ":" + ruler, 1, 0, Optional.of(to.seat()), Optional.empty(), "", to.name(), false);
				far = true;
			}
		}
		if (objective == null) {
			List<Villager> folk = level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(Economy.POPULATION_RANGE),
				v -> v.isAlive() && !v.isBaby() && v != giver && v.distanceToSqr(giver) > 16.0 * 16.0);
			if (folk.isEmpty()) {
				folk = level.getEntitiesOfClass(Villager.class, giver.getBoundingBox().inflate(Economy.POPULATION_RANGE), v -> v.isAlive() && !v.isBaby() && v != giver);
			}
			if (folk.isEmpty()) {
				return null;
			}
			Villager to = folk.get(random.nextInt(folk.size()));
			objective = new Quest.Objective("", 1, 0, Optional.of(to.blockPosition()), Optional.of(to.getUUID()), to.getName().getString(), f.name(), false);
		}
		int emeralds = far ? 8 + random.nextInt(6) : 3 + random.nextInt(3);
		return new Quest(UUID.randomUUID().toString(), Quest.DELIVER, far ? "mail.far" : "mail.near", who, objective,
			reward(level, who.job(), Quest.DELIVER, emeralds, HEARTS, trade, random), 0L);
	}

	private static Quest.Reward reward(ServerLevel level, String job, int kind, int emeralds, int hearts, Trade trade, RandomSource random) {
		List<String> items = new ArrayList<>();
		List<String> pool = new ArrayList<>(trade.pays());
		pool.removeIf(s -> BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(s.substring(0, s.indexOf('*')))).isEmpty());
		int picks = kind == Quest.FETCH ? 2 : 1;
		for (int i = 0; i < picks && !pool.isEmpty(); i++) {
			items.add(roll(pool.remove(random.nextInt(pool.size())), random));
		}
		Optional<String> book = Optional.empty();
		float bookChance = job.equals("librarian") ? 1.0F : job.equals("cleric") ? 0.5F : 0.08F;
		if (random.nextFloat() < bookChance) {
			book = Optional.of(BOOKS.get(random.nextInt(BOOKS.size())) + "@" + (1 + random.nextInt(3)));
		}
		Optional<String> relic = Optional.empty();
		boolean smith = job.equals("armorer") || job.equals("weaponsmith");
		boolean arms = job.equals("guard") || job.equals("fletcher");
		float relicChance = smith ? SMITH_RELIC : arms ? ARMS_RELIC : OTHER_RELIC;
		if (kind == Quest.GATHER || kind == Quest.DELIVER) {
			relicChance *= 0.5F;
		}
		if (random.nextFloat() < relicChance) {
			boolean weapon = job.equals("armorer") ? false : smith || arms || random.nextBoolean();
			relic = Optional.of(Relics.roll(random, weapon, false));
		}
		return new Quest.Reward(emeralds, items, book, relic, hearts, REPUTATION + random.nextInt(11), STANDING);
	}

	/** {@code id*min-max} rolled to {@code id*n}. */
	private static String roll(String spec, RandomSource random) {
		int star = spec.indexOf('*');
		String[] range = spec.substring(star + 1).split("-");
		int min = Integer.parseInt(range[0]), max = Integer.parseInt(range[1]);
		return spec.substring(0, star) + "*" + (min + random.nextInt(max - min + 1));
	}

	// ---- a king's ------------------------------------------------------------------------------------------------------

	/** A king's quest: a camp's chief slain, or a champion put down in its lair; null if neither can be found. */
	static @Nullable Quest king(ServerLevel level, LivingEntity king, Faction f, RandomSource random) {
		Quest.Giver who = new Quest.Giver(king.getUUID(), king.getName().getString(), f.id(), true, "king");
		Quest q = random.nextFloat() < 0.4F ? chief(level, f, who, random) : null;
		if (q == null) {
			q = champion(level, king, f, who, random);
		}
		return q != null ? q : chief(level, f, who, random);
	}

	private static @Nullable Quest chief(ServerLevel level, Faction f, Quest.Giver who, RandomSource random) {
		Faction best = null;
		double bestD = CAMP_RANGE * CAMP_RANGE;
		for (Faction camp : Factions.all(level.getServer(), FactionType.BARBARIAN)) {
			double d = camp.seat().distSqr(f.seat());
			if (!camp.fallen() && camp.leader().isPresent() && d < bestD) {
				best = camp;
				bestD = d;
			}
		}
		if (best == null) {
			return null;
		}
		boolean illager = Camps.illager(level.getServer(), best.id());
		Quest.Objective o = new Quest.Objective("camp:" + best.id(), 1, 0, Optional.of(best.seat()), best.leader(), "", best.name(), false);
		return new Quest(UUID.randomUUID().toString(), Quest.CHIEF, illager ? "king.warlord" : "king.chief", who, o, royalReward(random), 0L);
	}

	private static @Nullable Quest champion(ServerLevel level, LivingEntity king, Faction f, Quest.Giver who, RandomSource random) {
		Pair<BlockPos, Holder<Structure>> lair = locate(level, f.seat(), LAIRS, true, KING_SEARCH_CHUNKS);
		if (lair == null) {
			return null;
		}
		String where = lair.getSecond().unwrapKey().map(k -> k.identifier().toString()).orElse("");
		String foe = QuestFoes.championFor(where, random);
		Quest.Objective o = new Quest.Objective(where, 1, 0, Optional.of(lair.getFirst()), Optional.empty(), foe, where, false);
		return new Quest(UUID.randomUUID().toString(), Quest.CHAMPION, "king.champion." + foe, who, o, royalReward(random), 0L);
	}

	private static Quest.Reward royalReward(RandomSource random) {
		List<String> items = new ArrayList<>();
		items.add(roll(ROYAL_PAYS.get(random.nextInt(ROYAL_PAYS.size())), random));
		String book = ROYAL_BOOKS.get(random.nextInt(ROYAL_BOOKS.size()));
		return new Quest.Reward(40 + random.nextInt(25), items, Optional.of(book), Optional.of(Relics.roll(random, random.nextBoolean(), true)), 0,
			ROYAL_REPUTATION, ROYAL_STANDING);
	}

	// ---- the world's ruins ---------------------------------------------------------------------------------------------

	/**
	 * The nearest of the named structures (vanilla ids by path; with {@code mods}, the pack's ruin mods' too) to
	 * {@code from} within {@code chunks}, that's far enough away and isn't lived in; null if none.
	 */
	static @Nullable Pair<BlockPos, Holder<Structure>> locate(ServerLevel level, BlockPos from, List<String> names, boolean mods, int chunks) {
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		List<Holder<Structure>> wanted = new ArrayList<>();
		for (String name : names) {
			registry.get(Identifier.withDefaultNamespace(name)).ifPresent(wanted::add);
		}
		if (mods) {
			registry.listElements().forEach(h -> {
				Identifier id = h.key().identifier();
				if (RUIN_MODS.contains(id.getNamespace()) && !id.getPath().contains("village")) {
					wanted.add(h);
				}
			});
		}
		if (wanted.isEmpty()) {
			return null;
		}
		Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(wanted), from, chunks, false);
		if (found == null) {
			return null;
		}
		// (a ruin a settlement has taken over, a fiefdom in a Grim ruin say, is lived in: no quest there)
		BlockPos at = found.getFirst();
		for (Faction f : Factions.all(level.getServer())) {
			double dx = f.seat().getX() - at.getX(), dz = f.seat().getZ() - at.getZ();
			if (!f.fallen() && dx * dx + dz * dz < RUIN_SETTLED * RUIN_SETTLED) {
				return null;
			}
		}
		return found;
	}
}
