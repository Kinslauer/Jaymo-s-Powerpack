package com.jaymospowers.aether;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The floor of the sky woods (docs/aether.md, "The forest floor"): leaves shed {@link LeafPileBlock leaf piles}, the piles
 * rot into the soil, saplings and picked bushes draw on a pile beside them, {@link RotshroomBlock rotshrooms} eat piles and
 * fruit, and moss creeps over damp holystone. What they share is here.
 */
public final class ForestFloor {
	// ---- tuning ----
	/** Light under which fungus grows (vanilla mushrooms: under 13). */
	public static final int FUNGUS_LIGHT = 13;

	private ForestFloor() {
	}

	/** A leaf pile beside {@code pos} (the eight spots round it, on its level), or null. */
	public static @Nullable BlockPos pileBeside(ServerLevel level, BlockPos pos) {
		for (BlockPos at : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 0, 1))) {
			if (level.getBlockState(at).getBlock() instanceof LeafPileBlock) {
				return at.immutable();
			}
		}
		return null;
	}

	/** Uses up a leaf pile: it goes, and bare aether dirt under it greens over. */
	public static void spend(ServerLevel level, BlockPos pile) {
		level.setBlockAndUpdate(pile, Blocks.AIR.defaultBlockState());
		BlockPos below = pile.below();
		BlockState ground = level.getBlockState(below);
		if (ground.is(AetherBlocks.AETHER_DIRT)) {
			level.setBlockAndUpdate(below, AetherBlocks.AETHER_GRASS_BLOCK.defaultBlockState());
		}
	}

	/** How many blocks of {@code kind} stand within {@code range} of {@code pos} (two levels up and down). */
	public static int near(ServerLevel level, BlockPos pos, int range, Class<?> kind) {
		int n = 0;
		for (BlockPos at : BlockPos.betweenClosed(pos.offset(-range, -2, -range), pos.offset(range, 2, range))) {
			if (kind.isInstance(level.getBlockState(at).getBlock())) {
				n++;
			}
		}
		return n;
	}
}
