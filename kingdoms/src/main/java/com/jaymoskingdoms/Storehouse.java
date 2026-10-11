package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * A faction's storage, built: a kingdom's or fiefdom's <b>storehouse</b> by its seat (a timber-framed hall on a stone
 * course in the village's own materials, {@link Palette}, a gabled roof, chests and barrels along the back wall), or a
 * village's <b>shed</b> by its bell (a lean-to with a few chests). Built on open natural ground only: every block it
 * replaces is air, a plant, or the land's own dirt and stone, never anything someone built. Its containers and its
 * door are registered as the faction's storage ({@link Storage#register}). Camps' loot piles are the barbarians' own.
 */
public final class Storehouse {
	/** Looks for a site this far from the anchor (the keep's gate, or the bell). */
	static final int SITE_MIN = 8, SITE_MAX = 40, SITE_STEP = 4, SITE_ANGLES = 16;
	/** A big town (Epic Structures' towns fill the first ring) looks further out, up to this far. */
	static final int SITE_FAR = 96;
	/** The most the ground may rise or fall across a site. */
	static final int MAX_SPREAD = 3;
	/** The storehouse: half its width (x), its depth (z), its wall height. */
	static final int HALL_HALF = 4, HALL_DEPTH = 6, HALL_WALL = 3;
	/** The shed: half its width, its depth, its height. */
	static final int SHED_HALF = 2, SHED_DEPTH = 3, SHED_HIGH = 2;
	/** How long to wait before looking for a site again when none was found (ticks). */
	static final long RETRY_TICKS = 24000L;
	/** A faction without storage builds one when a player comes this close to its seat (so the ground is loaded). */
	static final double BUILD_PLAYER_RANGE = 96.0;

	/** Faction id → game time it may look for a site again. */
	private static final Map<Integer, Long> RETRY = new HashMap<>();

	private Storehouse() {
	}

