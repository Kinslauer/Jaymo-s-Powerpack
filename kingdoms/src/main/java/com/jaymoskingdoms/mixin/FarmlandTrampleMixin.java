package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.VillagerFixes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Villagers and golems don't trample farmland ({@link VillagerFixes#light}). */
@Mixin(FarmlandBlock.class)
abstract class FarmlandTrampleMixin {
	@Inject(method = "turnToBaseBlock", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$light(@Nullable Entity sourceEntity, BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
		if (VillagerFixes.light(sourceEntity)) {
			ci.cancel();
		}
	}
}
