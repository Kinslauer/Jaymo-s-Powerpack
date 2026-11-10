package com.jaymoskitchen.fresh;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.util.Prediction;

/**
 * A cheese wheel (Andy's TFC Hardrock picks): set down in a cold cellar ({@link Freshness#cellar}) it ages, a step about
 * every in-game day ({@link #AGE_CHANCE}), to {@link #AGED}. Used with an empty hand it gives a slice ({@link Cheese}):
 * young cheese until it's aged, then aged cheese, which fills you far more. {@link #SLICES} slices to a wheel.
 */
public class CheeseWheelBlock extends Block {
	public static final int AGED = 3, SLICES = 4;
	public static final IntegerProperty AGE = IntegerProperty.create("age", 0, AGED);
	public static final IntegerProperty CUT = IntegerProperty.create("cut", 0, SLICES - 1);
	/** One random tick in this many ages it a step (a block gets one about every minute). */
	static final int AGE_CHANCE = 18;
	private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 6, 14);

	public CheeseWheelBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(AGE, 0).setValue(CUT, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AGE, CUT);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return state.getValue(AGE) < AGED;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(AGE) < AGED && random.nextInt(AGE_CHANCE) == 0 && Freshness.cellar(level, pos)) {
			level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server) {
			ItemStack slice = new ItemStack(state.getValue(AGE) >= AGED ? Cheese.AGED_CHEESE : Cheese.CHEESE);
			player.getInventory().placeItemBackInInventory(slice, Prediction.SERVER_ONLY);
			int cut = state.getValue(CUT) + 1;
			if (cut >= SLICES) {
				server.removeBlock(pos, false);
			} else {
				server.setBlock(pos, state.setValue(CUT, cut), Block.UPDATE_ALL);
			}
			server.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 0.8F, 1.2F);
		}
		return InteractionResult.SUCCESS;
	}
}
