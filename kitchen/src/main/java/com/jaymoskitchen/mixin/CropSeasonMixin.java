package com.jaymoskitchen.mixin;

import com.jaymoskitchen.season.SeasonalCrops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.5.6) Crops out of season wait instead of growing, and winter's frost kills them (see {@link SeasonalCrops}). */
@Mixin({CropBlock.class, StemBlock.class, SweetBerryBushBlock.class, CocoaBlock.class, PitcherCropBlock.class})
abstract class CropSeasonMixin {
	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$outOfSeason(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (SeasonalCrops.waits(state, level, pos, random)) {
			ci.cancel();
		}
	}
}
