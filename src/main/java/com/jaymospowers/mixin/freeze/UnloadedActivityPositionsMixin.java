package com.jaymospowers.mixin.freeze;

import com.jaymospowers.core.IndexedPositions;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Unloaded Activity (Moono, shipped unchanged, nothing of it copied) keeps, per chunk and per group (water freezing,
 * oxidizing, grass growing), a list of the positions of every block of the group, and asks that list
 * {@code contains} on every block change. In fresh land a chunk of ocean has thousands of water blocks, so each change
 * walked the whole list: 158 samples of one 22 s freeze on the Steam Deck (2026-11-18). The list it hands out is now
 * an {@link IndexedPositions}, which answers {@code contains} from a set. Skipped when Unloaded Activity isn't there.
 * <p>
 * The list is swapped where it is set (made, or replaced on load), not where it is read: chunk saving reads it on a
 * worker thread, and a swap there could race the server thread's changes to it.
 */
@Pseudo
@Mixin(targets = "dev.moono.unloadedactivity.GroupChunkIndex", remap = false)
abstract class UnloadedActivityPositionsMixin {
	@Shadow
	private ArrayList<Long> positions;

	@Inject(method = {"<init>", "setPositions(Ljava/util/ArrayList;)V"}, at = @At("RETURN"), require = 0)
	private void jaymospowers$indexed(CallbackInfo ci) {
		if (this.positions != null && !(this.positions instanceof IndexedPositions)) {
			this.positions = new IndexedPositions(this.positions);
		}
	}
}
