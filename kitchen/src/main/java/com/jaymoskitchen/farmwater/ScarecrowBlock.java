package com.jaymoskitchen.farmwater;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A scarecrow (Medieval Deco's prop, our own): a straw man on a post. Put a carved pumpkin on its head and it keeps
 * crop-eating animals off crops within {@link Scarecrows#RANGE} blocks ({@link Scarecrows}); sneak with an empty hand to
 * take the pumpkin back.
 */
public class ScarecrowBlock extends HorizontalDirectionalBlock {
	public static final BooleanProperty PUMPKIN = BooleanProperty.create("pumpkin");
	private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 16, 13);

	public ScarecrowBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PUMPKIN, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, PUMPKIN);
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
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (!held.is(Items.CARVED_PUMPKIN) || state.getValue(PUMPKIN)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level instanceof ServerLevel server) {
			server.setBlockAndUpdate(pos, state.setValue(PUMPKIN, true));
			held.consume(1, player);
			server.playSound(null, pos, SoundEvents.PUMPKIN_CARVE, SoundSource.BLOCKS, 0.8F, 1.0F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!player.isShiftKeyDown() || !state.getValue(PUMPKIN)) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server) {
			server.setBlockAndUpdate(pos, state.setValue(PUMPKIN, false));
			Block.popResource(server, pos.above(), new ItemStack(Items.CARVED_PUMPKIN));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (level instanceof ServerLevel server && state.getValue(PUMPKIN)) {
			Scarecrows.keep(server, pos);
			server.scheduleTick(pos, this, Scarecrows.LOOK_EVERY);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(PUMPKIN)) {
			Scarecrows.keep(level, pos);
			Scarecrows.scare(level, Vec3.atCenterOf(pos));
			level.scheduleTick(pos, this, Scarecrows.LOOK_EVERY);
		}
	}
}
