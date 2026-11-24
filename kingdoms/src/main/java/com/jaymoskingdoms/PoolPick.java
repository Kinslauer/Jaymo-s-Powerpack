package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jspecify.annotations.Nullable;

/**
 * Which structure of a set the game will really start in a chunk not made yet, worked out from the seed the way the game
 * picks it ({@code ChunkGenerator.createStructures}): when a set holds more than one structure, it draws them in a weighted
 * order seeded by the chunk and the first that can stand there wins.
 *
 * <p>(Deck client test, 2026-11-24) The shared village pool ({@link SharedPools}) puts some 60 villages from every mod in one
 * set, so at a village spot the five Epic towns are only some of the draws. The capital's search and the kingdoms woken round
 * spawn asked only "could an Epic town stand here?", which was often true where another mod's village then won the draw: on
 * the Deck a new world made the chunks of 20 such spots one by one (~30 s each) and found an Epic town in none, kept the
 * player on "Your story is being written" for 11 minutes and raised the capital at spawn anyway.
 */
public final class PoolPick {
	/** How long a chunk asked for by {@link #starts} is held (ticks) while its structure starts are worked out. */
	static final long STARTS_HOLD_TICKS = 1200L;
	/** Holds such a chunk at the structure-starts step only: its land isn't made. */
	private static final TicketType STARTS = new TicketType(STARTS_HOLD_TICKS, TicketType.FLAG_LOADING);

	private PoolPick() {
	}

	/**
	 * {@code chunk} made only as far as its structure starts (which structures start in it), off the server thread: a
	 * fraction of making its land, which on the Deck took ~30 s a chunk. What really starts there is read from it, so a
	 * wrong guess costs a moment, not a made chunk.
	 */
	public static CompletableFuture<ChunkResult<ChunkAccess>> starts(ServerLevel level, ChunkPos chunk) {
		level.getChunkSource().addTicket(new Ticket(STARTS, ChunkLevel.byStatus(ChunkStatus.STRUCTURE_STARTS)), chunk);
		return level.getChunkSource().getChunkFuture(chunk.x(), chunk.z(), ChunkStatus.STRUCTURE_STARTS, true);
	}

	/**
	 * The structure the game will start at {@code chunk} for the set placed by {@code placement} (null: none at all, or not
	 * that set's chunk). Thread-safe like vanilla's structure search, with a climate sampler of the caller's own thread.
	 */
	public static @Nullable Holder<Structure> winner(RegistryAccess registries, StructureTemplateManager templates, Stacked.Ground ground,
		Climate.Sampler climate, ChunkPos chunk, StructurePlacement placement) {
		ChunkGeneratorStructureState state = ground.structures();
		StructureSet set = setOf(state, placement);
		if (set == null || !placement.isStructureChunk(state, chunk.x(), chunk.z())) {
			return null;
		}
		long seed = state.getLevelSeed();
		List<StructureSet.StructureSelectionEntry> options = new ArrayList<>(set.structures());
		if (options.size() == 1) {
			return stands(registries, templates, ground, climate, seed, chunk, options.getFirst().structure()) ? options.getFirst().structure() : null;
		}
		// the same draw as ChunkGenerator.createStructures, step for step
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
		random.setLargeFeatureSeed(seed, chunk.x(), chunk.z());
		int total = 0;
		for (StructureSet.StructureSelectionEntry option : options) {
			total += option.weight();
		}
		while (!options.isEmpty()) {
			int choice = random.nextInt(total);
			int index = 0;
			for (StructureSet.StructureSelectionEntry option : options) {
				choice -= option.weight();
				if (choice < 0) {
					break;
				}
				index++;
			}
			StructureSet.StructureSelectionEntry selected = options.get(index);
			if (stands(registries, templates, ground, climate, seed, chunk, selected.structure())) {
				return selected.structure();
			}
			options.remove(index);
			total -= selected.weight();
		}
		return null;
	}

	/** The set {@code placement} places, in {@code state}'s own (pooled) sets. */
	private static @Nullable StructureSet setOf(ChunkGeneratorStructureState state, StructurePlacement placement) {
		for (Holder<StructureSet> set : state.possibleStructureSets()) {
			if (set.value().placement() == placement) {
				return set.value();
			}
		}
		for (Holder<StructureSet> set : state.possibleStructureSets()) {
			if (set.value().placement().equals(placement)) {
				return set.value();
			}
		}
		return null;
	}

	/**
	 * Whether {@code structure} would start at {@code chunk}: Structure.generate as ChunkGenerator.tryGenerateStructure calls it,
	 * pieces and all. Its place and biome alone aren't enough: on a test world the vanilla plains village's place and biome were
	 * right at every spot it was drawn for, yet it laid no pieces there and Moog's villager statue, drawn after it, started instead.
	 */
	private static boolean stands(RegistryAccess registries, StructureTemplateManager templates, Stacked.Ground ground, Climate.Sampler climate,
		long seed, ChunkPos chunk, Holder<Structure> structure) {
		try {
			return structure.value().generate(structure, Level.OVERWORLD, registries, ground.generator(), ground.generator().getBiomeSource(),
				climate, ground.randomState(), templates, seed, chunk, 0, ground.heights(), structure.value().biomes()::contains).isValid();
		} catch (RuntimeException e) {
			JaymosKingdoms.LOGGER.debug("Couldn't foresee {} at {}", structure, chunk, e);
			return false;
		}
	}
}
