package com.jaymospowers.alchemy.flux;

import com.jaymospowers.alchemy.Flux;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blighted Soil: dirt and grass that flux has soured to a grey-violet crust. It creeps, slowly, onto touching dirt and
 * grass for as long as its chunk still holds flux over {@link FluxEffects#BLIGHT_AT}; once the flux is drained it stays
 * where it is (only Purifying Salts or a shovel remove it), so a cleansed chunk is left scarred but no longer growing.
 */
public class BlightedSoilBlock extends Block {
	/** One random tick in this many tries to spread. */
	public static final int SPREAD_ODDS = 4;

	public BlightedSoilBlock(Block.Properties properties) {
		super(properties);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (random.nextInt(SPREAD_ODDS) != 0 || Flux.get(level, pos) < FluxEffects.BLIGHT_AT) {
			return;
		}
		BlockPos target = pos.offset(random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1);
		if (level.hasChunkAt(target) && FluxEffects.soil(level.getBlockState(target)) && !level.getBlockState(target.above()).isSolid()) {
			level.setBlockAndUpdate(target, this.defaultBlockState());
		}
	}
}
