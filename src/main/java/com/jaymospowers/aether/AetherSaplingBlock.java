package com.jaymospowers.aether;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A sapling that grows one of our own tree features (a configured feature in the data, {@code jaymospowers:skyroot_tree}
 * or {@code golden_oak_tree}), where vanilla saplings grow trees of the {@code TreeGrower} they are made with. Planting,
 * light, random growth and bone meal are all the vanilla sapling's; only the tree is ours.
 */
public class AetherSaplingBlock extends SaplingBlock {
	// ---- tuning ----
	/** Vanilla saplings grow a step 1 tick in 7. */
	public static final int MULCHED_GROW_CHANCE = 2;

	private final ResourceKey<Feature> tree;

	public AetherSaplingBlock(ResourceKey<Feature> tree, BlockBehaviour.Properties properties) {
		// (the grower is never asked: advanceTree is replaced below)
		super(TreeGrower.OAK, properties);
		this.tree = tree;
	}

	@Override
	public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
		if (state.getValue(STAGE) == 0) {
			level.setBlock(pos, state.cycle(STAGE), Block.UPDATE_CLIENTS);
			return;
		}
		level.registryAccess().lookupOrThrow(Registries.FEATURE).get(this.tree).ifPresent(holder -> {
			level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			if (!holder.value().place(level, level.getChunkSource().getGenerator(), random, pos)) {
				level.setBlock(pos, state, Block.UPDATE_CLIENTS);
			}
		});
	}

	/** A leaf pile beside the sapling feeds it: in light it grows a step 1 tick in {@link #MULCHED_GROW_CHANCE}, and the pile is used up. */
	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		BlockPos pile = ForestFloor.pileBeside(level, pos);
		if (pile != null && level.getMaxLocalRawBrightness(pos.above()) >= 9) {
			if (random.nextInt(MULCHED_GROW_CHANCE) == 0) {
				ForestFloor.spend(level, pile);
				this.advanceTree(level, pos, state, random);
			}
			return;
		}
		super.randomTick(state, level, pos, random);
	}
}
