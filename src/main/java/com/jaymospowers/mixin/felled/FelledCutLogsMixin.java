package com.jaymospowers.mixin.felled;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Felled (all rights reserved, Raiiiden; shipped unchanged, nothing of it copied) notes every log placed or taken away in
 * a list it keeps per world ({@code CutLogs}), from a hook on every block change. World generation places blocks too, on
 * several worker threads at once (C2ME), and that list isn't made for more than one thread: they broke it under each
 * other. On the Steam Deck, with Chunky pre-generating a new world, that was "Index -1 out of bounds" in its set, then
 * the server down (crash 2026-11-13 20:17), and twice before an autosave copying the broken list till the heap ran out
 * (18:22, 19:03). The note is only taken on the server's own thread now: blocks world generation places aren't logs
 * anybody cut. Skipped when Felled isn't there.
 */
@Pseudo
@Mixin(targets = "com.raiiiden.felled.tree.CutLogs", remap = false)
abstract class FelledCutLogsMixin {
	@Inject(method = "onBlockChanged", at = @At("HEAD"), cancellable = true, require = 0)
	private static void jaymospowers$serverThreadOnly(ServerLevel level, BlockPos pos, BlockState before, BlockState after, CallbackInfo ci) {
		if (!level.getServer().isSameThread()) {
			ci.cancel();
		}
	}
}
