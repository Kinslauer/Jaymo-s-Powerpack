package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.jspecify.annotations.Nullable;

/**
 * Every generated structure needs a reason to be where it is (Andy, 2026-11-24: "remember the generation needs a reason";
 * the plan is "Structure placement and factions plan", track 2). Each {@link Rule} names a structure tag and the
 * neighbour it must have: a structure in it only generates where one of the neighbours will stand within a ring round it,
 * or (for some) where the land is harsh enough to explain it standing alone.
 * <ul>
 * <li><b>Farms</b> ({@code farms_by_homes}): within {@link #FARM_NEAR} chunks of a village or a lone home or homestead
 * ({@code farm_neighbours}). Andy: "they are farms and provide food somewhere".</li>
 * <li><b>Mines</b> ({@code mines_by_buyers}: quarries, miners' outposts, prospectors' camps): near a buyer, a village within
 * {@link #MINE_NEAR} chunks (about 400 blocks), but not on its doorstep (none within {@link #MINE_CLEAR}, about 150). The
 * ore is theirs to find: each mod already puts its mine in the hills or badlands it belongs in.</li>
 * <li><b>Stables</b> ({@code stables_by_towns}): a horse camp on the way to a village, within {@link #STABLE_NEAR} chunks
 * (about 300 blocks).</li>
 * <li><b>Watchtowers</b> ({@code towers_on_borders}: fire watches and lookouts): on a realm's edge, a village between
 * {@link #TOWER_CLEAR} and {@link #TOWER_NEAR} chunks off (about 100 to 400 blocks): far enough out to watch its
 * borders, near enough to be its own.</li>
 * <li><b>Lone homesteads</b> ({@code homesteads_by_towns}: Structory's outcast villager's horse barn, Homesteads' and
 * Moog's lone houses): a town they trade with within {@link #HOMESTEAD_NEAR} chunks (about 500 blocks, a day's ride).</li>
 * <li><b>Old cities</b> ({@code old_cities}: our old age's ruins, the consul's city and the rest): never on top of
 * anything lived in ({@code lived_in}: a village, a home, a farm, a stable, a mine), none within {@link #OLD_CLEAR}
 * chunks. Andy, 2026-11-24, on the Deck: the Pillared ruins had come up through an outcast villager's barn.</li>
 * <li><b>Ruins</b> ({@code ruins_with_heirs}: castle ruins, ruined towns, remnants): where an old realm stood, so either a
 * living village (its heir) within {@link #RUIN_NEAR} chunks (about 300 blocks), or alone in land at least
 * {@link #RUIN_HARSH} steps harsh ({@link HarshLands}), where the cold or the heat drove its people out.</li>
 * <li><b>Cairns</b> ({@code cairns_by_ways}): heaps of stones marking the ways out of a town, so a settlement within
 * {@link #CAIRN_NEAR} chunks (about 500 blocks) and none within {@link #CAIRN_CLEAR} (not in its streets).</li>
 * </ul>
 * A neighbour is found the way the world places it, without generating anything: each structure set holding one is asked
 * where its spread puts one near here, and whether the land there is one it stands in. A neighbour that later fails for
 * another reason (its own exclusion zone, the terrain) can still leave a structure without one; that is rare. Only new
 * chunks change.
 */
public final class PlacementReasons {
	/** Who counts as a farm's neighbour (a village, a lone home), and as a settlement (a village of any mod's). */
	public static final TagKey<Structure> FARM_NEIGHBOURS = tag("farm_neighbours");
	public static final TagKey<Structure> SETTLEMENTS = tag("settlements");

	/** Rings (chunks, either way): a neighbour must stand within the first number and none nearer than the second. */
	static final int FARM_NEAR = 6;
	static final int MINE_NEAR = 25, MINE_CLEAR = 9;
	static final int STABLE_NEAR = 19;
	static final int TOWER_NEAR = 25, TOWER_CLEAR = 6;
	static final int RUIN_NEAR = 19;
	static final int HOMESTEAD_NEAR = 32;
	static final int OLD_CLEAR = 4;
	static final int CAIRN_NEAR = 32, CAIRN_CLEAR = 3;
	/** Land this harsh explains a ruin with no heir. */
	static final int RUIN_HARSH = 2;

	/**
	 * A structure in {@code what} generates only with a {@code near} neighbour within {@code within} chunks and none
	 * within {@code clear}, or where the land is at least {@code harsh} steps harsh (0: never enough on its own).
	 * {@code within} 0: no neighbour is needed, only none within {@code clear}.
	 */
	public record Rule(TagKey<Structure> what, TagKey<Structure> near, int within, int clear, int harsh) {
	}

	static final List<Rule> RULES = List.of(
		new Rule(tag("farms_by_homes"), FARM_NEIGHBOURS, FARM_NEAR, 0, 0),
		new Rule(tag("mines_by_buyers"), SETTLEMENTS, MINE_NEAR, MINE_CLEAR, 0),
		new Rule(tag("stables_by_towns"), SETTLEMENTS, STABLE_NEAR, 0, 0),
		new Rule(tag("towers_on_borders"), SETTLEMENTS, TOWER_NEAR, TOWER_CLEAR, 0),
		new Rule(tag("ruins_with_heirs"), SETTLEMENTS, RUIN_NEAR, 0, RUIN_HARSH),
		new Rule(tag("homesteads_by_towns"), SETTLEMENTS, HOMESTEAD_NEAR, 0, 0),
		new Rule(tag("old_cities"), tag("lived_in"), 0, OLD_CLEAR, 0),
		new Rule(tag("cairns_by_ways"), SETTLEMENTS, CAIRN_NEAR, CAIRN_CLEAR, 0));

