package com.jaymospowers.aether;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The berry bush: a bush on aether soil that gives 1 to 3 blue berries when broken (more with Fortune) and leaves its
 * {@link BerryBushStemBlock stem}, which grows the bush back in good light. Nothing about it hurts.
 */
public class BerryBushBlock extends Block {
	private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 14.0, 15.0);

	public BerryBushBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	/** Bushes (and stems) stand on dirt, grass and the like. */
	static boolean survives(LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.below()).is(BlockTags.SUBSTRATE_OVERWORLD);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return survives(level, pos);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return direction == Direction.DOWN && !this.canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
	}

	/** Mined by a player: the berries (the loot table), and the stem stays. */
	@Override
	public void playerDestroy(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack destroyedWith) {
		super.playerDestroy(level, player, pos, state, blockEntity, destroyedWith);
		if (level.getBlockState(pos).isAir()) {
			level.setBlockAndUpdate(pos, AetherBlocks.BERRY_BUSH_STEM.defaultBlockState());
		}
	}
}
