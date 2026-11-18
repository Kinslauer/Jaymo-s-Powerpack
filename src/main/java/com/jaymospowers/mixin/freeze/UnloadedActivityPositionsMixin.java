package com.jaymospowers.mixin.freeze;

import com.jaymospowers.core.IndexedPositions;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Unloaded Activity (Moono, shipped unchanged, nothing of it copied) keeps, per chunk and per group (water freezing,
 * oxidizing, grass growing), a list of the positions of every block of the group, and asks that list
 * {@code contains} on every block change. In fresh land a chunk of ocean has thousands of water blocks, so each change
 * walked the whole list: 158 samples of one 22 s freeze on the Steam Deck (2026-11-18). The list it hands out is now
 * an {@link IndexedPositions}, which answers {@code contains} from a set. Skipped when Unloaded Activity isn't there.
 */
@Pseudo
@Mixin(targets = "dev.moono.unloadedactivity.GroupChunkIndex", remap = false)
abstract class UnloadedActivityPositionsMixin {
	@Shadow
	private ArrayList<Long> positions;

	@Inject(method = "getPositions", at = @At("HEAD"), require = 0)
	private void jaymospowers$indexed(CallbackInfoReturnable<ArrayList<Long>> cir) {
		if (this.positions != null && !(this.positions instanceof IndexedPositions)) {
			this.positions = new IndexedPositions(this.positions);
		}
	}
}