	/** Given a part to play by our own code once they stand (Grim Kingdoms' holds and camps), so not reported. */
	public static final TagKey<Structure> OWN_REASON = tag("own_reason");

	private PlacementReasons() {
	}

	static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(PlacementReasons::report);
	}

	/**
	 * At start-up, every structure in the game with no reason to be where it is, by mod, in the log: no rule here, not
	 * lived in itself, and not given a part of its own ({@code own_reason}: Grim Kingdoms' holds and camps, which {@link GrimSites} makes fiefs and barbarian camps; or ours). The list to give roles next.
	 */
	private static void report(net.minecraft.server.MinecraftServer server) {
		java.util.Map<String, List<String>> none = new java.util.TreeMap<>();
		TagKey<Structure> livedIn = tag("lived_in");
		for (Holder.Reference<Structure> s : server.registryAccess().lookupOrThrow(Registries.STRUCTURE).listElements().toList()) {
			String mod = s.key().identifier().getNamespace();
			if (mod.equals(JaymosKingdoms.MOD_ID) || rule(s) != null || s.is(livedIn) || s.is(OWN_REASON)) {
				continue;
			}
			none.computeIfAbsent(mod, k -> new ArrayList<>()).add(s.key().identifier().getPath());
		}
		int total = none.values().stream().mapToInt(List::size).sum();
		JaymosKingdoms.LOGGER.info("Structures with no placement reason yet: {}", total);
		none.forEach((mod, ids) -> JaymosKingdoms.LOGGER.info("  {} ({}): {}", mod, ids.size(), String.join(", ", ids)));
	}

	private static TagKey<Structure> tag(String name) {
		return TagKey.create(Registries.STRUCTURE, JaymosKingdoms.id(name));
	}

	/** The rule {@code structure} is placed by, or null if it has none (it generates as its mod placed it). */
	public static @Nullable Rule rule(Holder<Structure> structure) {
		for (Rule r : RULES) {
			if (structure.is(r.what())) {
				return r;
			}
		}
		return null;
	}

	/** Whether {@code rule}'s reason holds for the structure being placed in {@code context}. */
	public static boolean holds(Rule rule, Structure.GenerationContext context) {
		if (rule.harsh() > 0 && harshSteps(context) >= rule.harsh()) {
			return true;
		}
		if (rule.within() <= 0) {
			return nearest(context, rule.near(), rule.clear()) >= rule.clear();
		}
		int nearest = nearest(context, rule.near(), rule.within());
		return nearest <= rule.within() && nearest >= rule.clear();
	}

	/** How many steps harsh the land at the middle of the chunk being placed is (the heat map {@link HarshLands} reads). */
	private static int harshSteps(Structure.GenerationContext context) {
		ChunkPos here = context.chunkPos();
		Climate.Sampler sampler = context.randomState().createClimateSampler(SamplerContext.EMPTY_UNCACHED);
		float temperature = Climate.unquantizeCoord(sampler.sample(here.getMiddleBlockX() >> 2, 0, here.getMiddleBlockZ() >> 2).temperature());
		return HarshLands.steps(temperature);
	}

	/**
	 * How far (chunks, the larger of the two axes) the nearest of {@code near} will stand from the chunk being placed,
	 * looking {@code within} chunks out; past {@code within} if none.
	 */
	private static int nearest(Structure.GenerationContext context, TagKey<Structure> near, int within) {
		ChunkPos here = context.chunkPos();
		int best = within + 1;
		for (StructureSet set : context.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)) {
			if (!(set.placement() instanceof RandomSpreadStructurePlacement spread) || spread.spacing() <= 0) {
				continue;
			}
			List<Holder<Structure>> homes = new ArrayList<>();
			for (StructureSet.StructureSelectionEntry entry : set.structures()) {
				if (entry.structure().is(near)) {
					homes.add(entry.structure());
				}
			}
			if (homes.isEmpty()) {
				continue;
			}
			int spacing = spread.spacing();
			for (int rx = Math.floorDiv(here.x() - within, spacing); rx <= Math.floorDiv(here.x() + within, spacing); rx++) {
				for (int rz = Math.floorDiv(here.z() - within, spacing); rz <= Math.floorDiv(here.z() + within, spacing); rz++) {
					ChunkPos at = spread.getPotentialStructureChunk(context.seed(), rx * spacing, rz * spacing);
					int d = Math.max(Math.abs(at.x() - here.x()), Math.abs(at.z() - here.z()));
					if (d < best && standsIn(context, homes, at)) {
						best = d;
					}
				}
			}
		}
		return best;
	}

	/** Whether one of {@code homes} could stand at {@code at}: the land at its middle is one of theirs. */
	private static boolean standsIn(Structure.GenerationContext context, List<Holder<Structure>> homes, ChunkPos at) {
		int x = at.getMiddleBlockX(), z = at.getMiddleBlockZ();
		int y = context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
		Holder<Biome> land = context.biomeResolver().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
		for (Holder<Structure> home : homes) {
			if (home.value().biomes().contains(land)) {
				return true;
			}
		}
		return false;
	}
}
