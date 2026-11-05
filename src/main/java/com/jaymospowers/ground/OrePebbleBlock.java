package com.jaymospowers.ground;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (0.20.105) A lone pebble over a shallow ore vein (OreTells): either stained the ore's colour, or a plain-looking rock
 * that turns out to be a nugget of the ore when picked up. Like a loose rock: walk-through, gone with a punch (its loot
 * table), a right click picks it up, pops off when its ground goes.
 */
public final class OrePebbleBlock extends Block {
	private static final VoxelShape SHAPE = Block.box(5.5, 0.0, 6.0, 10.0, 3.0, 10.5);

	/** What a right click gives: a stained pebble gives a plain rock, a hidden one its nugget. */
	private final Supplier<Item> gives;

	public OrePebbleBlock(BlockBehaviour.Properties properties, Supplier<Item> gives) {
		super(properties);
		this.gives = gives;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE.move(state.getOffset(pos));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			if (!player.getAbilities().instabuild) {
				player.getInventory().placeItemBackInInventory(new ItemStack(this.gives.get()), Prediction.SERVER_ONLY);
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

	/** Middle click gives a plain rock, whatever the pebble hides. */
	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(GroundRocks.ROCKS.get(GroundRocks.Variant.STONE));
	}
}
