package com.jaymoskitchen.delight;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (Ender's Delight's chorusflame, MIT; its art, our own code.) A chorus bud that burns cold on the End's stone: it grows
 * through three sizes, and the bigger it is when picked, the more chorusflame it gives (its loot table).
 */
public class ChorusflameBlock extends EndBushBlock {
	public static final int MAX_AGE = 2;
	public static final IntegerProperty AGE = BlockStateProperties.AGE_2;
	/** A growth step comes on 1 in this many random ticks. */
	private static final int GROW_ODDS = 5;
	private static final VoxelShape[] SHAPES = {
		Block.box(4.0D, 0.0D, 4.0D, 12.0D, 8.0D, 12.0D), Block.box(3.0D, 0.0D, 3.0D, 13.0D, 10.0D, 13.0D),
		Block.box(3.0D, 0.0D, 3.0D, 13.0D, 15.0D, 13.0D)
	};

	public ChorusflameBlock(Properties properties) {
		super(SHAPES[0], properties);
		registerDefaultState(defaultBlockState().setValue(AGE, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AGE);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(AGE)];
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return state.getValue(AGE) < MAX_AGE;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int age = state.getValue(AGE);
		if (age < MAX_AGE && random.nextInt(GROW_ODDS) == 0) {
			BlockState grown = state.setValue(AGE, age + 1);
			level.setBlock(pos, grown, Block.UPDATE_CLIENTS);
			level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(grown));
		}
	}
}
