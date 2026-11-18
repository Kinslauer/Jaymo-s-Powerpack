package com.jaymospowers.buildblocks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Two different slabs in one block, a bottom one and a top one ({@link MixedSlabEntity} keeps them; they are drawn by
 * MixedSlabRenderer). Made by putting a slab into the empty half of another kind of slab ({@link MixedSlabs}); mining it takes
 * only the half the player looks at, leaving the other slab behind. It has no item of its own.
 */
public class MixedSlabBlock extends BaseEntityBlock {
	public MixedSlabBlock(Properties properties) {
		super(properties);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MixedSlabEntity(pos, state);
	}

	/** Drawn by its block entity's renderer, not as a block model. */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.block();
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	/** How fast the half the player looks at comes away, as that slab alone would. */
	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof MixedSlabEntity slabs) {
			return slabs.half(MixedSlabs.upperHalf(player, pos)).getDestroyProgress(player, level, pos);
		}
		return super.getDestroyProgress(state, player, level, pos);
	}

	/** Whatever a break not made by a player (a blast, a piston) leaves: both slabs. */
	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		List<ItemStack> drops = new ArrayList<>();
		if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof MixedSlabEntity slabs) {
			drops.addAll(slabs.bottom().getDrops(params));
			drops.addAll(slabs.top().getDrops(params));
		}
		return drops;
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return level.getBlockEntity(pos) instanceof MixedSlabEntity slabs ? new ItemStack(slabs.bottom().getBlock()) : ItemStack.EMPTY;
	}
}
