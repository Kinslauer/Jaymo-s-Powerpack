package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

/**
 * VillagersPlus' job houses in the builders' catalogue. Andy (2026-11-21): "can we add all these villager plus buildings
 * to the building catalogue? that way when they have room and can afford it, they build a specific building for that
 * occupation". With VillagersPlus installed (mod id {@value #MOD}, GPL-3.0; read at run time from the installed copy, as
 * {@link DntBuildings} reads Dungeons and Taverns: nothing of theirs copied into ours), kingdoms, fiefdoms and villages
 * put up its miner's, horticulturist's, alchemist's, oceanographer's and occultist's houses in their land's style, block
 * by block from storage like any building, once they are big enough ({@link #register}); each house's own workstation
 * gives a villager that job. Its miner's house stands over a shaft: only the house goes up (the shaft below is
 * VillagersPlus' world generation, not a crew's), and the town's own mine is still {@link Mines}. A land VillagersPlus
 * draws no miner's house for (snowy, desert) takes the plains one.
 * <p>
 * One of each; a mining fiefdom raises up to {@link Focus#MINING_MINE_HEADS} miner's houses ({@link Focus#max}), and
 * every miner's house finished adds {@link #MINE_ROCK} stone and {@link #MINE_ORES} ore a day to its storage
 * ({@link #produce}), so a vassal set to mining has a real reason for more than one.
 */
final class VpBuildings {
	static final String MOD = "villagersplus";
	static final String MINER_HOUSE = "miner_house";
	/** A miner's house's mine, a day: stone, and ore picked from {@link Focus#ORES}. */
	static final int MINE_ROCK = 16, MINE_ORES = 3;

	/** Each job's catalogue id and the size a kingdom or fiefdom, and a village, first builds it from. */
	private record Job(String job, int realmFrom, int villageFrom) {
		String id() {
			return this.job + "_house";
		}
	}

	private static final List<Job> JOBS = List.of(new Job("miner", 16, 12), new Job("horticulturist", 18, 14), new Job("oceanographer", 22, 18),
		new Job("alchemist", 26, 20), new Job("occultist", 32, 24));
	/** VillagersPlus' lands, and which have a miner's house. */
	private static final List<String> LANDS = List.of("plains", "taiga", "savanna", "snowy", "desert"), MINER_LANDS = List.of("plains", "taiga", "savanna");

	private static final Map<String, Optional<Blueprint>> CACHE = new HashMap<>();

	private VpBuildings() {
	}

	static boolean loaded() {
		return FabricLoader.getInstance().isModLoaded(MOD);
	}

	static void clear() {
		CACHE.clear();
	}

	/** The job houses in the kingdoms', fiefdoms' and villages' catalogues (only while VillagersPlus is installed). */
	static void register() {
		if (!loaded()) {
			return;
		}
		for (Job j : JOBS) {
			int most = j.job().equals("miner") ? Focus.MINING_MINE_HEADS : 1;
			for (FactionType type : new FactionType[] {FactionType.KINGDOM, FactionType.FIEFDOM, FactionType.VILLAGE}) {
				Blueprint.register(type, new Blueprint.Entry(j.id(), (level, faction, variant) -> {
					Blueprint bp = pick(level, faction, j.job());
					return bp != null ? bp : Buildings.firePit(Buildings.Kit.of(level, faction, variant, variant));
				}, most, type == FactionType.VILLAGE ? j.villageFrom() : j.realmFrom()));
			}
		}
	}

	static boolean isJob(String entry) {
		for (Job j : JOBS) {
			if (j.id().equals(entry)) {
				return true;
			}
		}
		return false;
	}

	/** A job house no VillagersPlus house can be read for in {@code f}'s land: not planned. */
	static boolean unavailable(MinecraftServer server, Faction f, String entry) {
		if (!isJob(entry)) {
			return false;
		}
		ServerLevel level = Factions.level(server);
		return pick(level, f, entry.substring(0, entry.length() - "_house".length())) == null;
	}

	/** VillagersPlus' house for {@code job} in {@code f}'s land (the plains one for a miner's house where its land has none), or null. */
	static @Nullable Blueprint pick(ServerLevel level, Faction f, String job) {
		String land = land(level, f);
		if (job.equals("miner") && !MINER_LANDS.contains(land)) {
			land = "plains";
		}
		Blueprint bp = read(level.getServer(), job, land);
		return bp != null || land.equals("plains") ? bp : read(level.getServer(), job, "plains");
	}

	/** VillagersPlus' land for {@code f}'s seat: the land of the Epic town that would stand there. */
	private static String land(ServerLevel level, Faction f) {
		String town = EpicTowns.villageFor(Palette.of(level.getBiome(f.seat())));
		String land = town.substring(town.indexOf('_') + 1);
		return LANDS.contains(land) ? land : "plains";
	}

	private static @Nullable Blueprint read(MinecraftServer server, String job, String land) {
		return CACHE.computeIfAbsent(job + "|" + land, k -> Optional.ofNullable(load(server, job, land))).orElse(null);
	}

	private static @Nullable Blueprint load(MinecraftServer server, String job, String land) {
		Identifier id = Identifier.fromNamespaceAndPath(MOD, "village/" + land + "/" + land + "_" + job);
		Optional<StructureTemplate> template;
		try {
			template = server.getStructureTemplateManager().get(id);
		} catch (RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("VillagersPlus' {} can't be read: {}", id, e.toString());
			return null;
		}
		if (template.isEmpty()) {
			return null;
		}
		try {
			Blueprint bp = CtovHalls.convert(job + "_house", template.get().save(new CompoundTag()));
			if (bp == null) {
				JaymosKingdoms.LOGGER.info("VillagersPlus' {} skipped (too big for a crew)", id);
			}
			return bp;
		} catch (RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("VillagersPlus' {} can't be used: {}", id, e.toString());
			return null;
		}
	}

	/** {@code days} of {@code f}'s miner's houses' mines into its storage. */
	static void produce(ServerLevel level, Faction f, int days) {
		if (!loaded() || days <= 0) {
			return;
		}
		int heads = HavenGrowth.built(Works.of(level.getServer(), f.id()), MINER_HOUSE);
		if (heads == 0) {
			return;
		}
		int rock = heads * MINE_ROCK * days;
		Storage.put(level, f, Items.COBBLESTONE, rock - rock / Production.STONE_ONE_IN);
		Storage.put(level, f, Items.STONE, rock / Production.STONE_ONE_IN);
		for (int i = 0; i < heads * MINE_ORES * days; i++) {
			Storage.put(level, f, Focus.ORES[level.getRandom().nextInt(Focus.ORES.length)], 1);
		}
	}
}
