package com.jaymoskitchen.forage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A felled log lying on its side, for growing mushrooms (from Spore &amp; Stem, Apache-2.0; see licenses/spore-and-stem).
 * Use a mushroom on a fresh log to inoculate it. It then fruits through four stages on random ticks: twice as fast on
 * Mycelial Soil, slower under the open sky by day. Use a ripe log to pick 2 to 3 mushrooms; it drops back to its first
 * flush and fruits again. It keeps no season.
 */
public class CultivationLogBlock extends Block {
	public static final EnumProperty<Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	public static final EnumProperty<CultivationFungus> FUNGUS = EnumProperty.create("fungus", CultivationFungus.class);
	public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 3);
	public static final int RIPE = 3;
	/** A stage comes with chance one in this per random tick: normally, on Mycelial Soil, in open daylight. */
	private static final int ODDS = 4, ODDS_ON_SOIL = 2, ODDS_IN_SUN = 8;
	/** A ripe log gives this many mushrooms, plus up to this many more at random; then it falls back to this stage. */
	private static final int PICK_MIN = 2, PICK_EXTRA = 2, PICKED_STAGE = 1;
	private static final VoxelShape SHAPE_X = Block.box(0.0, 0.0, 2.0, 16.0, 12.0, 14.0);
	private static final VoxelShape SHAPE_Z = Block.box(2.0, 0.0, 0.0, 14.0, 12.0, 16.0);

	public CultivationLogBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(AXIS, Axis.X).setValue(FUNGUS, CultivationFungus.NONE).setValue(STAGE, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS, FUNGUS, STAGE);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(AXIS) == Axis.X ? SHAPE_X : SHAPE_Z;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
			return state.setValue(AXIS, state.getValue(AXIS) == Axis.X ? Axis.Z : Axis.X);
		}
		return state;
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state;
	}

	public static boolean isRipe(BlockState state) {
		return state.getValue(FUNGUS) != CultivationFungus.NONE && state.getValue(STAGE) == RIPE;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit) {
		if (state.getValue(FUNGUS) != CultivationFungus.NONE) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		CultivationFungus fungus = CultivationFungus.of(stack);
		if (fungus == CultivationFungus.NONE) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!level.isClientSide()) {
			level.setBlock(pos, state.setValue(FUNGUS, fungus).setValue(STAGE, 0), Block.UPDATE_ALL);
			level.playSound(null, pos, Forage.LOG_INOCULATE, SoundSource.BLOCKS, 1.0F, 1.0F);
			((ServerLevel) level).sendParticles(ParticleTypes.MYCELIUM, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 14, 0.35, 0.15, 0.35, 0.01);
			level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, state));
			stack.consume(1, player);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!isRipe(state)) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			int count = PICK_MIN + level.getRandom().nextInt(PICK_EXTRA);
			popResourceFromFace(level, pos, Direction.UP, new ItemStack(state.getValue(FUNGUS).item(), count));
			level.setBlock(pos, state.setValue(STAGE, PICKED_STAGE), Block.UPDATE_ALL);
			level.playSound(null, pos, Forage.LOG_HARVEST, SoundSource.BLOCKS, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
			level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, state));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return state.getValue(FUNGUS) != CultivationFungus.NONE && state.getValue(STAGE) < RIPE;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int odds = ODDS;
		if (level.getBlockState(pos.below()).is(Forage.MYCELIAL_SOIL)) {
			odds = ODDS_ON_SOIL;
		} else if (level.canSeeSky(pos.above()) && level.isBrightOutside()) {
			odds = ODDS_IN_SUN;
		}
		if (random.nextInt(odds) == 0) {
			level.setBlock(pos, state.setValue(STAGE, state.getValue(STAGE) + 1), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (isRipe(state) && random.nextInt(5) == 0) {
			level.addParticle(ParticleTypes.MYCELIUM, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.9,
				pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, 0.01, 0.0);
		} else if (state.getValue(FUNGUS) == CultivationFungus.EMBERSTOOL && state.getValue(STAGE) > 1 && random.nextInt(10) == 0) {
			level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.95,
				pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.005, 0.0);
		}
	}
}
