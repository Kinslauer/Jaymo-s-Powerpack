package com.jaymoskitchen.mixin;

import com.jaymoskitchen.climate.ClimateGrowth;
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
 * Crops and saplings grow by the climate ({@link ClimateGrowth}): far from the one a plant likes, part of its random ticks
 * are dropped; in it, now and then one more follows the tick. On the block state, as the slow farms are, so every crop is
 * weighed.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class ClimateGrowthMixin {
	/** Where the tick under way was given one more (null: none), on the server thread. */
	@org.spongepowered.asm.mixin.Unique
	private static BlockPos jaymoskitchen$again;

	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$climate(ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		int growth = ClimateGrowth.growth((BlockState) (Object) this, level, pos, random);
		jaymoskitchen$again = growth > 0 ? pos.immutable() : null;
		if (growth < 0) {
			ci.cancel();
		}
	}

	@Inject(method = "randomTick", at = @At("TAIL"))
	private void jaymoskitchen$climateAgain(ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (pos.equals(jaymoskitchen$again)) {
			jaymoskitchen$again = null;
			ClimateGrowth.again((BlockState) (Object) this, level, pos, random);
		}
	}
}