	/** Builds storage for any standing faction that has none and a player near it (0.2 kingdoms, new villages). */
	static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || !f.holdings().storage().isEmpty() || f.type() == FactionType.BARBARIAN || RETRY.getOrDefault(f.id(), 0L) > now) {
				continue;
			}
			if (level.getNearestPlayer(f.seat().getX(), f.seat().getY(), f.seat().getZ(), BUILD_PLAYER_RANGE, false) == null) {
				continue;
			}
			if (build(level, f) == null) {
				RETRY.put(f.id(), now + RETRY_TICKS);
			}
		}
	}

	/**
	 * Builds {@code faction}'s storage (a storehouse, or a shed for a village), registers it, and brings its builder
	 * crew if it has none. Returns the depot (where workers load), or null if there was nowhere to build.
	 */
	public static @Nullable BlockPos build(ServerLevel level, Faction faction) {
		boolean shed = faction.type() == FactionType.VILLAGE || faction.type() == FactionType.BARBARIAN;
		BlockPos anchor = faction.seat();
		Castle.Keep keep = Factions.keep(level.getServer(), faction.id());
		if (keep != null) {
			anchor = keep.gate();
		}
		int half = shed ? SHED_HALF : HALL_HALF;
		int depth = shed ? SHED_DEPTH : HALL_DEPTH;
		Castle.Frame frame = site(level, anchor, half, depth);
		if (frame == null) {
			JaymosKingdoms.LOGGER.info("{} found no open ground for its {}", faction.name(), shed ? "shed" : "storehouse");
			return null;
		}
		Palette p = Palette.at(level, faction.seat());
		List<BlockPos> containers = new ArrayList<>();
		BlockPos depot = shed ? shed(level, frame, p, containers) : hall(level, frame, p, containers);
		Storage.register(level.getServer(), faction.id(), containers, depot);
		JaymosKingdoms.LOGGER.info("{} built its {} at {} ({} containers)", faction.name(), shed ? "shed" : "storehouse", depot, containers.size());
		Faction now = Factions.get(level.getServer(), faction.id());
		if (now != null && Builders.crewCount(now) == 0) {
			Builders.spawnCrew(level, now);
		}
		return depot;
	}

	/**
	 * The best open, natural, flat site {@code SITE_MIN}-{@code SITE_MAX} from {@code anchor} (or, if there's none, the nearest
	 * out to {@code SITE_FAR}), its door facing it; null if none.
	 */
	static Castle.@Nullable Frame site(ServerLevel level, BlockPos anchor, int half, int depth) {
		Castle.Frame best = null;
		double bestScore = Double.MAX_VALUE;
		for (int r = SITE_MIN; r <= SITE_FAR && (best == null || r <= SITE_MAX); r += SITE_STEP) {
			for (int i = 0; i < SITE_ANGLES; i++) {
				double a = (i + (r / SITE_STEP) * 0.5) / SITE_ANGLES * Math.PI * 2.0;
				BlockPos center = anchor.offset((int)Math.round(Math.cos(a) * r), 0, (int)Math.round(Math.sin(a) * r));
				if (!level.isLoaded(center)) {
					continue;
				}
				Direction facing = Direction.getApproximateNearest(anchor.getX() - center.getX(), 0, anchor.getZ() - center.getZ());
				Castle.Frame frame = new Castle.Frame(center, facing, 0, depth / 2);
				double score = score(level, frame, half, depth);
				if (score >= 0 && score + r * 0.1 < bestScore) {
					bestScore = score + r * 0.1;
					int floor = floor(level, frame, half, depth);
					best = new Castle.Frame(center, facing, floor, depth / 2);
				}
			}
		}
		return best;
	}

	/** The median ground height across the footprint. */
	private static int floor(ServerLevel level, Castle.Frame f, int half, int depth) {
		List<Integer> heights = new ArrayList<>();
		for (int x = -half - 1; x <= half + 1; x++) {
			for (int z = -1; z <= depth + 1; z++) {
				heights.add(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, f.at(x, 0, z)).getY());
			}
		}
		heights.sort(Integer::compare);
		return heights.get(heights.size() / 2);
	}

	/** How bad a site is (its ground's spread), or -1 if it can't be built on: water, built ground, or anything built in the way. */
	private static double score(ServerLevel level, Castle.Frame f, int half, int depth) {
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (int x = -half - 1; x <= half + 1; x++) {
			for (int z = -1; z <= depth + 1; z++) {
				BlockPos column = f.at(x, 0, z);
				if (!level.isLoaded(column)) {
					return -1;
				}
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
				BlockState ground = level.getBlockState(top.below());
				if (!level.getFluidState(top.below()).isEmpty() || !level.getFluidState(top).isEmpty() || !ground(ground)) {
					return -1;
				}
				lo = Math.min(lo, top.getY());
				hi = Math.max(hi, top.getY());
			}
		}
		if (hi - lo > MAX_SPREAD) {
			return -1;
		}
		// nothing built (and no trees) in the space it will fill
		for (int x = -half - 1; x <= half + 1; x++) {
			for (int z = -1; z <= depth + 1; z++) {
				for (int y = lo; y <= hi + HALL_WALL + 6; y++) {
					BlockPos at = f.at(x, 0, z).atY(y);
					BlockState state = level.getBlockState(at);
					if (!state.isAir() && !state.canBeReplaced() && !ground(state)) {
						return -1;
					}
				}
			}
		}
		return hi - lo;
	}

	/**
	 * The land's own ground (no logs or leaves: a site isn't cleared of trees). Plain terracotta is a savanna castle's floor,
	 * not the land's, so nothing is built in its courtyard.
	 */
	static boolean ground(BlockState state) {
		return Castle.natural(state) && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES) && !state.is(Blocks.DIRT_PATH)
			&& !state.is(Blocks.TERRACOTTA);
	}

	/** Levels the footprint: solid ground under it, the air cleared above (only plants and the land's own ground are ever replaced). */
	private static void prepare(ServerLevel level, Castle.Frame f, Palette p, int x0, int x1, int z0, int z1, int high) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int y = -4; y < 0; y++) {
					BlockState there = level.getBlockState(f.at(x, y, z));
					if (y == -1 || there.isAir() || there.canBeReplaced()) {
						f.set(level, x, y, z, y == -1 ? p.path() : Blocks.DIRT.defaultBlockState());
					}
				}
				for (int y = 0; y <= high; y++) {
					f.set(level, x, y, z, Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	/** The storehouse: returns its depot (just inside the door). Its containers are added to {@code containers}. */
	private static BlockPos hall(ServerLevel level, Castle.Frame f, Palette p, List<BlockPos> containers) {
		int h = HALL_HALF, d = HALL_DEPTH, w = HALL_WALL;
		prepare(level, f, p, -h - 1, h + 1, -1, d + 1, w + h + 3);
		for (int x = -h; x <= h; x++) {
			for (int z = 0; z <= d; z++) {
				boolean edge = Math.abs(x) == h || z == 0 || z == d;
				boolean corner = Math.abs(x) == h && (z == 0 || z == d);
				f.set(level, x, -1, z, edge ? p.wall() : p.floor());
				if (!edge) {
					continue;
				}
				for (int y = 0; y <= w; y++) {
					f.set(level, x, y, z, corner ? p.log() : y == 0 ? p.wall() : p.planks());
				}
			}
		}
		// the gabled roof, eaves out over the walls, the gable ends filled
		for (int x = -h - 1; x <= h + 1; x++) {
			int y = w + (h + 1 - Math.abs(x));
			for (int z = -1; z <= d + 1; z++) {
				BlockState roof = x == 0 ? p.woodSlab() : f.turn(p.stairs().setValue(StairBlock.FACING, x < 0 ? Direction.EAST : Direction.WEST));
				f.set(level, x, y, z, x == 0 ? p.woodSlab() : roof);
			}
			if (Math.abs(x) < h) {
				for (int gy = w + 1; gy < y; gy++) {
					f.set(level, x, gy, 0, p.planks());
					f.set(level, x, gy, d, p.planks());
				}
			}
		}
		// the door (an open arch) and windows
		f.set(level, 0, 0, 0, Blocks.AIR.defaultBlockState());
		f.set(level, 0, 1, 0, Blocks.AIR.defaultBlockState());
		f.set(level, 0, 2, 0, p.log());
		for (int z = 2; z <= d - 2; z += 2) {
			f.set(level, -h, 2, z, Blocks.GLASS_PANE.defaultBlockState());
			f.set(level, h, 2, z, Blocks.GLASS_PANE.defaultBlockState());
		}
		// chests and barrels along the back wall (barrels stacked; chests kept apart so none join)
		for (int x = -h + 1; x <= h - 1; x++) {
			if (Math.floorMod(x, 2) == 1) {
				containers.add(chest(level, f, x, 0, d - 1));
			} else {
				containers.add(barrel(level, f, x, 0, d - 1));
				containers.add(barrel(level, f, x, 1, d - 1));
			}
		}
		// barrels down the sides, a workbench by the door
		for (int z = 2; z <= d - 3; z++) {
			containers.add(barrel(level, f, -h + 1, 0, z));
		}
		f.set(level, h - 1, 0, 1, Blocks.CRAFTING_TABLE.defaultBlockState());
		f.set(level, -h + 1, 2, 2, f.turn(Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST)));
		f.set(level, h - 1, 2, d - 2, f.turn(Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST)));
		return f.at(0, 0, 2);
	}

	/** The village shed: an open-fronted lean-to. Returns its depot (in front of the chests). */
	private static BlockPos shed(ServerLevel level, Castle.Frame f, Palette p, List<BlockPos> containers) {
		int h = SHED_HALF, d = SHED_DEPTH, high = SHED_HIGH;
		prepare(level, f, p, -h - 1, h + 1, -1, d + 1, high + 3);
		for (int x = -h; x <= h; x++) {
			for (int z = 0; z <= d; z++) {
				f.set(level, x, -1, z, p.planks());
				boolean back = z == d, side = Math.abs(x) == h;
				boolean post = side && (z == 0 || z == d);
				for (int y = 0; y <= high; y++) {
					if (post) {
						f.set(level, x, y, z, p.log());
					} else if (back || (side && y > 0)) {
						f.set(level, x, y, z, p.planks());
					} else if (side) {
						f.set(level, x, y, z, p.fence());
					}
				}
			}
		}
		for (int x = -h - 1; x <= h + 1; x++) {
			for (int z = -1; z <= d + 1; z++) {
				f.set(level, x, high + 1, z, p.woodSlab());
			}
		}
		containers.add(chest(level, f, -1, 0, d - 1));
		containers.add(barrel(level, f, 0, 0, d - 1));
		containers.add(barrel(level, f, 0, 1, d - 1));
		containers.add(chest(level, f, 1, 0, d - 1));
		f.set(level, -h + 1, 0, 1, Blocks.COMPOSTER.defaultBlockState());
		return f.at(0, 0, 1);
	}

	private static BlockPos chest(ServerLevel level, Castle.Frame f, int x, int y, int z) {
		BlockPos at = f.at(x, y, z);
		level.setBlock(at, f.turn(Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH)), Block.UPDATE_CLIENTS);
		return at.immutable();
	}

	private static BlockPos barrel(ServerLevel level, Castle.Frame f, int x, int y, int z) {
		BlockPos at = f.at(x, y, z);
		level.setBlock(at, Blocks.BARREL.defaultBlockState(), Block.UPDATE_CLIENTS);
		return at.immutable();
	}

	static void clear() {
		RETRY.clear();
	}

	/** (for {@code /kingdom storehouse}: build one now, whoever's near) */
	static @Nullable BlockPos buildNow(ServerPlayer player, Faction faction) {
		RETRY.remove(faction.id());
		return build(Factions.level(player.level().getServer()), faction);
	}
}
