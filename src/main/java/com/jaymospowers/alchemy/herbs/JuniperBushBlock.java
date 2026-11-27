package com.jaymospowers.alchemy.herbs;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The juniper bush of the taiga (Hunter's Road, patch 7, "D7"): a low evergreen that ripens blue berries in four stages on
 * its own random ticks. Used by hand when ripe it gives its berries and drops back to a green stage; the berries, set down,
 * start a new bush. It stands on soil, in light.
 */
public class JuniperBushBlock extends VegetationBlock {
	public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
	/** Chance (one in this) that a random tick ripens it a stage, and the light it needs for that. */
	private static final int RIPEN_ODDS = 5, RIPEN_LIGHT = 9;
	/** Berries a ripe bush gives, and the stage it falls back to. */
	private static final int RIPE_MIN = 2, RIPE_MAX = 3, PICKED_AGE = 1;
	private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 12, 14);

	public JuniperBushBlock(Properties properties) {
		super(properties);
		registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AGE);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.is(BlockTags.DIRT) || state.is(Blocks.FARMLAND);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return state.getValue(AGE) < 3;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int age = state.getValue(AGE);
		if (age < 3 && level.getRawBrightness(pos.above(), 0) >= RIPEN_LIGHT && random.nextInt(RIPEN_ODDS) == 0) {
			level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (state.getValue(AGE) < 2) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server) {
			int count = state.getValue(AGE) == 3 ? RIPE_MIN + server.getRandom().nextInt(RIPE_MAX - RIPE_MIN + 1) : 1;
			Block.popResource(server, pos, new ItemStack(Garden.JUNIPER_BERRIES, count));
			server.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + server.getRandom().nextFloat() * 0.4F);
			server.setBlock(pos, state.setValue(AGE, PICKED_AGE), Block.UPDATE_CLIENTS);
		}
		return InteractionResult.SUCCESS;
	}
}
