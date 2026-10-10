package com.jaymoskitchen.mill;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The invisible top half of a two-block-tall machine (the chopper, the presses): solid where the machine is, and
 * clicks on it work the machine below. Breaking it breaks the machine.
 */
public class MachineFillerBlock extends Block {
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 13, 16);

	public MachineFillerBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level.getBlockState(pos.below()).getBlock() instanceof MachineBlock) {
			level.destroyBlock(pos.below(), !player.getAbilities().instabuild, player);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (level.getBlockEntity(pos.below()) instanceof MachineEntity machine) {
			InteractionResult result = machine.use(player, hand, held);
			return result == InteractionResult.PASS ? InteractionResult.TRY_WITH_EMPTY_HAND : result;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.getBlockEntity(pos.below()) instanceof MachineEntity machine) {
			return machine.use(player, InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		}
		return InteractionResult.PASS;
	}
}
