package com.jaymoskitchen.delight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * (Farmer's Delight's organic compost, MIT; their art, our own code.) A heap that rots down into rich soil over time,
 * quicker with sun, water and mushrooms (the {@code compost_activators} tag) around it.
 */
public class OrganicCompostBlock extends Block {
	public static final IntegerProperty COMPOSTING = IntegerProperty.create("composting", 0, 7);
	/** Each step of the rot: a random tick's chance is the base for open sky (or less in shade), plus these. */
	private static final float CHANCE_PER_ACTIVATOR = 0.02F, CHANCE_SUNNY = 0.1F, CHANCE_SHADED = 0.05F, CHANCE_WET = 0.1F;
	/** Sky light at or above this counts as sun on the heap. */
	private static final int SUNNY_LIGHT = 12;
	private static final int MAX_STAGE = 7;

	public OrganicCompostBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(COMPOSTING, 0));
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(COMPOSTING);
		super.createBlockStateDefinition(builder);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		float chance = 0.0F;
		boolean hasWater = false;
		int maxLight = 0;
		for (BlockPos neighborPos : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
			BlockState neighbor = level.getBlockState(neighborPos);
			if (neighbor.is(DelightFarm.COMPOST_ACTIVATORS)) {
				chance += CHANCE_PER_ACTIVATOR;
			}
			if (neighbor.getFluidState().is(FluidTags.WATER)) {
				hasWater = true;
			}
			maxLight = Math.max(maxLight, level.getBrightness(LightLayer.SKY, neighborPos.above()));
		}
		chance += maxLight > SUNNY_LIGHT ? CHANCE_SUNNY : CHANCE_SHADED;
		chance += hasWater ? CHANCE_WET : 0.0F;
		if (random.nextFloat() <= chance) {
			if (state.getValue(COMPOSTING) == MAX_STAGE) {
				level.setBlock(pos, DelightFarm.RICH_SOIL.defaultBlockState(), Block.UPDATE_ALL);
			} else {
				level.setBlock(pos, state.setValue(COMPOSTING, state.getValue(COMPOSTING) + 1), Block.UPDATE_ALL);
			}
		}
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return MAX_STAGE + 1 - state.getValue(COMPOSTING);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		super.animateTick(state, level, pos, random);
		if (random.nextInt(10) == 0) {
			level.addParticle(ParticleTypes.MYCELIUM, pos.getX() + random.nextFloat(), pos.getY() + 1.1D, pos.getZ() + random.nextFloat(), 0.0D, 0.0D, 0.0D);
		}
	}
}
