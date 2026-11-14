package com.jaymospowers.aether;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The rotshroom lives on rot: it eats a leaf pile beside it, and after {@link #FULL} piles it fruits into a cluster. A
 * cluster can be picked by hand (the rotshroom stays, hungry again), or left, when in the dark it seeds one more rotshroom
 * close by. With no litter it only waits.
 */
public class RotshroomBlock extends FloraPlantBlock {
	// ---- tuning ----
	/** Piles eaten before it fruits. */
	public static final int FULL = 3;
	/** A random tick eats a pile beside it with 1 chance in this many; a cluster seeds with 1 chance in this many. */
	public static final int EAT_CHANCE = 4, SEED_CHANCE = 6;
	/** A seed lands within this far, and not with this many rotshrooms already within {@link #CROWD_RANGE}. */
	public static final int SEED_RANGE = 2, CROWD = 4, CROWD_RANGE = 4;
	/** Rotshrooms a picked cluster gives. */
	public static final int PICKED = 2;

	public static final IntegerProperty FED = IntegerProperty.create("fed", 0, FULL);

	public RotshroomBlock(VoxelShape shape, BlockBehaviour.Properties properties) {
		super(shape, true, properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FED, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FED);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int fed = state.getValue(FED);
		if (fed < FULL) {
			BlockPos pile = random.nextInt(EAT_CHANCE) == 0 ? ForestFloor.pileBeside(level, pos) : null;
			if (pile != null) {
				ForestFloor.spend(level, pile);
				level.setBlock(pos, state.setValue(FED, fed + 1), Block.UPDATE_CLIENTS);
			}
			return;
		}
		if (random.nextInt(SEED_CHANCE) != 0 || ForestFloor.near(level, pos, CROWD_RANGE, RotshroomBlock.class) >= CROWD) {
			return;
		}
		BlockPos spot = pos.offset(random.nextInt(SEED_RANGE * 2 + 1) - SEED_RANGE, random.nextInt(3) - 1, random.nextInt(SEED_RANGE * 2 + 1) - SEED_RANGE);
		BlockState young = this.defaultBlockState();
		if (level.getBlockState(spot).isAir() && level.getMaxLocalRawBrightness(spot) < ForestFloor.FUNGUS_LIGHT && young.canSurvive(level, spot)) {
			level.setBlockAndUpdate(spot, young);
			level.setBlock(pos, young, Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (state.getValue(FED) < FULL) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			Block.popResource(level, pos, new ItemStack(this, PICKED));
			level.playSound(null, pos, SoundEvents.FUNGUS_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
			level.setBlock(pos, state.setValue(FED, 0), Block.UPDATE_CLIENTS);
		}
		return InteractionResult.SUCCESS;
	}
}
