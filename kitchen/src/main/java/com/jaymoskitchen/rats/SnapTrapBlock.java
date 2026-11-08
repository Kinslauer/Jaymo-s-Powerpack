package com.jaymoskitchen.rats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The snap trap (the Rats mod's rat trap, LGPL-3.0: its model and art, our own code): a wooden board with a spring bar.
 * Bait it; a rat that runs onto it is killed and the bar stays down until you right-click it to set it again. Step on a
 * set one (not sneaking) and it snaps on your foot. A shut trap gives a full comparator signal.
 */
public class SnapTrapBlock extends TrapBlock {
	public static final BooleanProperty SHUT = BooleanProperty.create("shut");
	/** What it does to a foot (half-hearts). */
	static final float FOOT_DAMAGE = 2.0F;
	private static final VoxelShape NS = Block.box(4, 0, 1, 12, 2, 15);
	private static final VoxelShape EW = Block.box(1, 0, 4, 15, 2, 12);

	public SnapTrapBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH).setValue(SHUT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HorizontalDirectionalBlock.FACING, SHUT);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(HorizontalDirectionalBlock.FACING).getAxis() == Direction.Axis.Z ? NS : EW;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SnapTrapEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? createTickerHelper(type, Rats.SNAP_TRAP_ENTITY, (l, pos, s, trap) -> trap.clientTick(s)) : null;
	}

	@Override
	protected boolean open(BlockState state) {
		return !state.getValue(SHUT);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (state.getValue(SHUT)) {
			if (!level.isClientSide()) {
				level.setBlockAndUpdate(pos, state.setValue(SHUT, false));
				level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.8F, 1.3F);
			}
			return InteractionResult.SUCCESS;
		}
		return super.useWithoutItem(state, level, pos, player, hit);
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise) {
		if (!(level instanceof ServerLevel server) || state.getValue(SHUT)) {
			return;
		}
		if (entity instanceof Rat rat && rat.isAlive()) {
			snap(server, pos, state);
			rat.hurtServer(server, server.damageSources().generic(), Float.MAX_VALUE);
			if (level.getBlockEntity(pos) instanceof SnapTrapEntity trap) {
				trap.eatBait();
			}
			scatter(server, pos, rat);
		} else if (entity instanceof Player player && !player.isShiftKeyDown() && !player.isCreative() && !player.isSpectator()) {
			snap(server, pos, state);
			player.hurtServer(server, server.damageSources().generic(), FOOT_DAMAGE);
		}
	}

	private static void snap(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlockAndUpdate(pos, state.setValue(SHUT, true));
		level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 1.4F);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return state.getValue(SHUT) ? 15 : 0;
	}
}
