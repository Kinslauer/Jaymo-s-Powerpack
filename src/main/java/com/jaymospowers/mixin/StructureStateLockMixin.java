package com.jaymospowers.mixin;

import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.21.9) Server froze, twice (2026-11-04 20:16 and 2026-11-05 15:00): every C2ME worker spun forever in
 * {@code Object2ObjectOpenHashMap.get} inside {@code ChunkGeneratorStructureState.generatePositions}, their chunks stuck
 * at structure_starts, so whatever then waited on one of those chunks (a start being placed, a scan) hung till the
 * watchdog stopped the server. Vanilla fills a generator's structure positions once, the first time
 * {@code ensureStructuresGenerated} is called, with an unlocked check-fill-flag; vanilla's own generator has it filled
 * up front, but One Dimension (CustomChunks' StackedChunkGenerator) keeps a generator per layer whose state fills
 * lazily, and C2ME's workers reached it together and filled the same hash map at once, which corrupts it. Here only one
 * thread fills it and the others wait for it; afterwards the maps are only read. Same steps as vanilla, in a lock.
 */
@Mixin(ChunkGeneratorStructureState.class)
public abstract class StructureStateLockMixin {
	@Shadow
	private boolean hasGeneratedPositions;

	@Shadow
	private void generatePositions() {
		throw new AssertionError();
	}

	@Inject(method = "ensureStructuresGenerated", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$fillOnce(CallbackInfo ci) {
		synchronized (this) {
			if (!this.hasGeneratedPositions) {
				generatePositions();
				this.hasGeneratedPositions = true;
			}
		}
		ci.cancel();
	}
}
