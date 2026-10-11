package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A building a builder crew can put up: its footprint and every block in the order it's laid (bottom up), offsets from
 * the plot's lowest north-west corner. Blueprints are drawn in code from the faction's {@link Palette} (or a camp's own
 * materials), like {@link Castle}. The construction code places a blueprint block by block from real items in storage;
 * each faction type's growth order is the list registered here.
 */
public record Blueprint(String id, int width, int depth, int height, List<Placement> blocks) {
	/** One block of the building: where (relative to the plot's corner) and what. */
	public record Placement(BlockPos offset, BlockState state) {
	}

	/** Makes a blueprint for a faction at a site (its level, for materials or the lie of the land). */
	@FunctionalInterface
	public interface Maker {
		Blueprint make(ServerLevel level, Faction faction);
	}

	/**
	 * (0.5.7) Makes one variant of a building: the same {@code variant} always draws the same building, so a project
	 * keeps its look once staked. Variant 0 is the building as first designed (projects saved before variants).
	 */
	@FunctionalInterface
	public interface Varied {
		Blueprint make(ServerLevel level, Faction faction, long variant);
	}

	/** One entry in a faction type's growth order: a building, how many of it at most, and from what settlement size. */
	public record Entry(String id, Varied maker, int max, int minPopulation) {
		/** A building that's always drawn the same way (camps, ruins). */
		public Entry(String id, Maker maker, int max, int minPopulation) {
			this(id, (level, faction, variant) -> maker.make(level, faction), max, minPopulation);
		}
	}

	private static final Map<FactionType, List<Entry>> CATALOGUE = new EnumMap<>(FactionType.class);

	/** Adds a building to a faction type's growth order (in the order they're registered). */
	public static void register(FactionType type, Entry entry) {
		CATALOGUE.computeIfAbsent(type, t -> new ArrayList<>()).add(entry);
	}

	/** A faction type's growth order. */
	public static List<Entry> catalogue(FactionType type) {
		return Collections.unmodifiableList(CATALOGUE.getOrDefault(type, List.of()));
	}
}
