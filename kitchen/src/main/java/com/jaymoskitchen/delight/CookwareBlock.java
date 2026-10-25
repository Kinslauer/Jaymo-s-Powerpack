package com.jaymoskitchen.delight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (Farmer's Delight's cooking pot and skillet, MIT; their models and art, our own code.) The Cooking Pot and the
 * Frying Pan set down as blocks. Over anything hot ({@link Delight#HEAT_SOURCES}: a lit campfire, a fire on the
 * ground, lava, a magma block, a lit furnace) they sit on a heating tray and cook: sneak-click one with an empty hand
 * for the kitchen, a pot the stews and soups, a pan the grilled and seared dishes (see
 * {@link com.jaymoskitchen.cooking.Station#HEARTH_POT}). Cold, they're just a pot and a pan.
 */
public class CookwareBlock extends HorizontalDirectionalBlock {
	/** Standing on something hot: drawn on a heating tray, and it cooks. */
	public static final BooleanProperty HEATED = BooleanProperty.create("heated");

	private static final VoxelShape POT = Block.box(2, 0, 2, 14, 10, 14);
	private static final VoxelShape PAN = Block.box(1, 0, 1, 15, 4, 15);

	private final boolean pot;

	public CookwareBlock(Properties properties, boolean pot) {
		super(properties);
		this.pot = pot;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HEATED, false));
	}

	public boolean isPot() {
		return pot;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HEATED);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return pot ? POT : PAN;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
			.setValue(HEATED, hot(context.getLevel().getBlockState(pos.below())));
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		if (direction == Direction.DOWN) {
			return state.setValue(HEATED, hot(neighbour));
		}
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
	}

	/** Whether a block below would heat cookware: in the heat sources tag and, if it can be lit, lit. */
	public static boolean hot(BlockState below) {
		return below.is(Delight.HEAT_SOURCES)
			&& (!below.hasProperty(BlockStateProperties.LIT) || below.getValue(BlockStateProperties.LIT));
	}

	/** Cookware at a spot that's hot enough to cook in. */
	public static boolean heated(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getBlock() instanceof CookwareBlock && hot(level.getBlockState(pos.below()));
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(HEATED)) {
			return;
		}
		double x = pos.getX() + 0.5, z = pos.getZ() + 0.5;
		if (pot) {
			double y = pos.getY() + 0.65;
			if (random.nextInt(3) == 0) {
				level.addParticle(ParticleTypes.BUBBLE_POP, x + (random.nextDouble() - 0.5) * 0.5, y, z + (random.nextDouble() - 0.5) * 0.5, 0, 0.02, 0);
			}
			if (random.nextInt(4) == 0) {
				level.addParticle(ParticleTypes.CLOUD, x, y + 0.1, z, 0, 0.03, 0);
			}
			if (random.nextInt(10) == 0) {
				level.playLocalSound(pos, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.5F, 1.0F, false);
			}
		} else {
			double y = pos.getY() + 0.3;
			if (random.nextInt(3) == 0) {
				level.addParticle(ParticleTypes.SMOKE, x + (random.nextDouble() - 0.5) * 0.5, y, z + (random.nextDouble() - 0.5) * 0.5, 0, 0.02, 0);
			}
			if (random.nextInt(10) == 0) {
				level.playLocalSound(pos, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.6F, 1.2F, false);
			}
		}
	}
}
