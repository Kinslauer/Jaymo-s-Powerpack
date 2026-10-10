package com.jaymoskingdoms;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Once a day, every faction's people put <b>real items</b> into its storage: building materials from its land (logs
 * where it's wooded, cobblestone and stone where it isn't) and food from its farms, scaled by how many work (its
 * villagers and folk); and a realm taxes emeralds into its treasury (a fiefdom sends a share of them to its overlord).
 * Villages produce less, camps much less (they live on loot). A faction whose storehouse isn't loaded at day's turn
 * catches up when it is (up to {@link #MAX_CATCH_UP} days). Its land is surveyed (villagers, crops, woodland) whenever
 * it produces with its seat loaded; otherwise the last survey stands.
 */
public final class Production {
	/** How often it's looked at (ticks). */
	static final int CHECK_TICKS = 200;
	/** A day, in clock ticks. */
	static final long DAY_TICKS = 24000L;
	/** The most missed days made up at once. */
	static final int MAX_CATCH_UP = 3;
	/** Labour a day: a base, plus per villager and per folk. */
	static final int LABOUR_BASE = 4, LABOUR_PER_VILLAGER = 2, LABOUR_PER_FOLK = 1;
	/** Materials (items) per unit of labour, and the most in a day. */
	static final int MATERIALS_PER_LABOUR = 2, MATERIALS_MOST = 192;
	/** Of the rock: one part in this many comes as smooth stone (the rest cobblestone). */
	static final int STONE_ONE_IN = 4;
	/** Food a day: a base, per villager, and one per this many crop blocks; the most in a day. */
	static final int FOOD_BASE = 2, FOOD_PER_VILLAGER = 1, CROPS_PER_FOOD = 4, FOOD_MOST = 128;
	/** Production by type, in percent. */
	static final int SHARE_KINGDOM = 100, SHARE_FIEFDOM = 80, SHARE_VILLAGE = 60, SHARE_CAMP = 30;
	/** Taxes (emeralds a day, realms only): a base, one per this many villagers, one per sworn member. */
	static final int TAX_BASE = 2, VILLAGERS_PER_TAX = 2, TAX_PER_MEMBER = 1;
	/** The share (percent) of a fiefdom's taxes that goes to its overlord. */
	static final int TRIBUTE_PERCENT = 30;
	/** The survey: villagers counted, and crops sought, this far round the seat; woodland sampled this far, this often. */
	static final int SURVEY_RANGE = 64, FARM_RANGE = 48, LAND_RANGE = 96, LAND_STEP = 6;
	/** Woodland's share (percent) is kept within these, so there's always some of each. */
	static final int FOREST_LEAST = 10, FOREST_MOST = 90;
	/** A wooded sample counts this many times over (a canopy from above hides the open ground between trees). */
	static final int FOREST_WEIGHT = 3;

	private Production() {
	}

	/** The day it is (the Overworld's clock: sleeping through the night moves it on). */
	public static long day(MinecraftServer server) {
		return Factions.level(server).getOverworldClockTime() / DAY_TICKS;
	}

	static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		if (level.getGameTime() % CHECK_TICKS != 0) {
			return;
		}
		long today = day(server);
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.holdings().storage().isEmpty()) {
				continue;
			}
			long last = f.holdings().lastDay();
			if (last < 0 || last > today) {
				// (a new storehouse, or the clock set back: start counting from today)
				Factions.update(server, f.id(), x -> x.withHoldings(x.holdings().withLastDay(today)));
				continue;
			}
			if (last == today || !Storage.loaded(level, f)) {
				continue;
			}
			int days = (int)Math.min(MAX_CATCH_UP, today - last);
			produce(level, f, days);
			Factions.update(server, f.id(), x -> x.withHoldings(x.holdings().withLastDay(today)));
		}
	}

	/** {@code days}' production for {@code faction}, into its storage (and treasury). Surveys its land first if it can. */
	public static void produce(ServerLevel level, Faction faction, int days) {
		MinecraftServer server = level.getServer();
		Faction f = survey(level, faction);
		RandomSource random = level.getRandom();
		int share = switch (f.type()) {
			case KINGDOM -> SHARE_KINGDOM;
			case FIEFDOM -> SHARE_FIEFDOM;
			case VILLAGE -> SHARE_VILLAGE;
			case BARBARIAN -> SHARE_CAMP;
		};
		Faction.Holdings h = f.holdings();
		int labour = LABOUR_BASE + h.population() * LABOUR_PER_VILLAGER + h.staff() * LABOUR_PER_FOLK;
		int materials = Math.min(MATERIALS_MOST, labour * MATERIALS_PER_LABOUR) * share / 100 * days;
		int logs = materials * h.forest() / 100;
		int rock = materials - logs;
		int stone = rock / STONE_ONE_IN;
		int food = Math.min(FOOD_MOST, FOOD_BASE + h.population() * FOOD_PER_VILLAGER + h.farms() / CROPS_PER_FOOD) * share / 100 * days;
		int lost = 0;
		lost += Storage.put(level, f, logItem(level, f), logs);
		lost += Storage.put(level, f, Items.COBBLESTONE, rock - stone);
		lost += Storage.put(level, f, Items.STONE, stone);
		// food: bread mostly, then what the fields grow
		Item[] crops = {Items.BREAD, Items.BREAD, Items.CARROT, Items.POTATO, Items.BEETROOT, Items.APPLE};
		int[] counts = new int[crops.length];
		for (int i = 0; i < food; i++) {
			counts[random.nextInt(crops.length)]++;
		}
		for (int i = 0; i < crops.length; i++) {
			lost += Storage.put(level, f, crops[i], counts[i]);
		}
		long taxes = 0;
		if (f.type().isRealm()) {
			taxes = (long)(TAX_BASE + h.population() / VILLAGERS_PER_TAX + f.members().size() * TAX_PER_MEMBER) * days;
			long tribute = f.type() == FactionType.FIEFDOM && f.overlord() >= 0 ? taxes * TRIBUTE_PERCENT / 100 : 0;
			long kept = taxes - tribute;
			Factions.update(server, f.id(), x -> x.withTreasury(x.treasury() + kept));
			if (tribute > 0) {
				Factions.update(server, f.overlord(), x -> x.withTreasury(x.treasury() + tribute));
			}
		}
		JaymosKingdoms.LOGGER.debug("{} produced {} logs, {} rock, {} food, {} emeralds ({} days; {} lost for want of room)", f.name(), logs, rock, food,
			taxes, days, lost);
	}

	/** The log its people cut: its palette's (oak on the plains, spruce in the taiga, ...). */
	public static Item logItem(ServerLevel level, Faction faction) {
		return Palette.of(level.getBiome(faction.seat())).log().getBlock().asItem();
	}

	/**
	 * Counts its villagers, its crops and its woodland, if its seat is loaded (otherwise the last survey stands), and
	 * stores what it found. Returns the faction as stored.
	 */
	public static Faction survey(ServerLevel level, Faction faction) {
		BlockPos seat = faction.seat();
		if (!level.isPositionEntityTicking(seat)) {
			return faction;
		}
		List<Villager> villagers = level.getEntitiesOfClass(Villager.class, new AABB(seat).inflate(SURVEY_RANGE), v -> v.isAlive());
		int farms = 0;
		for (int dx = -FARM_RANGE; dx <= FARM_RANGE; dx++) {
			for (int dz = -FARM_RANGE; dz <= FARM_RANGE; dz++) {
				BlockPos column = seat.offset(dx, 0, dz);
				if (!level.isLoaded(column)) {
					continue;
				}
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
				if (level.getBlockState(top).getBlock() instanceof CropBlock) {
					farms++;
				}
			}
		}
		int wooded = 0, sampled = 0;
		for (int dx = -LAND_RANGE; dx <= LAND_RANGE; dx += LAND_STEP) {
			for (int dz = -LAND_RANGE; dz <= LAND_RANGE; dz += LAND_STEP) {
				BlockPos column = seat.offset(dx, 0, dz);
				if (!level.isLoaded(column)) {
					continue;
				}
				BlockState top = level.getBlockState(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column).below());
				sampled++;
				if (top.is(BlockTags.LEAVES) || top.is(BlockTags.LOGS)) {
					wooded++;
				}
			}
		}
		int forest = sampled == 0 ? faction.holdings().forest()
			: Math.max(FOREST_LEAST, Math.min(FOREST_MOST, wooded * 100 * FOREST_WEIGHT / sampled));
		int population = villagers.size();
		int crops = farms;
		Faction stored = Factions.update(level.getServer(), faction.id(), x -> x.withHoldings(x.holdings().withSurvey(population, crops, forest)));
		return stored == null ? faction : stored;
	}
}
