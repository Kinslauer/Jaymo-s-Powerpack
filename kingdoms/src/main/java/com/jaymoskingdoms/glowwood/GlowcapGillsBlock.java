package com.jaymoskingdoms.glowwood;

import com.jaymoskingdoms.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Luminous gill curtains that hang under a giant glowcap's cap (or any ceiling), one or two blocks long; TIP marks the
 * lowest block, which tapers off. Drifting motes fall from the tip (ported from Spore & Stem, Apache-2.0).
 */
public class GlowcapGillsBlock extends Block {
	public static final BooleanProperty TIP = BlockStateProperties.BOTTOM;
	private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);
	private static final VoxelShape TIP_SHAPE = Block.box(2.0, 6.0, 2.0, 14.0, 16.0, 14.0);
	/** One tick in this many a mote falls from the tip; its colour and size. */
	private static final int MOTE_ONE_IN = 8;
	private static final int MOTE_COLOUR = 0x6BF2E3;
	private static final float MOTE_SIZE = 0.6F;

	public GlowcapGillsBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(TIP, true));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(TIP);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(TIP) ? TIP_SHAPE : SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos abovePos = pos.above();
		BlockState above = level.getBlockState(abovePos);
		return above.is(this) || above.is(GlowWoods.GLOWCAP_CAP) || above.isFaceSturdy(level, abovePos, Direction.DOWN);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = this.defaultBlockState();
		if (!state.canSurvive(context.getLevel(), context.getClickedPos())) {
			return null;
		}
		return state.setValue(TIP, !context.getLevel().getBlockState(context.getClickedPos().below()).is(this));
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
		Direction direction, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
		if (direction == Direction.UP && !state.canSurvive(level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		if (direction == Direction.DOWN) {
			return state.setValue(TIP, !neighbor.is(this));
		}
		return state;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(TIP) && random.nextInt(MOTE_ONE_IN) == 0) {
			level.addParticle(Fx.mote(MOTE_COLOUR, MOTE_SIZE), pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.35,
				pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, -0.01, 0.0);
		}
	}
}
