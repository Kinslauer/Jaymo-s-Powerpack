package com.jaymospowers.tinkering.table;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** The Pattern Chest and the Part Chest ({@link TinkerChestEntity}): a drawer chest with its front toward you. */
public class TinkerChestBlock extends HorizontalDirectionalBlock implements EntityBlock {
	/** The body and top on four legs: the floor shows between the legs, so the chest mustn't hide it. */
	private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 3, 1, 15, 15, 15), Block.box(0, 15, 0, 16, 16, 16),
		Block.box(0.5, 0, 0.5, 2.5, 3, 2.5), Block.box(13.5, 0, 0.5, 15.5, 3, 2.5), Block.box(0.5, 0, 13.5, 2.5, 3, 15.5),
		Block.box(13.5, 0, 13.5, 15.5, 3, 15.5));

	public TinkerChestBlock(Block.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TinkerChestEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MenuProvider chest) {
			player.openMenu(chest);
		}
		return InteractionResult.SUCCESS;
	}
}
