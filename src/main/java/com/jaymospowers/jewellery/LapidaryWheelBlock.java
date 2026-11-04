package com.jaymospowers.jewellery;

import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (0.19.87) The Lapidary Wheel: lay a gem on it, choose a cut, then cut in time with the bar. Use it again to take the
 * cut gem. The wheel stands along the way it faces.
 */
public class LapidaryWheelBlock extends HorizontalDirectionalBlock implements EntityBlock {
	private static final VoxelShape X_SHAPE = Shapes.or(Block.box(1, 0, 4, 15, 3, 12), Block.box(2, 3, 6, 4, 12, 10),
		Block.box(12, 3, 6, 14, 12, 10), Block.box(4, 4, 7, 12, 12, 9));
	private static final VoxelShape Z_SHAPE = Shapes.or(Block.box(4, 0, 1, 12, 3, 15), Block.box(6, 3, 2, 10, 12, 4),
		Block.box(6, 3, 12, 10, 12, 14), Block.box(7, 4, 4, 9, 12, 12));

	/**
	 * Set by the client: a use of this wheel while its cutting game is up here judges the stroke and sends it (true
	 * when it did). The server ignores uses during a game: the strokes come as packets.
	 */
	public static BiPredicate<Player, BlockPos> clientStroke = (player, pos) -> false;

	public LapidaryWheelBlock(Block.Properties properties) {
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
		return state.getValue(FACING).getAxis() == Direction.Axis.X ? Z_SHAPE : X_SHAPE;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new LapidaryWheelEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() || type != Jewellery.LAPIDARY_WHEEL_ENTITY ? null
			: (BlockEntityTicker<T>) (BlockEntityTicker<LapidaryWheelEntity>) LapidaryWheelEntity::serverTick;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit) {
		return interact(stack, level, pos, player, hand, true);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		return interact(ItemStack.EMPTY, level, pos, player, InteractionHand.MAIN_HAND, false);
	}

	private static InteractionResult interact(ItemStack stack, Level level, BlockPos pos, Player player, InteractionHand hand, boolean withItem) {
		if (hand != InteractionHand.MAIN_HAND || !(level.getBlockEntity(pos) instanceof LapidaryWheelEntity wheel)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			if (clientStroke.test(player, pos)) {
				return InteractionResult.SUCCESS;
			}
			return !withItem || Gems.of(stack) != null || !wheel.isEmpty() ? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		ServerPlayer server = (ServerPlayer) player;
		if (wheel.cutting(server) || wheel.justFinished(server)) {
			return InteractionResult.SUCCESS;
		}
		wheel.use(server, stack);
		return InteractionResult.SUCCESS;
	}
}
