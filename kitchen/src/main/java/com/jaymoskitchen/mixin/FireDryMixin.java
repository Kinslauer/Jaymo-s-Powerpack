package com.jaymoskitchen.mixin;

import com.jaymoskitchen.season.Tropics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fire spreads more readily in the dry season in the hot lands ({@link Tropics#DRY_FIRE}): a flame is likelier to catch
 * a flammable block next to it, vanilla fire's own spread. (Forest Fire's own simulation reads only the weather and
 * isn't touched here.)
 */
@Mixin(FireBlock.class)
abstract class FireDryMixin {
	@Inject(method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"), cancellable = true)
	private void jaymoskitchen$dryFire(LevelReader reader, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		if (cir.getReturnValueI() > 0 && reader instanceof Level level && Tropics.dryAt(level, pos)) {
			cir.setReturnValue(Math.round(cir.getReturnValueI() * Tropics.DRY_FIRE));
		}
	}
}
