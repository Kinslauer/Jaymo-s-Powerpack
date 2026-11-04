package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Lanterns;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** (0.7.538) Lanterns (and soul lanterns) take random ticks, on which they burn through their fuel ({@link Lanterns}). */
@Mixin(LanternBlock.class)
abstract class LanternBlockMixin extends Block {
	protected LanternBlockMixin(Properties properties) {
		super(properties);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return Lanterns.burns(state) || super.isRandomlyTicking(state);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (Lanterns.burns(state)) {
			Lanterns.randomTick(state, level, pos);
		} else {
			super.randomTick(state, level, pos, random);
		}
	}
}
