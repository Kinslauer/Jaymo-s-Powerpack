package com.jaymoskitchen.mill.machines;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The potter's wheel (Better With Mods' turntable, MIT; our own code): a wooden stand with a stone wheel on top that a
 * shaft from below turns, or a hand (use it with an empty hand). A clay block set on it is thrown, a turn at a time,
 * into a pot ({@link PottersWheelRecipes}). Its workings are in {@link PottersWheelEntity}.
 */
public class PottersWheelBlock extends BaseEntityBlock {
	/** Never placed: the state that holds the turning wheel's model, for the renderer. */
	public static final BooleanProperty WHEEL = BooleanProperty.create("wheel");

	private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

	public PottersWheelBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(WHEEL, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(WHEEL);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.getBlockEntity(pos) instanceof PottersWheelEntity wheel) {
			if (!level.isClientSide()) {
				if (player.isSecondaryUseActive()) {
					wheel.nextPick(player);
				} else {
					wheel.spinByHand(player);
				}
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PottersWheelEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? createTickerHelper(type, PowerMachines.POTTERS_WHEEL_ENTITY, PottersWheelEntity::clientTick)
			: createTickerHelper(type, PowerMachines.POTTERS_WHEEL_ENTITY, PottersWheelEntity::serverTick);
	}
}
