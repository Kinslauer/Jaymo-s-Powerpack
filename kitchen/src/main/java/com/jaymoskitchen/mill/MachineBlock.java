package com.jaymoskitchen.mill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** A mill machine (see {@link MachineKind}); its workings are in {@link MachineEntity}. */
public class MachineBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

	public final MachineKind kind;
	private final VoxelShape shape;

	public MachineBlock(MachineKind kind, BlockBehaviour.Properties properties) {
		super(properties);
		this.kind = kind;
		this.shape = switch (kind) {
			case HAND_GRINDSTONE -> Block.box(1, 0, 1, 15, 10, 15);
			case GRINDSTONE -> Block.box(0, 0, 0, 16, 8, 16);
			case CHOPPING_BLOCK -> Block.box(0, 0, 0, 16, 6, 16);
			default -> Block.box(0, 0, 0, 16, 16, 16);
		};
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		if (kind.tall && !context.getLevel().getBlockState(context.getClickedPos().above()).canBeReplaced(context)) {
			return null;
		}
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
		super.setPlacedBy(level, pos, state, by, stack);
		if (kind.tall) {
			level.setBlock(pos.above(), Mill.FILLER.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		if (kind.tall && level.getBlockState(pos.above()).is(Mill.FILLER)) {
			level.removeBlock(pos.above(), false);
		}
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shape;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MachineEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return kind.horse && !level.isClientSide() ? createTickerHelper(type, Mill.MACHINE_ENTITY, MachineEntity::serverTick) : null;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (level.getBlockEntity(pos) instanceof MachineEntity machine) {
			InteractionResult result = machine.use(player, hand, held);
			return result == InteractionResult.PASS ? InteractionResult.TRY_WITH_EMPTY_HAND : result;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.getBlockEntity(pos) instanceof MachineEntity machine) {
			return machine.use(player, InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		}
		return InteractionResult.PASS;
	}
}
