package com.jaymospowers.aether.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A prayer candle: a candle fixed to the face of a wall. It burns with a soft light and golden motes; an empty hand puts it out,
 * an ambrosium shard or flint and steel lights it again.
 */
public class PrayerCandleBlock extends Block {
	// ---- tuning ----
	/** Light of a lit candle. */
	public static final int LIGHT = 12;

	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	/** The candle stands 4 wide and 10 tall, flush against the wall behind it, which is opposite {@code FACING}. */
	private static final VoxelShape NORTH = Block.box(6.0, 3.0, 12.0, 10.0, 13.0, 16.0), SOUTH = Block.box(6.0, 3.0, 0.0, 10.0, 13.0, 4.0),
		WEST = Block.box(12.0, 3.0, 6.0, 16.0, 13.0, 10.0), EAST = Block.box(0.0, 3.0, 6.0, 4.0, 13.0, 10.0);

	public PrayerCandleBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, true));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LIT);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(FACING)) {
			case SOUTH -> SOUTH;
			case WEST -> WEST;
			case EAST -> EAST;
			default -> NORTH;
		};
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction facing = state.getValue(FACING);
		BlockPos behind = pos.relative(facing.getOpposite());
		return level.getBlockState(behind).isFaceSturdy(level, behind, facing);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		for (Direction d : context.getNearestLookingDirections()) {
			if (d.getAxis().isHorizontal()) {
				BlockState state = this.defaultBlockState().setValue(FACING, d.getOpposite());
				if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
					return state;
				}
			}
		}
		return null;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighborPos,
		BlockState neighbor, RandomSource random) {
		return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
			: super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (state.getValue(LIT)) {
			if (!level.isClientSide()) {
				level.setBlock(pos, state.setValue(LIT, false), 3);
				level.playSound(null, pos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!state.getValue(LIT) && (stack.is(com.jaymospowers.aether.AetherItems.AMBROSIUM_SHARD) || stack.is(Items.FLINT_AND_STEEL))) {
			if (!level.isClientSide()) {
				level.setBlock(pos, state.setValue(LIT, true), 3);
				level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
				if (stack.is(Items.FLINT_AND_STEEL)) {
					stack.hurtAndBreak(1, player, hand);
				} else {
					stack.consume(1, player);
				}
			}
			return InteractionResult.SUCCESS;
		}
		return super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(LIT) && random.nextInt(5) == 0) {
			Direction f = state.getValue(FACING);
			level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + f.getStepX() * 0.1, pos.getY() + 0.9, pos.getZ() + 0.5 + f.getStepZ() * 0.1, 0.0, 0.02, 0.0);
		}
	}
}
