package com.jaymoskitchen.farmwater;

import com.jaymoskitchen.butchery.Butchery;
import com.jaymoskitchen.compat.TideCompat;
import com.jaymoskitchen.fishing.Bait;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A wicker fish trap (Let's Do's idea, our own): set it in water, put a bait in (Fish Bait or offal) and it fills over a
 * day ({@link #FILL_TICKS}); empty it with an empty hand. What it holds comes from the stretch's stock
 * ({@link WaterStock}), so a trap in thin water, or in water already trapped out, comes up near empty. Fishing,
 * to the Tideborn ({@link TideCompat}).
 */
public class FishTrapBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
	public static final BooleanProperty BAITED = BooleanProperty.create("baited");
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	/** How long a full trap takes, and the most it holds. */
	static final long FILL_TICKS = 24000L;
	static final int TRAP_MAX = 6;
	/** Catch odds in a hundred: cod, then salmon, the rest tropical fish. */
	static final int COD = 60, SALMON = 90;
	private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 13, 13);

	public FishTrapBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(BAITED, false).setValue(WATERLOGGED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BAITED, WATERLOGGED);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FishTrapEntity(pos, state);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
		return defaultBlockState().setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		if (state.getValue(WATERLOGGED)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		boolean bait = held.is(Bait.FISH_BAIT) || held.is(Butchery.OFFAL);
		if (!bait || state.getValue(BAITED) || !state.getValue(WATERLOGGED)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof FishTrapEntity trap) {
			trap.setBaitedAt(server.getGameTime());
			server.setBlockAndUpdate(pos, state.setValue(BAITED, true));
			held.consume(1, player);
			server.playSound(null, pos, SoundEvents.BUCKET_EMPTY_FISH, SoundSource.BLOCKS, 0.6F, 1.0F);
			if (player instanceof ServerPlayer angler) {
				TideCompat.fished(angler, pos);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!state.getValue(BAITED)) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof FishTrapEntity trap) {
			long since = server.getGameTime() - trap.baitedAt();
			int fish = (int)Math.min(TRAP_MAX, TRAP_MAX * since / FILL_TICKS);
			if (fish <= 0) {
				return InteractionResult.PASS;
			}
			fish = WaterStock.take(server, pos, fish);
			for (int i = 0; i < fish; i++) {
				int roll = server.getRandom().nextInt(100);
				Block.popResource(server, pos.above(), new ItemStack(roll < COD ? Items.COD : roll < SALMON ? Items.SALMON : Items.TROPICAL_FISH));
			}
			trap.setBaitedAt(-1L);
			server.setBlockAndUpdate(pos, state.setValue(BAITED, false));
			server.playSound(null, pos, SoundEvents.BUCKET_FILL_FISH, SoundSource.BLOCKS, 0.7F, 1.0F);
			if (player instanceof ServerPlayer angler) {
				TideCompat.fished(angler, pos);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
