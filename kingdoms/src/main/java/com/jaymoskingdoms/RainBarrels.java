package com.jaymoskingdoms;

import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Rain barrels (Jaymo's Kitchen's, found by registry id, so Kingdoms needs no Kitchen jar to build or run): once a village's
 * builders finish a house ({@link Construction}), they set one rain barrel on the open ground against one of its outside
 * walls, to gather rain for the house. It is a separate step after the house is up, not part of its plan, so the plan a crew
 * levels, stakes and rebuilds is unchanged. At most one per house (it is set once, when the house is finished); a house
 * with no free, solid, unpathed ground beside it goes without. It starts empty and fills in the rain.
 */
public final class RainBarrels {
	private static final String KITCHEN = "jaymoskitchen";
	private static final Identifier BARREL = Identifier.fromNamespaceAndPath(KITCHEN, "rain_barrel");
	/** How far below and above the floor the ground beside the house is looked for. */
	private static final int DOWN = 3, UP = 2;

	private RainBarrels() {
	}

	/** A building finished: a village's house gets its barrel. */
	static void built(ServerLevel level, Faction f, String entry, BlockPos min, BlockPos max) {
		if (f.type() != FactionType.VILLAGE || !entry.equals("house") || !FabricLoader.getInstance().isModLoaded(KITCHEN)) {
			return;
		}
		Optional<Block> barrel = BuiltInRegistries.BLOCK.getOptional(BARREL);
		if (barrel.isEmpty()) {
			return;
		}
		// walk the ring just outside the footprint, starting at a spot that differs from house to house
		int w = max.getX() - min.getX() + 3, d = max.getZ() - min.getZ() + 3, ring = 2 * (w + d) - 4;
		int start = Math.floorMod(min.getX() * 31 + min.getZ() * 17, Math.max(1, ring));
		for (int i = 0; i < ring; i++) {
			int k = (start + i) % ring;
			int x, z;
			if (k < w) { x = k; z = 0; }
			else if (k < w + d - 1) { x = w - 1; z = k - w + 1; }
			else if (k < 2 * w + d - 2) { x = 2 * w + d - 3 - k; z = d - 1; }
			else { x = 0; z = ring - k; }
			BlockPos column = new BlockPos(min.getX() - 1 + x, min.getY(), min.getZ() - 1 + z);
			BlockPos spot = free(level, column);
			if (spot != null) {
				level.setBlock(spot, barrel.get().defaultBlockState(), Block.UPDATE_ALL);
				return;
			}
		}
	}

	/** The air above solid, unpathed ground in {@code column} (near the house's floor) that has a wall of the house beside it, or null. */
	private static BlockPos free(ServerLevel level, BlockPos column) {
		if (!level.hasChunkAt(column)) {
			return null;
		}
		for (int dy = UP; dy >= -DOWN; dy--) {
			BlockPos ground = column.above(dy), above = ground.above();
			BlockState g = level.getBlockState(ground);
			if (g.isFaceSturdy(level, ground, Direction.UP) && !g.is(Blocks.DIRT_PATH) && g.getFluidState().isEmpty()
				&& level.getBlockState(above).isAir() && level.getBlockState(above.above()).isAir() && level.getFluidState(above).isEmpty()) {
				for (Direction side : Direction.Plane.HORIZONTAL) {
					BlockState wall = level.getBlockState(above.relative(side));
					if (wall.isFaceSturdy(level, above.relative(side), side.getOpposite()) && !wall.is(Blocks.DIRT_PATH)) {
						return above;
					}
				}
				return null;
			}
		}
		return null;
	}
}
