package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * Andy (2026-11-24, with a screenshot of Moog's Voyager Structures' wheat farm out alone on a desert shore): "these should
 * spawn near villages or the single homes, they are farms and provide food somewhere, remember the generation needs a
 * reason". A structure in {@link #FARMS} (the other mods' lone fields, grain bins, haystacks, barns and the like) only
 * generates within {@link #NEAR_CHUNKS} chunks of where one in {@link #NEIGHBOURS} (a village, or a lone home or
 * homestead) will: someone lives there to work it and eat what it grows.
 * <p>
 * The neighbour is found the way the world places it, without generating anything: each structure set holding a neighbour
 * is asked where its spread puts one near here, and whether the land there is one that neighbour stands in. A neighbour
 * that later fails for another reason (its own exclusion zone, terrain) can still leave a farm without one; that is rare.
 */
public final class FarmNeighbours {
	/** The farms that need neighbours, and who counts as one. */
	public static final TagKey<Structure> FARMS = TagKey.create(Registries.STRUCTURE, JaymosKingdoms.id("farms_by_homes"));
	public static final TagKey<Structure> NEIGHBOURS = TagKey.create(Registries.STRUCTURE, JaymosKingdoms.id("farm_neighbours"));
	/** How near (chunks, either way) a neighbour must stand: about the walk out to a village's or a homestead's fields. */
	static final int NEAR_CHUNKS = 6;

	private FarmNeighbours() {
	}

	/** Whether a village or a home will stand near the structure being placed in {@code context}. */
	public static boolean near(Structure.GenerationContext context) {
		ChunkPos here = context.chunkPos();
		for (StructureSet set : context.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)) {
			if (!(set.placement() instanceof RandomSpreadStructurePlacement spread) || spread.spacing() <= 0) {
				continue;
			}
			List<Holder<Structure>> homes = new ArrayList<>();
			for (StructureSet.StructureSelectionEntry entry : set.structures()) {
				if (entry.structure().is(NEIGHBOURS)) {
					homes.add(entry.structure());
				}
			}
			if (homes.isEmpty()) {
				continue;
			}
			int spacing = spread.spacing();
			for (int rx = Math.floorDiv(here.x() - NEAR_CHUNKS, spacing); rx <= Math.floorDiv(here.x() + NEAR_CHUNKS, spacing); rx++) {
				for (int rz = Math.floorDiv(here.z() - NEAR_CHUNKS, spacing); rz <= Math.floorDiv(here.z() + NEAR_CHUNKS, spacing); rz++) {
					ChunkPos at = spread.getPotentialStructureChunk(context.seed(), rx * spacing, rz * spacing);
					if (Math.abs(at.x() - here.x()) <= NEAR_CHUNKS && Math.abs(at.z() - here.z()) <= NEAR_CHUNKS && standsIn(context, homes, at)) {
						return true;
					}
				}
			}
		}
		return false;
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
