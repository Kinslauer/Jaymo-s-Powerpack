package com.jaymospowers.mixin.endertech;

import com.jaymospowers.core.Loaded;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ForgeEndertech (CC-BY-ND, the library under Advanced Chimneys; shipped unchanged, nothing of it copied) asks
 * {@code GameWorld.isBlockLoaded} before its block searches (a chimney's pipes, the blocks around a tile) step onto a
 * block. It asks the game's {@code hasChunk}, which in 26.3 is also true of a chunk only scheduled, so a search along a
 * chimney at the edge of the loaded land read blocks the server thread then waited to have made (about 100 profile
 * samples of the 2026-11-18 freezes). Only a chunk that is made and loaded counts now, so the search stops at the edge.
 * Skipped when the mod isn't there.
 */
@Pseudo
@Mixin(targets = "com.endertech.minecraft.forge.world.GameWorld", remap = false)
abstract class WorldLoadedMixin {
	@Inject(method = "isBlockLoaded", at = @At("RETURN"), cancellable = true, require = 0)
	private static void jaymospowers$madeAndLoaded(LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && !Loaded.made(level, pos)) {
			cir.setReturnValue(false);
		}
	}
}
