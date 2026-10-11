package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * (0.5.7) The look of a settlement's own buildings, read from the blocks of the structure it was founded in (the
 * village's houses, the ruin's halls; {@link Seats#buildings}), or, with no structure there, from the made blocks
 * round its seat: which woods its planks are, what its roofs (stairs) are made of, its stonework, and its colours (wool,
 * beds, terracotta, concrete, glass). New buildings draw their materials from it ({@link Buildings.Kit}), so they take
 * after the buildings around them, each picking its own mix.
 */
final class Townscape {
	/** With no structure to read: how far round the seat (and how far below and above it) made blocks are counted. */
	static final int SCAN_RADIUS = 40, SCAN_BELOW = 6, SCAN_ABOVE = 18;
	/** At most this many blocks are read for one look; a look is read again after this long. */
	static final int MAX_BLOCKS = 120_000, KEEP_TICKS = 72_000;
	/** How many of each (woods, roofs, stonework, colours) a look keeps. */
	static final int KEEP = 4;

	/** What's counted: woods by name ("oak"), roof materials by their stairs ("stone_brick"), stonework, colours. */
	record Look(Map<String, Integer> woods, Map<String, Integer> roofs, Map<Block, Integer> masonry, Map<String, Integer> colours) {
		static final Look EMPTY = new Look(Map.of(), Map.of(), Map.of(), Map.of());
	}

	private record Cached(Look look, long at) {
	}

	private static final Map<Integer, Cached> CACHE = new HashMap<>();
	/** Colour names, longest first ("light_blue" before "blue"). */
	private static final List<String> COLOURS = Arrays.stream(DyeColor.values()).map(DyeColor::getName)
		.sorted(Comparator.comparingInt(String::length).reversed()).toList();
	/** Made stonework (never natural ground): matched by name. */
	private static final String[] MASONRY = {"brick", "cobblestone", "polished_", "cut_", "smooth_", "chiseled_", "tiles", "terracotta", "concrete",
		"packed_mud", "quartz_block", "prismarine", "calcite"};
	private static final String[] NOT_MASONRY = {"glazed", "powder", "_ore", "infested", "_stairs", "_slab", "_wall", "_button", "_pressure_plate"};

	private Townscape() {
	}

	/** {@code f}'s look (read once and kept a while). */
	static Look of(ServerLevel level, Faction f) {
		Cached c = CACHE.get(f.id());
		long now = level.getGameTime();
		if (c != null && now - c.at() < KEEP_TICKS) {
			return c.look();
		}
		Look look = read(level, f.seat());
		CACHE.put(f.id(), new Cached(look, now));
		return look;
	}

	static void clear() {
		CACHE.clear();
	}

	private static Look read(ServerLevel level, BlockPos seat) {
		List<BoundingBox> boxes = new ArrayList<>();
		Seats.Found found = Seats.structureAt(level, seat, holder -> true);
		if (found.status() == Seats.Status.FOUND && found.start() != null) {
			for (Seats.Building b : Seats.buildings(found.start())) {
				boxes.add(b.box());
			}
		}
		boolean structure = !boxes.isEmpty();
		if (!structure) {
			boxes.add(new BoundingBox(seat.getX() - SCAN_RADIUS, seat.getY() - SCAN_BELOW, seat.getZ() - SCAN_RADIUS,
				seat.getX() + SCAN_RADIUS, seat.getY() + SCAN_ABOVE, seat.getZ() + SCAN_RADIUS));
		}
		Map<String, Integer> woods = new HashMap<>(), roofs = new HashMap<>(), colours = new HashMap<>();
		Map<Block, Integer> masonry = new HashMap<>();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int read = 0;
		for (BoundingBox box : boxes) {
			for (int x = box.minX(); x <= box.maxX() && read < MAX_BLOCKS; x++) {
				for (int z = box.minZ(); z <= box.maxZ() && read < MAX_BLOCKS; z++) {
					at.set(x, box.minY(), z);
					if (!level.isLoaded(at)) {
						continue;
					}
					for (int y = box.minY(); y <= box.maxY(); y++, read++) {
						BlockState state = level.getBlockState(at.setY(y));
						if (!state.isAir()) {
							count(state, structure, woods, roofs, masonry, colours);
						}
					}
				}
			}
		}
		return new Look(top(woods), top(roofs), top(masonry), top(colours));
	}

	private static void count(BlockState state, boolean structure, Map<String, Integer> woods, Map<String, Integer> roofs, Map<Block, Integer> masonry,
		Map<String, Integer> colours) {
		Block block = state.getBlock();
		String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
		if (name.endsWith("_planks")) {
			woods.merge(name.substring(0, name.length() - "_planks".length()), 2, Integer::sum);
			return;
		}
		if (structure && name.endsWith("_log") && !name.startsWith("stripped_")) {
			woods.merge(name.substring(0, name.length() - "_log".length()), 1, Integer::sum);
			return;
		}
		if (name.endsWith("_stairs")) {
			roofs.merge(name.substring(0, name.length() - "_stairs".length()), 1, Integer::sum);
			return;
		}
		for (String c : COLOURS) {
			if (name.startsWith(c + "_")) {
				if (name.contains("wool") || name.contains("carpet") || name.contains("bed") || name.contains("banner") || name.contains("stained_glass")
					|| (name.contains("terracotta") || name.contains("concrete")) && !name.contains("glazed") && !name.contains("powder")) {
					colours.merge(c, 1, Integer::sum);
				}
				break;
			}
		}
		if (!state.getProperties().isEmpty()) {
			return;
		}
		for (String no : NOT_MASONRY) {
			if (name.contains(no)) {
				return;
			}
		}
		for (String yes : MASONRY) {
			if (name.contains(yes)) {
				masonry.merge(block, 1, Integer::sum);
				return;
			}
		}
	}

	private static <T> Map<T, Integer> top(Map<T, Integer> counts) {
		Map<T, Integer> out = new LinkedHashMap<>();
		counts.entrySet().stream().sorted(Map.Entry.<T, Integer>comparingByValue().reversed()).limit(KEEP).forEach(e -> out.put(e.getKey(), e.getValue()));
		return out;
	}

	/**
	 * One of {@code weights} (more often the commoner), or {@code home} (the land's own, weighted {@code homeShare} of
	 * the rest, at least 1): {@code home} if there's nothing else.
	 */
	static <T> T pick(Map<T, Integer> weights, T home, double homeShare, RandomSource r) {
		int total = 0;
		for (int w : weights.values()) {
			total += w;
		}
		int homeWeight = Math.max(1, (int)(total * homeShare));
		int roll = r.nextInt(total + homeWeight);
		for (Map.Entry<T, Integer> e : weights.entrySet()) {
			roll -= e.getValue();
			if (roll < 0) {
				return e.getKey();
			}
		}
		return home;
	}

	/** A block by name, or null if there's no such block. */
	static Block named(String name) {
		Block b = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name));
		return b == Blocks.AIR ? null : b;
	}
}
