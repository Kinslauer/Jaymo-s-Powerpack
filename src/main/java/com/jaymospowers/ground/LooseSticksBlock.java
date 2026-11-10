package com.jaymospowers.ground;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A few sticks lying on the ground (GroundSticks), 1 to {@link #MAX_STICKS} in a pile. Like a loose rock pile:
 * walk-through, gone with a punch (the loot table drops a stick for each), a right click picks one stick up into the
 * inventory, pops off when its ground goes. Every stick is a plain vanilla stick. Models: tools/gen_loose_sticks.py.
 */
public final class LooseSticksBlock extends Block {
	/** Keep in step with STICKS in tools/gen_loose_sticks.py (one model per pile size). */
	public static final int MAX_STICKS = 4;
	public static final IntegerProperty STICKS = IntegerProperty.create("sticks", 1, MAX_STICKS);
	/** The outline, square so it holds whichever way the blockstate turns the pile. */
	private static final VoxelShape SMALL = Block.box(2.5, 0.0, 2.5, 13.5, 1.5, 13.5);
	private static final VoxelShape LARGE = Block.box(0.5, 0.0, 0.5, 15.5, 1.5, 15.5);
	/** Piles of this many sticks or more spread over the whole block. */
	private static final int LARGE_FROM = 3;

	public LooseSticksBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(STICKS, 1));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(STICKS);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return (state.getValue(STICKS) >= LARGE_FROM ? LARGE : SMALL).move(state.getOffset(pos));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			int left = state.getValue(STICKS) - 1;
			level.setBlock(pos, left > 0 ? state.setValue(STICKS, left) : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			if (!player.getAbilities().instabuild) {
				player.getInventory().placeItemBackInInventory(new ItemStack(Items.STICK), Prediction.SERVER_ONLY);
			}
			level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 0.8F + level.getRandom().nextFloat() * 0.4F);
			level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return Block.canSupportCenter(level, pos.below(), Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		return direction == Direction.DOWN && !this.canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
			: super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(Items.STICK);
	}
}
