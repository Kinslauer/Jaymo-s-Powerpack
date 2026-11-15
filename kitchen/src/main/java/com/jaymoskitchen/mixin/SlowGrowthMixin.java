package com.jaymoskitchen.mixin;

import com.jaymoskitchen.farm.SlowFarms;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Slow farms ({@link SlowFarms}): a plant in {@code #jaymoskitchen:slow_growing} drops part of its random ticks. Here, on
 * the block state, so crops with their own random tick (the Kitchen's rice and tomatoes) are slowed as well.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class SlowGrowthMixin {
	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$slowGrowth(ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (SlowFarms.slows((BlockState) (Object) this, random)) {
			ci.cancel();
		}
	}
}
