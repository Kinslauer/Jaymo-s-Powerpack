package com.jaymospowers.core.wounds.herbs;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (0.19.20) A wild herb (comfrey, yarrow, plantain, wild garlic, chamomile): a small bushy plant with four growth stages
 * (age 0 to 3) that grows on random ticks in light, like a sweet berry bush. Right-click a grown one to pick
 * {@link #MIN_PICKED} to {@link #MAX_PICKED} herbs; it drops back to {@link #PICKED_AGE} and grows again. Breaking it gives
 * the herb back (the loot table: 1, or 1 to 3 when grown). The herb item is this block's item, so it plants itself (age 0)
 * on grass, dirt or farmland. The default state is a grown plant: that is what worldgen places.
 */
public class HerbBushBlock extends Block {
	// ---- tuning (0.19.20) ----
	/** A random tick grows it with 1 chance in this many, with this much light on it. */
	public static final int GROW_CHANCE = 6, GROW_LIGHT = 9;
	/** The age a picked plant goes back to, and the herbs a right-click gives. */
	public static final int PICKED_AGE = 1, MIN_PICKED = 1, MAX_PICKED = 3;
	public static final int RIPE = 3;

	public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
	private static final VoxelShape[] SHAPES = {
		Block.box(4.0, 0.0, 4.0, 12.0, 5.0, 12.0),
		Block.box(3.0, 0.0, 3.0, 13.0, 9.0, 13.0),
		Block.box(2.0, 0.0, 2.0, 14.0, 12.0, 14.0),
		Block.box(2.0, 0.0, 2.0, 14.0, 14.0, 14.0),
	};

	private final Supplier<Item> herb;

	public HerbBushBlock(BlockBehaviour.Properties properties, Supplier<Item> herb) {
		super(properties);
		this.herb = herb;
		this.registerDefaultState(this.stateDefinition.any().setValue(AGE, RIPE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AGE);
	}

	/** Planted by hand it starts small. */
	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(AGE, 0);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(AGE)];
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.below()).is(BlockTags.SUBSTRATE_OVERWORLD);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return direction == Direction.DOWN && !this.canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return state.getValue(AGE) < RIPE;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int age = state.getValue(AGE);
		if (age < RIPE && random.nextInt(GROW_CHANCE) == 0 && level.getMaxLocalRawBrightness(pos.above()) >= GROW_LIGHT) {
			level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (state.getValue(AGE) < RIPE) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server) {
			int count = MIN_PICKED + server.getRandom().nextInt(MAX_PICKED - MIN_PICKED + 1);
			Block.popResource(server, pos, new ItemStack(this.herb.get(), count));
			server.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + server.getRandom().nextFloat() * 0.4F);
			server.setBlock(pos, state.setValue(AGE, PICKED_AGE), Block.UPDATE_CLIENTS);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(this.herb.get());
	}
}
