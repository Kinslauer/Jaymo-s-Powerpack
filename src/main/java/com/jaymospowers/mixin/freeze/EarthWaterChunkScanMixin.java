package com.jaymospowers.mixin.freeze;

import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Earth &amp; Water (potatowolfie, all rights reserved; shipped unchanged, nothing of it copied) looks at every block of
 * every chunk as it loads, to schedule a tick for five of its blocks. In fresh land that is 16 x 16 x height reads per
 * chunk: 110 to 204 samples in each of three freezes on the Steam Deck (2026-11-18). Its scan is skipped for a chunk
 * none of whose sections' palettes hold a block of that mod at all; where one does, the scan runs as it always did.
 * Skipped when Earth &amp; Water isn't there.
 */
@Pseudo
@Mixin(targets = "potatowolfie.earth_and_water.EarthWater", remap = false)
abstract class EarthWaterChunkScanMixin {
	private static final String NAMESPACE = "earth-and-water";
	private static final Predicate<BlockState> IS_THEIRS = state -> NAMESPACE.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace());

	@Inject(method = "scheduleBlockTicksForChunk", at = @At("HEAD"), cancellable = true, require = 0)
	private void jaymospowers$skipChunksWithoutTheirBlocks(ServerLevel level, LevelChunk chunk, CallbackInfo ci) {
		for (LevelChunkSection section : chunk.getSections()) {
			if (section.maybeHas(IS_THEIRS)) {
				return;
			}
		}
		ci.cancel();
	}
}
