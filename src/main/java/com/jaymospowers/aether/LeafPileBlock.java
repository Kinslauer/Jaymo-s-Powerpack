package com.jaymospowers.aether;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A pile of fallen leaves. One a tree shed ({@link SheddingLeavesBlock}) rots away in time, greening bare aether dirt under
 * it and now and then leaving a rotshroom in the dark; one a player or the world set down stays. Either kind feeds a sapling
 * or picked bush beside it and is eaten by a rotshroom beside it.
 */
public class LeafPileBlock extends CarpetBlock {
	// ---- tuning ----
	/** A shed pile rots on a random tick with 1 chance in this many; a rotted pile leaves a rotshroom 1 time in this many. */
	public static final int ROT_CHANCE = 25, SPROUT_CHANCE = 8;
	/** No rotshroom sprouts with this many already within this range. */
	public static final int SPROUT_CROWD = 2, SPROUT_RANGE = 4;

	/** Set down rather than shed: it does not rot by itself. */
	public static final BooleanProperty PERSISTENT = BlockStateProperties.PERSISTENT;

	public LeafPileBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(PERSISTENT, true));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PERSISTENT);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState();
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return !state.getValue(PERSISTENT);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (random.nextInt(ROT_CHANCE) != 0) {
			return;
		}
		ForestFloor.spend(level, pos);
		if (random.nextInt(SPROUT_CHANCE) == 0 && level.getMaxLocalRawBrightness(pos) < ForestFloor.FUNGUS_LIGHT
			&& ForestFloor.near(level, pos, SPROUT_RANGE, RotshroomBlock.class) < SPROUT_CROWD) {
			BlockState shroom = AetherFlora.ROTSHROOM.defaultBlockState();
			if (shroom.canSurvive(level, pos)) {
				level.setBlockAndUpdate(pos, shroom);
			}
		}
	}
}
