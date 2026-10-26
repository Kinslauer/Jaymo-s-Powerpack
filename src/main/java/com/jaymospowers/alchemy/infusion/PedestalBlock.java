package com.jaymospowers.alchemy.infusion;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A stone pillar that holds one item ({@link PedestalEntity}), shown floating and turning above it. Right-click with
 * something to set one of it down; right-click again (any hand) to take it back. The centre and component pedestals of an
 * infusion ({@link Infusion}) are all this block.
 */
public class PedestalBlock extends Block implements EntityBlock {
	private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 2, 14), Block.box(4, 2, 4, 12, 12, 12), Block.box(2, 12, 2, 14, 14, 14));

	public PedestalBlock(Block.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PedestalEntity(pos, state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit) {
		if (level.getBlockEntity(pos) instanceof PedestalEntity pedestal && pedestal.stack().isEmpty() && !stack.isEmpty()) {
			if (!level.isClientSide()) {
				pedestal.setStack(stack.copyWithCount(1));
				if (!player.hasInfiniteMaterials()) {
					stack.shrink(1);
				}
				level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 0.7F, 1.2F);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.getBlockEntity(pos) instanceof PedestalEntity pedestal && !pedestal.stack().isEmpty()) {
			if (!level.isClientSide()) {
				player.getInventory().placeItemBackInInventory(pedestal.take(), Prediction.SERVER_ONLY);
				level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 1.0F);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}
}
