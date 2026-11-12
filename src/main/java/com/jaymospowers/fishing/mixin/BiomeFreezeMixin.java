package com.jaymospowers.fishing.mixin;

import com.jaymospowers.fishing.IceFishing;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (Track B leftovers) A hole someone cut in the ice for fishing stays open a while instead of freezing over the next tick. */
@Mixin(Biome.class)
public abstract class BiomeFreezeMixin {
	@Inject(method = "shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Z", at = @At("HEAD"), cancellable = true)
	private void keepHoleOpen(LevelReader level, BlockPos pos, boolean checkNeighbors, CallbackInfoReturnable<Boolean> cir) {
		if (level instanceof ServerLevel server && IceFishing.open(server, pos)) {
			cir.setReturnValue(false);
		}
	}
}
