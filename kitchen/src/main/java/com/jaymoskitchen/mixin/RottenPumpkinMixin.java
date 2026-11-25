package com.jaymoskitchen.mixin;

import com.jaymoskitchen.farmwater.RottenPumpkins;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After a vine's random tick: a pumpkin it has just set may be rotten ({@link RottenPumpkins}). */
@Mixin(StemBlock.class)
abstract class RottenPumpkinMixin {
	@Inject(method = "randomTick", at = @At("TAIL"))
	private void jaymoskitchen$rotten(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		RottenPumpkins.ticked(level, pos, state, random);
	}
}
