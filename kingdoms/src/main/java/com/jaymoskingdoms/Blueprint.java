package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

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

	/**
	 * Reads a saved structure template (a structure block's file, as {@code StructureTemplate.save} writes it) into a
	 * blueprint: every block but air, structure voids, jigsaws and structure blocks, trimmed to what's there, its lowest
	 * layer laid in place of the ground. Containers come empty (a blueprint has no contents). Null if it holds no blocks.
	 */
	public static @Nullable Blueprint fromTemplate(String id, CompoundTag tag) {
		ListTag palette = tag.getList("palette").orElseGet(() -> tag.getListOrEmpty("palettes").getListOrEmpty(0));
		List<BlockState> states = new ArrayList<>();
		for (int i = 0; i < palette.size(); i++) {
			states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK, palette.getCompoundOrEmpty(i)));
		}
		List<Placement> raw = new ArrayList<>();
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE,
			maxZ = Integer.MIN_VALUE;
		ListTag blocks = tag.getListOrEmpty("blocks");
		for (int i = 0; i < blocks.size(); i++) {
			CompoundTag b = blocks.getCompoundOrEmpty(i);
			int s = b.getIntOr("state", -1);
			if (s < 0 || s >= states.size()) {
				continue;
			}
			BlockState state = states.get(s);
			if (state.isAir() || state.is(Blocks.STRUCTURE_VOID) || state.is(Blocks.JIGSAW) || state.is(Blocks.STRUCTURE_BLOCK)) {
				continue;
			}
			ListTag pos = b.getListOrEmpty("pos");
			BlockPos at = new BlockPos(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0));
			raw.add(new Placement(at, state));
			minX = Math.min(minX, at.getX());
			minY = Math.min(minY, at.getY());
			minZ = Math.min(minZ, at.getZ());
			maxX = Math.max(maxX, at.getX());
			maxY = Math.max(maxY, at.getY());
			maxZ = Math.max(maxZ, at.getZ());
		}
		if (raw.isEmpty()) {
			return null;
		}
		List<Placement> placed = new ArrayList<>();
		for (Placement pl : raw) {
			placed.add(new Placement(pl.offset().offset(-minX, -minY, -minZ), pl.state()));
		}
		return new Blueprint(id, maxX - minX + 1, maxZ - minZ + 1, maxY - minY + 1, List.copyOf(placed));
	}
}
