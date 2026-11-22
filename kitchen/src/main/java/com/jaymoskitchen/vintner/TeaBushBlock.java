package com.jaymoskitchen.vintner;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A tea bush. Sympathy: it likes mist and shade, so it grows in the rain or out of the open sky, and only a fraction as
 * often in bare sun. Picking the young flush at the top gives fresh leaves and the bush shoots again from the same
 * stage; left alone it just keeps its leaves. The leaves dry on a drying rack in the sun ({@link Vintner#TEA_LEAVES}).
 */
public class TeaBushBlock extends VegetationBlock {
	public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
	public static final int RIPE = 3;
	/** What a picked bush drops back to, and the leaves in a picking (the low and the spread). */
	public static final int REGROW_AGE = 1;
	private static final int PICK_MIN = 2, PICK_SPREAD = 2;
	/** A growth step comes on 1 in this many random ticks, and bare sun makes it this many times fewer. */
	private static final int GROW_ODDS = 10, SUN_SLOWS = 4;
	private static final int GROW_LIGHT = 8;
	private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 14.0D, 14.0D);

	public TeaBushBlock(Properties properties) {
		super(properties);
		// worldgen places the default state: a wild bush in leaf (planting one starts it young)
		registerDefaultState(stateDefinition.any().setValue(AGE, RIPE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AGE);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = super.getStateForPlacement(context);
		return state == null ? null : state.setValue(AGE, 0);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.SUPPORTS_CROPS);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (state.getValue(AGE) < RIPE) {
			return InteractionResult.PASS;
		}
		popResource(level, pos, new ItemStack(Vintner.TEA_LEAVES, PICK_MIN + level.getRandom().nextInt(PICK_SPREAD + 1)));
		level.setBlock(pos, state.setValue(AGE, REGROW_AGE), Block.UPDATE_CLIENTS);
		level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 0.8F, 1.3F);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return state.getValue(AGE) < RIPE;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!level.hasChunksAt(pos.offset(-1, -1, -1), pos.offset(1, 1, 1)) || level.getRawBrightness(pos, 0) < GROW_LIGHT) {
			return;
		}
		boolean misty = level.isRainingAt(pos.above()) || !level.canSeeSky(pos.above());
		if (random.nextInt(misty ? GROW_ODDS : GROW_ODDS * SUN_SLOWS) == 0) {
			level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), Block.UPDATE_CLIENTS);
		}
	}
}
