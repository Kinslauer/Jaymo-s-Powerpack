package com.jaymospowers.core;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jspecify.annotations.Nullable;

/**
 * Where the nearest structure of a kind stands, worked out without making a single chunk. Vanilla's
 * findNearestMapStructure asks the same question (vanilla's own check whether a start can be made in a candidate chunk:
 * placement, biome, layout), but then makes each likely chunk on the server thread to be sure; right after someone
 * joins, the world generation queue is full of their land, and the server thread waited behind it past its 60 s
 * watchdog (crashes 2026-11-13 07:02 and 07:43, Steam Deck; Jaymo's Kingdoms has the same class for its starts). This
 * takes the prediction as the answer. A start that vanilla predicts and then fails to make is rare; the place is still
 * in land where one fits.
 * <p>
 * Candidates are the start chunks of each structure's spacing grid out to {@code rings} rings (findNearestMapStructure's
 * unit), nearest first. A cheap biome check at the candidate's surface (no chunk made: the biome comes from the
 * generator when the chunk isn't there) skips the ones that can't hold it before vanilla's costlier check lays it out.
 */
public final class Scout {
	private Scout() {
	}

	private record Spot(ChunkPos chunk, StructurePlacement placement, List<Holder<Structure>> structures, int distance) {
	}

	/** The nearest start of a structure tagged {@code tag} within {@code rings} rings round {@code from}, or null. */
	public static @Nullable BlockPos nearest(ServerLevel level, TagKey<Structure> tag, BlockPos from, int rings) {
		Optional<HolderSet.Named<Structure>> set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(tag);
		return set.isEmpty() ? null : nearest(level, set.get(), from, rings);
	}

	/** The nearest start of any of {@code wanted} within {@code rings} rings round {@code from}, or null. */
	public static @Nullable BlockPos nearest(ServerLevel level, HolderSet<Structure> wanted, BlockPos from, int rings) {
		Pair<BlockPos, Holder<Structure>> found = find(level, wanted, from, rings);
		return found == null ? null : found.getFirst();
	}

	/** {@link #nearest(ServerLevel, HolderSet, BlockPos, int)} with which structure it is, as findNearestMapStructure gives. */
	public static @Nullable Pair<BlockPos, Holder<Structure>> find(ServerLevel level, HolderSet<Structure> wanted, BlockPos from, int rings) {
		StructureManager structures = level.structureManager();
		if (!structures.shouldGenerateStructures()) {
			return null;
		}
		ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
		// the structures that share a placement are looked at together, as vanilla does
		Map<StructurePlacement, Set<Holder<Structure>>> byPlacement = new LinkedHashMap<>();
		for (Holder<Structure> structure : wanted) {
			for (StructurePlacement placement : state.getPlacementsForStructure(structure)) {
				// (the concentric rings of strongholds aren't searched: no start needs one)
				if (placement instanceof RandomSpreadStructurePlacement) {
					byPlacement.computeIfAbsent(placement, p -> new LinkedHashSet<>()).add(structure);
				}
			}
		}
		if (byPlacement.isEmpty()) {
			return null;
		}
		ChunkPos origin = ChunkPos.containing(from);
		List<Spot> spots = new ArrayList<>();
		for (Map.Entry<StructurePlacement, Set<Holder<Structure>>> e : byPlacement.entrySet()) {
			RandomSpreadStructurePlacement spread = (RandomSpreadStructurePlacement)e.getKey();
			int spacing = spread.spacing();
			List<Holder<Structure>> kinds = List.copyOf(e.getValue());
			for (int dx = -rings; dx <= rings; dx++) {
				for (int dz = -rings; dz <= rings; dz++) {
					ChunkPos chunk = spread.getPotentialStructureChunk(state.getLevelSeed(), origin.x() + dx * spacing, origin.z() + dz * spacing);
					spots.add(new Spot(chunk, spread, kinds, chunk.distanceSquared(origin)));
				}
			}
		}
		spots.sort(Comparator.comparingInt(Spot::distance));
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState random = level.getChunkSource().randomState();
		for (Spot spot : spots) {
			int x = spot.chunk().getMinBlockX() + 8, z = spot.chunk().getMinBlockZ() + 8;
			Holder<Biome> biome = null;
			for (Holder<Structure> structure : spot.structures()) {
				if (biome == null) {
					int y = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
					biome = level.getBiome(new BlockPos(x, y, z));
				}
				if (!structure.value().biomes().contains(biome)) {
					continue;
				}
				StructureCheckResult result = structures.checkStructurePresence(spot.chunk(), structure.value(), spot.placement(), false);
				// START_PRESENT: made already; CHUNK_LOAD_NEEDED: vanilla's check says one is made there (the chunk isn't made yet)
				if (result != StructureCheckResult.START_NOT_PRESENT) {
					return Pair.of(spot.placement().getLocatePos(spot.chunk()), structure);
				}
			}
		}
		return null;
	}
}
