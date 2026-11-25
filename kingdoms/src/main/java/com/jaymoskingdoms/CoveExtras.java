package com.jaymoskingdoms;

import com.jaymoskingdoms.StyleBuildings.House;
import com.jaymoskingdoms.StyleBuildings.Look;
import com.jaymoskingdoms.StyleBuildings.Roof;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Pirate coves as a village style (V7, {@link VillageStyles}): the cove itself is {@link PirateCoves}' (a camp, with its
 * ship); this is where it may be and what stands round it beyond its first shack.
 * <p>
 * Where: a rocky cove (stone, gravel or stony shore underfoot), at least {@link #NAVY_CLEAR} blocks from any realm that keeps
 * a harbour. Why: a hidden cove is one the navy does not sail past: the rocks hide a boat from the water and the
 * navy's harbours are far.
 * <p>
 * What: two more tarred shacks along the shore, a careening beach (a hull hauled up on props, a tar barrel and a fire to
 * scrape her by) and a fence's market (three stalls where stolen goods are sold on, a couple of traders at them).
 */
final class CoveExtras {
	/** No realm with a harbour (a navy) this near (blocks). */
	static final int NAVY_CLEAR = 600;
	/** Rocky: at least this many of {@link #ROCK_POINTS} points ten blocks round are rock, gravel or a stony shore. */
	static final int ROCK_ENOUGH = 3, ROCK_POINTS = 12, ROCK_RING = 10;
	static final int TRADERS = 2;

	private CoveExtras() {
	}

	/** {@code at} is a hidden cove's ground: rocky, and out of every navy's reach. */
	static boolean hidden(ServerLevel level, BlockPos at) {
		MinecraftServer server = level.getServer();
		for (Faction f : Factions.all(server)) {
			if (f.type().isRealm() && !f.fallen() && Navies.harbour(server, f.id()) != null && Raids.horizontal(f.seat(), at) < (double)NAVY_CLEAR * NAVY_CLEAR) {
				return false;
			}
		}
		if (VillageStyles.has(VillageStyles.biome(level, at), "stony_shore")) {
			return true;
		}
		int rock = 0;
		for (int i = 0; i < ROCK_POINTS; i++) {
			double a = Math.PI * 2.0 * i / ROCK_POINTS;
			BlockPos column = at.offset((int)Math.round(Math.cos(a) * ROCK_RING), 0, (int)Math.round(Math.sin(a) * ROCK_RING));
			if (!Loaded.made(level, column)) {
				continue;
			}
			BlockState ground = level.getBlockState(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below());
			if (ground.is(BlockTags.BASE_STONE_OVERWORLD) || ground.is(Blocks.GRAVEL) || ground.is(Blocks.COBBLESTONE)) {
				rock++;
			}
		}
		return rock >= ROCK_ENOUGH;
	}

	private static Look tarred() {
		return Look.of(Blocks.DARK_OAK_PLANKS.defaultBlockState(), Blocks.SPRUCE_LOG.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(),
			Blocks.DARK_OAK_STAIRS.defaultBlockState(), Blocks.DARK_OAK_SLAB.defaultBlockState()).foot(Blocks.SPRUCE_LOG.defaultBlockState())
			.fence(Blocks.DARK_OAK_FENCE.defaultBlockState()).door(Blocks.DARK_OAK_DOOR);
	}

	/** The cove's extras round {@code fire}, its sea toward {@code seaward}: shacks, the careening beach, the fence's market. */
	static void build(ServerLevel level, BlockPos fire, Direction seaward) {
		Direction back = seaward.getOpposite(), side = seaward.getClockWise();
		Look look = tarred();
		BlockState foot = Blocks.SPRUCE_LOG.defaultBlockState();
		for (int s : new int[] {-1, 1}) {
			BlockPos at = fire.relative(side, 10 * s).relative(back, 4);
			VillageStyles.stamp(level, new House("cove_shack", 5, 7, 3).roof(Roof.GABLE).build(look), at.atY(VillageStyles.groundY(level, at.getX(), at.getZ())), seaward, foot);
		}
		// the careening beach, on the sea side, to one side of the jetty
		BlockPos hull = fire.relative(seaward, 7).relative(side, -9);
		VillageStyles.stamp(level, careened(), hull.atY(VillageStyles.groundY(level, hull.getX(), hull.getZ())), side, foot);
		// the fence's market, the other side
		BlockPos market = fire.relative(seaward, 3).relative(side, 11);
		for (int i = 0; i < 3; i++) {
			BlockPos at = market.relative(back, i * 4);
			VillageStyles.stamp(level, StyleBuildings.stall(look, i == 1 ? "black" : "red", "gray"), at.atY(VillageStyles.groundY(level, at.getX(), at.getZ())),
				side.getOpposite(), foot);
			if (i < TRADERS) {
				EntityTypes.VILLAGER.spawn(level, Construction.standable(level, at.relative(side.getOpposite(), 2), 4), EntitySpawnReason.STRUCTURE);
			}
		}
	}

	/** A hull hauled up the beach (9 x 11): planks and a keel, propped on logs either side, a tar barrel and a fire to scrape her by. */
	static Blueprint careened() {
		Buildings.Sketch s = new Buildings.Sketch("careened_hull", 9, 11, 6);
		BlockState plank = Blocks.DARK_OAK_PLANKS.defaultBlockState();
		for (int z = 0; z < 11; z++) {
			int half = z == 0 || z == 10 ? 0 : z == 1 || z == 9 ? 1 : 2;
			for (int x = 4 - half; x <= 4 + half; x++) {
				s.set(x, 1, z, plank);
				if (x == 4 - half || x == 4 + half) {
					s.set(x, 2, z, plank);
					if (z % 2 == 0) {
						s.set(x, 3, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
					}
				}
			}
			s.set(4, 0, z, Blocks.SPRUCE_LOG.defaultBlockState());
		}
		for (int z : new int[] {2, 5, 8}) {
			for (int x : new int[] {0, 8}) {
				s.set(x, 0, z, Blocks.SPRUCE_LOG.defaultBlockState());
				s.set(x + (x == 0 ? 1 : -1), 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
			}
		}
		s.set(0, 1, 0, Blocks.BARREL.defaultBlockState());
		s.set(8, 0, 0, Blocks.CAMPFIRE.defaultBlockState());
		s.set(7, 0, 0, Blocks.GRINDSTONE.defaultBlockState());
		return s.done();
	}
}
