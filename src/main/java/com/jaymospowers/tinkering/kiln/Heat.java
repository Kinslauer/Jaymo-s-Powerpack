package com.jaymospowers.tinkering.kiln;

import com.jaymospowers.tinkering.forge.BellowsBlock;
import com.jaymospowers.tinkering.forge.ForgeHearthBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How hot a block burns, for the brick kiln and anything else that cooks over a fire (after Better With Mods' heat
 * registry, Copyright (c) 2016-2018 BeetoGuy, primetoxinz, BordListian, MIT). A lit campfire or fire is 1; a soul
 * campfire, soul fire, lava, magma and a lit forge hearth are 2. Bellows blowing on it add {@link BellowsBlock#HEAT_BONUS}.
 */
public final class Heat {
	public static final int CAMPFIRE = 1, FIRE = 1, SOUL = 2, LAVA = 2, MAGMA = 2, HEARTH = 2;

	private Heat() {
	}

	/** The heat at {@code pos}, bellows included (0: no fire there). */
	public static int at(Level level, BlockPos pos) {
		int heat = of(level.getBlockState(pos));
		return heat > 0 && BellowsBlock.blowing(level, pos) ? heat + BellowsBlock.HEAT_BONUS : heat;
	}

	/** The heat a block gives by itself (0: none, or not lit). */
	public static int of(BlockState state) {
		if (state.is(Blocks.CAMPFIRE)) {
			return state.getValue(CampfireBlock.LIT) ? CAMPFIRE : 0;
		}
		if (state.is(Blocks.SOUL_CAMPFIRE)) {
			return state.getValue(CampfireBlock.LIT) ? SOUL : 0;
		}
		if (state.is(Blocks.FIRE)) {
			return FIRE;
		}
		if (state.is(Blocks.SOUL_FIRE)) {
			return SOUL;
		}
		if (state.is(Blocks.MAGMA_BLOCK)) {
			return MAGMA;
		}
		if (state.getBlock() instanceof ForgeHearthBlock) {
			return state.getValue(ForgeHearthBlock.LIT) ? HEARTH : 0;
		}
		return state.getFluidState().is(FluidTags.LAVA) ? LAVA : 0;
	}

	/** Whether a block could heat a kiln once lit (a cold campfire or hearth counts: the kiln is built before the fire). */
	public static boolean source(BlockState state) {
		return of(state) > 0 || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE) || state.getBlock() instanceof ForgeHearthBlock;
	}
}
