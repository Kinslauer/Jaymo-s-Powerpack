package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Lanterns;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** (0.7.541) Lanterns take random ticks, on which they burn through their fuel, and set down from an item burn on what it carried ({@link Lanterns}). */
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

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity by, ItemStack stack) {
		super.setPlacedBy(level, pos, state, by, stack);
		Lanterns.placed(level, pos, state, stack);
	}
}
